/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package com.arojas.jce_consulta_api.client;

import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.arojas.jce_consulta_api.config.JceConfigurationProperties;
import feign.Logger;
import feign.Request;
import feign.RequestInterceptor;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author arojas
 *         * Configuración personalizada para el cliente Feign JCE
 *
 */

@Configuration
@RequiredArgsConstructor
@Slf4j
public class JceFeignClientConfiguration {

	private final JceConfigurationProperties jceProperties;

	@Bean
	public RequestInterceptor requestInterceptor() {
		return requestTemplate -> {
			requestTemplate.header("User-Agent",
					"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
			requestTemplate.header("Accept",
					"text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8");
			requestTemplate.header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6");
			requestTemplate.header("Connection", "keep-alive");
			requestTemplate.header("sec-ch-ua",
					"\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\"");
			requestTemplate.header("sec-ch-ua-mobile", "?0");
			requestTemplate.header("sec-ch-ua-platform", "\"Windows\"");
			requestTemplate.header("Upgrade-Insecure-Requests", "1");
			requestTemplate.header("Referer", "https://dataportal.jce.gob.do/");
			requestTemplate.header("Origin", "https://dataportal.jce.gob.do");
		};
	}

	@Bean
	public Request.Options requestOptions() {
		return new Request.Options(
				jceProperties.getTimeout().getConnect(),
				TimeUnit.MILLISECONDS,
				jceProperties.getTimeout().getRead(),
				TimeUnit.MILLISECONDS,
				true);
	}

	@Bean
	public Retryer retryer() {
		return new Retryer.Default(
				jceProperties.getRetry().getBackoffDelay(),
				TimeUnit.SECONDS.toMillis(5),
				jceProperties.getRetry().getMaxAttempts());
	}

	@Bean
	public Logger.Level feignLoggerLevel() {
		return Logger.Level.BASIC;
	}

	@Bean
	public ErrorDecoder errorDecoder() {
		return new JceErrorDecoder();
	}

	/**
	 * Decodificador de errores personalizado para JCE
	 */
	public static class JceErrorDecoder implements ErrorDecoder {
		private final ErrorDecoder defaultErrorDecoder = new Default();

		@Override
		public Exception decode(String methodKey, feign.Response response) {
			log.error("Error en llamada a JCE. Método: {}, Status: {}, Reason: {}",
					methodKey, response.status(), response.reason());

			switch (response.status()) {
				case 400:
					return new JceClientException("Parámetros inválidos en la consulta JCE");
				case 404:
					return new JceClientException("Servicio JCE no encontrado");
				case 409:
					return new JceClientException("Acceso Denegado por JCE (Bot Detection)");
				case 500:
					return new JceClientException("Error interno del servidor JCE");
				case 503:
					return new JceClientException("Servicio JCE temporalmente no disponible");
				default:
					return defaultErrorDecoder.decode(methodKey, response);
			}
		}
	}

	/**
	 * Excepción personalizada para errores del cliente JCE
	 */
	public static class JceClientException extends RuntimeException {
		public JceClientException(String message) {
			super(message);
		}

		public JceClientException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}