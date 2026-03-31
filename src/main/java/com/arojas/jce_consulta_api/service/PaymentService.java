package com.arojas.jce_consulta_api.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.arojas.jce_consulta_api.dto.PaymentOrderDto;
import com.arojas.jce_consulta_api.entity.PaymentOrder;
import com.arojas.jce_consulta_api.entity.PaymentOrder.PaymentStatus;
import com.arojas.jce_consulta_api.entity.User;
import com.arojas.jce_consulta_api.entity.User.Role;
import com.arojas.jce_consulta_api.exception.payment.PaymentExceptions;
import com.arojas.jce_consulta_api.repository.PaymentOrderRepository;
import com.arojas.jce_consulta_api.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Servicio para gestión de pagos y órdenes de compra de tokens
 * Implementa transaccionalidad y manejo de errores robusto
 *
 * @author arojas
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PaymentService {

	// Repositories
	private final PaymentOrderRepository paymentOrderRepository;
	private final UserRepository userRepository;

	// Services
	private final UserService userService;
	private final AppSettingsService appSettingsService;
	private final EmailService emailService;
	private final ObjectMapper objectMapper;

	// Configuration
	@Value("${app.payment.buymeacoffee.base-url}")
	private String buyMeACoffeeBaseUrl;

	@Value("${app.payment.buymeacoffee.username:}")
	private String buyMeACoffeeUsername;

	@Value("${app.payment.verification.enabled}")
	private boolean paymentVerificationEnabled;

	@Value("${app.payment.auto-confirm.delay}")
	private long autoConfirmDelay;

	// ================= USER OPERATIONS =================

	/**
	 * Crea una orden de pago para tokens
	 */
	public PaymentOrderDto createPaymentOrder(String userEmail, int tokenQuantity) {
		log.info("Creating payment order for user: {} - tokens: {}", userEmail, tokenQuantity);

		validateTokenQuantity(tokenQuantity);

		User user = getUserByEmailOrThrow(userEmail);
		validateUserIsActive(user);

		BigDecimal tokenPrice = appSettingsService.getTokenPrice();
		BigDecimal totalAmount = calculateTotalAmount(tokenPrice, tokenQuantity);
		// Pre-save to get ID or generate one
		String orderId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
		String buyMeACoffeeUrl = generateBuyMeACoffeeUrl(orderId, tokenQuantity, totalAmount);

		PaymentOrder paymentOrder = createPaymentOrderEntity(user, tokenQuantity, totalAmount, buyMeACoffeeUrl);
		paymentOrder.setId(orderId);
		paymentOrder = paymentOrderRepository.save(paymentOrder);

		scheduleAutoConfirmationIfEnabled(paymentOrder.getId());

		log.info("Payment order created successfully: {} for user: {}", paymentOrder.getId(), userEmail);
		return convertToDto(paymentOrder);
	}

	/**
	 * Obtiene el historial de pagos de un usuario
	 */
	@Transactional(readOnly = true)
	public Page<PaymentOrderDto> getUserPaymentHistory(String userEmail, int page, int size) {
		log.info("Getting payment history for user: {} - page: {}", userEmail, page);

		User user = getUserByEmailOrThrow(userEmail);

		Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
		Page<PaymentOrder> payments = paymentOrderRepository.findByUserIdOrderByCreatedAtDesc(
				user.getId(), pageable);

		return payments.map(this::convertToDto);
	}

	/**
	 * Obtiene una orden de pago por ID
	 */
	@Transactional(readOnly = true)
	public PaymentOrderDto getPaymentOrderById(String paymentOrderId, String userEmail) {
		log.info("Getting payment order: {} for user: {}", paymentOrderId, userEmail);

		User user = getUserByEmailOrThrow(userEmail);

		PaymentOrder paymentOrder = paymentOrderRepository.findByIdAndUserId(paymentOrderId, user.getId())
				.orElseThrow(() -> PaymentExceptions.paymentNotFound(paymentOrderId));

		return convertToDto(paymentOrder);
	}

	/**
	 * Obtiene estadísticas de pagos del usuario
	 */
	@Transactional(readOnly = true)
	public PaymentStatsDto getUserPaymentStats(String userEmail) {
		log.info("Getting payment stats for user: {}", userEmail);

		User user = getUserByEmailOrThrow(userEmail);

		return buildPaymentStats(user.getId());
	}

	// ================= ADMIN OPERATIONS =================

	/**
	 * Confirma un pago manualmente (para administradores)
	 */
	public PaymentOrderDto confirmPayment(String paymentOrderId, String adminEmail) {
		log.info("Admin {} confirming payment: {}", adminEmail, paymentOrderId);

		User admin = getUserByEmailOrThrow(adminEmail);
		validateUserIsAdmin(admin);

		PaymentOrder paymentOrder = getPaymentOrderByIdOrThrow(paymentOrderId);
		validatePaymentIsPending(paymentOrder);

		return processPaymentConfirmation(paymentOrder);
	}

	/**
	 * Marca un pago como fallido
	 */
	public PaymentOrderDto failPayment(String paymentOrderId, String reason) {
		log.info("Failing payment: {} - reason: {}", paymentOrderId, reason);

		PaymentOrder paymentOrder = getPaymentOrderByIdOrThrow(paymentOrderId);

		paymentOrder.setStatus(PaymentStatus.FAILED);
		paymentOrder.setErrorMessage(reason);
		paymentOrder.setCompletedAt(LocalDateTime.now());

		paymentOrder = paymentOrderRepository.save(paymentOrder);

		return convertToDto(paymentOrder);
	}

	/**
	 * Obtiene pagos pendientes para verificación
	 */
	@Transactional(readOnly = true)
	public List<PaymentOrderDto> getPendingPayments() {
		log.info("Getting pending payments");

		List<PaymentOrder> pendingPayments = paymentOrderRepository
				.findByStatusOrderByCreatedAtAsc(PaymentStatus.PENDING);

		return pendingPayments.stream().map(this::convertToDto).toList();
	}

	/**
	 * Obtiene pagos pendientes antiguos (para limpieza automática)
	 */
	@Transactional(readOnly = true)
	public List<PaymentOrderDto> getExpiredPendingPayments(int hoursOld) {
		log.info("Getting expired pending payments (older than {} hours)", hoursOld);

		LocalDateTime cutoffDate = LocalDateTime.now().minusHours(hoursOld);
		List<PaymentOrder> expiredPayments = paymentOrderRepository.findByStatusAndCreatedAtBefore(
				PaymentStatus.PENDING, cutoffDate);

		return expiredPayments.stream().map(this::convertToDto).toList();
	}

	/**
	 * Limpia pagos pendientes expirados
	 */
	public int cleanupExpiredPendingPayments(int hoursOld) {
		log.info("Cleaning expired pending payments (older than {} hours)", hoursOld);

		LocalDateTime cutoffDate = LocalDateTime.now().minusHours(hoursOld);
		List<PaymentOrder> expiredPayments = paymentOrderRepository.findByStatusAndCreatedAtBefore(
				PaymentStatus.PENDING, cutoffDate);

		expiredPayments.forEach(this::markPaymentAsExpired);

		int count = expiredPayments.size();
		log.info("Cleaned {} expired pending payments", count);
		return count;
	}

	// ================= WEBHOOK OPERATIONS =================

	/**
	 * Procesa webhook de Buy Me a Coffee
	 */
	public String processBuyMeACoffeeWebhook(String payload, String signature) {
		log.info("Processing Buy Me a Coffee webhook");

		try {
			validateWebhookSignature(payload, signature);
			
			// Parse payload
			Map<String, Object> body = objectMapper.readValue(payload, Map.class);
			Map<String, Object> data = (Map<String, Object>) body.get("data");
			
			if (data == null) {
				throw new RuntimeException("Payload webhook inválido: data missing");
			}

			String message = (String) data.get("support_note");
			if (message == null) message = (String) data.get("message");
			
			log.info("Webhook BMC message: {}", message);

			// Extract OrderID from message
			if (message != null && message.contains("OrderID: ")) {
				String orderId = message.split("OrderID: ")[1].trim().split(" ")[0];
				log.info("Found OrderID in webhook: {}", orderId);
				
				PaymentOrder order = getPaymentOrderByIdOrThrow(orderId);
				if (order.getStatus() == PaymentStatus.PENDING) {
					processPaymentConfirmation(order);
					return "Pago confirmado automáticamente para orden: " + orderId;
				}
			}

			return "Webhook recibido pero no se pudo asociar a una orden pendiente";

		} catch (Exception e) {
			log.error("Error processing webhook: {}", e.getMessage(), e);
			throw PaymentExceptions.processingError("webhook", e.getMessage(), e);
		}
	}

	// ================= PRIVATE HELPER METHODS =================

	private void validateTokenQuantity(int tokenQuantity) {
		if (tokenQuantity <= 0) {
			throw PaymentExceptions.invalidTokenQuantity(tokenQuantity);
		}
	}

	private User getUserByEmailOrThrow(String email) {
		return userService.getUserByEmail(email)
				.map(userDto -> userRepository.findById(userDto.getId()).orElse(null))
				.orElseThrow(() -> PaymentExceptions.userNotFound(email));
	}

	private void validateUserIsActive(User user) {
		if (!user.getIsActive()) {
			throw PaymentExceptions.userInactive(user.getId());
		}
	}

	private void validateUserIsAdmin(User user) {
		if (!user.getRole().equals(Role.ADMIN)) {
			throw PaymentExceptions.insufficientPermissions("confirmar pagos manualmente");
		}
	}

	private PaymentOrder getPaymentOrderByIdOrThrow(String paymentOrderId) {
		return paymentOrderRepository.findById(paymentOrderId)
				.orElseThrow(() -> PaymentExceptions.paymentNotFound(paymentOrderId));
	}

	private void validatePaymentIsPending(PaymentOrder paymentOrder) {
		if (paymentOrder.getStatus() != PaymentStatus.PENDING) {
			throw PaymentExceptions.invalidStatus("PENDING", paymentOrder.getStatus().toString());
		}
	}

	private BigDecimal calculateTotalAmount(BigDecimal tokenPrice, int tokenQuantity) {
		return tokenPrice.multiply(BigDecimal.valueOf(tokenQuantity));
	}

	private String generateBuyMeACoffeeUrl(String orderId, int tokens, BigDecimal amount) {
		String baseUrl = String.format("%s/%s", buyMeACoffeeBaseUrl, buyMeACoffeeUsername);
		String message = String.format("Compra de %d tokens - OrderID: %s", tokens, orderId);

		return String.format("%s?message=%s&amount=%.2f",
				baseUrl,
				message.replace(" ", "%20"),
				amount.doubleValue());
	}

	private PaymentOrder createPaymentOrderEntity(User user, int tokenQuantity,
			BigDecimal totalAmount, String buyMeACoffeeUrl) {
		return PaymentOrder.builder()
				.user(user)
				.tokens(tokenQuantity)
				.amount(totalAmount)
				.status(PaymentStatus.PENDING)
				.buyMeCoffeeUrl(buyMeACoffeeUrl)
				.createdAt(LocalDateTime.now())
				.build();
	}

	private void scheduleAutoConfirmationIfEnabled(String paymentOrderId) {
		if (!paymentVerificationEnabled) {
			scheduleAutoConfirmation(paymentOrderId);
		}
	}

	@Async
	private void scheduleAutoConfirmation(String paymentOrderId) {
		CompletableFuture.runAsync(() -> {
			try {
				Thread.sleep(autoConfirmDelay);
				autoConfirmPaymentIfPending(paymentOrderId);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				log.warn("Auto-confirmation interrupted for payment: {}", paymentOrderId);
			} catch (Exception e) {
				log.error("Error in auto-confirmation for payment {}: {}", paymentOrderId, e.getMessage());
			}
		});
	}

	private void autoConfirmPaymentIfPending(String paymentOrderId) {
		paymentOrderRepository.findById(paymentOrderId)
				.filter(payment -> payment.getStatus() == PaymentStatus.PENDING)
				.ifPresent(payment -> {
					log.info("Auto-confirming payment for testing: {}", paymentOrderId);
					processPaymentConfirmation(payment);
				});
	}

	/**
	 * Procesa la confirmación de un pago
	 */
	private PaymentOrderDto processPaymentConfirmation(PaymentOrder paymentOrder) {
		log.info("Processing payment confirmation: {}", paymentOrder.getId());

		try {
			updatePaymentToCompleted(paymentOrder);
			addTokensToUser(paymentOrder);
			sendConfirmationEmailAsync(paymentOrder);

			log.info("Payment confirmed successfully: {} - tokens added: {}",
					paymentOrder.getId(), paymentOrder.getTokens());

			return convertToDto(paymentOrder);

		} catch (Exception e) {
			log.error("Error processing payment confirmation {}: {}", paymentOrder.getId(), e.getMessage());

			markPaymentAsFailed(paymentOrder, e.getMessage());
			throw PaymentExceptions.processingError(paymentOrder.getId(), e.getMessage(), e);
		}
	}

	private void updatePaymentToCompleted(PaymentOrder paymentOrder) {
		paymentOrder.setStatus(PaymentStatus.COMPLETED);
		paymentOrder.setCompletedAt(LocalDateTime.now());
		paymentOrderRepository.save(paymentOrder);
	}

	private void addTokensToUser(PaymentOrder paymentOrder) {
		userService.addTokens(paymentOrder.getUser().getId(), paymentOrder.getTokens());
	}

	@Async
	private void sendConfirmationEmailAsync(PaymentOrder paymentOrder) {
		try {
			emailService.sendPaymentConfirmationEmail(
					paymentOrder.getUser().getEmail(), paymentOrder);
		} catch (Exception e) {
			log.error("Error sending payment confirmation email: {}", e.getMessage());
			// Don't fail the process due to email error
		}
	}

	private void markPaymentAsFailed(PaymentOrder paymentOrder, String errorMessage) {
		paymentOrder.setStatus(PaymentStatus.FAILED);
		paymentOrder.setErrorMessage(errorMessage);
		paymentOrder.setCompletedAt(LocalDateTime.now());
		paymentOrderRepository.save(paymentOrder);
	}

	private void markPaymentAsExpired(PaymentOrder payment) {
		payment.setStatus(PaymentStatus.FAILED);
		payment.setErrorMessage("Pago expirado - no confirmado a tiempo");
		payment.setCompletedAt(LocalDateTime.now());
		paymentOrderRepository.save(payment);
	}

	private PaymentStatsDto buildPaymentStats(String userId) {
		long totalPayments = paymentOrderRepository.countByUserId(userId);
		long completedPayments = paymentOrderRepository.countByUserIdAndStatus(userId, PaymentStatus.COMPLETED);
		long pendingPayments = paymentOrderRepository.countByUserIdAndStatus(userId, PaymentStatus.PENDING);
		long failedPayments = paymentOrderRepository.countByUserIdAndStatus(userId, PaymentStatus.FAILED);

		BigDecimal totalAmount = Optional.ofNullable(
				paymentOrderRepository.sumAmountByUserIdAndStatus(userId, PaymentStatus.COMPLETED))
				.orElse(BigDecimal.ZERO);

		Integer totalTokensPurchased = Optional.ofNullable(
				paymentOrderRepository.sumTokensByUserIdAndStatus(userId, PaymentStatus.COMPLETED))
				.orElse(0);

		return PaymentStatsDto.builder()
				.totalPayments(totalPayments)
				.completedPayments(completedPayments)
				.pendingPayments(pendingPayments)
				.failedPayments(failedPayments)
				.totalAmountSpent(totalAmount)
				.totalTokensPurchased(totalTokensPurchased)
				.build();
	}

	@Value("${app.payment.buymeacoffee.webhook-secret:}")
	private String webhookSecret;

	private void validateWebhookSignature(String payload, String signature) {
		if (signature == null || webhookSecret == null || webhookSecret.isEmpty()) {
			log.warn("Webhook validation skipped: missing signature or secret");
			return;
		}

		try {
			javax.crypto.Mac sha256_HMAC = javax.crypto.Mac.getInstance("HmacSHA256");
			javax.crypto.spec.SecretKeySpec secret_key = new javax.crypto.spec.SecretKeySpec(
					webhookSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
			sha256_HMAC.init(secret_key);

			byte[] hash = sha256_HMAC.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			StringBuilder hexString = new StringBuilder();
			for (byte b : hash) {
				String hex = Integer.toHexString(0xff & b);
				if (hex.length() == 1) hexString.append('0');
				hexString.append(hex);
			}

			if (!hexString.toString().equals(signature)) {
				log.error("Invalid webhook signature from Buy Me a Coffee");
				throw new RuntimeException("Invalid signature");
			}
			log.info("Webhook signature validated successfully");
		} catch (Exception e) {
			throw new RuntimeException("Error validating signature", e);
		}
	}

	private PaymentOrderDto convertToDto(PaymentOrder paymentOrder) {
		return PaymentOrderDto.builder()
				.id(paymentOrder.getId())
				.userId(paymentOrder.getUser().getId())
				.tokens(paymentOrder.getTokens())
				.amount(paymentOrder.getAmount())
				.status(paymentOrder.getStatus())
				.buyMeCoffeeUrl(paymentOrder.getBuyMeCoffeeUrl())
				.createdAt(paymentOrder.getCreatedAt())
				.completedAt(paymentOrder.getCompletedAt())
				.errorMessage(paymentOrder.getErrorMessage())
				.build();
	}

	// ================= NESTED DTO CLASS =================

	/**
	 * DTO para estadísticas de pagos del usuario
	 */
	public static class PaymentStatsDto {
		private final long totalPayments;
		private final long completedPayments;
		private final long pendingPayments;
		private final long failedPayments;
		private final BigDecimal totalAmountSpent;
		private final int totalTokensPurchased;

		public PaymentStatsDto(long totalPayments, long completedPayments, long pendingPayments,
				long failedPayments, BigDecimal totalAmountSpent, int totalTokensPurchased) {
			this.totalPayments = totalPayments;
			this.completedPayments = completedPayments;
			this.pendingPayments = pendingPayments;
			this.failedPayments = failedPayments;
			this.totalAmountSpent = totalAmountSpent;
			this.totalTokensPurchased = totalTokensPurchased;
		}

		public static PaymentStatsDtoBuilder builder() {
			return new PaymentStatsDtoBuilder();
		}

		// Getters
		public long getTotalPayments() {
			return totalPayments;
		}

		public long getCompletedPayments() {
			return completedPayments;
		}

		public long getPendingPayments() {
			return pendingPayments;
		}

		public long getFailedPayments() {
			return failedPayments;
		}

		public BigDecimal getTotalAmountSpent() {
			return totalAmountSpent;
		}

		public int getTotalTokensPurchased() {
			return totalTokensPurchased;
		}

		public static class PaymentStatsDtoBuilder {
			private long totalPayments;
			private long completedPayments;
			private long pendingPayments;
			private long failedPayments;
			private BigDecimal totalAmountSpent;
			private int totalTokensPurchased;

			public PaymentStatsDtoBuilder totalPayments(long totalPayments) {
				this.totalPayments = totalPayments;
				return this;
			}

			public PaymentStatsDtoBuilder completedPayments(long completedPayments) {
				this.completedPayments = completedPayments;
				return this;
			}

			public PaymentStatsDtoBuilder pendingPayments(long pendingPayments) {
				this.pendingPayments = pendingPayments;
				return this;
			}

			public PaymentStatsDtoBuilder failedPayments(long failedPayments) {
				this.failedPayments = failedPayments;
				return this;
			}

			public PaymentStatsDtoBuilder totalAmountSpent(BigDecimal totalAmountSpent) {
				this.totalAmountSpent = totalAmountSpent;
				return this;
			}

			public PaymentStatsDtoBuilder totalTokensPurchased(int totalTokensPurchased) {
				this.totalTokensPurchased = totalTokensPurchased;
				return this;
			}

			public PaymentStatsDto build() {
				return new PaymentStatsDto(totalPayments, completedPayments, pendingPayments,
						failedPayments, totalAmountSpent, totalTokensPurchased);
			}
		}
	}
}