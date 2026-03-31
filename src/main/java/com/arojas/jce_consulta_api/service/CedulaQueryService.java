package com.arojas.jce_consulta_api.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.arojas.jce_consulta_api.dto.CedulaQueryDto;
import com.arojas.jce_consulta_api.dto.CedulaResultDto;
import com.arojas.jce_consulta_api.dto.UserDto;
import com.arojas.jce_consulta_api.entity.CedulaQuery;
import com.arojas.jce_consulta_api.entity.CedulaQuery.QueryStatus;
import com.arojas.jce_consulta_api.entity.CedulaResult;
import com.arojas.jce_consulta_api.entity.User;
import com.arojas.jce_consulta_api.exception.query.CedulaQueryExceptions;
import com.arojas.jce_consulta_api.repository.CedulaQueryRepository;
import com.arojas.jce_consulta_api.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Servicio para gestión de consultas de cédulas dominicanas
 * Implementa validaciones, transaccionalidad y manejo robusto de errores
 *
 * @author arojas
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CedulaQueryService {

	// Repositories
	private final CedulaQueryRepository cedulaQueryRepository;
	private final UserRepository userRepository;

	// Services
	private final JceClient jceClient;
	private final UserService userService;
	private final AppSettingsService appSettingsService;

	// Constants
	private static final BigDecimal QUERY_COST = BigDecimal.ONE; // 1 token per query

	// ================= QUERY OPERATIONS =================

	@Async
	public CompletableFuture<CedulaQueryDto> performCedulaQueryAsync(String cedula, String userEmail) {
		log.info("Starting async cedula query: {} for user: {}", cedula, userEmail);
		return CompletableFuture.supplyAsync(() -> {
			try {
				return performCedulaQuery(cedula, userEmail);
			} catch (Exception e) {
				log.error("Error in async cedula query: {}", e.getMessage(), e);
				throw CedulaQueryExceptions.processingError(cedula, e.getMessage(), e);
			}
		});
	}

	public CedulaQueryDto performCedulaQuery(String cedula, String userEmail) {
		log.info("Performing cedula query: {} for user: {}", cedula, userEmail);

		validateCedulaFormat(cedula);
		User user = getUserByEmailOrThrow(userEmail);
		validateUserCanQuery(user);

		CedulaQuery query = createPendingQuery(cedula, user);

		try {
			consumeUserToken(user);
			CedulaResultDto result = queryJceService(cedula);
			updateQueryWithSuccess(query, result);
			log.info("Cedula query completed successfully: {}", cedula);
			return convertToDto(query);

		} catch (Exception e) {
			log.error("Error performing cedula query {}: {}", cedula, e.getMessage());
			refundUserToken(user);
			updateQueryWithError(query, e.getMessage());
			throw CedulaQueryExceptions.processingError(cedula, e.getMessage(), e);
		}
	}

	@Transactional(readOnly = true)
	public boolean canUserQuery(String userEmail) {
		User user = getUserByEmailOrThrow(userEmail);
		return user.getTokens() > 0 && user.getIsActive();
	}

	// ================= HISTORY OPERATIONS =================

	@Transactional(readOnly = true)
	public Page<CedulaQueryDto> getUserQueryHistory(String userEmail, int page, int size,
			String sortBy, String sortDir) {
		User user = getUserByEmailOrThrow(userEmail);
		Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);
		Pageable pageable = PageRequest.of(page, size, sort);
		Page<CedulaQuery> queries = cedulaQueryRepository.findByUserIdOrderByQueryDateDesc(user.getId(), pageable);
		return queries.map(this::convertToDto);
	}

	@Transactional(readOnly = true)
	public CedulaQueryDto getQueryById(String queryId, String userEmail) {
		User user = getUserByEmailOrThrow(userEmail);
		CedulaQuery query = cedulaQueryRepository.findByIdAndUserId(queryId, user.getId())
				.orElseThrow(() -> CedulaQueryExceptions.queryNotFound(queryId));
		return convertToDto(query);
	}

	@Transactional(readOnly = true)
	public List<CedulaQueryDto> getRecentQueries(String userEmail, int limit) {
		User user = getUserByEmailOrThrow(userEmail);
		Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "queryDate"));
		Page<CedulaQuery> page = cedulaQueryRepository.findByUserId(user.getId(), pageable);
		return page.getContent().stream().map(this::convertToDto).toList();
	}

	// ================= SEARCH OPERATIONS =================

	@Transactional(readOnly = true)
	public List<CedulaQueryDto> searchQueriesByCedula(String userEmail, String cedula) {
		User user = getUserByEmailOrThrow(userEmail);
		List<CedulaQuery> queries = cedulaQueryRepository
				.findByUserIdAndCedulaContainingOrderByQueryDateDesc(user.getId(), cedula);
		return queries.stream().map(this::convertToDto).toList();
	}

	// ================= STATS OPERATIONS =================

	@Transactional(readOnly = true)
	public CedulaQueryStatsDto getUserQueryStats(String userEmail) {
		User user = getUserByEmailOrThrow(userEmail);
		return buildQueryStats(Long.parseLong(user.getId()));

	}

	// ================= PRIVATE HELPER METHODS =================

	private User getUserByEmailOrThrow(String email) {
		UserDto userDto = userService.getUserByEmail(email)
				.orElseThrow(() -> CedulaQueryExceptions.userNotFound(email));
		return userRepository.findById(userDto.getId())
				.orElseThrow(() -> CedulaQueryExceptions.userNotFound(email));
	}

	private void validateCedulaFormat(String cedula) {
		if (cedula == null || cedula.trim().isEmpty())
			throw CedulaQueryExceptions.invalidFormat(cedula, "La cédula no puede estar vacía");

		String cleanCedula = cedula.replaceAll("\\D", "");
		if (cleanCedula.length() != 11)
			throw CedulaQueryExceptions.invalidFormat(cedula, "La cédula debe tener 11 dígitos");
		if (!isValidDominicanCedula(cleanCedula))
			throw CedulaQueryExceptions.invalidFormat(cedula, "Formato de cédula dominicana inválido");
	}

	private boolean isValidDominicanCedula(String cedula) {
		return !cedula.matches("0{11}") && !cedula.matches("(\\d)\\1{10}");
	}

	private void validateUserCanQuery(User user) {
		if (!user.getIsActive())
			throw CedulaQueryExceptions.userInactive(user.getEmail());
		if (user.getTokens() <= 0)
			throw CedulaQueryExceptions.insufficientTokens(user.getId(), user.getTokens(), 1);
	}

	private CedulaQuery createPendingQuery(String cedula, User user) {
		CedulaQuery query = CedulaQuery.builder()
				.cedula(cedula)
				.user(user)
				.queryDate(LocalDateTime.now())
				.cost(QUERY_COST)
				.status(QueryStatus.PENDING)
				.build();
		return cedulaQueryRepository.save(query);
	}

	private void consumeUserToken(User user) {
		try {
			userService.consumeToken(user.getId());
		} catch (Exception e) {
			throw CedulaQueryExceptions.insufficientTokens(user.getId());
		}
	}

	private void refundUserToken(User user) {
		try {
			userService.addTokens(user.getId(), 1);
		} catch (Exception e) {
			log.error("Error refunding token for user {}: {}", user.getId(), e.getMessage());
		}
	}

	private CedulaResultDto queryJceService(String cedula) {
		try {
			return jceClient.queryCedula(cedula);
		} catch (Exception e) {
			log.error("JCE service error for cedula {}: {}", cedula, e.getMessage());
			throw CedulaQueryExceptions.jceUnavailable(e.getMessage(), e);
		}
	}

	private void updateQueryWithSuccess(CedulaQuery query, CedulaResultDto resultDto) {
		CedulaResult result = buildCedulaResult(resultDto);
		query.setResult(result);
		query.setStatus(QueryStatus.COMPLETED);
		query.setCompletedAt(LocalDateTime.now());
		cedulaQueryRepository.save(query);
	}

	private void updateQueryWithError(CedulaQuery query, String errorMessage) {
		query.setStatus(QueryStatus.FAILED);
		query.setErrorMessage(errorMessage);
		query.setCompletedAt(LocalDateTime.now());
		cedulaQueryRepository.save(query);
	}

	private CedulaResult buildCedulaResult(CedulaResultDto resultDto) {
		LocalDate fechaNac = null;
		if (resultDto.getFechaNacimiento() != null && !resultDto.getFechaNacimiento().isEmpty()) {
			try {
				// JCE format is often "M/d/yyyy h:mm:ss a" or "M/d/yyyy"
				String dateStr = resultDto.getFechaNacimiento();
				if (dateStr.contains(" ")) {
					java.time.format.DateTimeFormatter jceFormatter = java.time.format.DateTimeFormatter
							.ofPattern("M/d/yyyy h:mm:ss a", java.util.Locale.US);
					fechaNac = LocalDateTime.parse(dateStr, jceFormatter).toLocalDate();
				} else {
					java.time.format.DateTimeFormatter jceFormatterShort = java.time.format.DateTimeFormatter
							.ofPattern("M/d/yyyy", java.util.Locale.US);
					fechaNac = LocalDate.parse(dateStr, jceFormatterShort);
				}
			} catch (Exception e) {
				log.warn("Failed to parse JCE date: {}, falling back to ISO", resultDto.getFechaNacimiento());
				try {
					fechaNac = LocalDate.parse(resultDto.getFechaNacimiento(), DateTimeFormatter.ISO_DATE);
				} catch (Exception e2) {
					log.error("Total failure parsing date: {}", resultDto.getFechaNacimiento());
				}
			}
		}


		return CedulaResult.builder()
				.nombres(resultDto.getNombres())
				.apellidos(resultDto.getApellidos())
				.fechaNacimiento(fechaNac)
				.lugarNacimiento(resultDto.getLugarNacimiento())
				.estadoCivil(resultDto.getEstadoCivil())
				.ocupacion(resultDto.getOcupacion())
				.nacionalidad(resultDto.getNacionalidad())
				.sexo(resultDto.getSexo())
				.foto(resultDto.getFoto())
				.build();
	}

	private CedulaQueryStatsDto buildQueryStats(Long userId) {
		String userIdStr = userId.toString(); // ← conversión
		long totalQueries = cedulaQueryRepository.countByUserId(userIdStr);
		long completedQueries = cedulaQueryRepository.countByUserIdAndStatus(userIdStr, QueryStatus.COMPLETED);
		long failedQueries = cedulaQueryRepository.countByUserIdAndStatus(userIdStr, QueryStatus.FAILED);
		long pendingQueries = cedulaQueryRepository.countByUserIdAndStatus(userIdStr, QueryStatus.PENDING);

		LocalDateTime today = LocalDateTime.now().toLocalDate().atStartOfDay();
		long todayQueries = cedulaQueryRepository.countByUserIdAndQueryDateAfter(userIdStr, today);

		return CedulaQueryStatsDto.builder()
				.totalQueries(totalQueries)
				.completedQueries(completedQueries)
				.failedQueries(failedQueries)
				.pendingQueries(pendingQueries)
				.todayQueries(todayQueries)
				.build();
	}

	private CedulaQueryDto convertToDto(CedulaQuery query) {
		CedulaResultDto resultDto = null;
		if (query.getResult() != null) {
			CedulaResult result = query.getResult();
			String fotoUrl = result.getFoto();
			if (fotoUrl != null && (fotoUrl.startsWith("/") || !fotoUrl.toLowerCase().startsWith("http"))) {
				fotoUrl = "https://dataportal.jce.gob.do" + (fotoUrl.startsWith("/") ? "" : "/") + fotoUrl;
			}

			resultDto = CedulaResultDto.builder()
					.success(true)
					.message("Consulta exitosa")
					.nombres(result.getNombres())
					.apellido1(result.getApellidos())
					.apellido2("") // Avoid repetition as apellido1 contains full last names
					.fechaNacimiento(result.getFechaNacimiento() != null
							? result.getFechaNacimiento().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
							: null)
					.lugarNacimiento(result.getLugarNacimiento())
					.estadoCivil(result.getEstadoCivil())
					.ocupacion(result.getOcupacion())
					.nacionalidad(result.getNacionalidad())
					.sexo(result.getSexo())
					.fotoUrl(fotoUrl)
					.fotoBase64(downloadPhotoAsBase64(fotoUrl))
					.build();


		}

		return CedulaQueryDto.builder()
				.id(query.getId())
				.cedula(query.getCedula())
				.queryDate(query.getQueryDate())
				.userId(query.getUser().getId())
				.result(resultDto)
				.cost(query.getCost())
				.status(query.getStatus())
				.errorMessage(query.getErrorMessage())
				.completedAt(query.getCompletedAt())
				.build();
	}

	// ================= NESTED DTO CLASS =================

	public static class CedulaQueryStatsDto {
		private final long totalQueries;
		private final long completedQueries;
		private final long failedQueries;
		private final long pendingQueries;
		private final long todayQueries;

		public CedulaQueryStatsDto(long totalQueries, long completedQueries, long failedQueries,
				long pendingQueries, long todayQueries) {
			this.totalQueries = totalQueries;
			this.completedQueries = completedQueries;
			this.failedQueries = failedQueries;
			this.pendingQueries = pendingQueries;
			this.todayQueries = todayQueries;
		}

		public static CedulaQueryStatsDtoBuilder builder() {
			return new CedulaQueryStatsDtoBuilder();
		}

		public long getTotalQueries() {
			return totalQueries;
		}

		public long getCompletedQueries() {
			return completedQueries;
		}

		public long getFailedQueries() {
			return failedQueries;
		}

		public long getPendingQueries() {
			return pendingQueries;
		}

		public long getTodayQueries() {
			return todayQueries;
		}

		public static class CedulaQueryStatsDtoBuilder {
			private long totalQueries;
			private long completedQueries;
			private long failedQueries;
			private long pendingQueries;
			private long todayQueries;

			public CedulaQueryStatsDtoBuilder totalQueries(long totalQueries) {
				this.totalQueries = totalQueries;
				return this;
			}

			public CedulaQueryStatsDtoBuilder completedQueries(long completedQueries) {
				this.completedQueries = completedQueries;
				return this;
			}

			public CedulaQueryStatsDtoBuilder failedQueries(long failedQueries) {
				this.failedQueries = failedQueries;
				return this;
			}

			public CedulaQueryStatsDtoBuilder pendingQueries(long pendingQueries) {
				this.pendingQueries = pendingQueries;
				return this;
			}

			public CedulaQueryStatsDtoBuilder todayQueries(long todayQueries) {
				this.todayQueries = todayQueries;
				return this;
			}

			public CedulaQueryStatsDto build() {
				return new CedulaQueryStatsDto(totalQueries, completedQueries, failedQueries, pendingQueries,
						todayQueries);
			}
		}
	}

	private String downloadPhotoAsBase64(String url) {
		if (url == null || !url.startsWith("http"))
			return null;
		try {
			java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
					.connectTimeout(java.time.Duration.ofSeconds(10))
					.build();

			java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
					.uri(java.net.URI.create(url))
					.header("User-Agent",
							"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
					.header("Referer", "https://dataportal.jce.gob.do/")
					.timeout(java.time.Duration.ofSeconds(15))
					.build();

			java.net.http.HttpResponse<byte[]> response = client.send(request,
					java.net.http.HttpResponse.BodyHandlers.ofByteArray());

			if (response.statusCode() == 200) {
				String contentType = response.headers().firstValue("Content-Type").orElse("image/jpeg");
				byte[] body = response.body();
				if (body.length > 500) { // Valid image size
					return "data:" + contentType + ";base64," + java.util.Base64.getEncoder().encodeToString(body);
				}
			}
			log.warn("JCE photo download returned status: {} for URL: {}", response.statusCode(), url);
		} catch (Exception e) {
			log.error("Failed to download JCE photo from {}: {}", url, e.getMessage());
		}
		return null;
	}
}
