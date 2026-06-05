package com.is1.proyecto.models;

import org.javalite.activejdbc.Model;
import org.javalite.activejdbc.annotations.IdName;
import org.javalite.activejdbc.annotations.Table;

@Table("PlanDeEstudios")
@IdName("cod_plan") 
public class PlanDeEstudios extends Model{

  public Integer getCod() {
        return getInteger("cod_plan");
    }

    public void setCod(Integer cod_plan) {
        set("cod_plan", cod_plan);
    }

    public Integer getAño() {
        return getInteger("año");
    }

    public void setAño(Integer año) {
        set("año", año);
    }

    public Integer getVigencia() {
        return getInteger("vigencia");
    }

    public void setVigencia(Integer vigencia) {
        set("vigencia", vigencia);
    }

    public Integer getAñosTotal() {
        return getInteger("años_total");
    }

    public void setAñosTotal(Integer años_total) {
        set("años_total", años_total);
    }

    public Integer getCantidadMaterias() {
        return getInteger("cantidad_materias_total");
    }

    public void setCantidadMaterias(Integer cantidad_materias_total) {
        set("cantidad_materias_total", cantidad_materias_total);
    }

    public void setCodCarrera(Integer codCarrera) {
        set("cod_carrera", codCarrera);
    }

    public Integer getCodCarrera() {
        return getInteger("cod_carrera");
    }
}