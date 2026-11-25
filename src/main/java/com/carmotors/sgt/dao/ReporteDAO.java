package com.carmotors.sgt.dao;

import com.carmotors.sgt.model.RegistroConsumoRepuesto;
import com.carmotors.sgt.model.RegistroMantenimientoPreventivo;
import com.carmotors.sgt.model.RegistroProductividadTecnico;
import com.carmotors.sgt.model.RegistroServicioSolicitado;
import com.carmotors.sgt.model.Servicio;

import java.time.LocalDate;
import java.util.List;

/**
 * Contrato de consultas de solo lectura para los reportes gerenciales (RF-21, RF-25, RF-26, RF-27).
 * Las reglas de agregación, filtrado de negocio y ordenamiento con Stream API residen en {@code service}.
 */
public interface ReporteDAO {

    /**
     * Consulta los registros históricos de repuestos asignados en órdenes completadas o posteriores
     * ({@code Completado}, {@code Facturado}, {@code Entregado}) acotados por {@code fecha_completado}
     * (RF-27, §4.2).
     *
     * @param fechaDesde límite inferior inclusivo de {@code fecha_completado} (opcional, puede ser {@code null})
     * @param fechaHasta límite superior inclusivo de {@code fecha_completado} (opcional, puede ser {@code null})
     * @return lista de registros históricos con snapshots de nombre y precio unitario de venta
     */
    List<RegistroConsumoRepuesto> listarConsumoRepuestos(LocalDate fechaDesde, LocalDate fechaHasta);

    /** Recupera las líneas finalizadas y sus repuestos en un rango inclusivo de fechas. */
    List<RegistroServicioSolicitado> listarServiciosSolicitados(LocalDate fechaDesde, LocalDate fechaHasta);

    /**
     * Recupera un registro por cada técnico asignado a una línea de órdenes Completadas, Facturadas o
     * Entregadas cuya {@code fecha_completado} esté entre el inicio del día {@code fechaDesde} y el final
     * del día {@code fechaHasta} (RF-26, §4.2). Las líneas sin técnicos llegan con {@code tecnicoId} nulo.
     *
     * @param fechaDesde límite inferior inclusivo (obligatorio)
     * @param fechaHasta límite superior inclusivo (obligatorio)
     * @return registros planos de línea y técnico ordenados por línea y técnico
     */
    List<RegistroProductividadTecnico> listarProductividadTecnicos(LocalDate fechaDesde, LocalDate fechaHasta);

    /**
     * Recupera un registro por cada línea de servicio preventivo con {@code intervalo_meses} definido
     * que pertenece a una orden Entregada, con los datos del vehículo, su cliente actual, el tipo de
     * servicio y la {@code fecha_entrega} de la orden (RF-21, RN-15, §4.2). No filtra por fecha: el
     * servicio elige la entrega más reciente de cada vehículo y tipo.
     *
     * @return registros planos ordenados por vehículo, tipo de servicio y fecha de entrega descendente
     */
    List<RegistroMantenimientoPreventivo> listarMantenimientosPreventivos();

    /**
     * Consulta y devuelve la lista de todos los servicios que cuentan con registro
     * de fecha_entrega y fecha_recepcion en el sistema, junto a su tipo de mantenimiento.
     *
     * @return lista de servicios completados
     */
    default List<Servicio> listarServiciosConFechaEntrega() {
        return java.util.Collections.emptyList();
    }

    /**
     * Alias de compatibilidad para listar todos los servicios completados.
     *
     * @return lista de servicios completados
     */
    default List<Servicio> listarServiciosCompletados() {
        return listarServiciosConFechaEntrega();
    }
}
