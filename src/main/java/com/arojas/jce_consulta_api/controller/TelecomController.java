package com.arojas.jce_consulta_api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.arojas.jce_consulta_api.dto.response.ApiResponse;
import com.arojas.jce_consulta_api.dto.response.TelecomResultDto;
import com.arojas.jce_consulta_api.service.TelecomService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Endpoint para consulta de líneas telefónicas a través de ingeniería inversa
 */
@RestController
@RequestMapping("/api/v1/telecom")
@RequiredArgsConstructor
@Tag(name = "Telecom", description = "Consulta de líneas móviles en Altice y Claro")
@SecurityRequirement(name = "bearerAuth")
@CrossOrigin(origins = "*", maxAge = 3600)
public class TelecomController {

    private final TelecomService telecomService;

    @Operation(summary = "Consultar líneas por cédula", description = "Busca líneas registradas en Claro y Altice asociadas a una cédula")
    @GetMapping("/lines/{cedula}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ApiResponse<TelecomResultDto>> getLines(@PathVariable String cedula) {
        TelecomResultDto result = telecomService.queryAllLines(cedula);
        return ResponseEntity.ok(ApiResponse.success(result, "Consulta de telecom completada"));
    }
}
