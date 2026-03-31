package com.arojas.jce_consulta_api.dto;

import java.time.LocalDateTime;

import com.arojas.jce_consulta_api.entity.CedulaResult;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para el resultado de consulta de cédula
 * Incluye la respuesta JSON convertida desde XML
 * Maneja información adicional de validación de la cédula
 *
 * @author arojas
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CedulaResultDto {

	@JsonProperty("cedula")
	private String cedula;

	@JsonProperty("success")
	private boolean success;

	@JsonProperty("message")
	private String message;

	@JsonProperty("queryTimestamp")
	private LocalDateTime queryTimestamp;

	@JsonProperty("nombres")
	private String nombres;

	@JsonProperty("apellido1")
	private String apellido1;

	@JsonProperty("apellido2")
	private String apellido2;

	@JsonProperty("nombreCompleto")
	private String nombreCompleto;

	@JsonProperty("fechaNacimiento")
	private String fechaNacimiento;

	@JsonProperty("lugarNacimiento")
	private String lugarNacimiento;

	@JsonProperty("sexo")
	private String sexo;

	@JsonProperty("estadoCivil")
	private String estadoCivil;

	@JsonProperty("municipioCedula")
	private String municipioCedula;

	@JsonProperty("secuenciaCedula")
	private String secuenciaCedula;

	@JsonProperty("codigoNacionalidad")
	private String codigoNacionalidad;

	@JsonProperty("nacionalidad")
	private String nacionalidad;

	@JsonProperty("descripcionNacionalidad")
	private String descripcionNacionalidad;

	@JsonProperty("fechaExpiracion")
	private String fechaExpiracion;

	@JsonProperty("categoria")
	private String categoria;

	@JsonProperty("ocupacion")
	private String ocupacion;

	@JsonProperty("descripcionCategoria")
	private String descripcionCategoria;

	@JsonProperty("estatus")
	private String estatus;

	@JsonProperty("fotoUrl")
	private String fotoUrl;

	/**
	 * Respuesta JSON original convertida desde XML
	 */
	@JsonProperty("jsonResponse")
	private String jsonResponse;

	/**
	 * Información adicional de validación de la cédula
	 */
	@JsonProperty("validationInfo")
	private CedulaValidationInfo validationInfo;

	/**
	 * Verifica si el resultado contiene datos válidos de persona
	 */
	public boolean hasValidPersonData() {
		return success && nombres != null && !nombres.trim().isEmpty()
				&& apellido1 != null && !apellido1.trim().isEmpty();
	}

	@JsonProperty("apellidos")
	public String getApellidos() {
		String a1 = apellido1 != null ? apellido1 : "";
		String a2 = apellido2 != null ? apellido2 : "";
		return (a1 + " " + a2).trim();
	}

	@JsonProperty("ocupacion")
	public String getOcupacion() {
		return ocupacion;
	}

	@JsonProperty("nacionalidad")
	public String getNacionalidad() {
		return nacionalidad != null ? nacionalidad : descripcionNacionalidad;
	}

	@JsonProperty("foto")
	public String getFoto() {
		return fotoUrl;
	}

	/**
	 * Obtiene el nombre completo concatenado si no está ya establecido
	 */
	public String getNombreCompleto() {
		if (nombreCompleto != null && !nombreCompleto.trim().isEmpty()) {
			return nombreCompleto;
		}

		if (nombres == null && apellido1 == null && apellido2 == null) {
			return null;
		}

		return String.join(" ",
				nombres != null ? nombres.trim() : "",
				apellido1 != null ? apellido1.trim() : "",
				apellido2 != null ? apellido2.trim() : "").trim();
	}

	public static CedulaResultDto fromEntity(CedulaResult entity) {
		if (entity == null)
			return null;

		return CedulaResultDto.builder()
				.cedula(entity.getQuery() != null ? entity.getQuery().getCedula() : null)
				.success(true) // O false si deseas por defecto
				.nombres(entity.getNombres())
				.apellido1(entity.getApellidos() != null ? entity.getApellidos().split(" ")[0] : null)
				.apellido2(entity.getApellidos() != null && entity.getApellidos().contains(" ")
						? entity.getApellidos().substring(entity.getApellidos().indexOf(" ") + 1)
						: null)
				.fechaNacimiento(entity.getFechaNacimiento() != null ? entity.getFechaNacimiento().toString() : null)
				.lugarNacimiento(entity.getLugarNacimiento())
				.sexo(entity.getSexo())
				.estadoCivil(entity.getEstadoCivil())
				.ocupacion(entity.getOcupacion())
				.nacionalidad(entity.getNacionalidad())
				.fotoUrl(entity.getFoto())
				.queryTimestamp(entity.getCreatedAt())
				.build();
	}

	/**
	 * Información de validación de la cédula
	 */
	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public static class CedulaValidationInfo {

		@JsonProperty("digitoVerificadorValido")
		private Boolean digitoVerificadorValido;

		@JsonProperty("formatoValido")
		private Boolean formatoValido;

		@JsonProperty("municipioCodigo")
		private String municipioCodigo;

		@JsonProperty("secuencia")
		private String secuencia;

		@JsonProperty("digitoVerificador")
		private String digitoVerificador;

		@JsonProperty("cedulaFormateada")
		private String cedulaFormateada;

		@JsonProperty("cedulaNormalizada")
		private String cedulaNormalizada;
	}
}