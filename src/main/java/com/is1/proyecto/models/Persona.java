package com.is1.proyecto.models;

import org.javalite.activejdbc.Model;
import org.javalite.activejdbc.annotations.Table;

@Table("Persona")
public class Persona extends Model {
    public Integer getDni() {
        return getInteger("dni");
    }

    public void setDni(Integer dni) {
        set("dni", dni);
    }

    public String getNombre() {
        return getString("nombre");
    }

    public void setNombre(String nombre) {
        set("nombre", nombre);
    }

    public String getApellido() {
        return getString("apellido");
    }

    public void setApellido(String apellido) {
        set("apellido", apellido);
    }

    public String getFechaNacimiento() {
        return getString("fecha_nacimiento");
    }

    public void setFechaNacimiento(String fecha_nacimiento) {
        set("fecha_nacimiento", fecha_nacimiento);
    }

    public String getTelefono() {
        return getString("telefono");
    }

    public void setTelefono(String telefono) {
        set("telefono", telefono);
    }

    public String getDireccion() {
        return getString("direccion");
    }

    public void setDireccion(String direccion) {
        set("direccion", direccion);
    }
}