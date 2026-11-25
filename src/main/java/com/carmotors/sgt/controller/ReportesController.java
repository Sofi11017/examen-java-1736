package com.carmotors.sgt.controller;

import com.carmotors.sgt.model.RecordatorioMantenimiento;
import com.carmotors.sgt.model.ReporteConsumoRepuestos;
import com.carmotors.sgt.model.ReporteProductividadTecnicos;
import com.carmotors.sgt.model.ResumenServicioSolicitado;
import com.carmotors.sgt.model.TiempoPromedioReparacion;
import com.carmotors.sgt.service.ReporteService;

import java.time.LocalDate;
import java.util.List;

/**
 * Controlador de reportes gerenciales (RNF-04): delega en {@link ReporteService}.
 */
public class ReportesController {

    private final ReporteService reporteService;

    public ReportesController(ReporteService reporteService) {
        if (reporteService == null) {
            throw new IllegalArgumentException("El servicio de reportes es obligatorio.");
        }
        this.reporteService = reporteService;
    }

    public ReporteConsumoRepuestos generarReporteConsumoRepuestos(LocalDate fechaDesde, LocalDate fechaHasta) {
        return reporteService.generarReporteConsumoRepuestos(fechaDesde, fechaHasta);
    }

    public List<ResumenServicioSolicitado> generarReporteServiciosSolicitados(LocalDate fechaDesde, LocalDate fechaHasta) {
        return reporteService.generarReporteServiciosSolicitados(fechaDesde, fechaHasta);
    }

    public ReporteProductividadTecnicos generarReporteProductividadTecnicos(LocalDate fechaDesde, LocalDate fechaHasta) {
        return reporteService.generarReporteProductividadTecnicos(fechaDesde, fechaHasta);
    }

    public List<RecordatorioMantenimiento> generarReporteRecordatorios(LocalDate fechaReferencia) {
        return reporteService.generarReporteRecordatorios(fechaReferencia);
    }

    public List<TiempoPromedioReparacion> obtenerTiemposPromedioReparacion() {
        return reporteService.calcularTiemposPromedioReparacion();
    }

    public List<TiempoPromedioReparacion> calcularTiemposPromedioReparacion() {
        return reporteService.calcularTiemposPromedioReparacion();
    }
}
