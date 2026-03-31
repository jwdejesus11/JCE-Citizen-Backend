/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package com.arojas.jce_consulta_api.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 *
 * @author arojas
 */

@Entity
@Table(name = "cedula_queries")
@Data
@Builder
@AllArgsConstructor
public class CedulaQuery {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", columnDefinition = "VARCHAR(36)")
	private String id;

	@Column(name = "cedula", nullable = false, length = 20)
	private String cedula;

	@CreationTimestamp
	@Column(name = "query_date", nullable = false)
	private LocalDateTime queryDate;

	@Column(name = "completed_at")
	private LocalDateTime completedAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	@JoinColumn(name = "result_id")
	private CedulaResult result;

	@Column(name = "cost", nullable = false, precision = 10, scale = 2)
	private BigDecimal cost;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private QueryStatus status = QueryStatus.PENDING;

	@Column(name = "error_message")
	private String errorMessage;

	@UpdateTimestamp
	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	// Constructors
	public CedulaQuery() {
		this.id = UUID.randomUUID().toString();
	}

	public CedulaQuery(String cedula, User user, BigDecimal cost) {
		this();
		this.cedula = cedula;
		this.user = user;
		this.cost = cost;
	}

	// Getters and Setters
	public String getId() {
		return id;
	}

	public LocalDateTime getCompletedAt() {
		return completedAt;
	}

	public void setCompletedAt(LocalDateTime completedAt) {
		this.completedAt = completedAt;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getCedula() {
		return cedula;
	}

	public void setCedula(String cedula) {
		this.cedula = cedula;
	}

	public LocalDateTime getQueryDate() {
		return queryDate;
	}

	public void setQueryDate(LocalDateTime queryDate) {
		this.queryDate = queryDate;
	}

	public User getUser() {
		return user;
	}

	public void setUser(User user) {
		this.user = user;
	}

	public CedulaResult getResult() {
		return result;
	}

	public void setResult(CedulaResult result) {
		this.result = result;
	}

	public BigDecimal getCost() {
		return cost;
	}

	public void setCost(BigDecimal cost) {
		this.cost = cost;
	}

	public QueryStatus getStatus() {
		return status;
	}

	public void setStatus(QueryStatus status) {
		this.status = status;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}

	// Utility methods
	public void markAsCompleted(CedulaResult result) {
		this.result = result;
		this.status = QueryStatus.COMPLETED;
	}

	public void markAsFailed(String errorMessage) {
		this.errorMessage = errorMessage;
		this.status = QueryStatus.FAILED;
	}

	public enum QueryStatus {
		PENDING, COMPLETED, FAILED
	}
}