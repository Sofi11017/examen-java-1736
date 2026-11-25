package com.carmotors.sgt.service;

import com.carmotors.sgt.exception.ValidacionException;
import com.carmotors.sgt.model.ProductividadTecnico;
import com.carmotors.sgt.model.RecordatorioMantenimiento;
import com.carmotors.sgt.model.RegistroConsumoRepuesto;
import com.carmotors.sgt.model.RegistroMantenimientoPreventivo;
import com.carmotors.sgt.model.RegistroProductividadTecnico;
import com.carmotors.sgt.model.RegistroServicioSolicitado;
import com.carmotors.sgt.model.ResumenServicioSolicitado;
import com.carmotors.sgt.model.ReporteConsumoRepuestos;
import com.carmotors.sgt.model.ReporteConsumoRepuestos.DetalleConsumo;
import com.carmotors.sgt.model.ReporteConsumoRepuestos.ResumenConsumo;
import com.carmotors.sgt.model.ReporteProductividadTecnicos;
import com.carmotors.sgt.model.Servicio;
import com.carmotors.sgt.model.Tecnico;
import com.carmotors.sgt.model.TiempoPromedioReparacion;
import com.carmotors.sgt.dao.ReporteDAO;
import com.carmotors.sgt.dao.ServicioDAO;
import com.carmotors.sgt.dao.TecnicoDAO;
import com.carmotors.sgt.dao.impl.ReporteDAOImpl;
import com.carmotors.sgt.dao.impl.ServicioDAOImpl;
import com.carmotors.sgt.dao.impl.TecnicoDAOImpl;
import com.carmotors.sgt.service.state.EstadoCompletado;
import com.carmotors.sgt.service.state.EstadoEntregado;
import com.carmotors.sgt.service.state.EstadoFacturado;
import com.carmotors.sgt.util.InputValidator;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Capa de negocio para los reportes gerenciales de CarMotors SGT (RF-21, RF-25, RF-26, RF-27).
 * Procesa, agrupa y ordena los datos históricos mediante Lambdas y Stream API (RNF-11),
 * utilizando {@link BigDecimal} con escala 2 y redondeo {@code HALF_UP} (RNF-15).
 */
public class ReporteService {

    private static final BigDecimal CERO_MONETARIO =
            BigDecimal.ZERO.setScale(InputValidator.ESCALA_MONETARIA, InputValidator.REDONDEO_MONETARIO);
    private static final int ESTADO_COMPLETADO = new EstadoCompletado().getEstadoId();
    private static final int ESTADO_FACTURADO = new EstadoFacturado().getEstadoId();
    private static final int ESTADO_ENTREGADO = new EstadoEntregado().getEstadoId();

    /** Días hacia adelante en los que un recordatorio se considera próximo (RF-21). */
    private static final int DIAS_RECORDATORIO_PROXIMO = 30;

    private final ReporteDAO reporteDAO;
    private final TecnicoDAO tecnicoDAO;
    private final ServicioDAO servicioDAO;

    public ReporteService() {
        this(new ReporteDAOImpl(), new TecnicoDAOImpl());
    }

    public ReporteService(ReporteDAO reporteDAO) {
        this(reporteDAO, new TecnicoDAOImpl());
    }

    /**
     * Constructor con el DAO de técnicos inyectado, necesario para incluir en el reporte de
     * productividad (RF-26) a los técnicos activos sin trabajo en el periodo.
     */
    public ReporteService(ReporteDAO reporteDAO, TecnicoDAO tecnicoDAO) {
        this(reporteDAO, tecnicoDAO, reporteDAO instanceof ServicioDAO ? (ServicioDAO) reporteDAO : new ServicioDAOImpl());
    }

    public ReporteService(ReporteDAO reporteDAO, TecnicoDAO tecnicoDAO, ServicioDAO servicioDAO) {
        if (reporteDAO == null) {
            throw new ValidacionException("El DAO de reportes es obligatorio.");
        }
        if (tecnicoDAO == null) {
            throw new ValidacionException("El DAO de técnicos es obligatorio.");
        }
        this.reporteDAO = reporteDAO;
        this.tecnicoDAO = tecnicoDAO;
        this.servicioDAO = servicioDAO != null ? servicioDAO : (reporteDAO instanceof ServicioDAO ? (ServicioDAO) reporteDAO : new ServicioDAOImpl());
    }

    /**
     * Genera el reporte de consumo de repuestos por repuesto y por tipo de servicio en un rango
     * de {@code fecha_completado} (RF-27, §4.2, §4.3, HU-13).
     *
     * <p>Solo contabiliza órdenes en estado {@code Completado}, {@code Facturado} o
     * {@code Entregado}; excluye estrictamente reservas de órdenes {@code Pendiente},
     * {@code En Proceso} y órdenes {@code Cancelada}. El valor comercial se calcula con el
     * {@code precio_unitario_venta} histórico guardado en {@code detalle_repuestos} (RN-11) y
     * todos los resultados se ordenan por valor comercial descendente.</p>
     *
     * @param fechaDesde límite inferior inclusivo de {@code fecha_completado} (puede ser {@code null})
     * @param fechaHasta límite superior inclusivo de {@code fecha_completado} (puede ser {@code null})
     * @return reporte consolidado con totales por repuesto, por tipo de servicio y desglose combinado
     */
    public ReporteConsumoRepuestos generarReporteConsumoRepuestos(LocalDate fechaDesde, LocalDate fechaHasta) {
        validarRangoFechas(fechaDesde, fechaHasta);

        List<RegistroConsumoRepuesto> registrosCrudos = reporteDAO.listarConsumoRepuestos(fechaDesde, fechaHasta);
        List<RegistroConsumoRepuesto> validos = registrosCrudos.stream()
                .filter(registro -> registro != null)
                .filter(registro -> esEstadoCompletadoOPosterior(registro.getEstadoId()))
                .filter(registro -> registro.getFechaCompletado() != null)
                .filter(registro -> estaEnRango(registro.getFechaCompletado().toLocalDate(), fechaDesde, fechaHasta))
                .filter(registro -> registro.getCantidadAsignada() > 0 && registro.getPrecioUnitarioVenta() != null)
                .collect(Collectors.toList());

        List<ResumenConsumo> porRepuesto = agruparPorRepuesto(validos);
        List<ResumenConsumo> porTipoServicio = agruparPorTipoServicio(validos);
        List<DetalleConsumo> detalleCombinado = agruparPorRepuestoYServicio(validos);

        int totalUnidades = validos.stream()
                .mapToInt(RegistroConsumoRepuesto::getCantidadAsignada)
                .sum();
        BigDecimal valorTotal = validos.stream()
                .map(this::calcularValorComercial)
                .reduce(CERO_MONETARIO, BigDecimal::add)
                .setScale(InputValidator.ESCALA_MONETARIA, InputValidator.REDONDEO_MONETARIO);

        return new ReporteConsumoRepuestos(
                fechaDesde, fechaHasta, porRepuesto, porTipoServicio, detalleCombinado, totalUnidades, valorTotal);
    }

    public List<ResumenServicioSolicitado> generarReporteServiciosSolicitados(LocalDate fechaDesde, LocalDate fechaHasta) {
        if (fechaDesde == null || fechaHasta == null) {
            throw new ValidacionException("Indique ambas fechas del rango de finalización.");
        }
        validarRangoFechas(fechaDesde, fechaHasta);

        return reporteDAO.listarServiciosSolicitados(fechaDesde, fechaHasta).stream()
                .filter(registro -> esEstadoCompletadoOPosterior(registro.getEstadoId()))
                .filter(registro -> registro.getFechaCompletado() != null)
                .filter(registro -> estaEnRango(registro.getFechaCompletado().toLocalDate(), fechaDesde, fechaHasta))
                .collect(Collectors.groupingBy(RegistroServicioSolicitado::getServicioTipoId)).values().stream()
                .map(this::resumirTipoServicio)
                .sorted(Comparator.comparingInt(ResumenServicioSolicitado::getCantidadLineas).reversed()
                        .thenComparingInt(ResumenServicioSolicitado::getServicioTipoId))
                .collect(Collectors.toList());
    }

    /**
     * Genera el reporte de productividad por técnico en un rango de {@code fecha_completado}
     * (RF-26, §4.2, §4.3).
     *
     * <p>La mano de obra de cada línea se reparte entre sus técnicos en proporción a sus horas
     * ({@code costo × horas_del_técnico / total_horas_de_la_línea}) con {@link BigDecimal}, escala 2 y
     * {@code HALF_UP}. La diferencia de centavos se asigna al técnico con más horas de la línea (en
     * empate, al de menor ID), de modo que la suma por línea es exactamente su costo. Si una línea no
     * tiene horas (o no tiene técnicos), su mano de obra va al total "sin distribuir". Incluye a los
     * técnicos con trabajo en el periodo (aunque hoy estén inactivos) y a los activos sin trabajo con
     * cero; se ordena por mano de obra descendente, horas descendente y nombre ascendente.</p>
     *
     * @param fechaDesde límite inferior inclusivo de {@code fecha_completado} (obligatorio)
     * @param fechaHasta límite superior inclusivo de {@code fecha_completado} (obligatorio)
     * @return reporte con el resultado por técnico, el total sin distribuir y el total del periodo
     */
    public ReporteProductividadTecnicos generarReporteProductividadTecnicos(LocalDate fechaDesde, LocalDate fechaHasta) {
        if (fechaDesde == null || fechaHasta == null) {
            throw new ValidacionException("Indique ambas fechas del rango de finalización.");
        }
        validarRangoFechas(fechaDesde, fechaHasta);

        List<RegistroProductividadTecnico> registros = reporteDAO.listarProductividadTecnicos(fechaDesde, fechaHasta)
                .stream()
                .filter(registro -> registro != null)
                .collect(Collectors.toList());

        Map<Integer, List<RegistroProductividadTecnico>> porLinea = registros.stream()
                .collect(Collectors.groupingBy(RegistroProductividadTecnico::getDetalleId));

        BigDecimal manoObraTotal = porLinea.values().stream()
                .map(linea -> costoDeLinea(linea))
                .reduce(CERO_MONETARIO, BigDecimal::add);
        BigDecimal sinDistribuir = porLinea.values().stream()
                .filter(linea -> sumarHoras(participantesDeLinea(linea)).signum() == 0)
                .map(linea -> costoDeLinea(linea))
                .reduce(CERO_MONETARIO, BigDecimal::add);
        Map<Integer, Map<Integer, BigDecimal>> repartoPorLinea = porLinea.entrySet().stream()
                .filter(entrada -> sumarHoras(participantesDeLinea(entrada.getValue())).signum() > 0)
                .collect(Collectors.toMap(entrada -> entrada.getKey(),
                        entrada -> repartirManoObra(entrada.getValue())));

        Map<Integer, List<RegistroProductividadTecnico>> porTecnico = registros.stream()
                .filter(registro -> registro.tieneTecnico())
                .collect(Collectors.groupingBy(RegistroProductividadTecnico::getTecnicoId));

        List<ProductividadTecnico> conTrabajo = porTecnico.values().stream()
                .map(trabajo -> resumirTecnico(trabajo, repartoPorLinea))
                .collect(Collectors.toList());
        List<ProductividadTecnico> activosSinTrabajo = tecnicoDAO.listarActivos().stream()
                .filter(tecnico -> !porTecnico.containsKey(tecnico.getId()))
                .map(tecnico -> tecnicoSinTrabajo(tecnico))
                .collect(Collectors.toList());

        List<ProductividadTecnico> resultado = new ArrayList<>(conTrabajo);
        resultado.addAll(activosSinTrabajo);
        List<ProductividadTecnico> ordenado = resultado.stream()
                .sorted(comparadorProductividad())
                .collect(Collectors.toList());

        return new ReporteProductividadTecnicos(fechaDesde, fechaHasta, ordenado, porLinea.size(),
                sinDistribuir, manoObraTotal);
    }

    /**
     * Genera el reporte de recordatorios de mantenimiento preventivo (RF-21, RN-15, §4.2).
     *
     * <p>Agrupa por vehículo y tipo de servicio y toma solo la entrega más reciente de cada grupo
     * (un servicio entregado más reciente reemplaza al anterior). La fecha sugerida es la fecha de
     * esa entrega más {@code intervalo_meses}. Incluye los vencidos y los que vencen dentro de los
     * próximos 30 días (ambos límites incluidos), solo de clientes activos. Ordena primero los
     * vencidos, del más atrasado al más reciente, y luego los próximos, del más cercano al más
     * lejano; en empate, por placa. No envía notificaciones: la gestión es manual.</p>
     *
     * @param fechaReferencia fecha desde la que se calculan vencidos y próximos (normalmente hoy)
     * @return recordatorios ordenados; lista vacía si no hay ninguno
     */
    public List<RecordatorioMantenimiento> generarReporteRecordatorios(LocalDate fechaReferencia) {
        if (fechaReferencia == null) {
            throw new ValidacionException("La fecha de referencia del reporte de recordatorios es obligatoria.");
        }
        LocalDate fechaLimite = fechaReferencia.plusDays(DIAS_RECORDATORIO_PROXIMO);

        Map<String, List<RegistroMantenimientoPreventivo>> porVehiculoYServicio = reporteDAO
                .listarMantenimientosPreventivos().stream()
                .filter(registro -> registro != null)
                .filter(registro -> registro.getFechaEntrega() != null)
                .filter(registro -> registro.getIntervaloMeses() != null && registro.getIntervaloMeses() > 0)
                .collect(Collectors.groupingBy(
                        registro -> registro.getVehiculoId() + ":" + registro.getServicioTipoId()));

        return porVehiculoYServicio.values().stream()
                .map(grupo -> ultimaEntrega(grupo))
                .filter(ultima -> ultima.isClienteActivo())
                .map(ultima -> crearRecordatorio(ultima, fechaReferencia))
                .filter(recordatorio -> !recordatorio.getFechaSugerida().isAfter(fechaLimite))
                .sorted(comparadorRecordatorios())
                .collect(Collectors.toList());
    }

    private RegistroMantenimientoPreventivo ultimaEntrega(List<RegistroMantenimientoPreventivo> grupo) {
        List<RegistroMantenimientoPreventivo> masRecientePrimero = grupo.stream()
                .sorted((a, b) -> {
                    int porFecha = b.getFechaEntrega().compareTo(a.getFechaEntrega());
                    if (porFecha != 0) {
                        return porFecha;
                    }
                    return Integer.compare(b.getOrdenId(), a.getOrdenId());
                })
                .collect(Collectors.toList());
        return masRecientePrimero.get(0);
    }

    private RecordatorioMantenimiento crearRecordatorio(RegistroMantenimientoPreventivo ultima,
                                                        LocalDate fechaReferencia) {
        LocalDate fechaEntrega = ultima.getFechaEntrega().toLocalDate();
        LocalDate fechaSugerida = fechaEntrega.plusMonths(ultima.getIntervaloMeses());
        int diasRestantes = (int) ChronoUnit.DAYS.between(fechaReferencia, fechaSugerida);
        return new RecordatorioMantenimiento(ultima.getClienteId(), ultima.getClienteNombre(),
                ultima.getClienteTelefono(), ultima.getClienteEmail(), ultima.getVehiculoId(), ultima.getPlaca(),
                ultima.getMarca(), ultima.getModelo(), ultima.getServicioTipoId(), ultima.getNombreServicio(),
                ultima.getIntervaloMeses(), ultima.getOrdenId(), fechaEntrega, fechaSugerida, diasRestantes);
    }

    /**
     * Vencidos antes que próximos; los vencidos del más atrasado al más reciente y los próximos del
     * más cercano al más lejano (en ambos casos, fecha sugerida ascendente); luego placa y servicio.
     */
    private Comparator<RecordatorioMantenimiento> comparadorRecordatorios() {
        return (a, b) -> {
            if (a.isVencido() != b.isVencido()) {
                return a.isVencido() ? -1 : 1;
            }
            int porFecha = a.getFechaSugerida().compareTo(b.getFechaSugerida());
            if (porFecha != 0) {
                return porFecha;
            }
            int porPlaca = a.getPlaca().compareToIgnoreCase(b.getPlaca());
            if (porPlaca != 0) {
                return porPlaca;
            }
            return a.getNombreServicio().compareToIgnoreCase(b.getNombreServicio());
        };
    }

    private Map<Integer, BigDecimal> repartirManoObra(List<RegistroProductividadTecnico> linea) {
        BigDecimal costo = costoDeLinea(linea);
        List<RegistroProductividadTecnico> participantes = participantesDeLinea(linea);
        BigDecimal totalHoras = sumarHoras(participantes);

        Map<Integer, BigDecimal> reparto = new HashMap<>();
        for (RegistroProductividadTecnico participante : participantes) {
            BigDecimal parte = costo.multiply(horasDe(participante))
                    .divide(totalHoras, InputValidator.ESCALA_MONETARIA, InputValidator.REDONDEO_MONETARIO);
            reparto.put(participante.getTecnicoId(), parte);
        }

        BigDecimal repartido = reparto.values().stream().reduce(CERO_MONETARIO, BigDecimal::add);
        BigDecimal diferencia = costo.subtract(repartido);
        if (diferencia.signum() != 0) {
            List<RegistroProductividadTecnico> prioridad = participantes.stream()
                    .sorted(comparadorCentavos())
                    .collect(Collectors.toList());
            int receptor = prioridad.get(0).getTecnicoId();
            reparto.put(receptor, reparto.get(receptor).add(diferencia));
        }
        return reparto;
    }

    private ProductividadTecnico resumirTecnico(List<RegistroProductividadTecnico> trabajo,
                                                Map<Integer, Map<Integer, BigDecimal>> repartoPorLinea) {
        RegistroProductividadTecnico primero = trabajo.get(0);
        int tecnicoId = primero.getTecnicoId();
        int lineas = (int) trabajo.stream()
                .map(registro -> registro.getDetalleId())
                .distinct()
                .count();
        BigDecimal manoObra = trabajo.stream()
                .map(registro -> montoAtribuido(repartoPorLinea, registro.getDetalleId(), tecnicoId))
                .reduce(CERO_MONETARIO, BigDecimal::add);
        String nombre = primero.getNombreTecnico() == null || primero.getNombreTecnico().isBlank()
                ? "Técnico " + tecnicoId : primero.getNombreTecnico().trim();
        return new ProductividadTecnico(tecnicoId, nombre, primero.isTecnicoActivo(), lineas,
                sumarHoras(trabajo), manoObra);
    }

    private ProductividadTecnico tecnicoSinTrabajo(Tecnico tecnico) {
        return new ProductividadTecnico(tecnico.getId(), tecnico.getNombre(), tecnico.isActivo(), 0,
                CERO_MONETARIO, CERO_MONETARIO);
    }

    private BigDecimal montoAtribuido(Map<Integer, Map<Integer, BigDecimal>> repartoPorLinea, int detalleId,
                                      int tecnicoId) {
        Map<Integer, BigDecimal> reparto = repartoPorLinea.get(detalleId);
        if (reparto == null || reparto.get(tecnicoId) == null) {
            return CERO_MONETARIO;
        }
        return reparto.get(tecnicoId);
    }

    private List<RegistroProductividadTecnico> participantesDeLinea(List<RegistroProductividadTecnico> linea) {
        return linea.stream()
                .filter(registro -> registro.tieneTecnico())
                .collect(Collectors.toList());
    }

    private BigDecimal costoDeLinea(List<RegistroProductividadTecnico> linea) {
        BigDecimal costo = linea.get(0).getCostoManoObra();
        return (costo == null ? CERO_MONETARIO : costo)
                .setScale(InputValidator.ESCALA_MONETARIA, InputValidator.REDONDEO_MONETARIO);
    }

    private BigDecimal sumarHoras(List<RegistroProductividadTecnico> registros) {
        return registros.stream()
                .map(registro -> horasDe(registro))
                .reduce(CERO_MONETARIO, BigDecimal::add)
                .setScale(InputValidator.ESCALA_MONETARIA, InputValidator.REDONDEO_MONETARIO);
    }

    private BigDecimal horasDe(RegistroProductividadTecnico registro) {
        return registro.getHorasTrabajadas() == null ? CERO_MONETARIO : registro.getHorasTrabajadas();
    }

    /** Recibe los centavos de redondeo el técnico con más horas; en empate, el de menor ID (§4.3). */
    private Comparator<RegistroProductividadTecnico> comparadorCentavos() {
        return (a, b) -> {
            int porHoras = horasDe(b).compareTo(horasDe(a));
            if (porHoras != 0) {
                return porHoras;
            }
            return Integer.compare(a.getTecnicoId(), b.getTecnicoId());
        };
    }

    private Comparator<ProductividadTecnico> comparadorProductividad() {
        return (a, b) -> {
            int porManoObra = b.getManoObraAtribuida().compareTo(a.getManoObraAtribuida());
            if (porManoObra != 0) {
                return porManoObra;
            }
            int porHoras = b.getHorasTrabajadas().compareTo(a.getHorasTrabajadas());
            if (porHoras != 0) {
                return porHoras;
            }
            int porNombre = a.getNombre().compareToIgnoreCase(b.getNombre());
            if (porNombre != 0) {
                return porNombre;
            }
            return Integer.compare(a.getTecnicoId(), b.getTecnicoId());
        };
    }

    private ResumenServicioSolicitado resumirTipoServicio(List<RegistroServicioSolicitado> registros) {
        RegistroServicioSolicitado servicio = registros.get(0);
        Map<Integer, List<RegistroServicioSolicitado>> porLinea = registros.stream()
                .collect(Collectors.groupingBy(RegistroServicioSolicitado::getDetalleId));
        BigDecimal ingresos = porLinea.values().stream()
                .map(this::calcularIngresoLinea)
                .reduce(CERO_MONETARIO, BigDecimal::add)
                .setScale(InputValidator.ESCALA_MONETARIA, InputValidator.REDONDEO_MONETARIO);

        return new ResumenServicioSolicitado(servicio.getServicioTipoId(), servicio.getNombreServicio(),
                porLinea.size(), ingresos);
    }

    private BigDecimal calcularIngresoLinea(List<RegistroServicioSolicitado> registros) {
        return registros.stream()
                .filter(registro -> registro.getPrecioUnitarioVenta() != null)
                .map(registro -> registro.getPrecioUnitarioVenta()
                        .multiply(BigDecimal.valueOf(registro.getCantidadRepuesto())))
                .reduce(registros.get(0).getManoDeObra(), BigDecimal::add);
    }

    private void validarRangoFechas(LocalDate fechaDesde, LocalDate fechaHasta) {
        if (fechaDesde != null && fechaHasta != null && fechaDesde.isAfter(fechaHasta)) {
            throw new ValidacionException("La fecha inicial debe ser anterior o igual a la fecha final.");
        }
    }

    private boolean esEstadoCompletadoOPosterior(int estadoId) {
        return estadoId == ESTADO_COMPLETADO
                || estadoId == ESTADO_FACTURADO
                || estadoId == ESTADO_ENTREGADO;
    }

    private boolean estaEnRango(LocalDate fecha, LocalDate desde, LocalDate hasta) {
        if (desde != null && fecha.isBefore(desde)) {
            return false;
        }
        return hasta == null || !fecha.isAfter(hasta);
    }

    private BigDecimal calcularValorComercial(RegistroConsumoRepuesto registro) {
        return registro.getPrecioUnitarioVenta()
                .multiply(BigDecimal.valueOf(registro.getCantidadAsignada()))
                .setScale(InputValidator.ESCALA_MONETARIA, InputValidator.REDONDEO_MONETARIO);
    }

    private List<ResumenConsumo> agruparPorRepuesto(List<RegistroConsumoRepuesto> registros) {
        Map<Integer, List<RegistroConsumoRepuesto>> grupos = registros.stream()
                .collect(Collectors.groupingBy(RegistroConsumoRepuesto::getRepuestoId));

        return grupos.entrySet().stream()
                .map(entry -> {
                    int repuestoId = entry.getKey();
                    List<RegistroConsumoRepuesto> items = entry.getValue();
                    String nombre = seleccionarNombreMasReciente(items, true);
                    int unidades = items.stream().mapToInt(RegistroConsumoRepuesto::getCantidadAsignada).sum();
                    BigDecimal valor = sumarValorComercial(items);
                    return new ResumenConsumo(repuestoId, nombre, unidades, valor);
                })
                .sorted(comparadorResumenConsumo())
                .collect(Collectors.toList());
    }

    private List<ResumenConsumo> agruparPorTipoServicio(List<RegistroConsumoRepuesto> registros) {
        Map<Integer, List<RegistroConsumoRepuesto>> grupos = registros.stream()
                .collect(Collectors.groupingBy(RegistroConsumoRepuesto::getServicioTipoId));

        return grupos.entrySet().stream()
                .map(entry -> {
                    int servicioTipoId = entry.getKey();
                    List<RegistroConsumoRepuesto> items = entry.getValue();
                    String nombre = seleccionarNombreMasReciente(items, false);
                    int unidades = items.stream().mapToInt(RegistroConsumoRepuesto::getCantidadAsignada).sum();
                    BigDecimal valor = sumarValorComercial(items);
                    return new ResumenConsumo(servicioTipoId, nombre, unidades, valor);
                })
                .sorted(comparadorResumenConsumo())
                .collect(Collectors.toList());
    }

    private List<DetalleConsumo> agruparPorRepuestoYServicio(List<RegistroConsumoRepuesto> registros) {
        Map<String, List<RegistroConsumoRepuesto>> grupos = registros.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getRepuestoId() + ":" + r.getServicioTipoId()));

        return grupos.values().stream()
                .map(items -> {
                    RegistroConsumoRepuesto primero = items.get(0);
                    String nombreRepuesto = seleccionarNombreMasReciente(items, true);
                    String nombreServicio = seleccionarNombreMasReciente(items, false);
                    int unidades = items.stream().mapToInt(RegistroConsumoRepuesto::getCantidadAsignada).sum();
                    BigDecimal valor = sumarValorComercial(items);
                    return new DetalleConsumo(
                            primero.getRepuestoId(), nombreRepuesto,
                            primero.getServicioTipoId(), nombreServicio,
                            unidades, valor);
                })
                .sorted(comparadorDetalleConsumo())
                .collect(Collectors.toList());
    }

    private BigDecimal sumarValorComercial(List<RegistroConsumoRepuesto> items) {
        return items.stream()
                .map(this::calcularValorComercial)
                .reduce(CERO_MONETARIO, BigDecimal::add)
                .setScale(InputValidator.ESCALA_MONETARIA, InputValidator.REDONDEO_MONETARIO);
    }

    private String seleccionarNombreMasReciente(List<RegistroConsumoRepuesto> items, boolean esRepuesto) {
        RegistroConsumoRepuesto seleccionado = items.get(0);
        for (RegistroConsumoRepuesto actual : items) {
            if (actual.getFechaCompletado().isAfter(seleccionado.getFechaCompletado())
                    || (actual.getFechaCompletado().isEqual(seleccionado.getFechaCompletado())
                    && actual.getOrdenId() > seleccionado.getOrdenId())) {
                seleccionado = actual;
            }
        }
        String nombre = esRepuesto ? seleccionado.getNombreRepuesto() : seleccionado.getNombreServicio();
        return nombre == null || nombre.isBlank() ? "Sin nombre" : nombre.trim();
    }

    private Comparator<ResumenConsumo> comparadorResumenConsumo() {
        return (a, b) -> {
            int porValor = b.getValorComercial().compareTo(a.getValorComercial());
            if (porValor != 0) {
                return porValor;
            }
            int porUnidades = Integer.compare(b.getUnidades(), a.getUnidades());
            if (porUnidades != 0) {
                return porUnidades;
            }
            int porNombre = a.getNombre().compareToIgnoreCase(b.getNombre());
            if (porNombre != 0) {
                return porNombre;
            }
            return Integer.compare(a.getId(), b.getId());
        };
    }

    private Comparator<DetalleConsumo> comparadorDetalleConsumo() {
        return (a, b) -> {
            int porValor = b.getValorComercial().compareTo(a.getValorComercial());
            if (porValor != 0) {
                return porValor;
            }
            int porUnidades = Integer.compare(b.getUnidades(), a.getUnidades());
            if (porUnidades != 0) {
                return porUnidades;
            }
            int porRepuesto = a.getNombreRepuesto().compareToIgnoreCase(b.getNombreRepuesto());
            if (porRepuesto != 0) {
                return porRepuesto;
            }
            int porServicio = a.getNombreServicio().compareToIgnoreCase(b.getNombreServicio());
            if (porServicio != 0) {
                return porServicio;
            }
            int porRepuestoId = Integer.compare(a.getRepuestoId(), b.getRepuestoId());
            if (porRepuestoId != 0) {
                return porRepuestoId;
            }
            return Integer.compare(a.getServicioTipoId(), b.getServicioTipoId());
        };
    }

    /**
     * Calcula los tiempos promedio de finalización de servicios completados agrupados por tipo
     * de mantenimiento ("Preventivo" y "Correctivo").
     *
     * @return lista de {@link TiempoPromedioReparacion} con los promedios calculados
     */
    public List<TiempoPromedioReparacion> calcularTiemposPromedioReparacion() {
        List<Servicio> servicios = servicioDAO != null
                ? servicioDAO.listarServiciosConFechaEntrega()
                : reporteDAO.listarServiciosConFechaEntrega();
        return calcularTiemposPromedioReparacion(servicios);
    }

    /**
     * Alias de conveniencia para {@link #calcularTiemposPromedioReparacion()}.
     */
    public List<TiempoPromedioReparacion> obtenerTiemposPromedioReparacion() {
        return calcularTiemposPromedioReparacion();
    }

    /**
     * Procesa la lista de servicios completados obtenida desde el DAO y calcula la duración de cada
     * servicio utilizando la API {@link java.time.Duration}.
     * A continuación, utiliza la API Stream y sus colectores ({@link Collectors#groupingBy}
     * y el colector de promedio {@link Collectors#averagingDouble}) para procesar la lista de
     * duraciones y obtener el promedio para cada tipo de mantenimiento.
     *
     * @param servicios lista de servicios completados
     * @return lista de {@link TiempoPromedioReparacion} con los promedios obtenidos
     */
    public List<TiempoPromedioReparacion> calcularTiemposPromedioReparacion(List<Servicio> servicios) {
        if (servicios == null || servicios.isEmpty()) {
            return Collections.emptyList();
        }

        // Agrupación y cálculo del promedio usando java.time.Duration y Stream API con groupingBy y averagingDouble
        Map<String, Double> promedioSegundosPorTipo = servicios.stream()
                .filter(s -> s != null && s.getTipoMantenimiento() != null && !s.getTipoMantenimiento().isBlank())
                .filter(s -> s.getFechaRecepcion() != null && s.getFechaEntrega() != null)
                .filter(s -> !s.getFechaEntrega().isBefore(s.getFechaRecepcion()))
                .collect(Collectors.groupingBy(
                        s -> s.getTipoMantenimiento().trim(),
                        Collectors.averagingDouble(s -> {
                            Duration duracion = Duration.between(s.getFechaRecepcion(), s.getFechaEntrega());
                            return (double) duracion.getSeconds();
                        })
                ));

        List<TiempoPromedioReparacion> resultado = new ArrayList<>();
        List<String> ordenPrioritario = List.of("Preventivo", "Correctivo");

        for (String tipo : ordenPrioritario) {
            if (promedioSegundosPorTipo.containsKey(tipo)) {
                long segundosPromedio = Math.round(promedioSegundosPorTipo.get(tipo));
                resultado.add(new TiempoPromedioReparacion(tipo, Duration.ofSeconds(segundosPromedio)));
            }
        }

        promedioSegundosPorTipo.forEach((tipo, seg) -> {
            if (!ordenPrioritario.contains(tipo)) {
                resultado.add(new TiempoPromedioReparacion(tipo, Duration.ofSeconds(Math.round(seg))));
            }
        });

        return resultado;
    }
}
