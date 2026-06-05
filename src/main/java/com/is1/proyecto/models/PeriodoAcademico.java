package com.is1.proyecto.models;

import org.javalite.activejdbc.Model;
import org.javalite.activejdbc.annotations.IdName;
import org.javalite.activejdbc.annotations.Table;

@Table("PeriodoAcademico")
@IdName("id")
public class PeriodoAcademico extends Model {

    public Integer getId() {
        return getInteger("id");
    }

    public Integer getCodigoProfesor() {
        return getInteger("codigo_profesor");
    }

    public void setCodigoProfesor(Integer codigoProfesor) {
        set("codigo_profesor", codigoProfesor);
    }

    public Integer getCodMateria() {
        return getInteger("cod_materia");
    }

    public void setCodMateria(Integer codMateria) {
        set("cod_materia", codMateria);
    }

    public String getFecha() {
        return getString("fecha");
    }

    public void setFecha(String fecha) {
        set("fecha", fecha);
    }

    public String getCargo() {
        return getString("cargo");
    }

    public void setCargo(String cargo) {
        set("cargo", cargo);
    }
}