package com.carmotors.sgt.dao;

import com.carmotors.sgt.model.Servicio;

import java.util.List;

/**
 * Contrato de acceso a datos para servicios completados y operaciones de catálogo de servicios.
 */
@FunctionalInterface
public interface ServicioDAO {

    /**
     * Consulta y devuelve la lista de todos los servicios que cuentan con registro
     * de fecha_entrega y fecha_recepcion en el sistema, junto a su tipo de mantenimiento.
     *
     * @return lista de servicios completados
     */
    List<Servicio> listarServiciosConFechaEntrega();

    /**
     * Alias de compatibilidad para listar todos los servicios completados.
     *
     * @return lista de servicios completados
     */
    default List<Servicio> listarServiciosCompletados() {
        return listarServiciosConFechaEntrega();
    }
}
