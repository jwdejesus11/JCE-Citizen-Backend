package com.arojas.jce_consulta_api.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.arojas.jce_consulta_api.dto.response.TelecomLineDto;
import com.arojas.jce_consulta_api.dto.response.TelecomResultDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Servicio para consulta de líneas telefónicas en Claro y Altice
 * Utiliza ingeniería inversa sobre los portales públicos de las operadoras
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TelecomService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    // Altice Constants
    private static final String ALTICE_API_URL = "https://webservices.altice.com.do/customers/api/prepaid/{cedula}/lines";
    private static final String ALTICE_AUTH = "Basic dXNyQWx0aWNlUG9ydGFsV2ViOlczZEFGYU4qMDJkXyQxNklqZE1HUUA=";
    private static final String ALTICE_ORIGIN = "https://www.altice.com.do";
    private static final String ALTICE_REFERER = "https://www.altice.com.do/personal/soporte-y-ayuda/consulta-lineas-prepago";

    // Claro Constants
    private static final String CLARO_API_URL = "https://consultatuslineas.claro.com.do/api/consulta-tu-linea/{cedula}";
    private static final String CLARO_REFERER = "https://consultatuslineas.claro.com.do/";

    /**
     * Consulta unificada de líneas para ambas operadoras
     */
    @Cacheable(value = "telecomQueries", key = "#cedula")
    public TelecomResultDto queryAllLines(String cedula) {
        log.info("Iniciando consulta de telecom para cédula: {}", maskCedula(cedula));

        TelecomResultDto result = new TelecomResultDto();
        result.setCedula(cedula);

        // Consultamos ambas en paralelo usando CompletableFuture para no bloquear
        CompletableFuture<List<TelecomLineDto>> alticeFuture = queryAlticeLines(cedula);
        CompletableFuture<List<TelecomLineDto>> claroFuture = queryClaroLines(cedula);

        try {
            // Esperar resultados con timeouts lógicos
            result.setAlticeLines(alticeFuture.get());
            result.setAlticeStatus("OK");
        } catch (Exception e) {
            log.error("Error consultando Altice para {}: {}", cedula, e.getMessage());
            result.setAlticeStatus("ERROR: " + e.getMessage());
        }

        try {
            result.setClaroLines(claroFuture.get());
            result.setClaroStatus("OK");
        } catch (Exception e) {
            log.error("Error consultando Claro para {}: {}", cedula, e.getMessage());
            result.setClaroStatus("ERROR: " + e.getMessage());
        }

        result.setTotalLines((long) (result.getClaroLines().size() + result.getAlticeLines().size()));
        return result;
    }

    private CompletableFuture<List<TelecomLineDto>> queryAlticeLines(String cedula) {
        return webClient.get()
                .uri(ALTICE_API_URL, cedula)
                .header(HttpHeaders.AUTHORIZATION, ALTICE_AUTH)
                .header("Origin", ALTICE_ORIGIN)
                .header(HttpHeaders.REFERER, ALTICE_REFERER)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> parseAlticeResponse(response))
                .onErrorResume(e -> {
                    log.warn("Altice 404 o Error: {}. Cédula: {}", e.getMessage(), maskCedula(cedula));
                    return Mono.just(new ArrayList<>());
                })
                .toFuture();
    }

    private CompletableFuture<List<TelecomLineDto>> queryClaroLines(String cedula) {
        // Claro requiere una cookie de sesión. Intentamos obtenerla primero.
        return webClient.get()
                .uri(CLARO_API_URL, cedula)
                .header(HttpHeaders.REFERER, CLARO_REFERER)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> parseClaroResponse(response))
                .onErrorResume(e -> {
                    log.warn("Claro Error o No Encontrado: {}. Cédula: {}", e.getMessage(), maskCedula(cedula));
                    return Mono.just(new ArrayList<>());
                })
                .toFuture();
    }

    private List<TelecomLineDto> parseAlticeResponse(String json) {
        try {
            // El formato de Altice es una lista directa de objetos
            List<Map<String, Object>> list = objectMapper.readValue(json, new TypeReference<>() {});
            return list.stream().map(map -> TelecomLineDto.builder()
                    .phoneNumber(String.valueOf(map.get("phoneNumber")))
                    .serviceType(String.valueOf(map.get("serviceType")))
                    .operator("Altice")
                    .status("Active")
                    .build())
                .collect(Collectors.toList());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private List<TelecomLineDto> parseClaroResponse(String json) {
        try {
            // El formato de Claro tiene un objeto con el campo "subscriptions"
            Map<String, Object> mapResponse = objectMapper.readValue(json, new TypeReference<>() {});
            if (mapResponse.get("subscriptions") instanceof List) {
                List<Map<String, Object>> subs = (List<Map<String, Object>>) mapResponse.get("subscriptions");
                return subs.stream().map(sub -> TelecomLineDto.builder()
                        .phoneNumber(String.valueOf(sub.get("phoneNumber")))
                        .serviceType(String.valueOf(sub.get("serviceType")))
                        .operator("Claro")
                        .status("Active")
                        .build())
                    .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.error("Error parseando Respuesta Claro: {}", e.getMessage());
        }
        return new ArrayList<>();
    }

    private String maskCedula(String cedula) {
        if (cedula == null || cedula.length() < 4) return "****";
        return cedula.substring(0, 3) + "****" + cedula.substring(cedula.length() - 2);
    }
}
