/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package com.arojas.jce_consulta_api.security;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * @author arojas
 *         Configuración de seguridad con rutas organizadas y permisos
 *         granulares
 */
@Configuration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true)
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtAuthFilter;
	private final UserDetailsService userDetailsService;

	@Value("${app.security.cors.allowed-origins}")
	private String[] allowedOrigins;

	public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter, UserDetailsService userDetailsService) {
		this.jwtAuthFilter = jwtAuthFilter;
		this.userDetailsService = userDetailsService;
	}

	/**
	 * Configuración principal de la cadena de filtros de seguridad
	 */
	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
				.csrf(AbstractHttpConfigurer::disable)
				.cors(cors -> cors.configurationSource(corsConfigurationSource()))
				.authorizeHttpRequests(authz -> authz
						// ============= CONFIGURACIONES PÚBLICAS (Sin Autenticación) =============
						.requestMatchers(HttpMethod.GET, "/api/v1/settings/public").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/settings/features").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/settings/testimonials").permitAll()

						// ============= RUTAS PÚBLICAS (Sin Autenticación) =============
						.requestMatchers(getPublicEndpoints()).permitAll()

						// ============= RUTAS DE DOCUMENTACIÓN =============
						.requestMatchers(getDocumentationEndpoints()).permitAll()

						// ============= RUTAS DE MONITOREO =============
						.requestMatchers(getMonitoringEndpoints()).permitAll()

						// ============= RUTAS ADMINISTRATIVAS (Solo ADMIN) =============
						.requestMatchers(getAdminEndpoints()).hasRole("ADMIN")

						// ============= RUTAS DE LOGS (ADMIN y USER con permisos) =============
						.requestMatchers(HttpMethod.GET, "/api/v1/logs/**").hasAnyRole("ADMIN", "USER")
						.requestMatchers(HttpMethod.POST, "/api/v1/logs/create").hasRole("ADMIN")
						.requestMatchers(HttpMethod.POST, "/api/v1/logs/cleanup/**").hasRole("ADMIN")

						// ============= RUTAS DE USUARIO (Autenticado) =============
						.requestMatchers(getUserEndpoints()).hasAnyRole("USER", "ADMIN")

						// ============= RUTAS DE CONSULTA JCE (Requiere Tokens) =============
						.requestMatchers("/api/v1/cedula/**").hasAnyRole("USER", "ADMIN")
						.requestMatchers("/api/v1/query/**").hasAnyRole("USER", "ADMIN")
						.requestMatchers("/api/v1/cedula-queries/**").hasAnyRole("USER", "ADMIN")
						.requestMatchers("/api/v1/telecom/**").hasAnyRole("USER", "ADMIN")


						// ============= RUTAS DE PAGOS (Usuario autenticado) =============
						.requestMatchers(HttpMethod.POST, "/api/v1/payments/**").hasAnyRole("USER", "ADMIN")
						.requestMatchers(HttpMethod.GET, "/api/v1/payments/my-payments").hasAnyRole("USER", "ADMIN")

						// ============= CONFIGURACIONES (Escritura admin)
						// =============
						.requestMatchers(HttpMethod.POST, "/api/v1/settings/**").hasRole("ADMIN")
						.requestMatchers(HttpMethod.PUT, "/api/v1/settings/**").hasRole("ADMIN")
						.requestMatchers(HttpMethod.DELETE, "/api/v1/settings/**").hasRole("ADMIN")

						// ============= CUALQUIER OTRA RUTA (Requiere autenticación) =============
						.anyRequest().authenticated())

				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authenticationProvider(authenticationProvider())
				.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
				.build();
	}

	/**
	 * Rutas públicas que no requieren autenticación
	 */
	private String[] getPublicEndpoints() {
		return new String[] {
				// Autenticación (Solo estos son 100% públicos)
				"/api/v1/auth/login",
				"/api/v1/auth/register",
				"/api/v1/auth/refresh",

				// Rutas públicas generales
				"/api/v1/public/**",

				// Health check básico
				"/api/v1/health",


				// Información de la aplicación
				"/api/v1/info",

				// Webhooks de pagos (para servicios externos)
				"/api/v1/webhooks/**",

				// Página de inicio o landing
				"/",
				"/index.html",
				"/favicon.ico",

				// Recursos estáticos (si los tienes)
				"/static/**",
				"/assets/**",
				"/images/**",
				"/css/**",
				"/js/**"
		};
	}

	/**
	 * Rutas de documentación de API
	 */
	private String[] getDocumentationEndpoints() {
		return new String[] {
				// OpenAPI/Swagger
				"/api-docs/**",
				"/swagger-ui/**",
				"/swagger-ui.html",
				"/swagger-resources/**",
				"/webjars/**",

				// Documentación personalizada
				"/docs/**",
				"/documentation/**",

				// Schema de la API
				"/v3/api-docs/**"
		};
	}

	/**
	 * Rutas de monitoreo y métricas
	 */
	private String[] getMonitoringEndpoints() {
		return new String[] {
				// Actuator endpoints
				"/actuator/health",
				"/actuator/health/**",
				"/actuator/info",
				"/actuator/metrics",
				"/actuator/prometheus",
				"/actuator/env",

				// Métricas personalizadas públicas
				"/api/v1/metrics/public/**"
		};
	}

	/**
	 * Rutas administrativas (solo ADMIN)
	 */
	private String[] getAdminEndpoints() {
		return new String[] {
				// Gestión de usuarios
				"/api/v1/admin/**",

				// Gestión de tokens
				"/api/v1/admin/tokens/**",

				// Configuraciones del sistema
				"/api/v1/admin/settings/**",

				// Métricas completas del sistema
				"/api/v1/admin/metrics/**",

				// Gestión de plantillas de email
				"/api/v1/admin/email-templates/**",

				// Endpoints completos de actuator (para admin)
				"/actuator/**"
		};
	}

	/**
	 * Rutas de usuario autenticado
	 */
	private String[] getUserEndpoints() {
		return new String[] {
				// Perfil de usuario
				"/api/v1/user/profile",
				"/api/v1/user/profile/**",

				// Tokens del usuario
				"/api/v1/user/tokens",
				"/api/v1/user/tokens/**",

				// Historial de consultas
				"/api/v1/user/history",
				"/api/v1/user/history/**",

				// Configuraciones personales
				"/api/v1/user/settings",
				"/api/v1/user/preferences"
		};
	}

	/**
	 * Configuración de CORS mejorada
	 */
	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		var configuration = new CorsConfiguration();

		// Orígenes permitidos (desde configuración)
		configuration.setAllowedOriginPatterns(List.of(allowedOrigins));

		// Métodos HTTP permitidos
		configuration.setAllowedMethods(List.of(
				"GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD"));

		// Headers permitidos
		configuration.setAllowedHeaders(List.of(
				"Authorization",
				"Content-Type",
				"Accept",
				"X-Requested-With",
				"X-Request-ID",
				"X-Correlation-ID",
				"Cache-Control"));

		// Headers expuestos al cliente
		configuration.setExposedHeaders(List.of(
				"X-Request-ID",
				"X-Correlation-ID",
				"X-Rate-Limit-Remaining",
				"X-Rate-Limit-Reset"));

		configuration.setAllowCredentials(true);
		configuration.setMaxAge(3600L);

		var source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

	@Bean
	public AuthenticationProvider authenticationProvider() {
		var authProvider = new DaoAuthenticationProvider();
		authProvider.setUserDetailsService(userDetailsService);
		authProvider.setPasswordEncoder(passwordEncoder());
		return authProvider;
	}

	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
		return config.getAuthenticationManager();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(12); // Aumentado el strength para mayor seguridad
	}
}