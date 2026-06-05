package com.is1.proyecto.models;

import org.javalite.activejdbc.annotations.Table;

@Table("Docente")
public class Docente extends Persona {
    public Integer getDNI() {
        return getInteger("dni");
    }

    public void setDNI(Integer dni) {
        set("dni", dni);
    }

    public Integer getCodigoProfesor() {
        return getInteger("codigo_profesor");
    }

    public void setCodigoProfesor(Integer codigo) {
        set("codigo_profesor", codigo);
    }

    public String getEmail() {
        return getString("email");
    }

    public void setEmail(String email) {
        set("email", email);
    }
}
