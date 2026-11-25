package com.carmotors.sgt.dao.impl;

import com.carmotors.sgt.config.ConnectionFactory;
import com.carmotors.sgt.exception.CarMotorsException;
import com.carmotors.sgt.model.ServicioEstado;
import com.carmotors.sgt.dao.ServicioEstadoDAO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementación JDBC para la consulta de estados de servicio.
 */
public class
ServicioEstadoDAOImpl implements ServicioEstadoDAO {

    private final Connection conexionCompartida;

    /**
     * Constructor por defecto que obtiene conexiones desde {@link ConnectionFactory}.
     */
    public ServicioEstadoDAOImpl() {
        this.conexionCompartida = null;
    }

    /**
     * Constructor que recibe una conexión compartida para participar en transacciones atómicas.
     *
     * @param conexion conexión JDBC compartida
     */
    public ServicioEstadoDAOImpl(Connection conexion) {
        this.conexionCompartida = conexion;
    }

    @Override
    public List<ServicioEstado> listarTodos() {
        String sql = "SELECT id, nombre FROM servicio_estados ORDER BY id ASC";
        if (conexionCompartida != null) {
            return consultarLista(conexionCompartida, sql);
        }
        try (Connection conn = ConnectionFactory.getInstance().getConnection()) {
            return consultarLista(conn, sql);
        } catch (SQLException e) {
            throw new CarMotorsException("Error al listar estados de servicio: " + e.getMessage(), e);
        }
    }

    @Override
    public ServicioEstado buscarPorId(int id) {
        String sql = "SELECT id, nombre FROM servicio_estados WHERE id = ?";
        if (conexionCompartida != null) {
            return consultarPorId(conexionCompartida, sql, id);
        }
        try (Connection conn = ConnectionFactory.getInstance().getConnection()) {
            return consultarPorId(conn, sql, id);
        } catch (SQLException e) {
            throw new CarMotorsException("Error al buscar estado de servicio por ID " + id + ": " + e.getMessage(), e);
        }
    }

    private List<ServicioEstado> consultarLista(Connection conn, String sql) {
        List<ServicioEstado> lista = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(new ServicioEstado(rs.getInt("id"), rs.getString("nombre")));
            }
            return lista;
        } catch (SQLException e) {
            throw new CarMotorsException("Error al consultar estados de servicio: " + e.getMessage(), e);
        }
    }

    private ServicioEstado consultarPorId(Connection conn, String sql, int id) {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new ServicioEstado(rs.getInt("id"), rs.getString("nombre"));
                }
                return null;
            }
        } catch (SQLException e) {
            throw new CarMotorsException("Error al consultar estado de servicio con ID " + id + ": " + e.getMessage(), e);
        }
    }
}
