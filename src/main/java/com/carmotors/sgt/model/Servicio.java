package com.carmotors.sgt.model;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entidad y DTO que representa un servicio ejecutado en el taller con sus fechas de
 * recepción y entrega, y su tipo de mantenimiento asociado.
 */
public class Servicio {

    private int id;
    private int ordenId;
    private String nombreServicio;
    private String tipoMantenimiento;
    private LocalDateTime fechaRecepcion;
    private LocalDateTime fechaEntrega;

    public Servicio() {
    }

    public Servicio(int id, int ordenId, String nombreServicio, String tipoMantenimiento,
                    LocalDateTime fechaRecepcion, LocalDateTime fechaEntrega) {
        this.id = id;
        this.ordenId = ordenId;
        this.nombreServicio = nombreServicio;
        this.tipoMantenimiento = tipoMantenimiento;
        this.fechaRecepcion = fechaRecepcion;
        this.fechaEntrega = fechaEntrega;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getOrdenId() {
        return ordenId;
    }

    public void setOrdenId(int ordenId) {
        this.ordenId = ordenId;
    }

    public String getNombreServicio() {
        return nombreServicio;
    }

    public void setNombreServicio(String nombreServicio) {
        this.nombreServicio = nombreServicio;
    }

    public String getTipoMantenimiento() {
        return tipoMantenimiento;
    }

    public void setTipoMantenimiento(String tipoMantenimiento) {
        this.tipoMantenimiento = tipoMantenimiento;
    }

    public LocalDateTime getFechaRecepcion() {
        return fechaRecepcion;
    }

    public void setFechaRecepcion(LocalDateTime fechaRecepcion) {
        this.fechaRecepcion = fechaRecepcion;
    }

    public LocalDateTime getFechaEntrega() {
        return fechaEntrega;
    }

    public void setFechaEntrega(LocalDateTime fechaEntrega) {
        this.fechaEntrega = fechaEntrega;
    }

    /**
     * Calcula la duración total del trabajo entre la recepción y la entrega del servicio.
     *
     * @return {@link Duration} entre recepción y entrega, o {@code Duration.ZERO} si alguna fecha es nula
     */
    public Duration getDuracion() {
        if (fechaRecepcion == null || fechaEntrega == null) {
            return Duration.ZERO;
        }
        return Duration.between(fechaRecepcion, fechaEntrega);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Servicio servicio = (Servicio) o;
        return id == servicio.id &&
                ordenId == servicio.ordenId &&
                Objects.equals(nombreServicio, servicio.nombreServicio) &&
                Objects.equals(tipoMantenimiento, servicio.tipoMantenimiento) &&
                Objects.equals(fechaRecepcion, servicio.fechaRecepcion) &&
                Objects.equals(fechaEntrega, servicio.fechaEntrega);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, ordenId, nombreServicio, tipoMantenimiento, fechaRecepcion, fechaEntrega);
    }

    @Override
    public String toString() {
        return "Servicio{" +
                "id=" + id +
                ", ordenId=" + ordenId +
                ", nombreServicio='" + nombreServicio + '\'' +
                ", tipoMantenimiento='" + tipoMantenimiento + '\'' +
                ", fechaRecepcion=" + fechaRecepcion +
                ", fechaEntrega=" + fechaEntrega +
                '}';
    }
}
