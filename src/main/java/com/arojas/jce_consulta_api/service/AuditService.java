/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package com.arojas.jce_consulta_api.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.arojas.jce_consulta_api.entity.LogEntry.LogLevel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Servicio para auditoría de operaciones críticas.
 * Utiliza DbLoggerService para persistir logs de auditoría en la base de datos.
 * 
 * @author arojas
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AuditService {

	private final DbLoggerService dbLogger;

	public void logPaymentOperation(String userId, String operation, String details) {
		log.info("AUDIT_PAYMENT - User: {} | Operation: {} | Details: {}",
				userId, operation, details);

		dbLogger.log()
				.level(LogLevel.INFO)
				.source("AUDIT_PAYMENT")
				.user(userId)
				.operation(operation)
				.message("Pago procesado: " + details)
				.context("details", details)
				.save();
	}

	public void logQueryOperation(String userId, String cedula, String operation, String status) {
		log.info("AUDIT_QUERY - User: {} | Cedula: {} | Operation: {} | Status: {}",
				userId, maskCedula(cedula), operation, status);

		dbLogger.log()
				.level(LogLevel.INFO)
				.source("AUDIT_QUERY")
				.user(userId)
				.operation(operation)
				.message("Consulta de cédula: " + maskCedula(cedula))
				.context("cedula", maskCedula(cedula))
				.context("status", status)
				.save();
	}

	public void logSecurityEvent(String userId, String event, String ipAddress) {
		log.warn("SECURITY_AUDIT - User: {} | Event: {} | IP: {}",
				userId, event, ipAddress);

		dbLogger.log()
				.level(LogLevel.WARN)
				.source("SECURITY_AUDIT")
				.user(userId)
				.operation(event)
				.message("Evento de seguridad detectado")
				.context("ipAddress", ipAddress)
				.save();
	}

	private String maskCedula(String cedula) {
		if (cedula == null || cedula.length() < 4)
			return "***";
		return cedula.substring(0, 3) + "****" + cedula.substring(cedula.length() - 2);
	}
}

