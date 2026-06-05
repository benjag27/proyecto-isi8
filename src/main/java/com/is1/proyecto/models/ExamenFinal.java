package com.is1.proyecto.models;
 
import org.javalite.activejdbc.Model;
import org.javalite.activejdbc.annotations.IdName;
import org.javalite.activejdbc.annotations.Table;
 
@Table("ExamenFinal")
@IdName("id")
public class ExamenFinal extends Model {
 
    // ---- Getters y Setters ----
 
    public Integer getId() {
        return getInteger("id");
    }
 
    public Integer getCodMateria() {
        return getInteger("cod_materia");
    }
 
    public void setCodMateria(Integer codMateria) {
        set("cod_materia", codMateria);
    }
 
    public Integer getCodigoProfesor() {
        return getInteger("codigo_profesor");
    }
 
    public void setCodigoProfesor(Integer codigoProfesor) {
        set("codigo_profesor", codigoProfesor);
    }
 
    public String getFecha() {
        return getString("fecha");
    }
 
    public void setFecha(String fecha) {
        set("fecha", fecha);
    }
 
    public String getHorario() {
        return getString("horario");
    }
 
    public void setHorario(String horario) {
        set("horario", horario);
    }
 
    public String getAula() {
        return getString("aula");
    }
 
    public void setAula(String aula) {
        set("aula", aula);
    }
 
    public String getObservaciones() {
        return getString("observaciones");
    }
 
    public void setObservaciones(String observaciones) {
        set("observaciones", observaciones);
    }
 
    // ---- Métodos de validación ----
 
    /**
     * Verifica que los campos obligatorios no estén vacíos.
     */
    public boolean esValido() {
        return getCodMateria() != null
            && getCodigoProfesor() != null
            && getFecha() != null && !getFecha().isEmpty()
            && getHorario() != null && !getHorario().isEmpty()
            && getAula() != null && !getAula().isEmpty();
    }
 
    /**
     * Verifica si ya existe otro examen del mismo docente
     * para la misma materia en la misma fecha (evita duplicados).
     * Excluye el propio registro al editar (por id).
     */
    public static boolean existeDuplicado(Integer codMateria, Integer codigoProfesor, String fecha, Integer idExcluir) {
        if (idExcluir != null) {
            return ExamenFinal.count(
                "cod_materia = ? AND codigo_profesor = ? AND fecha = ? AND id != ?",
                codMateria, codigoProfesor, fecha, idExcluir
            ) > 0;
        }
        return ExamenFinal.count(
            "cod_materia = ? AND codigo_profesor = ? AND fecha = ?",
            codMateria, codigoProfesor, fecha
        ) > 0;
    }
}