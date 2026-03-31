package com.arojas.jce_consulta_api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * Representa una línea telefónica de una operadora
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelecomLineDto {
    private String phoneNumber;
    private String serviceType;
    private String operator;
    private String status;
}
