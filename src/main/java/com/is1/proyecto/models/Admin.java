package com.is1.proyecto.models;

import org.javalite.activejdbc.annotations.Table;

@Table("Admin")
public class Admin extends Persona {

    public Integer getDni() {
        return getInteger("dni");
    }

    public void setDni(Integer dni) {
        set("dni", dni);
    }

    public Integer getUserId() {
        return getInteger("user_id");
    }

    public void setUserId(Integer userId) {
        set("user_id", userId);
    }

    // Permisos especiales del admin
    public boolean puedeGestionarUsuarios() { return true; }
    public boolean puedeGestionarCarreras()  { return true; }
    public boolean puedeGestionarDocentes()  { return true; }
    public boolean puedeGestionarEstudiantes() { return true; }
    public boolean puedeVerReportes()        { return true; }
}