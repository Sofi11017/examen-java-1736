package com.carmotors.sgt.model;

import java.time.Duration;
import java.util.Objects;

/**
 * Modelo para el reporte de tiempos promedio de finalización de reparaciones
 * agrupados por naturaleza de mantenimiento (Preventivo / Correctivo).
 */
public class TiempoPromedioReparacion {

    private String tipoMantenimiento;
    private Duration duracionPromedio;

    public TiempoPromedioReparacion() {
    }

    public TiempoPromedioReparacion(String tipoMantenimiento, Duration duracionPromedio) {
        this.tipoMantenimiento = tipoMantenimiento;
        this.duracionPromedio = duracionPromedio;
    }

    public String getTipoMantenimiento() {
        return tipoMantenimiento;
    }

    public void setTipoMantenimiento(String tipoMantenimiento) {
        this.tipoMantenimiento = tipoMantenimiento;
    }

    public Duration getDuracionPromedio() {
        return duracionPromedio;
    }

    public void setDuracionPromedio(Duration duracionPromedio) {
        this.duracionPromedio = duracionPromedio;
    }

    /**
     * Formatea la duración promedio en formato legible para humanos:
     * {@code "X Días, Y Horas, Z Minutos"}.
     *
     * @return cadena formateada o {@code "0 Días, 0 Horas, 0 Minutos"} si la duración es nula
     */
    public String getTiempoFormateado() {
        if (duracionPromedio == null) {
            return "0 Días, 0 Horas, 0 Minutos";
        }
        long dias = duracionPromedio.toDays();
        long horas = Math.abs(duracionPromedio.toHoursPart());
        long minutos = Math.abs(duracionPromedio.toMinutesPart());
        return String.format("%d Días, %d Horas, %d Minutos", dias, horas, minutos);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TiempoPromedioReparacion that = (TiempoPromedioReparacion) o;
        return Objects.equals(tipoMantenimiento, that.tipoMantenimiento) &&
                Objects.equals(duracionPromedio, that.duracionPromedio);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tipoMantenimiento, duracionPromedio);
    }

    @Override
    public String toString() {
        return "TiempoPromedioReparacion{" +
                "tipoMantenimiento='" + tipoMantenimiento + '\'' +
                ", duracionPromedio=" + getTiempoFormateado() +
                '}';
    }
}
