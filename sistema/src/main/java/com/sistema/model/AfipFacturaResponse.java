package com.sistema.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class AfipFacturaResponse {

    private String cae;
    private LocalDate fechaVencimiento;
    private Long numeroComprobante;

    public AfipFacturaResponse(String cae, LocalDate fechaVencimiento, Long numeroComprobante) {
        this.cae = cae;
        this.fechaVencimiento = fechaVencimiento;
        this.numeroComprobante = numeroComprobante;
    }
}

