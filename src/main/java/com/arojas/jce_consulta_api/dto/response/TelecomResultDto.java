package com.arojas.jce_consulta_api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.ArrayList;

/**
 * Resultado unificado para la consulta de líneas en Claro y Altice
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelecomResultDto {
    private String cedula;
    private List<TelecomLineDto> claroLines = new ArrayList<>();
    private List<TelecomLineDto> alticeLines = new ArrayList<>();
    private String claroStatus;
    private String alticeStatus;
    private Long totalLines;
}
