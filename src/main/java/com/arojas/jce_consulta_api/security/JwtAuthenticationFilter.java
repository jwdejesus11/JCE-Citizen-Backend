/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package com.arojas.jce_consulta_api.security;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.arojas.jce_consulta_api.entity.LogEntry.LogLevel;
import com.arojas.jce_consulta_api.service.DbLoggerService;
import com.arojas.jce_consulta_api.service.JwtService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * @author arojas
 *         Filtro JWT mejorado con logging detallado y manejo de errores
 */
@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtService jwtService;
	private final UserDetailsService userDetailsService;
	private final DbLoggerService dbLoggerService;

	public JwtAuthenticationFilter(JwtService jwtService,
			UserDetailsService userDetailsService,
			DbLoggerService dbLoggerService) {
		this.jwtService = jwtService;
		this.userDetailsService = userDetailsService;
		this.dbLoggerService = dbLoggerService;
	}

	@Override
	protected void doFilterInternal(
			@NonNull HttpServletRequest request,
			@NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain) throws ServletException, IOException {

		// Configurar contexto de trazabilidad
		setupTraceContext(request, response);

		try {
			// Procesar autenticación JWT
			processJwtAuthentication(request);
		} catch (Exception e) {
			log.warn("Error durante la autenticación JWT: {}", e.getMessage());
			logAuthenticationError(request, e);
			// No interrumpimos el flujo, dejamos que continúe sin autenticación
		}

		try {
			filterChain.doFilter(request, response);
		} finally {
			// Limpiar MDC al final del request
			MDC.clear();
		}
	}

	/**
	 * Procesa la autenticación JWT
	 */
	private void processJwtAuthentication(HttpServletRequest request) {
		String authHeader = request.getHeader("Authorization");

		// Verificar si el header de autorización existe y es válido
		if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
			logNoAuthHeader(request);
			return;
		}

		String jwt = authHeader.substring(7).trim(); // Limpiar espacios accidentales
		if (!StringUtils.hasText(jwt)) {
			logEmptyToken(request);
			return;
		}

		try {
			String username = jwtService.extractUsername(jwt);

			if (StringUtils.hasText(username) && SecurityContextHolder.getContext().getAuthentication() == null) {
				authenticateUser(request, jwt, username);
			}
		} catch (Exception e) {
			log.debug("Error extrayendo información del JWT: {}", e.getMessage());
			logInvalidToken(request, e);
		}
	}

	/**
	 * Autentica al usuario basado en el JWT
	 */
	private void authenticateUser(HttpServletRequest request, String jwt, String username) {
		try {
			var userDetails = userDetailsService.loadUserByUsername(username);

			if (jwtService.isTokenValid(jwt, userDetails)) {
				// Crear token de autenticación
				var authToken = new UsernamePasswordAuthenticationToken(
						userDetails, null, userDetails.getAuthorities());
				authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

				// Establecer autenticación en el contexto
				SecurityContextHolder.getContext().setAuthentication(authToken);

				// Actualizar MDC con información del usuario
				MDC.put("userEmail", username);
				MDC.put("userRoles", userDetails.getAuthorities().toString());

				logSuccessfulAuthentication(request, username);
			} else {
				logInvalidToken(request, new RuntimeException("Token inválido o expirado"));
			}
		} catch (IllegalArgumentException | UsernameNotFoundException e) {
			log.debug("Error autenticando usuario {}: {}", username, e.getMessage());
			logAuthenticationError(request, e);
		}
	}

	/**
	 * Configura el contexto de trazabilidad para el request
	 */
	private void setupTraceContext(HttpServletRequest request, HttpServletResponse response) {
		// Correlation ID para trazar requests
		String correlationId = request.getHeader("X-Correlation-ID");
		if (!StringUtils.hasText(correlationId)) {
			correlationId = UUID.randomUUID().toString().substring(0, 8);
		}

		// Request ID único
		String requestId = UUID.randomUUID().toString().substring(0, 8);

		// Session ID si existe
		String sessionId = request.getSession(false) != null ? request.getSession().getId() : null;

		// Configurar MDC
		MDC.put("correlationId", correlationId);
		MDC.put("requestId", requestId);
		MDC.put("requestURI", request.getRequestURI());
		MDC.put("requestMethod", request.getMethod());

		if (StringUtils.hasText(sessionId)) {
			MDC.put("sessionId", sessionId);
		}

		// Agregar headers de trazabilidad a la respuesta
		response.setHeader("X-Correlation-ID", correlationId);
		response.setHeader("X-Request-ID", requestId);
	}

	/**
	 * Verifica si el endpoint requiere autenticación
	 */
	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI();

		// Lista de paths que no requieren filtrado JWT
		String[] excludedPaths = {
				"/api/v1/auth/login",
				"/api/v1/auth/register",
				"/api/v1/auth/refresh",
				"/api/v1/settings/public",
				"/api/v1/public/",
				"/actuator/health",
				"/actuator/info",
				"/api-docs",
				"/v3/api-docs",
				"/swagger-ui",
				"/swagger-resources",
				"/webjars"
		};

		// Validar si el path contiene alguna de las palabras clave de exclusión
		for (String excludedPath : excludedPaths) {
			if (path.contains(excludedPath)) {
				return true;
			}
		}

		// Especial: configuraciones públicas
		if (path.contains("/settings/public") || path.contains("/api/v1/public")) {
			return true;
		}

		return false;
	}

	// ============= MÉTODOS DE LOGGING =============

	private void logNoAuthHeader(HttpServletRequest request) {
		dbLoggerService.log()
				.level(LogLevel.DEBUG)
				.source("JwtAuthenticationFilter")
				.operation("NO_AUTH_HEADER")
				.message("Request sin header de autorización")
				.withRequest(request)
				.context("path", request.getRequestURI())
				.context("method", request.getMethod())
				.save();
	}

	private void logEmptyToken(HttpServletRequest request) {
		dbLoggerService.log()
				.level(LogLevel.WARN)
				.source("JwtAuthenticationFilter")
				.operation("EMPTY_TOKEN")
				.message("Token JWT vacío en header Authorization")
				.withRequest(request)
				.context("path", request.getRequestURI())
				.save();
	}

	private void logInvalidToken(HttpServletRequest request, Exception e) {
		dbLoggerService.log()
				.level(LogLevel.WARN)
				.source("JwtAuthenticationFilter")
				.operation("INVALID_TOKEN")
				.message("Token JWT inválido o expirado")
				.withRequest(request)
				.exception(e)
				.context("path", request.getRequestURI())
				.context("errorType", e.getClass().getSimpleName())
				.save();
	}

	private void logSuccessfulAuthentication(HttpServletRequest request, String username) {
		dbLoggerService.log()
				.level(LogLevel.DEBUG)
				.source("JwtAuthenticationFilter")
				.operation("JWT_AUTH_SUCCESS")
				.message("Autenticación JWT exitosa")
				.user(username)
				.withRequest(request)
				.context("path", request.getRequestURI())
				.context("method", request.getMethod())
				.context("authenticatedUser", username)
				.save();
	}

	private void logAuthenticationError(HttpServletRequest request, Exception e) {
		dbLoggerService.log()
				.level(LogLevel.ERROR)
				.source("JwtAuthenticationFilter")
				.operation("JWT_AUTH_ERROR")
				.message("Error durante autenticación JWT")
				.withRequest(request)
				.exception(e)
				.context("path", request.getRequestURI())
				.context("method", request.getMethod())
				.save();
	}
}