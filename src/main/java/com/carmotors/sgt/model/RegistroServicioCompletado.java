package com.carmotors.sgt.model;

import java.time.LocalDateTime;

/**
 * Registro especializado para servicios completados con fecha de recepción y entrega.
 * Hereda de {@link Servicio} para máxima compatibilidad con el modelo de dominio.
 */
public class RegistroServicioCompletado extends Servicio {

    public RegistroServicioCompletado() {
        super();
    }

    public RegistroServicioCompletado(int id, int ordenId, String nombreServicio, String tipoMantenimiento,
                                      LocalDateTime fechaRecepcion, LocalDateTime fechaEntrega) {
        super(id, ordenId, nombreServicio, tipoMantenimiento, fechaRecepcion, fechaEntrega);
    }
}
