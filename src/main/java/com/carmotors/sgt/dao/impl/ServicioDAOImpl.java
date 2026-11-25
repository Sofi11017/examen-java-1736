package com.carmotors.sgt.dao.impl;

import com.carmotors.sgt.config.ConnectionFactory;
import com.carmotors.sgt.dao.ServicioDAO;
import com.carmotors.sgt.exception.CarMotorsException;
import com.carmotors.sgt.model.Servicio;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementación JDBC de {@link ServicioDAO} para consultar servicios completados.
 */
public class ServicioDAOImpl implements ServicioDAO {

    private final Connection conexionCompartida;

    public ServicioDAOImpl() {
        this(null);
    }

    public ServicioDAOImpl(Connection conexionCompartida) {
        this.conexionCompartida = conexionCompartida;
    }

    @Override
    public List<Servicio> listarServiciosConFechaEntrega() {
        String sql = "SELECT os.id AS orden_id, osd.id AS detalle_id, "
                + "os.fecha_recepcion, os.fecha_entrega, "
                + "mt.nombre AS tipo_mantenimiento, "
                + "osd.nombre_servicio_snapshot AS nombre_servicio "
                + "FROM ordenes_servicio os "
                + "INNER JOIN orden_servicio_detalle osd ON osd.orden_id = os.id "
                + "INNER JOIN servicio_tipos st ON st.id = osd.servicio_tipo_id "
                + "INNER JOIN mantenimiento_tipos mt ON mt.id = st.mantenimiento_tipo_id "
                + "WHERE os.fecha_recepcion IS NOT NULL AND os.fecha_entrega IS NOT NULL "
                + "ORDER BY os.id ASC, osd.id ASC";

        if (conexionCompartida != null) {
            try {
                return ejecutarConsulta(conexionCompartida, sql);
            } catch (SQLException e) {
                throw new CarMotorsException("Error al consultar servicios completados: " + e.getMessage(), e);
            }
        }

        try (Connection connection = ConnectionFactory.getInstance().getConnection()) {
            return ejecutarConsulta(connection, sql);
        } catch (SQLException e) {
            throw new CarMotorsException("Error al consultar servicios completados: " + e.getMessage(), e);
        }
    }

    private List<Servicio> ejecutarConsulta(Connection connection, String sql) throws SQLException {
        List<Servicio> servicios = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                Timestamp tsRecepcion = rs.getTimestamp("fecha_recepcion");
                Timestamp tsEntrega = rs.getTimestamp("fecha_entrega");
                LocalDateTime fechaRecepcion = (tsRecepcion != null) ? tsRecepcion.toLocalDateTime() : null;
                LocalDateTime fechaEntrega = (tsEntrega != null) ? tsEntrega.toLocalDateTime() : null;

                servicios.add(new Servicio(
                        rs.getInt("detalle_id"),
                        rs.getInt("orden_id"),
                        rs.getString("nombre_servicio"),
                        rs.getString("tipo_mantenimiento"),
                        fechaRecepcion,
                        fechaEntrega
                ));
            }
        }
        return servicios;
    }
}
