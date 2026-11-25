package com.carmotors.sgt.dao.impl;

import com.carmotors.sgt.config.ConnectionFactory;
import com.carmotors.sgt.exception.CarMotorsException;
import com.carmotors.sgt.model.RegistroConsumoRepuesto;
import com.carmotors.sgt.model.RegistroMantenimientoPreventivo;
import com.carmotors.sgt.model.RegistroProductividadTecnico;
import com.carmotors.sgt.model.RegistroServicioSolicitado;
import com.carmotors.sgt.dao.ReporteDAO;
import com.carmotors.sgt.dao.ServicioDAO;
import com.carmotors.sgt.model.Servicio;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Implementación JDBC de las consultas de reportes gerenciales (RNF-05, RNF-06).
 * Todas las consultas parametrizables utilizan {@link PreparedStatement}.
 */
public class ReporteDAOImpl implements ReporteDAO, ServicioDAO {

    private static final Logger LOGGER = Logger.getLogger(ReporteDAOImpl.class.getName());

    /** IDs de {@code servicio_estados} para órdenes completadas o posteriores (§4.1). */
    private static final int ESTADO_COMPLETADO = 3;
    private static final int ESTADO_FACTURADO = 4;
    private static final int ESTADO_ENTREGADO = 5;

    /** ID de {@code mantenimiento_tipos} para los servicios preventivos. */
    private static final int MANTENIMIENTO_PREVENTIVO = 1;

    private final Connection conexionCompartida;

    public ReporteDAOImpl() {
        this(null);
    }

    public ReporteDAOImpl(Connection conexionCompartida) {
        this.conexionCompartida = conexionCompartida;
    }

    @Override
    public List<RegistroConsumoRepuesto> listarConsumoRepuestos(LocalDate fechaDesde, LocalDate fechaHasta) {
        StringBuilder sql = new StringBuilder(
                "SELECT o.id AS orden_id, "
                        + "o.estado_id, "
                        + "o.fecha_completado, "
                        + "dr.repuesto_id, "
                        + "dr.nombre_repuesto_snapshot, "
                        + "osd.servicio_tipo_id, "
                        + "osd.nombre_servicio_snapshot, "
                        + "dr.cantidad_asignada, "
                        + "dr.precio_unitario_venta "
                        + "FROM ordenes_servicio o "
                        + "INNER JOIN orden_servicio_detalle osd ON osd.orden_id = o.id "
                        + "INNER JOIN detalle_repuestos dr ON dr.detalle_id = osd.id "
                        + "WHERE o.estado_id IN (3, 4, 5) "
                        + "AND o.fecha_completado IS NOT NULL");

        if (fechaDesde != null) {
            sql.append(" AND o.fecha_completado >= ?");
        }
        if (fechaHasta != null) {
            sql.append(" AND o.fecha_completado < ?");
        }
        sql.append(" ORDER BY o.fecha_completado ASC, o.id ASC, osd.id ASC, dr.repuesto_id ASC");

        if (conexionCompartida != null) {
            return ejecutarConsultaConsumo(conexionCompartida, sql.toString(), fechaDesde, fechaHasta);
        }

        try (Connection connection = ConnectionFactory.getInstance().getConnection()) {
            return ejecutarConsultaConsumo(connection, sql.toString(), fechaDesde, fechaHasta);
        } catch (SQLException e) {
            throw new CarMotorsException("Error al consultar el consumo de repuestos: " + e.getMessage(), e);
        }
    }

    @Override
    public List<RegistroServicioSolicitado> listarServiciosSolicitados(LocalDate fechaDesde, LocalDate fechaHasta) {
        try {
            if (conexionCompartida != null) {
                return ejecutarConsultaServicios(conexionCompartida, fechaDesde, fechaHasta);
            }
            try (Connection connection = ConnectionFactory.getInstance().getConnection()) {
                return ejecutarConsultaServicios(connection, fechaDesde, fechaHasta);
            }
        } catch (SQLException e) {
            LOGGER.warning("Falló la consulta de servicios solicitados (código JDBC " + e.getErrorCode() + ").");
            throw new CarMotorsException("No se pudieron consultar los servicios solicitados.", e);
        }
    }

    @Override
    public List<RegistroProductividadTecnico> listarProductividadTecnicos(LocalDate fechaDesde, LocalDate fechaHasta) {
        try {
            if (conexionCompartida != null) {
                return ejecutarConsultaProductividad(conexionCompartida, fechaDesde, fechaHasta);
            }
            try (Connection connection = ConnectionFactory.getInstance().getConnection()) {
                return ejecutarConsultaProductividad(connection, fechaDesde, fechaHasta);
            }
        } catch (SQLException e) {
            LOGGER.warning("Falló la consulta de productividad por técnico (código JDBC " + e.getErrorCode() + ").");
            throw new CarMotorsException("No se pudo consultar la productividad por técnico.", e);
        }
    }

    private List<RegistroProductividadTecnico> ejecutarConsultaProductividad(Connection connection,
                                                                             LocalDate fechaDesde,
                                                                             LocalDate fechaHasta) throws SQLException {
        String sql = "SELECT osd.id AS detalle_id, osd.costo_mano_obra, dt.tecnico_id, t.nombre AS nombre_tecnico, "
                + "t.activo AS tecnico_activo, dt.horas_trabajadas "
                + "FROM ordenes_servicio o "
                + "JOIN orden_servicio_detalle osd ON osd.orden_id = o.id "
                + "LEFT JOIN detalle_tecnicos dt ON dt.detalle_id = osd.id "
                + "LEFT JOIN tecnicos t ON t.id = dt.tecnico_id "
                + "WHERE o.estado_id IN (?, ?, ?) AND o.fecha_completado >= ? AND o.fecha_completado < ? "
                + "ORDER BY osd.id ASC, dt.tecnico_id ASC";

        List<RegistroProductividadTecnico> registros = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, ESTADO_COMPLETADO);
            statement.setInt(2, ESTADO_FACTURADO);
            statement.setInt(3, ESTADO_ENTREGADO);
            statement.setTimestamp(4, Timestamp.valueOf(fechaDesde.atStartOfDay()));
            statement.setTimestamp(5, Timestamp.valueOf(fechaHasta.plusDays(1).atStartOfDay()));
            try (ResultSet filas = statement.executeQuery()) {
                while (filas.next()) {
                    int tecnicoId = filas.getInt("tecnico_id");
                    Integer tecnico = filas.wasNull() ? null : tecnicoId;
                    registros.add(new RegistroProductividadTecnico(filas.getInt("detalle_id"),
                            filas.getBigDecimal("costo_mano_obra"), tecnico, filas.getString("nombre_tecnico"),
                            filas.getBoolean("tecnico_activo"), filas.getBigDecimal("horas_trabajadas")));
                }
            }
        }
        return registros;
    }

    @Override
    public List<RegistroMantenimientoPreventivo> listarMantenimientosPreventivos() {
        try {
            if (conexionCompartida != null) {
                return ejecutarConsultaMantenimientos(conexionCompartida);
            }
            try (Connection connection = ConnectionFactory.getInstance().getConnection()) {
                return ejecutarConsultaMantenimientos(connection);
            }
        } catch (SQLException e) {
            LOGGER.warning("Falló la consulta de mantenimientos preventivos (código JDBC " + e.getErrorCode() + ").");
            throw new CarMotorsException("No se pudieron consultar los mantenimientos preventivos.", e);
        }
    }

    private List<RegistroMantenimientoPreventivo> ejecutarConsultaMantenimientos(Connection connection)
            throws SQLException {
        String sql = "SELECT v.id AS vehiculo_id, v.placa, v.marca, v.modelo, c.id AS cliente_id, "
                + "c.nombre AS cliente_nombre, c.telefono AS cliente_telefono, c.email AS cliente_email, "
                + "c.activo AS cliente_activo, st.id AS servicio_tipo_id, st.nombre AS nombre_servicio, "
                + "st.intervalo_meses, o.id AS orden_id, o.fecha_entrega "
                + "FROM ordenes_servicio o "
                + "JOIN orden_servicio_detalle osd ON osd.orden_id = o.id "
                + "JOIN servicio_tipos st ON st.id = osd.servicio_tipo_id "
                + "JOIN vehiculos v ON v.id = o.vehiculo_id "
                + "JOIN clientes c ON c.id = v.cliente_id "
                + "WHERE o.estado_id = ? AND st.mantenimiento_tipo_id = ? "
                + "AND st.intervalo_meses IS NOT NULL AND o.fecha_entrega IS NOT NULL "
                + "ORDER BY v.id ASC, st.id ASC, o.fecha_entrega DESC, o.id DESC";

        List<RegistroMantenimientoPreventivo> registros = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, ESTADO_ENTREGADO);
            statement.setInt(2, MANTENIMIENTO_PREVENTIVO);
            try (ResultSet filas = statement.executeQuery()) {
                while (filas.next()) {
                    int intervalo = filas.getInt("intervalo_meses");
                    Integer intervaloMeses = filas.wasNull() ? null : intervalo;
                    registros.add(new RegistroMantenimientoPreventivo(filas.getInt("vehiculo_id"),
                            filas.getString("placa"), filas.getString("marca"), filas.getString("modelo"),
                            filas.getInt("cliente_id"), filas.getString("cliente_nombre"),
                            filas.getString("cliente_telefono"), filas.getString("cliente_email"),
                            filas.getBoolean("cliente_activo"), filas.getInt("servicio_tipo_id"),
                            filas.getString("nombre_servicio"), intervaloMeses, filas.getInt("orden_id"),
                            filas.getTimestamp("fecha_entrega").toLocalDateTime()));
                }
            }
        }
        return registros;
    }

    private List<RegistroServicioSolicitado> ejecutarConsultaServicios(Connection connection, LocalDate fechaDesde,
                                                                        LocalDate fechaHasta) throws SQLException {
        String sql = "SELECT osd.id AS detalle_id, osd.servicio_tipo_id, osd.nombre_servicio_snapshot AS nombre_servicio, "
                + "o.estado_id, o.fecha_completado, osd.costo_mano_obra, dr.cantidad_asignada, "
                + "dr.precio_unitario_venta "
                + "FROM ordenes_servicio o "
                + "JOIN orden_servicio_detalle osd ON osd.orden_id = o.id "
                + "LEFT JOIN detalle_repuestos dr ON dr.detalle_id = osd.id "
                + "WHERE o.estado_id IN (3, 4, 5) AND o.fecha_completado >= ? AND o.fecha_completado < ?";

        List<RegistroServicioSolicitado> registros = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.valueOf(fechaDesde.atStartOfDay()));
            statement.setTimestamp(2, Timestamp.valueOf(fechaHasta.plusDays(1).atStartOfDay()));
            try (ResultSet filas = statement.executeQuery()) {
                while (filas.next()) {
                    registros.add(new RegistroServicioSolicitado(filas.getInt("detalle_id"),
                            filas.getInt("servicio_tipo_id"), filas.getString("nombre_servicio"),
                            filas.getInt("estado_id"), filas.getTimestamp("fecha_completado").toLocalDateTime(),
                            filas.getBigDecimal("costo_mano_obra"), filas.getInt("cantidad_asignada"),
                            filas.getBigDecimal("precio_unitario_venta")));
                }
            }
        }
        return registros;
    }

    private List<RegistroConsumoRepuesto> ejecutarConsultaConsumo(Connection connection, String sql,
                                                                  LocalDate fechaDesde, LocalDate fechaHasta) {
        List<RegistroConsumoRepuesto> registros = new ArrayList<>();
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            int indice = 1;
            if (fechaDesde != null) {
                preparedStatement.setTimestamp(indice++, Timestamp.valueOf(fechaDesde.atStartOfDay()));
            }
            if (fechaHasta != null) {
                preparedStatement.setTimestamp(indice, Timestamp.valueOf(fechaHasta.plusDays(1).atStartOfDay()));
            }

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    Timestamp completado = resultSet.getTimestamp("fecha_completado");
                    RegistroConsumoRepuesto registro = new RegistroConsumoRepuesto(
                            resultSet.getInt("orden_id"),
                            resultSet.getInt("estado_id"),
                            completado != null ? completado.toLocalDateTime() : null,
                            resultSet.getInt("repuesto_id"),
                            resultSet.getString("nombre_repuesto_snapshot"),
                            resultSet.getInt("servicio_tipo_id"),
                            resultSet.getString("nombre_servicio_snapshot"),
                            resultSet.getInt("cantidad_asignada"),
                            resultSet.getBigDecimal("precio_unitario_venta")
                    );
                    registros.add(registro);
                }
            }
            return registros;
        } catch (SQLException e) {
            throw new CarMotorsException("Error al consultar el consumo de repuestos: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Servicio> listarServiciosCompletados() {
        return listarServiciosConFechaEntrega();
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
                return ejecutarConsultaServiciosCompletados(conexionCompartida, sql);
            } catch (SQLException e) {
                throw new CarMotorsException("Error al consultar servicios completados: " + e.getMessage(), e);
            }
        }

        try (Connection connection = ConnectionFactory.getInstance().getConnection()) {
            return ejecutarConsultaServiciosCompletados(connection, sql);
        } catch (SQLException e) {
            throw new CarMotorsException("Error al consultar servicios completados: " + e.getMessage(), e);
        }
    }

    private List<Servicio> ejecutarConsultaServiciosCompletados(Connection connection, String sql) throws SQLException {
        List<Servicio> servicios = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                Timestamp tsRecepcion = rs.getTimestamp("fecha_recepcion");
                Timestamp tsEntrega = rs.getTimestamp("fecha_entrega");
                java.time.LocalDateTime fechaRecepcion = (tsRecepcion != null) ? tsRecepcion.toLocalDateTime() : null;
                java.time.LocalDateTime fechaEntrega = (tsEntrega != null) ? tsEntrega.toLocalDateTime() : null;

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
