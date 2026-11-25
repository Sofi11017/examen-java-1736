package com.carmotors.sgt.service;

import com.carmotors.sgt.dao.ReporteDAO;
import com.carmotors.sgt.dao.ServicioDAO;
import com.carmotors.sgt.dao.TecnicoDAO;

/**
 * Servicio de reportes de referencia (Examen 1736). Extiende {@link ReporteService}.
 */
public class ReporteServicio extends ReporteService {

    public ReporteServicio() {
        super();
    }

    public ReporteServicio(ReporteDAO reporteDAO) {
        super(reporteDAO);
    }

    public ReporteServicio(ReporteDAO reporteDAO, TecnicoDAO tecnicoDAO) {
        super(reporteDAO, tecnicoDAO);
    }

    public ReporteServicio(ReporteDAO reporteDAO, TecnicoDAO tecnicoDAO, ServicioDAO servicioDAO) {
        super(reporteDAO, tecnicoDAO, servicioDAO);
    }
}
