package com.carmotors.sgt.service;

import com.carmotors.sgt.controller.ReportesController;
import com.carmotors.sgt.dao.ReporteDAO;
import com.carmotors.sgt.dao.ServicioDAO;
import com.carmotors.sgt.model.RegistroConsumoRepuesto;
import com.carmotors.sgt.model.RegistroMantenimientoPreventivo;
import com.carmotors.sgt.model.RegistroProductividadTecnico;
import com.carmotors.sgt.model.RegistroServicioSolicitado;
import com.carmotors.sgt.model.Servicio;
import com.carmotors.sgt.model.TiempoPromedioReparacion;
import com.carmotors.sgt.view.ReportesView;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReporteTiemposPromedioTest {

    @Test
    void calculaTiemposPromedioCorrectamenteParaPreventivoYCorrectivo() {
        LocalDateTime base = LocalDateTime.of(2026, 11, 1, 8, 0, 0);

        // Servicio 1: Preventivo - 4 horas
        Servicio s1 = new Servicio(1, 1, "Cambio de aceite", "Preventivo",
                base, base.plusHours(4));
        // Servicio 2: Preventivo - 5 horas 4 minutos
        Servicio s2 = new Servicio(2, 2, "Alineación", "Preventivo",
                base, base.plusHours(5).plusMinutes(4));
        // Promedio Preventivo: (4h + 5h 4m) / 2 = 4h 32m -> "0 Días, 4 Horas, 32 Minutos"

        // Servicio 3: Correctivo - 2 días 7 horas 15 minutos
        Servicio s3 = new Servicio(3, 3, "Reparación motor", "Correctivo",
                base, base.plusDays(2).plusHours(7).plusMinutes(15));
        // Servicio 4: Correctivo - 2 días 7 horas 15 minutos
        Servicio s4 = new Servicio(4, 4, "Reparación suspensión", "Correctivo",
                base, base.plusDays(2).plusHours(7).plusMinutes(15));
        // Promedio Correctivo: 2 días 7 horas 15 minutos -> "2 Días, 7 Horas, 15 Minutos"

        ReporteService service = new ReporteService();
        List<TiempoPromedioReparacion> reporte = service.calcularTiemposPromedioReparacion(List.of(s1, s2, s3, s4));

        assertEquals(2, reporte.size());

        TiempoPromedioReparacion preventivo = reporte.get(0);
        assertEquals("Preventivo", preventivo.getTipoMantenimiento());
        assertEquals("0 Días, 4 Horas, 32 Minutos", preventivo.getTiempoFormateado());
        assertEquals(Duration.ofHours(4).plusMinutes(32), preventivo.getDuracionPromedio());

        TiempoPromedioReparacion correctivo = reporte.get(1);
        assertEquals("Correctivo", correctivo.getTipoMantenimiento());
        assertEquals("2 Días, 7 Horas, 15 Minutos", correctivo.getTiempoFormateado());
        assertEquals(Duration.ofDays(2).plusHours(7).plusMinutes(15), correctivo.getDuracionPromedio());
    }

    @Test
    void ignoraServiciosConFechasNulasOInvalidas() {
        LocalDateTime base = LocalDateTime.of(2026, 11, 1, 8, 0, 0);

        Servicio valido = new Servicio(1, 1, "Cambio de aceite", "Preventivo",
                base, base.plusHours(2));
        Servicio sinEntrega = new Servicio(2, 2, "Revisión", "Preventivo",
                base, null);
        Servicio sinRecepcion = new Servicio(3, 3, "Frenos", "Preventivo",
                null, base.plusHours(1));
        Servicio fechaInvertida = new Servicio(4, 4, "Batería", "Preventivo",
                base.plusDays(1), base);
        Servicio sinTipo = new Servicio(5, 5, "Otro", null,
                base, base.plusHours(1));

        ReporteService service = new ReporteService();
        List<TiempoPromedioReparacion> reporte = service.calcularTiemposPromedioReparacion(
                List.of(valido, sinEntrega, sinRecepcion, fechaInvertida, sinTipo));

        assertEquals(1, reporte.size());
        assertEquals("Preventivo", reporte.get(0).getTipoMantenimiento());
        assertEquals(Duration.ofHours(2), reporte.get(0).getDuracionPromedio());
        assertEquals("0 Días, 2 Horas, 0 Minutos", reporte.get(0).getTiempoFormateado());
    }

    @Test
    void listaVaciaONulaRetornaListaVacia() {
        ReporteService service = new ReporteService();
        assertTrue(service.calcularTiemposPromedioReparacion(null).isEmpty());
        assertTrue(service.calcularTiemposPromedioReparacion(Collections.emptyList()).isEmpty());
    }

    @Test
    void controladorDelegaEnServicioCorrectamente() {
        LocalDateTime base = LocalDateTime.of(2026, 11, 1, 8, 0, 0);
        List<Servicio> servicios = List.of(
                new Servicio(1, 1, "Servicio Test", "Preventivo", base, base.plusHours(1))
        );

        ReporteDAO mockDao = new ReporteDAO() {
            @Override
            public List<Servicio> listarServiciosConFechaEntrega() {
                return servicios;
            }
            @Override
            public List<RegistroConsumoRepuesto> listarConsumoRepuestos(LocalDate desde, LocalDate hasta) { return List.of(); }
            @Override
            public List<RegistroServicioSolicitado> listarServiciosSolicitados(LocalDate desde, LocalDate hasta) { return List.of(); }
            @Override
            public List<RegistroProductividadTecnico> listarProductividadTecnicos(LocalDate desde, LocalDate hasta) { return List.of(); }
            @Override
            public List<RegistroMantenimientoPreventivo> listarMantenimientosPreventivos() { return List.of(); }
        };
        ReporteService service = new ReporteService(mockDao);
        ReportesController controller = new ReportesController(service);

        List<TiempoPromedioReparacion> reporte = controller.obtenerTiemposPromedioReparacion();
        assertNotNull(reporte);
        assertEquals(1, reporte.size());
        assertEquals("Preventivo", reporte.get(0).getTipoMantenimiento());
        assertEquals("0 Días, 1 Horas, 0 Minutos", reporte.get(0).getTiempoFormateado());
    }

    @Test
    void formatearDuracionEnVistaProduceCadenaEsperada() {
        assertEquals("0 Días, 0 Horas, 0 Minutos", ReportesView.formatearDuracion(null));
        assertEquals("0 Días, 0 Horas, 0 Minutos", ReportesView.formatearDuracion(Duration.ZERO));
        assertEquals("0 Días, 4 Horas, 32 Minutos",
                ReportesView.formatearDuracion(Duration.ofHours(4).plusMinutes(32)));
        assertEquals("2 Días, 7 Horas, 15 Minutos",
                ReportesView.formatearDuracion(Duration.ofDays(2).plusHours(7).plusMinutes(15)));
    }
}
