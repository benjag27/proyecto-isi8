package com.is1.proyecto.models;

import org.javalite.activejdbc.Model;
import org.javalite.activejdbc.annotations.Table;

@Table("Materia")
public class Materia extends Model {
    public Integer getCodMateria() {
        return getInteger("cod_materia");
    }

    public void setCodMateria(Integer cod_materia) {
        set("cod_materia", cod_materia);
    }

    public String getNombre() {
        return getString("nombre");
    }

    public void setNombre(String nombre) {
        set("nombre", nombre);
    }

    public String getDescripcion() {
        return getString("descripcion");
    }

    public void setDescripcion(String descripcion) {
        set("descripcion", descripcion);
    }

    // CodigoPlan
    public Integer getCodPlan() {
        return getInteger("cod_plan");
    }

    public void setCodPlan(Integer codPlan) {
        set("cod_plan", codPlan);
    }
}