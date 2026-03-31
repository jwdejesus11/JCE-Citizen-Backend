/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package com.arojas.jce_consulta_api.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "cedula_results")
@Data
@Builder
@AllArgsConstructor
public class CedulaResult {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(name = "id", columnDefinition = "VARCHAR(36)")
	private String id;

	@Column(name = "nombres", nullable = false)
	private String nombres;

	@Column(name = "apellidos", nullable = false)
	private String apellidos;

	@Column(name = "fecha_nacimiento")
	private LocalDate fechaNacimiento;

	@Column(name = "lugar_nacimiento")
	private String lugarNacimiento;

	@Column(name = "estado_civil")
	private String estadoCivil;

	@Column(name = "ocupacion")
	private String ocupacion;

	@Column(name = "nacionalidad")
	private String nacionalidad;

	@Column(name = "sexo")
	private String sexo;

	@Column(name = "foto", columnDefinition = "TEXT")
	private String foto;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@OneToOne(mappedBy = "result")
	private CedulaQuery query;

	// Constructors
	public CedulaResult() {
		this.id = UUID.randomUUID().toString();
	}

	public CedulaResult(String nombres, String apellidos, LocalDate fechaNacimiento,
			String lugarNacimiento, String estadoCivil, String ocupacion,
			String nacionalidad, String sexo, String foto) {
		this();
		this.nombres = nombres;
		this.apellidos = apellidos;
		this.fechaNacimiento = fechaNacimiento;
		this.lugarNacimiento = lugarNacimiento;
		this.estadoCivil = estadoCivil;
		this.ocupacion = ocupacion;
		this.nacionalidad = nacionalidad;
		this.sexo = sexo;
		this.foto = foto;
	}

	// Getters and Setters
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getNombres() {
		return nombres;
	}

	public void setNombres(String nombres) {
		this.nombres = nombres;
	}

	public String getApellidos() {
		return apellidos;
	}

	public void setApellidos(String apellidos) {
		this.apellidos = apellidos;
	}

	public LocalDate getFechaNacimiento() {
		return fechaNacimiento;
	}

	public void setFechaNacimiento(LocalDate fechaNacimiento) {
		this.fechaNacimiento = fechaNacimiento;
	}

	public String getLugarNacimiento() {
		return lugarNacimiento;
	}

	public void setLugarNacimiento(String lugarNacimiento) {
		this.lugarNacimiento = lugarNacimiento;
	}

	public String getEstadoCivil() {
		return estadoCivil;
	}

	public void setEstadoCivil(String estadoCivil) {
		this.estadoCivil = estadoCivil;
	}

	public String getOcupacion() {
		return ocupacion;
	}

	public void setOcupacion(String ocupacion) {
		this.ocupacion = ocupacion;
	}

	public String getNacionalidad() {
		return nacionalidad;
	}

	public void setNacionalidad(String nacionalidad) {
		this.nacionalidad = nacionalidad;
	}

	public String getSexo() {
		return sexo;
	}

	public void setSexo(String sexo) {
		this.sexo = sexo;
	}

	public String getFoto() {
		return foto;
	}

	public void setFoto(String foto) {
		this.foto = foto;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public CedulaQuery getQuery() {
		return query;
	}

	public void setQuery(CedulaQuery query) {
		this.query = query;
	}

	// Utility method
	public String getFullName() {
		return nombres + " " + apellidos;
	}
}
