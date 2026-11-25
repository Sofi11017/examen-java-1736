package com.carmotors.sgt.view;

import com.carmotors.sgt.controller.ReportesController;
import com.carmotors.sgt.exception.CarMotorsException;
import com.carmotors.sgt.model.ProductividadTecnico;
import com.carmotors.sgt.model.RecordatorioMantenimiento;
import com.carmotors.sgt.model.ReporteConsumoRepuestos;
import com.carmotors.sgt.model.ReporteConsumoRepuestos.DetalleConsumo;
import com.carmotors.sgt.model.ReporteConsumoRepuestos.ResumenConsumo;
import com.carmotors.sgt.model.ReporteProductividadTecnicos;
import com.carmotors.sgt.model.ResumenServicioSolicitado;
import com.carmotors.sgt.model.TiempoPromedioReparacion;
import com.carmotors.sgt.util.ConsoleReader;
import com.carmotors.sgt.util.ConsoleTableFormatter;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Submenú de consola para los reportes gerenciales de CarMotors SGT (RF-21, RF-25, RF-26, RF-27).
 * Solo captura parámetros e imprime resultados; las reglas residen en {@code service} (RNF-03).
 */
public class ReportesView implements SubmenuVista {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ReportesController reportesController;

    public ReportesView(ReportesController reportesController) {
        if (reportesController == null) {
            throw new IllegalArgumentException("El controlador de reportes es obligatorio.");
        }
        this.reportesController = reportesController;
    }

    @Override
    public String obtenerTitulo() {
        return "Reportes Gerenciales";
    }

    @Override
    public void mostrarMenu() {
        boolean ejecutando = true;
        while (ejecutando) {
            System.out.println("\n==================================================");
            System.out.println("               REPORTES GERENCIALES               ");
            System.out.println("==================================================");
            System.out.println("  1. Consumo de repuestos (RF-27)");
            System.out.println("  2. Servicios más solicitados (RF-25)");
            System.out.println("  3. Productividad por técnico (RF-26)");
            System.out.println("  4. Recordatorios de mantenimiento preventivo (RF-21)");
            System.out.println("  5. Ver Tiempos Promedio de Reparación");
            System.out.println("  0. Volver al menú principal");
            System.out.println("==================================================");

            int opcion = ConsoleReader.leerEnteroEnRango("Seleccione una opción", 0, 5);
            switch (opcion) {
                case 1:
                    mostrarReporteConsumoRepuestos();
                    break;
                case 2:
                    mostrarReporteServiciosSolicitados();
                    break;
                case 3:
                    mostrarReporteProductividadTecnicos();
                    break;
                case 4:
                    mostrarReporteRecordatorios();
                    break;
                case 5:
                    mostrarTiemposPromedioReparacion();
                    break;
                case 0:
                    ejecutando = false;
                    break;
                default:
                    break;
            }
        }
    }

    private void mostrarTiemposPromedioReparacion() {
        try {
            List<TiempoPromedioReparacion> reportes = reportesController.obtenerTiemposPromedioReparacion();
            imprimirInformeTiemposPromedio(reportes);
        } catch (CarMotorsException e) {
            System.out.println("\n  [!] No se pudo generar el reporte: " + e.getMessage());
        } finally {
            ConsoleReader.pausar();
        }
    }

    public void imprimirInformeTiemposPromedio(List<TiempoPromedioReparacion> reportes) {
        System.out.println("\n=============================================================");
        System.out.println("        INFORME DE TIEMPOS PROMEDIO DE REPARACIÓN");
        System.out.println("=============================================================");
        System.out.println();
        System.out.println("Tipo de Mantenimiento | Tiempo Promedio de Finalización");
        System.out.println("-------------------------------------------------------------");
        if (reportes == null || reportes.isEmpty()) {
            System.out.println("  [i] No se encontraron servicios completados registrados.");
        } else {
            for (TiempoPromedioReparacion item : reportes) {
                String duracionTexto = formatearDuracion(item.getDuracionPromedio());
                System.out.printf("%-21s | %s%n", item.getTipoMantenimiento(), duracionTexto);
            }
        }
        System.out.println("-------------------------------------------------------------");
    }

    public static String formatearDuracion(Duration duracion) {
        if (duracion == null) {
            return "0 Días, 0 Horas, 0 Minutos";
        }
        long dias = duracion.toDays();
        long horas = Math.abs(duracion.toHoursPart());
        long minutos = Math.abs(duracion.toMinutesPart());
        return String.format("%d Días, %d Horas, %d Minutos", dias, horas, minutos);
    }

    private void mostrarReporteServiciosSolicitados() {
        try {
            System.out.println("\n--- Servicios más solicitados (RF-25) ---");
            LocalDate desde = ConsoleReader.leerFecha("Completado desde (yyyy-MM-dd)");
            LocalDate hasta = ConsoleReader.leerFecha("Completado hasta (yyyy-MM-dd)");
            List<ResumenServicioSolicitado> servicios = reportesController.generarReporteServiciosSolicitados(desde, hasta);
            System.out.println("  Rango de finalización: " + formatearRango(desde, hasta));
            if (servicios.isEmpty()) {
                System.out.println("  [i] No se encontraron servicios finalizados en el rango indicado.");
            } else {
                List<String[]> filas = new ArrayList<>();
                for (ResumenServicioSolicitado servicio : servicios) {
                    filas.add(new String[]{
                            String.valueOf(servicio.getServicioTipoId()),
                            servicio.getNombreServicio(),
                            String.valueOf(servicio.getCantidadLineas()),
                            "$" + servicio.getIngresos().toPlainString()
                    });
                }
                ConsoleTableFormatter.imprimirTabla(
                        new String[]{"ID", "Servicio", "Líneas", "Ingresos (sin IVA)"},
                        filas,
                        new boolean[]{true, false, true, true});
            }
        } catch (CarMotorsException e) {
            System.out.println("\n  [!] No se pudo generar el reporte: " + e.getMessage());
        } finally {
            ConsoleReader.pausar();
        }
    }

    private void mostrarReporteRecordatorios() {
        try {
            LocalDate hoy = LocalDate.now();
            List<RecordatorioMantenimiento> recordatorios = reportesController.generarReporteRecordatorios(hoy);
            imprimirRecordatorios(recordatorios, hoy);
        } catch (CarMotorsException e) {
            System.out.println("\n  [!] No se pudo generar el reporte: " + e.getMessage());
        } finally {
            ConsoleReader.pausar();
        }
    }

    private void imprimirRecordatorios(List<RecordatorioMantenimiento> recordatorios, LocalDate hoy) {
        System.out.println("\n==================================================================");
        System.out.println("          RECORDATORIOS DE MANTENIMIENTO PREVENTIVO (RF-21)");
        System.out.println("==================================================================");
        System.out.println("  Fecha de referencia: " + hoy.format(FORMATO_FECHA)
                + " | Incluye vencidos y los que vencen en los próximos 30 días.");
        System.out.println("  Solo clientes activos. La notificación al cliente se gestiona manualmente.");

        if (recordatorios.isEmpty()) {
            System.out.println("\n  [i] No hay mantenimientos preventivos vencidos ni próximos a vencer.");
            return;
        }

        System.out.println();
        List<String[]> filas = new ArrayList<>();
        for (RecordatorioMantenimiento recordatorio : recordatorios) {
            filas.add(new String[]{
                    textoOpcional(recordatorio.getClienteNombre()),
                    textoOpcional(recordatorio.getClienteTelefono()),
                    textoOpcional(recordatorio.getClienteEmail()),
                    textoOpcional(recordatorio.getPlaca()),
                    textoOpcional(recordatorio.getNombreServicio()),
                    recordatorio.getFechaUltimaEntrega().format(FORMATO_FECHA),
                    recordatorio.getFechaSugerida().format(FORMATO_FECHA),
                    textoEstado(recordatorio)
            });
        }
        ConsoleTableFormatter.imprimirTabla(
                new String[]{"Cliente", "Teléfono", "Email", "Placa", "Servicio", "Últ. entrega", "Sugerida", "Estado"},
                filas,
                null,
                30);
        System.out.println("  Total de recordatorios: " + recordatorios.size());
    }

    private String textoEstado(RecordatorioMantenimiento recordatorio) {
        if (recordatorio.isVencido()) {
            return "VENCIDO hace " + textoDias(recordatorio.getDiasAtraso());
        }
        return "En " + textoDias(recordatorio.getDiasRestantes());
    }

    private String textoDias(int dias) {
        return dias + (dias == 1 ? " día" : " días");
    }

    private String textoOpcional(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return "N/A";
        }
        return texto.trim();
    }

    private void mostrarReporteProductividadTecnicos() {
        try {
            System.out.println("\n--- Productividad por técnico (RF-26) ---");
            System.out.println("  Criterio de rango: fecha de completado de órdenes Completadas, Facturadas o Entregadas.");
            LocalDate desde = ConsoleReader.leerFecha("Completado desde (yyyy-MM-dd)");
            LocalDate hasta = ConsoleReader.leerFecha("Completado hasta (yyyy-MM-dd)");

            ReporteProductividadTecnicos reporte = reportesController.generarReporteProductividadTecnicos(desde, hasta);
            imprimirReporteProductividad(reporte);
        } catch (CarMotorsException e) {
            System.out.println("\n  [!] No se pudo generar el reporte: " + e.getMessage());
        } finally {
            ConsoleReader.pausar();
        }
    }

    private void imprimirReporteProductividad(ReporteProductividadTecnicos reporte) {
        System.out.println("\n==================================================================");
        System.out.println("                    PRODUCTIVIDAD POR TÉCNICO");
        System.out.println("==================================================================");
        System.out.println("  Rango fecha_completado: " + formatearRango(reporte.getFechaDesde(), reporte.getFechaHasta()));
        if (reporte.sinLineasEnPeriodo()) {
            System.out.println("  [i] No hay órdenes completadas en el rango indicado; los técnicos activos aparecen en cero.");
        }

        System.out.println();
        List<String[]> filas = new ArrayList<>();
        for (ProductividadTecnico tecnico : reporte.getTecnicos()) {
            String nombre = tecnico.isActivo() ? tecnico.getNombre() : tecnico.getNombre() + " (inactivo)";
            filas.add(new String[]{
                    nombre,
                    String.valueOf(tecnico.getLineasCompletadas()),
                    tecnico.getHorasTrabajadas().toPlainString(),
                    "$" + tecnico.getManoObraAtribuida().toPlainString()
            });
        }
        ConsoleTableFormatter.imprimirTabla(
                new String[]{"Técnico", "Líneas", "Horas", "Mano de obra"},
                filas,
                new boolean[]{false, true, true, true});
        System.out.println("  Sin distribuir: $" + reporte.getManoObraSinDistribuir().toPlainString());
        System.out.println("  TOTAL general:  $" + reporte.getManoObraTotal().toPlainString());
        System.out.println("==================================================================");
    }

    private void mostrarReporteConsumoRepuestos() {
        try {
            System.out.println("\n--- Reporte de Consumo de Repuestos (RF-27) ---");
            System.out.println("  Criterio de rango: fecha de completado de órdenes Completadas, Facturadas o Entregadas.");
            LocalDate desde = ConsoleReader.leerFechaOpcional("Completado desde (yyyy-MM-dd, Enter para sin límite)");
            LocalDate hasta = ConsoleReader.leerFechaOpcional("Completado hasta (yyyy-MM-dd, Enter para sin límite)");

            ReporteConsumoRepuestos reporte = reportesController.generarReporteConsumoRepuestos(desde, hasta);
            imprimirReporteConsumoRepuestos(reporte);
        } catch (CarMotorsException e) {
            System.out.println("\n  [!] No se pudo generar el reporte: " + e.getMessage());
        } finally {
            ConsoleReader.pausar();
        }
    }

    private void imprimirReporteConsumoRepuestos(ReporteConsumoRepuestos reporte) {
        System.out.println("\n==================================================");
        System.out.println("         CONSUMO DE REPUESTOS (VALOR COMERCIAL)   ");
        System.out.println("==================================================");
        System.out.println("  Rango fecha_completado: " + formatearRango(reporte.getFechaDesde(), reporte.getFechaHasta()));
        System.out.println("  Nota: Excluye reservas de órdenes Pendientes/En Proceso y órdenes Canceladas.");
        System.out.println("        Valorizado al precio de venta histórico (valor comercial, no costo ni utilidad).");

        if (reporte.estaVacio()) {
            System.out.println("\n  [i] No se encontraron consumos de repuestos en el rango de fechas indicado.");
            return;
        }

        System.out.println("\n  1) Consumo total por repuesto (ordenado por valor comercial descendente):");
        List<String[]> filasRepuesto = new ArrayList<>();
        for (ResumenConsumo item : reporte.getPorRepuesto()) {
            filasRepuesto.add(new String[]{
                    String.valueOf(item.getId()),
                    item.getNombre(),
                    String.valueOf(item.getUnidades()),
                    "$" + item.getValorComercial().toPlainString()
            });
        }
        ConsoleTableFormatter.imprimirTabla(
                new String[]{"ID", "Repuesto", "Unidades", "Valor comercial"},
                filasRepuesto,
                new boolean[]{true, false, true, true});

        System.out.println("\n  2) Consumo total por tipo de servicio (ordenado por valor comercial descendente):");
        List<String[]> filasServicio = new ArrayList<>();
        for (ResumenConsumo item : reporte.getPorTipoServicio()) {
            filasServicio.add(new String[]{
                    String.valueOf(item.getId()),
                    item.getNombre(),
                    String.valueOf(item.getUnidades()),
                    "$" + item.getValorComercial().toPlainString()
            });
        }
        ConsoleTableFormatter.imprimirTabla(
                new String[]{"ID", "Tipo de servicio", "Unidades", "Valor comercial"},
                filasServicio,
                new boolean[]{true, false, true, true});

        System.out.println("\n  3) Desglose por repuesto y tipo de servicio:");
        List<String[]> filasDetalle = new ArrayList<>();
        for (DetalleConsumo detalle : reporte.getDetallePorRepuestoYServicio()) {
            filasDetalle.add(new String[]{
                    String.valueOf(detalle.getRepuestoId()),
                    detalle.getNombreRepuesto(),
                    String.valueOf(detalle.getServicioTipoId()),
                    detalle.getNombreServicio(),
                    String.valueOf(detalle.getUnidades()),
                    "$" + detalle.getValorComercial().toPlainString()
            });
        }
        ConsoleTableFormatter.imprimirTabla(
                new String[]{"ID Rep.", "Repuesto", "ID Serv.", "Servicio", "Unidades", "Valor comercial"},
                filasDetalle,
                new boolean[]{true, false, true, false, true, true});

        System.out.println("\n  --------------------------------------------------");
        System.out.println("  Total unidades consumidas: " + reporte.getTotalUnidades());
        System.out.println("  Valor comercial total:     $" + reporte.getValorComercialTotal().toPlainString());
        System.out.println("  --------------------------------------------------");
    }

    private String formatearRango(LocalDate desde, LocalDate hasta) {
        String textoDesde = desde != null ? desde.format(FORMATO_FECHA) : "Inicio";
        String textoHasta = hasta != null ? hasta.format(FORMATO_FECHA) : "Actualidad";
        return textoDesde + " a " + textoHasta;
    }
}
