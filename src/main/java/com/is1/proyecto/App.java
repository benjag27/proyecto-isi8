package com.is1.proyecto; // Define el paquete de la aplicación, debe coincidir con la estructura de carpetas.

import java.util.ArrayList;
import java.util.HashMap; // Utilidad para serializar/deserializar objetos Java a/desde JSON.
import java.util.List;
import java.util.Map; // Importa los métodos estáticos principales de Spark (get, post, before, after, etc.).

import org.javalite.activejdbc.Base; // Clase central de ActiveJDBC para gestionar la conexión a la base de datos.
import org.mindrot.jbcrypt.BCrypt; // Utilidad para hashear y verificar contraseñas de forma segura.
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper; // Representa un modelo de datos y el nombre de la vista a renderizar.
import com.is1.proyecto.config.DBConfigSingleton; // Motor de plantillas Mustache para Spark.
import com.is1.proyecto.models.Carrera;
import com.is1.proyecto.models.Docente; // Para crear mapas de datos (modelos para las plantillas).
import com.is1.proyecto.models.Estudiante;
import com.is1.proyecto.models.ExamenFinal;
import com.is1.proyecto.models.Materia;
import com.is1.proyecto.models.PeriodoAcademico;
import com.is1.proyecto.models.Persona;
import com.is1.proyecto.models.PlanDeEstudios;
import com.is1.proyecto.models.User;

import spark.ModelAndView; // Interfaz Map, utilizada para Map.of() o HashMap.
import spark.Request;
import static spark.Spark.after; // Clase Singleton para la configuración de la base de datos.
import static spark.Spark.before;
import static spark.Spark.exception;
import static spark.Spark.get; // Modelo de ActiveJDBC que representa la tabla 'users'.
import static spark.Spark.halt;
import static spark.Spark.internalServerError;
import static spark.Spark.notFound;
import static spark.Spark.port;
import static spark.Spark.post;
import spark.template.mustache.MustacheTemplateEngine;
// mvn clean compile activejdbc-instrumentation:instrument exec:java "-Dexec.mainClass=com.is1.proyecto.App"

/**
 * Clase principal de la aplicación Spark.
 * Configura las rutas, filtros y el inicio del servidor web.
 */
public class App {

    // Instancia estática y final de ObjectMapper para la
    // serialización/deserialización JSON.
    // Se inicializa una sola vez para ser reutilizada en toda la aplicación.
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger logger = LoggerFactory.getLogger(App.class);

    private static void ejecutarScheme() {
    try {
        String sql = new String(
            App.class.getClassLoader()
                .getResourceAsStream("scheme.sql")
                .readAllBytes()
        );

        Base.exec(sql);

        System.out.println("Schema ejecutado correctamente.");

    } catch (Exception e) {
        System.err.println("Error ejecutando schema.sql");
        e.printStackTrace();
    }
}
    
    /**
     * Método principal que se ejecuta al iniciar la aplicación.
     * Aquí se configuran todas las rutas y filtros de Spark.
     */
    public static void main(String[] args) {
        port(8080); // Configura el puerto en el que la aplicación Spark escuchará las peticiones
                    // (por defecto es 8080).

        // Obtener la instancia única del singleton de configuración de la base de
        // datos.
        DBConfigSingleton dbConfig = DBConfigSingleton.getInstance();

        // --- Filtro 'before' para gestionar la conexión a la base de datos ---
        // Este filtro se ejecuta antes de cada solicitud HTTP.
        before((req, res) -> {
            try {
                // Abre una conexión a la base de datos utilizando las credenciales del
                // singleton.
                if(!Base.hasConnection()) {
                    Base.open(dbConfig.getDriver(), dbConfig.getDbUrl(), dbConfig.getUser(), dbConfig.getPass());
                }   
                System.out.println(req.url());

            } catch (Exception e) {
                // Si ocurre un error al abrir la conexión, se registra y se detiene la
                // solicitud
                // con un código de estado 500 (Internal Server Error) y un mensaje JSON.
                System.err.println("Error al abrir conexión con ActiveJDBC: " + e.getMessage());
                halt(500, "{\"error\": \"Error interno del servidor: Fallo al conectar a la base de datos.\"}"
                        + e.getMessage());
            }
        });
        
        before("/admin/*", (req, res) -> {
            if (!isAdmin(req)) {
                res.redirect("/dashboard?error=Debes ser administrador para acceder a esta pagina.");
                halt();
            }
        });

        before("/admin", (req, res) -> {
            if (!isAdmin(req)) {
                res.redirect("/dashboard?error=Debes ser administrador para acceder a esta pagina.");
                halt();
            }
        });
        registrarRutasExamenes();
        
        
        try {
            Base.open(
                dbConfig.getDriver(),
                dbConfig.getDbUrl(),
                dbConfig.getUser(),
                dbConfig.getPass()
            );

            ejecutarScheme();

            Base.close();

        } catch (Exception e) {
            e.printStackTrace();
        }

        // --- Filtro 'after' para cerrar la conexión a la base de datos ---
        // Este filtro se ejecuta después de que cada solicitud HTTP ha sido procesada.
        after((req, res) -> {
            try {
                // Cierra la conexión a la base de datos para liberar recursos.
                Base.close();
            } catch (Exception e) {
                // Si ocurre un error al cerrar la conexión, se registra.
                System.err.println("Error al cerrar conexión con ActiveJDBC: " + e.getMessage());
            }
        });

        // --- Rutas GET para renderizar formularios y páginas HTML ---

        // GET: Muestra el formulario de creación de cuenta.
        // Soporta la visualización de mensajes de éxito o error pasados como query
        // parameters.
        get("/user/create", (req, res) -> {
            Map<String, Object> model = new HashMap<>(); // Crea un mapa para pasar datos a la plantilla.

            // Obtener y añadir mensaje de éxito de los query parameters (ej.
            // ?message=Cuenta creada!)
            String successMessage = req.queryParams("message");
            if (successMessage != null && !successMessage.isEmpty()) {
                model.put("successMessage", successMessage);
            }

            // Obtener y añadir mensaje de error de los query parameters (ej. ?error=Campos
            // vacíos)
            String errorMessage = req.queryParams("error");
            if (errorMessage != null && !errorMessage.isEmpty()) {
                model.put("errorMessage", errorMessage);
            }

            // Renderiza la plantilla 'user_form.mustache' con los datos del modelo.
            return new ModelAndView(model, "user_form.mustache");
        }, new MustacheTemplateEngine()); // Especifica el motor de plantillas para esta ruta.

        // GET: Ruta para mostrar el dashboard (panel de control) del usuario.
        // Requiere que el usuario esté autenticado.
        get("/dashboard", (req, res) -> {
            Map<String, Object> model = new HashMap<>(); // Modelo para la plantilla del dashboard.

            // Intenta obtener el nombre de usuario y la bandera de login de la sesión.
            String currentUsername = req.session().attribute("currentUserUsername");
            Boolean loggedIn = req.session().attribute("loggedIn");

            // 1. Verificar si el usuario ha iniciado sesión.
            // Si no hay un nombre de usuario en la sesión, la bandera es nula o falsa,
            // significa que el usuario no está logueado o su sesión expiró.
            if (currentUsername == null || loggedIn == null || !loggedIn) {
                System.out.println("DEBUG: Acceso no autorizado a /dashboard. Redirigiendo a /login.");
                // Redirige al login con un mensaje de error.
                res.redirect("/?error=Debes iniciar sesion para acceder a esta pagina.");
                return null; // Importante retornar null después de una redirección.
            }

            String userRol = req.session().attribute("userRol"); // Obtiene el rol del usuario
            // 2. Si el usuario está logueado, añade el nombre de usuario al modelo para la
            // plantilla.
            model.put("username", currentUsername);
            String rolFormateado = userRol.substring(0,1).toUpperCase() +
                       userRol.substring(1).toLowerCase();
            model.put("rol", rolFormateado);
            model.put("isAdmin",   "ADMINISTRADOR".equals(userRol));
            model.put("isDocente", "DOCENTE".equals(userRol));
            model.put("isAlumno",  "ALUMNO".equals(userRol));
            model.put("isUnassigned",  "UNASSIGNED".equals(userRol));
            
            String rolClass = "";

            switch (userRol) {
                case "ADMINISTRADOR":
                    rolClass = "bg-purple-100 text-purple-700";
                    break;

                case "DOCENTE":
                    rolClass = "bg-blue-100 text-blue-700";
                    break;

                case "ESTUDIANTE":
                    rolClass = "bg-green-100 text-green-700";
                    break;
                default: rolClass = "bg-gray-100 text-gray-700";
            }
            model.put("rolClass", rolClass);
            
            String errorMessage = req.queryParams("error");
            if (errorMessage != null && !errorMessage.isEmpty()) {
                model.put("errorMessage", errorMessage);
            }
            // 3. Renderiza la plantilla del dashboard con el nombre de usuario.
            return new ModelAndView(model, "dashboard.mustache");
        }, new MustacheTemplateEngine()); // Especifica el motor de plantillas para esta ruta.

        // GET: Ruta para cerrar la sesión del usuario.
        get("/logout", (req, res) -> {
            // Invalida completamente la sesión del usuario.
            // Esto elimina todos los atributos guardados en la sesión y la marca como
            // inválida.
            // La cookie JSESSIONID en el navegador también será gestionada para
            // invalidarse.
            req.session().invalidate();

            System.out.println("DEBUG: Sesión cerrada. Redirigiendo a /login.");

            // Redirige al usuario a la página de login con un mensaje de éxito.
            res.redirect("/");

            return null; // Importante retornar null después de una redirección.
        });

        // GET: Muestra el formulario de inicio de sesión (login).
        // Nota: Esta ruta debería ser capaz de leer también mensajes de error/éxito de
        // los query params
        // si se la usa como destino de redirecciones. (Tu código de /user/create ya lo
        // hace, aplicar similar).
        get("/", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            String errorMessage = req.queryParams("error");
            if (errorMessage != null && !errorMessage.isEmpty()) {
                model.put("errorMessage", errorMessage);
            }
            String successMessage = req.queryParams("message");
            if (successMessage != null && !successMessage.isEmpty()) {
                model.put("successMessage", successMessage);
            }
            return new ModelAndView(model, "login.mustache");
        }, new MustacheTemplateEngine()); // Especifica el motor de plantillas para esta ruta.

        // GET: Ruta de alias para el formulario de creación de cuenta.
        // En una aplicación real, probablemente querrías unificar con '/user/create'
        // para evitar duplicidad.
        get("/user/new", (req, res) -> {
            return new ModelAndView(new HashMap<>(), "user_form.mustache"); // No pasa un modelo específico, solo el
                                                                            // formulario.
        }, new MustacheTemplateEngine()); // Especifica el motor de plantillas para esta ruta.

        // --- Rutas POST para manejar envíos de formularios y APIs ---

        // POST: Maneja el envío del formulario de creación de nueva cuenta.
        post("/user/new", (req, res) -> {
            String name = req.queryParams("name");
            String password = req.queryParams("password");

            // Validaciones básicas: campos no pueden ser nulos o vacíos.
            if (name == null || name.isEmpty() || password == null || password.isEmpty()) {
                res.status(400); // Código de estado HTTP 400 (Bad Request).
                // Redirige al formulario de creación con un mensaje de error.
                res.redirect("/user/create?error=Nombre y contraseña son requeridos.");
                return ""; // Retorna una cadena vacía ya que la respuesta ya fue redirigida.
            }

            try {
                
                User usuarioExistente = User.findFirst("name = ?", name);
                if (usuarioExistente != null) {
                    res.status(400);
                    res.redirect("/user/create?error=El nombre de usuario ya existe.");
                    return "";
                }

                // Intenta crear y guardar la nueva cuenta en la base de datos.
                User ac = new User(); // Crea una nueva instancia del modelo User.
                // Hashea la contraseña de forma segura antes de guardarla.
                String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());

                ac.set("name", name); // Asigna el nombre de usuario.
                ac.set("password", hashedPassword); // Asigna la contraseña hasheada.
                ac.set("rol", "UNASSIGNED"); // Asigna el rol por defecto a la nueva cuenta.
                ac.saveIt(); // Guarda el nuevo usuario en la tabla 'users'.

                res.status(201); // Código de estado HTTP 201 (Created) para una creación exitosa.
                // Redirige al formulario de creación con un mensaje de éxito.
                res.redirect("/user/create?message=Cuenta creada exitosamente para " + name + "!");
                return ""; // Retorna una cadena vacía.

            } catch (Exception e) {
                // Si ocurre cualquier error durante la operación de DB (ej. nombre de usuario
                // duplicado),
                // se captura aquí y se redirige con un mensaje de error.
                System.err.println("Error al registrar la cuenta: " + e.getMessage());
                e.printStackTrace(); // Imprime el stack trace para depuración.
                res.status(500); // Código de estado HTTP 500 (Internal Server Error).
                res.redirect("/user/create?error=Error interno al crear la cuenta. Intente de nuevo.");
                return ""; // Retorna una cadena vacía.
            }
        });

        // POST: Maneja el envío del formulario de inicio de sesión.
        post("/login", (req, res) -> {
            Map<String, Object> model = new HashMap<>(); // Modelo para la plantilla de login o dashboard.

            String username = req.queryParams("username");
            String plainTextPassword = req.queryParams("password");

            // Validaciones básicas: campos de usuario y contraseña no pueden ser nulos o
            // vacíos.
            if (username == null || username.isEmpty() || plainTextPassword == null || plainTextPassword.isEmpty()) {
                res.status(400); // Bad Request.
                model.put("errorMessage", "El nombre de usuario y la contraseña son requeridos.");
                return new ModelAndView(model, "login.mustache"); // Renderiza la plantilla de login con error.
            }

            // Busca la cuenta en la base de datos por el nombre de usuario.
            User ac = User.findFirst("name = ?", username);

            // Si no se encuentra ninguna cuenta con ese nombre de usuario.
            if (ac == null) {
                res.status(401); // Unauthorized.
                model.put("errorMessage", "Usuario o contraseña incorrectos."); // Mensaje genérico por seguridad.
                return new ModelAndView(model, "login.mustache"); // Renderiza la plantilla de login con error.
            }

            // Obtiene la contraseña hasheada almacenada en la base de datos.
            String storedHashedPassword = ac.getString("password");

            // Compara la contraseña en texto plano ingresada con la contraseña hasheada
            // almacenada.
            // BCrypt.checkpw hashea la plainTextPassword con el salt de
            // storedHashedPassword y compara.
            if (BCrypt.checkpw(plainTextPassword, storedHashedPassword)) {
                // Autenticación exitosa.
                res.status(200); // OK.

                // --- Gestión de Sesión ---
                req.session(true).attribute("currentUserUsername", username); // Guarda el nombre de usuario en la sesión.
                req.session().attribute("userId", ac.getId()); // Guarda el ID de la cuenta en la sesión (útil).
                req.session().attribute("userRol", ac.getRol()); // Guarda el rol del usuario.
                req.session().attribute("loggedIn", true); // Establece una bandera para indicar que el usuario está logueado.

                System.out.println("DEBUG: Login exitoso para la cuenta: " + username);
                System.out.println("DEBUG: ID de Sesion: " + req.session().id());

                res.redirect("/dashboard"); 
                return null;
            } else {
                // Contraseña incorrecta.
                res.status(401); // Unauthorized.
                System.out.println("DEBUG: Intento de login fallido para: " + username);
                model.put("errorMessage", "Usuario o contraseña incorrectos."); // Mensaje genérico por seguridad.
                return new ModelAndView(model, "login.mustache"); // Renderiza la plantilla de login con error.
            }
        }, new MustacheTemplateEngine()); // Especifica el motor de plantillas para esta ruta POST.

        // POST: Endpoint para añadir usuarios (API que devuelve JSON, no HTML).
        // Advertencia: Esta ruta tiene un propósito diferente a las de formulario HTML.
        post("/add_users", (req, res) -> {
            res.type("application/json"); // Establece el tipo de contenido de la respuesta a JSON.

            // Obtiene los parámetros 'name' y 'password' de la solicitud.
            String name = req.queryParams("name");
            String password = req.queryParams("password");

            // --- Validaciones básicas ---
            if (name == null || name.isEmpty() || password == null || password.isEmpty()) {
                res.status(400); // Bad Request.
                return objectMapper.writeValueAsString(Map.of("error", "Nombre y contraseña son requeridos."));
            }

            try {
                // --- Creación y guardado del usuario usando el modelo ActiveJDBC ---
                User newUser = new User(); // Crea una nueva instancia de tu modelo User.
                // ¡ADVERTENCIA DE SEGURIDAD CRÍTICA!
                // En una aplicación real, las contraseñas DEBEN ser hasheadas (ej. con BCrypt)
                // ANTES de guardarse en la base de datos, NUNCA en texto plano.
                // (Nota: El código original tenía la contraseña en texto plano aquí.
                // Se recomienda usar `BCrypt.hashpw(password, BCrypt.gensalt())` como en la
                // ruta '/user/new').
                newUser.set("name", name); // Asigna el nombre al campo 'name'.
                newUser.set("password", BCrypt.hashpw(password, BCrypt.gensalt())); // Asigna la contraseña al campo 'password'.
                newUser.set("rol", "UNASSIGNED"); // Asigna el rol UNASSIGNED al nuevo usuario.
                newUser.saveIt(); // Guarda el nuevo usuario en la tabla 'users'.

                res.status(201); // Created.
                // Devuelve una respuesta JSON con el mensaje y el ID del nuevo usuario.
                return objectMapper.writeValueAsString(
                        Map.of("message", "Usuario '" + name + "' registrado con éxito.", "id", newUser.getId()));

            } catch (Exception e) {
                // Si ocurre cualquier error durante la operación de DB, se captura aquí.
                System.err.println("Error al registrar usuario: " + e.getMessage());
                e.printStackTrace(); // Imprime el stack trace para depuración.
                res.status(500); // Internal Server Error.
                return objectMapper
                        .writeValueAsString(Map.of("error", "Error interno al registrar usuario: " + e.getMessage()));
            }
        });


        get("/profile", (req, res) -> {
            String currentUsername = req.session().attribute("currentUserUsername");
            Boolean loggedIn = req.session().attribute("loggedIn");

            if (currentUsername == null || loggedIn == null || !loggedIn) {
                res.redirect("/?error=Debes iniciar sesion para acceder a esta pagina.");
                return null;
            }

            User usuario = User.findFirst(
                    "name = ?",
                    currentUsername
            );

            if (usuario == null) {
                req.session().invalidate();
                res.redirect("/?error=Sesion invalida.");
                return null;
            }

            Map<String, Object> model = new HashMap<>();

            model.put("usuario", usuario);

            String rol = usuario.getString("rol");

            model.put("esAdmin", "ADMINISTRADOR".equals(rol));
            model.put("esDocente", "DOCENTE".equals(rol));
            model.put("esAlumno", "ALUMNO".equals(rol));

            if ("DOCENTE".equals(rol)) {
                Docente docente = Docente.findFirst("user_id = ?", usuario.getInteger("id"));

                if (docente != null) {
                    Persona persona = Persona.findFirst("dni = ?",docente.getInteger("dni"));
                    model.put("docente", docente);
                    model.put("persona", persona);
                }
            }

            if ("ALUMNO".equals(rol)) {
                Estudiante estudiante = Estudiante.findFirst("user_id = ?",usuario.getInteger("id"));

                if (estudiante != null) {
                    Persona persona = Persona.findFirst("dni = ?", estudiante.getInteger("dni"));
                    model.put("estudiante", estudiante);
                    model.put("persona", persona);
                }
            }

            return new ModelAndView(model, "perfil.mustache");

        }, new MustacheTemplateEngine()); 

        post("/docente/new", (req, res) -> {

            String nombre = req.queryParams("nombre");
            String apellido = req.queryParams("apellido");
            String dniString = req.queryParams("dni");
            String email = req.queryParams("email");

            // NUEVOS DATOS
            String fechaNacimiento = req.queryParams("fecha_nacimiento");
            String telefono = req.queryParams("telefono");
            String direccion = req.queryParams("direccion");

            // USER ASOCIADO
            String username = req.queryParams("username");

            // =========================
            // VALIDACIONES
            // =========================

            if (
                dniString.isEmpty() ||
                nombre.isEmpty() ||
                apellido.isEmpty() ||
                email.isEmpty() ||
                fechaNacimiento.isEmpty() ||
                telefono.isEmpty() ||
                direccion.isEmpty() ||
                username.isEmpty()
            ) {

                res.redirect("/admin/docentes/agregar?errorMessage=Todos los campos son obligatorios.");
                return null;
            }

            // Email válido
            if (!esEmailValido(email)) {
                res.redirect("/admin/docentes/agregar?errorMessage=Ingrese un email valido.");
                return null;
            }

            // Email repetido
            Docente docenteExistente = Docente.findFirst("email = ?", email);

            if (docenteExistente != null) {
                res.redirect("/admin/docentes/agregar?errorMessage=Ya existe un docente con ese email.");
                return null;
            }

            // Verificar que exista el user
            User usuarioExistente = User.findFirst("name = ?", username);

            if (usuarioExistente == null) {
                res.redirect("/admin/docentes/agregar?errorMessage=No existe un usuario con ese nombre.");
                return null;
            }

            // Verificar que el usuario no esté asociado a otro docente
            Docente docenteConUsuario = Docente.findFirst("user_id = ?", usuarioExistente.getId());

            if (docenteConUsuario != null) {
                res.redirect("/admin/docentes/agregar?errorMessage=Ese usuario ya esta asociado a otro docente.");
                return null;
            }
            
            // Verificar que el user NO es ADMIN
            if ("ADMINISTRADOR".equals(usuarioExistente.getString("rol"))) {
                res.redirect("/admin/docentes/agregar?errorMessage=No puedes asignar un administrador como docente.");
                return null;
            }
            
            // VALIDAR NUMEROS VALIDOS
            Integer dni;
            Integer codigoProfesor;
            try {
                dni = Integer.parseInt(dniString);
            } catch (NumberFormatException e) {
                res.redirect("/admin/docentes/agregar?errorMessage=DNI debe ser un numero valido.");
                return null;
            }

            // VALIDAR DNI NO REPETIDO
            Persona personaExistente = Persona.findFirst("dni = ?", dni);
            if (personaExistente != null) {
                res.redirect("/admin/docentes/agregar?errorMessage=Ya existe una persona registrada con ese DNI.");
                return null;
            }

            try {                
                // =========================
                // CREAR PERSONA
                // =========================

                Persona persona = new Persona();

                persona.setDni(dni);
                persona.setNombre(nombre);
                persona.setApellido(apellido);

                persona.setFechaNacimiento(fechaNacimiento);
                persona.setTelefono(telefono);
                persona.setDireccion(direccion);

                // =========================
                // CREAR DOCENTE
                // =========================

                Docente docente = new Docente();

                docente.setDNI(dni);
                docente.setEmail(email);

                // Asociar user
                docente.set("user_id", usuarioExistente.getId());

                // Guardar docente
                docente.saveIt();

                // Actualizar rol
                usuarioExistente.set("rol", "DOCENTE");
                usuarioExistente.saveIt();
                persona.saveIt();
                res.redirect("/admin/docentes/agregar?successMessage=Docente agregado correctamente.");
                return null;
            } catch (Exception e) {

                String msg = e.getMessage();

                if (msg != null && msg.contains("UNIQUE constraint failed: Persona.dni")) {

                    res.redirect("/admin/docentes/agregar?errorMessage=Ya existe una persona registrada con ese DNI.");
                    return null;
                }

                res.redirect("/admin/docentes/agregar?errorMessage=Error al agregar docente: " + msg);
                return null;
            }
        });

        // Con esto podemos hacer localhost:puerto/admin/docentes/agregar
        get("/admin/docentes/agregar", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
        
            // Obtener y añadir mensaje de éxito de los query parameters (ej.
            // ?message=Cuenta creada!)
            String successMessage = req.queryParams("successMessage");
            if (successMessage != null && !successMessage.isEmpty()) {
                model.put("successMessage", successMessage);
            }

            // Obtener y añadir mensaje de error de los query parameters (ej. ?error=Campos
            // vacíos)
            String errorMessage = req.queryParams("errorMessage");
            if (errorMessage != null && !errorMessage.isEmpty()) {
                model.put("errorMessage", errorMessage);
            }

            // Inicializamos variables
            model.put("dni", "");
            model.put("nombre", "");
            model.put("apellido", "");
            model.put("email", "");

            model.put("fecha_nacimiento", "");
            model.put("telefono", "");
            model.put("direccion", "");
            model.put("username", "");

            // Renderizamos la plantilla
            return new ModelAndView(model, "admin/docentes/agregarDocente.mustache");
        }, new MustacheTemplateEngine());
        
        // ==========================
        //  DASHBOARD ADMINISTRACIÓN
        // ==========================

        get("/admin", (req, res) -> {
            return new ModelAndView(new HashMap<>(), "admin/adminDashboard.mustache");

        }, new MustacheTemplateEngine());
        
        // DOCENTES
        get("/admin/docentes", (req, res) -> {
            Map<String, Object> model = new HashMap<>();

            List<Docente> docentesDB = Docente.findAll();

            List<Map<String, Object>> docentes = new ArrayList<>();

            for (Docente docente : docentesDB) {

                Map<String, Object> docenteView = new HashMap<>();
                
                // DATOS DOCENTE
                docenteView.put("id", docente.getInteger("codigo_profesor"));

                docenteView.put("email", docente.getString("email"));

                // PERSONA
                Integer dni = docente.getInteger("dni");

                Persona persona = Persona.findFirst("dni = ?", dni);

                if (persona != null) {
                    docenteView.put("dni", persona.getInteger("dni"));
                    docenteView.put("nombre",persona.getString("nombre"));
                    docenteView.put("apellido", persona.getString("apellido"));
                    docenteView.put("telefono", persona.getString("telefono"));
                    docenteView.put("direccion", persona.getString("direccion"));
                }
                
                // USER
                Integer userId = docente.getInteger("user_id");
                User user = User.findById(userId);

                if (user != null) {
                    docenteView.put("username", user.getString("name"));
                }

                docentes.add(docenteView);
            }

            model.put("docentes", docentes);

            model.put(
                    "successMessage",
                    req.queryParams("successMessage")
            );

            model.put(
                    "errorMessage",
                    req.queryParams("errorMessage")
            );

            return new ModelAndView(
                    model,
                    "admin/docentes/docentesDashboard.mustache"
            );

        }, new MustacheTemplateEngine());

        get("/admin/docentes/:id/edit", (req, res) -> {

            Map<String, Object> model = new HashMap<>();

            Integer codigoProfesor = Integer.parseInt(req.params(":id"));
            
            // DOCENTE
            Docente docente = Docente.findFirst("codigo_profesor = ?", codigoProfesor);

            if (docente == null) {
                res.redirect("/admin/docentes?errorMessage=Error: docente no encontrado");
                return null;
            }

            // PERSONA
            Integer dni = docente.getInteger("dni");

            Persona persona = Persona.findFirst("dni = ?", dni);

            // MODEL
            model.put("codigoProfesor", docente.getInteger("codigo_profesor"));
            model.put("email", docente.getString("email"));
            model.put("dni", persona.getInteger("dni"));
            model.put("nombre", persona.getString("nombre"));
            model.put("apellido", persona.getString("apellido"));
            model.put("fechaNacimiento", persona.getString("fecha_nacimiento"));
            model.put("telefono", persona.getString("telefono"));
            model.put("direccion",persona.getString("direccion"));

            return new ModelAndView(model,"admin/docentes/editarDocente.mustache");

        }, new MustacheTemplateEngine());

        post("/admin/docentes/:id/edit", (req, res) -> {

            Integer codigoProfesor = Integer.parseInt(req.params(":id"));

            Docente docente = Docente.findFirst("codigo_profesor = ?", codigoProfesor);

            if (docente == null) {

                res.redirect(
                        "/admin/docentes?errorMessage=Docente no encontrado"
                );

                return null;
            }

            Integer dni = docente.getInteger("dni");

            // FORM
            String nombre = req.queryParams("nombre");
            String apellido = req.queryParams("apellido");
            String fechaNacimiento = req.queryParams("fecha_nacimiento");
            String telefono = req.queryParams("telefono");
            String direccion = req.queryParams("direccion");
            String email = req.queryParams("email");

            try {

                Base.openTransaction();
                
                // UPDATE PERSONA
                Base.exec(
                        "UPDATE Persona " +
                        "SET nombre = ?, apellido = ?, fecha_nacimiento = ?, telefono = ?, direccion = ? " +
                        "WHERE dni = ?",

                        nombre,
                        apellido,
                        fechaNacimiento,
                        telefono,
                        direccion,
                        dni
                );
                
                // UPDATE DOCENTE
                Base.exec(
                        "UPDATE Docente SET email = ? WHERE codigo_profesor = ?",
                        email,
                        codigoProfesor
                );

                Base.commitTransaction();

                res.redirect(
                        "/admin/docentes?successMessage=Docente actualizado correctamente"
                );

                return null;

            } catch (Exception e) {

                Base.rollbackTransaction();

                e.printStackTrace();

                res.redirect(
                        "/admin/docentes/" + codigoProfesor + "/edit?errorMessage=Error al actualizar docente");

                return null;
            }

        });

        get("/admin/docentes/:id/delete", (req, res) -> {
            Map<String, Object> model = new HashMap<>();

            Integer codigoProfesor = Integer.parseInt(req.params(":id"));

            Docente docente = Docente.findFirst("codigo_profesor = ?", codigoProfesor);

            if (docente == null) {
                res.redirect("/admin/docentes?errorMessage=Docente no encontrado");
                return null;
            }

            Integer dni = docente.getInteger("dni");

            Persona persona = Persona.findFirst("dni = ?", dni);

            Integer userId = docente.getInteger("user_id");

            User user = User.findById(userId);

            model.put("codigoProfesor", codigoProfesor);
            model.put("email", docente.getString("email"));

            if (persona != null) {
                model.put("dni", persona.getInteger("dni"));
                model.put("nombre", persona.getString("nombre"));
                model.put("apellido", persona.getString("apellido"));
            }

            if (user != null) {
                model.put("username", user.getString("name"));
            }

            return new ModelAndView(model, "admin/docentes/eliminarDocente.mustache");

        }, new MustacheTemplateEngine());

        post("/admin/docentes/:id/delete", (req, res) -> {

            Integer codigoProfesor = Integer.parseInt(req.params(":id"));

            Docente docente = Docente.findFirst("codigo_profesor = ?", codigoProfesor);
            if (docente == null) {
                res.redirect("/admin/docentes?errorMessage=Docente no encontrado");
                return null;
            }

            Integer dni = docente.getInteger("dni");
            Integer userId = docente.getInteger("user_id");

            User user = User.findById(userId);

            try {
                Base.openTransaction();

                // ELIMINAR DOCENTE
                Base.exec("DELETE FROM Docente WHERE codigo_profesor = ?", codigoProfesor);

                // ELIMINAR PERSONA
                Base.exec("DELETE FROM Persona WHERE dni = ?", dni);

                // RESET ROL USER
                if (user != null) {
                    user.set("rol", "UNASSIGNED");
                    user.saveIt();
                }

                Base.commitTransaction();

                res.redirect("/admin/docentes?successMessage=Docente eliminado correctamente");

                return null;

            } catch (Exception e) {
                Base.rollbackTransaction();
                e.printStackTrace();
                res.redirect("/admin/docentes?errorMessage=Error al eliminar docente");

                return null;
            }
        });
        
        // VER LISTADO
        get("/admin/docentes/listado", (req, res) -> {
            Map<String, Object> model = new HashMap<>();

            List<Docente> docentesDB = Docente.findAll();

            List<Map<String, Object>> docentes = new ArrayList<>();

            for (Docente docente : docentesDB) {

                Map<String, Object> docenteView = new HashMap<>();
                
                // DATOS DOCENTE
                docenteView.put("id", docente.getInteger("codigo_profesor"));
                docenteView.put("email", docente.getString("email"));

                // PERSONA
                Integer dni = docente.getInteger("dni");

                Persona persona = Persona.findFirst("dni = ?", dni);

                if (persona != null) {
                    docenteView.put("dni", persona.getInteger("dni"));
                    docenteView.put("nombre",persona.getString("nombre"));
                    docenteView.put("apellido", persona.getString("apellido"));
                    docenteView.put("telefono", persona.getString("telefono"));
                    docenteView.put("direccion", persona.getString("direccion"));
                    docenteView.put("fecha_nacimiento",persona.getString("fecha_nacimiento"));
                }
                
                // USER
                Integer userId = docente.getInteger("user_id");
                User user = User.findById(userId);

                if (user != null) {
                    docenteView.put("username", user.getString("name"));
                }

                docentes.add(docenteView);
            }

            model.put("docentes", docentes);

            model.put(
                    "successMessage",
                    req.queryParams("successMessage")
            );

            model.put(
                    "errorMessage",
                    req.queryParams("errorMessage")
            );

            return new ModelAndView(model, "admin/docentes/listadoDocentes.mustache");

        }, new MustacheTemplateEngine());

        //! ESTUDIANTES
        post("/estudiante/new", (req, res) -> {

            String nombre = req.queryParams("nombre");
            String apellido = req.queryParams("apellido");
            String dniString = req.queryParams("dni");
            String email = req.queryParams("email");

            // NUEVOS DATOS
            String fechaNacimiento = req.queryParams("fecha_nacimiento");
            String telefono = req.queryParams("telefono");
            String direccion = req.queryParams("direccion");
            String nroLegajoString = req.queryParams("nro_legajo");    

            // USER ASOCIADO
            String username = req.queryParams("username");



            // =========================
            // VALIDACIONES
            // =========================

            if (
                dniString.isEmpty() ||
                nombre.isEmpty() ||
                apellido.isEmpty() ||
                email.isEmpty() ||
                fechaNacimiento.isEmpty() ||
                telefono.isEmpty() ||
                direccion.isEmpty() ||
                username.isEmpty() ||
                nroLegajoString.isEmpty()
            ) {

                res.redirect("/admin/estudiantes/agregar?errorMessage=Todos los campos son obligatorios.");
                return null;
            }

            // Email válido
            if (!esEmailValido(email)) {
                res.redirect("/admin/estudiantes/agregar?errorMessage=Ingrese un email valido.");
                return null;
            }

            // Email repetido
            Estudiante estudianteExistete = Estudiante.findFirst("email = ?", email);

            if (estudianteExistete != null) {
                res.redirect("/admin/estudiantes/agregar?errorMessage=Ya existe un estudiante con ese email.");
                return null;
            }

            // Verificar que exista el user
            User usuarioExistente = User.findFirst("name = ?", username);

            if (usuarioExistente == null) {
                res.redirect("/admin/estudiantes/agregar?errorMessage=No existe un usuario con ese nombre.");
                return null;
            }

            // Verificar que el usuario no esté asociado a otro docente
            Estudiante estudianteConUsuario = Estudiante.findFirst("user_id = ?", usuarioExistente.getId());

            if (estudianteConUsuario != null) {
                res.redirect("/admin/estudiantes/agregar?errorMessage=Ese usuario ya esta asociado a otro estudiante.");
                return null;
            }
            
            // Verificar que el user NO es ADMIN
            if ("ADMINISTRADOR".equals(usuarioExistente.getString("rol"))) {
                res.redirect("/admin/estudiantes/agregar?errorMessage=No puedes asignar un administrador como estudiante.");
                return null;
            }
            
            // VALIDAR NUMEROS VALIDOS
            Integer dni;
            Integer nro_legajo;
            try {
                dni = Integer.parseInt(dniString);
                nro_legajo = Integer.parseInt(nroLegajoString);
            } catch (NumberFormatException e) {
                res.redirect("/admin/estudiantes/agregar?errorMessage=DNI debe ser un numero valido.");
                return null;
            }

            // VALIDAR DNI NO REPETIDO
            Persona personaExistente = Persona.findFirst("dni = ?", dni);
            if (personaExistente != null) {
                res.redirect("/admin/estudiantes/agregar?errorMessage=Ya existe una persona registrada con ese DNI.");
                return null;
            }

            //  VALIDAR NRO LEGAJO NO REPETIDO
            if (Estudiante.findFirst("nro_legajo = ?", nro_legajo) != null) {
                res.redirect("/admin/estudiantes/agregar?errorMessage=Ya existe un estudiante con ese legajo.");
                return null;
            }   

            try {                
                // =========================
                // CREAR PERSONA
                // =========================

                Persona persona = new Persona();

                persona.setDni(dni);
                persona.setNombre(nombre);
                persona.setApellido(apellido);

                persona.setFechaNacimiento(fechaNacimiento);
                persona.setTelefono(telefono);
                persona.setDireccion(direccion);
                
                // =========================
                // CREAR ESTUDIANTE
                // =========================

                Estudiante estudiante = new Estudiante();

                estudiante.setDni(dni);
                estudiante.setEmail(email);
                estudiante.setNroLegajo(nro_legajo);

                // Asociar user
                estudiante.set("user_id", usuarioExistente.getId());

                // Guardar estudiante
                estudiante.saveIt();

                // Actualizar rol
                usuarioExistente.set("rol", "ALUMNO");
                usuarioExistente.saveIt();
                persona.saveIt();
                res.redirect("/admin/estudiantes/agregar?successMessage=Estudiante agregado correctamente.");
                return null;
            } catch (Exception e) {

                String msg = e.getMessage();

                if (msg != null && msg.contains("UNIQUE constraint failed: Persona.dni")) {

                    res.redirect("/admin/estudiantes/agregar?errorMessage=Ya existe una persona registrada con ese DNI.");
                    return null;
                }

                res.redirect("/admin/estudiantes/agregar?errorMessage=Error al agregar estudiante: " + msg);
                return null;
            }
        });

        // Con esto podemos hacer localhost:puerto/admin/docentes/agregar
        get("/admin/estudiantes/agregar", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
        
            // Obtener y añadir mensaje de éxito de los query parameters (ej.
            // ?message=Cuenta creada!)
            String successMessage = req.queryParams("successMessage");
            if (successMessage != null && !successMessage.isEmpty()) {
                model.put("successMessage", successMessage);
            }

            // Obtener y añadir mensaje de error de los query parameters (ej. ?error=Campos
            // vacíos)
            String errorMessage = req.queryParams("errorMessage");
            if (errorMessage != null && !errorMessage.isEmpty()) {
                model.put("errorMessage", errorMessage);
            }

            // Inicializamos variables
            model.put("dni", "");
            model.put("nombre", "");
            model.put("apellido", "");
            model.put("email", "");

            model.put("fecha_nacimiento", "");
            model.put("telefono", "");
            model.put("direccion", "");
            model.put("username", "");

            // Renderizamos la plantilla
            return new ModelAndView(model, "admin/estudiantes/agregarEstudiante.mustache");
        }, new MustacheTemplateEngine());

        //! ESTUDIANTES
        get("/admin/estudiantes", (req, res) -> {
            Map<String, Object> model = new HashMap<>();

            List<Estudiante> estudiantesDB = Estudiante.findAll();

            List<Map<String, Object>> estudiantes = new ArrayList<>();

            for (Estudiante estudiante : estudiantesDB) {

                Map<String, Object> estudianteView = new HashMap<>();
                
                // DATOS ESTUDIANTE
                estudianteView.put("id", estudiante.getInteger("nro_legajo"));

                estudianteView.put("email", estudiante.getString("email"));

                // PERSONA
                Integer dni = estudiante.getInteger("dni");

                Persona persona = Persona.findFirst("dni = ?", dni);

                if (persona != null) {
                    estudianteView.put("dni", persona.getInteger("dni"));
                    estudianteView.put("nombre",persona.getString("nombre"));
                    estudianteView.put("apellido", persona.getString("apellido"));
                    estudianteView.put("telefono", persona.getString("telefono"));
                    estudianteView.put("direccion", persona.getString("direccion"));
                }
                
                // USER
                Integer userId = estudiante.getInteger("user_id");
                User user = User.findById(userId);

                if (user != null) {
                    estudianteView.put("username", user.getString("name"));
                }

                estudiantes.add(estudianteView);
            }

            model.put("estudiantes", estudiantes);

            model.put(
                    "successMessage",
                    req.queryParams("successMessage")
            );

            model.put(
                    "errorMessage",
                    req.queryParams("errorMessage")
            );

            return new ModelAndView(model,"admin/estudiantes/estudiantesDashboard.mustache");

        }, new MustacheTemplateEngine());

        get("/admin/estudiantes/:id/edit", (req, res) -> {

            Map<String, Object> model = new HashMap<>();

            Integer nro_legajo = Integer.parseInt(req.params(":id"));
            
            // ESTUDIANTE
            Estudiante estudiante = Estudiante.findFirst("nro_legajo = ?", nro_legajo);

            if (estudiante == null) {
                res.redirect("/admin/estudiantes?errorMessage=Error: estudiante no encontrado");
                return null;
            }

            // PERSONA
            Integer dni = estudiante.getInteger("dni");

            Persona persona = Persona.findFirst("dni = ?", dni);

            // MODEL
            model.put("nro_legajo", estudiante.getInteger("nro_legajo"));
            model.put("email", estudiante.getString("email"));
            model.put("dni", persona.getInteger("dni"));
            model.put("nombre", persona.getString("nombre"));
            model.put("apellido", persona.getString("apellido"));
            model.put("fechaNacimiento", persona.getString("fecha_nacimiento"));
            model.put("telefono", persona.getString("telefono"));
            model.put("direccion",persona.getString("direccion"));

            return new ModelAndView(model,"admin/estudiantes/editarEstudiante.mustache");

        }, new MustacheTemplateEngine());

        post("/admin/estudiantes/:id/edit", (req, res) -> {

            Integer nro_legajo = Integer.parseInt(req.params(":id"));

            Estudiante estudiante = Estudiante.findFirst("nro_legajo = ?", nro_legajo);

            if (estudiante == null) {

                res.redirect(
                        "/admin/estudiantes?errorMessage=Estudiante no encontrado"
                );

                return null;
            }

            Integer dni = estudiante.getInteger("dni");

            // FORM
            String nombre = req.queryParams("nombre");
            String apellido = req.queryParams("apellido");
            String fechaNacimiento = req.queryParams("fecha_nacimiento");
            String telefono = req.queryParams("telefono");
            String direccion = req.queryParams("direccion");
            String email = req.queryParams("email");
            String nroLegajoString = req.queryParams("nro_legajo");

            try {

                Base.openTransaction();
                
                // UPDATE PERSONA
                Base.exec(
                        "UPDATE Persona " +
                        "SET nombre = ?, apellido = ?, fecha_nacimiento = ?, telefono = ?, direccion = ? " +
                        "WHERE dni = ?",

                        nombre,
                        apellido,
                        fechaNacimiento,
                        telefono,
                        direccion,
                        dni
                );
                
                // UPDATE ESTUDIANTE
                Base.exec(
                        "UPDATE Estudiante SET email = ? WHERE nro_legajo = ?",
                        email,
                        nro_legajo
                );

                Base.commitTransaction();

                res.redirect(
                        "/admin/estudiantes?successMessage=Estudiante actualizado correctamente"
                );

                return null;

            } catch (Exception e) {

                Base.rollbackTransaction();

                e.printStackTrace();

                res.redirect(
                        "/admin/estudiantes/" + nro_legajo + "/edit?errorMessage=Error al actualizar estudiante");

                return null;
            }

        });

        get("/admin/estudiantes/:id/delete", (req, res) -> {
            Map<String, Object> model = new HashMap<>();

            Integer nro_legajo = Integer.parseInt(req.params(":id"));

            Estudiante estudiante = Estudiante.findFirst("nro_legajo = ?", nro_legajo);

            if (estudiante == null) {
                res.redirect("/admin/estudiantes?errorMessage=Estudiante no encontrado");
                return null;
            }

            Integer dni = estudiante.getInteger("dni");

            Persona persona = Persona.findFirst("dni = ?", dni);

            Integer userId = estudiante.getInteger("user_id");

            User user = User.findById(userId);

            model.put("nro_legajo", nro_legajo);
            model.put("email", estudiante.getString("email"));

            if (persona != null) {
                model.put("dni", persona.getInteger("dni"));
                model.put("nombre", persona.getString("nombre"));
                model.put("apellido", persona.getString("apellido"));
            }

            if (user != null) {
                model.put("username", user.getString("name"));
            }

            return new ModelAndView(model, "admin/estudiantes/eliminarEstudiante.mustache");

        }, new MustacheTemplateEngine());

        post("/admin/estudiantes/:id/delete", (req, res) -> {

            Integer nro_legajo = Integer.parseInt(req.params(":id"));

            Estudiante estudiante = Estudiante.findFirst("nro_legajo = ?", nro_legajo);
            if (estudiante == null) {
                res.redirect("/admin/estudiantes?errorMessage=Estudiante no encontrado");
                return null;
            }

            Integer dni = estudiante.getInteger("dni");
            Integer userId = estudiante.getInteger("user_id");

            User user = User.findById(userId);

            try {
                Base.openTransaction();

                // ELIMINAR ESTUDIANTE
                Base.exec("DELETE FROM Estudiante WHERE nro_legajo = ?", nro_legajo);

                // ELIMINAR PERSONA
                Base.exec("DELETE FROM Persona WHERE dni = ?", dni);

                // RESET ROL USER
                if (user != null) {
                    user.set("rol", "UNASSIGNED");
                    user.saveIt();
                }

                Base.commitTransaction();

                res.redirect("/admin/estudiantes?successMessage=Estudiante eliminado correctamente");

                return null;

            } catch (Exception e) {
                Base.rollbackTransaction();
                e.printStackTrace();
                res.redirect("/admin/estudiantes?errorMessage=Error al eliminar estudiante");

                return null;
            }

        });

        get("/admin/estudiantes/listado", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            List<Estudiante> estudiantesDB = Estudiante.findAll();
            List<Map<String, Object>> estudiantes = new ArrayList<>();

            for (Estudiante estudiante : estudiantesDB) {

                Map<String, Object> estudianteView = new HashMap<>();

                estudianteView.put("id", estudiante.getInteger("nro_legajo"));
                estudianteView.put("email", estudiante.getString("email"));

                Integer dni = estudiante.getInteger("dni");

                Persona persona = Persona.findFirst("dni = ?", dni);

                if (persona != null) {
                    estudianteView.put("dni", persona.getInteger("dni"));
                    estudianteView.put("nombre", persona.getString("nombre"));
                    estudianteView.put("apellido", persona.getString("apellido"));
                    estudianteView.put("telefono", persona.getString("telefono")); 
                    estudianteView.put("direccion", persona.getString("direccion"));
                    estudianteView.put("fecha_nacimiento", persona.getString("fecha_nacimiento"));
                }

                Integer userId = estudiante.getInteger("user_id");

                User user = User.findById(userId);

                if (user != null) {
                    estudianteView.put("username", user.getString("name"));
                }

                estudiantes.add(estudianteView);
            }

            model.put("estudiantes", estudiantes);

            return new ModelAndView(model,"admin/estudiantes/listadoEstudiantes.mustache");

        }, new MustacheTemplateEngine());


        // CRUD plan de estudios
        // GENERAL
        get("/admin/planes", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            List<PlanDeEstudios> planesDB = PlanDeEstudios.findAll();
            List<Map<String, Object>> planes = new ArrayList<>();

            for (PlanDeEstudios plan : planesDB) {
                Map<String, Object> planView = new HashMap<>();
                planView.put("codPlan",    plan.getCod());
                planView.put("anio",       plan.getAño());
                planView.put("vigencia",   plan.getVigencia());
                planView.put("aniosTotal", plan.getAñosTotal());
                planView.put("cantMaterias", plan.getCantidadMaterias());

                // Nombre de la carrera asociada
                Carrera carrera = Carrera.findFirst("cod_carrera = ?", plan.getCod());
                planView.put("nombreCarrera", carrera != null ? carrera.getNombre() : "Sin carrera");

                planes.add(planView);
            }

            model.put("planes", planes);
            model.put("successMessage", req.queryParams("successMessage"));
            model.put("errorMessage",   req.queryParams("errorMessage"));

            return new ModelAndView(model, "admin/planes/planesDashboard.mustache");
        }, new MustacheTemplateEngine());

        // FORMULARIO CREAR
        get("/admin/planes/agregar", (req, res) -> {
            Map<String, Object> model = new HashMap<>();

            // Pasar lista de carreras para el <select>
            List<Carrera> carrerasDB = Carrera.findAll();
            List<Map<String, Object>> carreras = new ArrayList<>();
            for (Carrera c : carrerasDB) {
                Map<String, Object> cv = new HashMap<>();
                cv.put("codCarrera", c.getCodigo());
                cv.put("nombre",     c.getNombre());
                carreras.add(cv);
            }
            model.put("carreras", carreras);
            model.put("successMessage", req.queryParams("successMessage"));
            model.put("errorMessage",   req.queryParams("errorMessage"));

            return new ModelAndView(model, "admin/planes/agregarPlan.mustache");
        }, new MustacheTemplateEngine());

        // CREAR
        post("/admin/planes/agregar", (req, res) -> {
            String anioStr        = req.queryParams("anio");
            String vigenciaStr    = req.queryParams("vigencia");
            String aniosTotalStr  = req.queryParams("anios_total");
            String cantMatStr     = req.queryParams("cantidad_materias_total");
            String codCarreraStr  = req.queryParams("cod_carrera");

            if (anioStr.isEmpty() || vigenciaStr.isEmpty() || aniosTotalStr.isEmpty()
                    || cantMatStr.isEmpty() || codCarreraStr.isEmpty()) {
                res.redirect("/admin/planes/agregar?errorMessage=Todos los campos son obligatorios.");
                return null;
            }

            try {
                int año        = Integer.parseInt(anioStr);
                int vigencia   = Integer.parseInt(vigenciaStr);
                int aniosTotal = Integer.parseInt(aniosTotalStr);
                int cantMat    = Integer.parseInt(cantMatStr);
                int codCarrera = Integer.parseInt(codCarreraStr);

                Carrera carrera = Carrera.findFirst("cod_carrera = ?", codCarrera);
                if (carrera == null) {
                    res.redirect("/admin/planes/agregar?errorMessage=La carrera seleccionada no existe.");
                    return null;
                }

                PlanDeEstudios plan = new PlanDeEstudios();
                plan.setAño(año);
                plan.setVigencia(vigencia);
                plan.setAñosTotal(aniosTotal);
                plan.setCantidadMaterias(cantMat);
                plan.set("cod_carrera", codCarrera); // <-- directo, sin pasar por setCod()
                plan.saveIt();

                res.redirect("/admin/planes?successMessage=Plan creado correctamente.");
                return null;

            } catch (NumberFormatException e) {
                res.redirect("/admin/planes/agregar?errorMessage=Los campos numéricos deben ser números válidos.");
                return null;
            } catch (Exception e) {
                e.printStackTrace();
                res.redirect("/admin/planes/agregar?errorMessage=Error al crear el plan: " + e.getMessage());
                return null;
            }
        });

        // FORMULARIO EDITAR
        get("/admin/planes/:id/edit", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            int codPlan = Integer.parseInt(req.params(":id"));

            PlanDeEstudios plan = PlanDeEstudios.findFirst("cod_plan = ?", codPlan);
            if (plan == null) {
                res.redirect("/admin/planes?errorMessage=Plan no encontrado.");
                return null;
            }

            model.put("codPlan",      plan.getCod());
            model.put("anio",         plan.getAño());
            model.put("vigencia",     plan.getVigencia());
            model.put("aniosTotal",   plan.getAñosTotal());
            model.put("cantMaterias", plan.getCantidadMaterias());

            // Lista de carreras separadas para el <select>
            List<Carrera> carrerasDB = Carrera.findAll();
            List<Map<String, Object>> carreraSelected    = new ArrayList<>();
            List<Map<String, Object>> carrerasNoSelected = new ArrayList<>();

            for (Carrera c : carrerasDB) {
                Map<String, Object> cv = new HashMap<>();
                cv.put("codCarrera", c.getCodigo());
                cv.put("nombre",     c.getNombre());

                if (c.getCodigo().equals(plan.getCod())) {
                    carreraSelected.add(cv);
                } else {
                    carrerasNoSelected.add(cv);
                }
            }

            model.put("carreraSelected",    carreraSelected);
            model.put("carrerasNoSelected", carrerasNoSelected);
            model.put("errorMessage", req.queryParams("errorMessage"));

            return new ModelAndView(model, "admin/planes/editarPlan.mustache");
        }, new MustacheTemplateEngine());

        // EDITAR
        post("/admin/planes/:id/edit", (req, res) -> {
            int codPlan = Integer.parseInt(req.params(":id"));

            PlanDeEstudios plan = PlanDeEstudios.findFirst("cod_plan = ?", codPlan);
            if (plan == null) {
                res.redirect("/admin/planes?errorMessage=Plan no encontrado.");
                return null;
            }

            String vigenciaStr   = req.queryParams("vigencia");
            String aniosTotalStr = req.queryParams("anios_total");
            String cantMatStr    = req.queryParams("cantidad_materias_total");
            String codCarreraStr = req.queryParams("cod_carrera");

            if (vigenciaStr.isEmpty() || aniosTotalStr.isEmpty()
                    || cantMatStr.isEmpty() || codCarreraStr.isEmpty()) {
                res.redirect("/admin/planes/" + codPlan + "/edit?errorMessage=Todos los campos son obligatorios.");
                return null;
            }

            try {
                Base.openTransaction();

                Base.exec(
                    "UPDATE PlanDeEstudios SET vigencia = ?, años_total = ?, " +
                    "cantidad_materias_total = ?, cod_carrera = ? WHERE cod_plan = ?",
                    Integer.parseInt(vigenciaStr),
                    Integer.parseInt(aniosTotalStr),
                    Integer.parseInt(cantMatStr),
                    Integer.parseInt(codCarreraStr),
                    codPlan
                );

                Base.commitTransaction();
                res.redirect("/admin/planes?successMessage=Plan actualizado correctamente.");
                return null;

            } catch (Exception e) {
                Base.rollbackTransaction();
                e.printStackTrace();
                res.redirect("/admin/planes/" + codPlan + "/edit?errorMessage=Error al actualizar: " + e.getMessage());
                return null;
            }
        });

        // CONFIRMAR ELIMINAR
        get("/admin/planes/:id/delete", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            int codPlan = Integer.parseInt(req.params(":id"));

            PlanDeEstudios plan = PlanDeEstudios.findFirst("cod_plan = ?", codPlan);
            if (plan == null) {
                res.redirect("/admin/planes?errorMessage=Plan no encontrado.");
                return null;
            }

            Carrera carrera = Carrera.findFirst("cod_carrera = ?", plan.getCod());

            model.put("codPlan",      plan.getCod());
            model.put("anio",         plan.getAño());
            model.put("vigencia",     plan.getVigencia());
            model.put("nombreCarrera", carrera != null ? carrera.getNombre() : "Sin carrera");

            // Listar materias asociadas
            List<Materia> materiasDB = Materia.where("cod_plan = ?", codPlan);
            List<Map<String, Object>> materias = new ArrayList<>();
            for (Materia m : materiasDB) {
                Map<String, Object> mv = new HashMap<>();
                mv.put("codMateria", m.getInteger("cod_materia"));
                mv.put("nombre",     m.getString("nombre"));
                materias.add(mv);
            }
            model.put("materias",      materias);
            model.put("tieneMaterias", !materias.isEmpty());

            return new ModelAndView(model, "admin/planes/eliminarPlan.mustache");
        }, new MustacheTemplateEngine());

        // ELIMINAR
        post("/admin/planes/:id/delete", (req, res) -> {
            int codPlan = Integer.parseInt(req.params(":id"));

            PlanDeEstudios plan = PlanDeEstudios.findFirst("cod_plan = ?", codPlan);
            if (plan == null) {
                res.redirect("/admin/planes?errorMessage=Plan no encontrado.");
                return null;
            }

            try {
                Base.openTransaction();

                // Primero eliminar materias asociadas (integridad referencial)
                Base.exec("DELETE FROM Materia WHERE cod_plan = ?", codPlan);
                Base.exec("DELETE FROM PlanDeEstudios WHERE cod_plan = ?", codPlan);

                Base.commitTransaction();
                res.redirect("/admin/planes?successMessage=Plan eliminado correctamente.");
                return null;

            } catch (Exception e) {
                Base.rollbackTransaction();
                e.printStackTrace();
                res.redirect("/admin/planes?errorMessage=Error al eliminar el plan.");
                return null;
            }
        });

        // LISTAR MATERIAS DE UN PLAN
        get("/admin/planes/:id/materias", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            int codPlan = Integer.parseInt(req.params(":id"));

            PlanDeEstudios plan = PlanDeEstudios.findFirst("cod_plan = ?", codPlan);
            if (plan == null) {
                res.redirect("/admin/planes?errorMessage=Plan no encontrado.");
                return null;
            }

            Carrera carrera = Carrera.findFirst("cod_carrera = ?", plan.getCod());

            List<Materia> materiasDB = Materia.where("cod_plan = ?", codPlan);
            List<Map<String, Object>> materias = new ArrayList<>();
            for (Materia m : materiasDB) {
                Map<String, Object> mv = new HashMap<>();
                mv.put("codMateria",  m.getInteger("cod_materia"));
                mv.put("nombre",      m.getString("nombre"));
                mv.put("descripcion", m.getString("descripcion"));
                materias.add(mv);
            }

            model.put("codPlan",      plan.getCod());
            model.put("anio",         plan.getAño());
            model.put("vigencia",     plan.getVigencia());
            model.put("nombreCarrera", carrera != null ? carrera.getNombre() : "Sin carrera");
            model.put("materias",     materias);
            model.put("sinMaterias",  materias.isEmpty());

            return new ModelAndView(model, "admin/planes/materiasPlan.mustache");
        }, new MustacheTemplateEngine());

        // CRUD de carreras
        // LISTAR
        get("/admin/carreras", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            List<Carrera> carrerasDB = Carrera.findAll();
            List<Map<String, Object>> carreras = new ArrayList<>();

            for (Carrera c : carrerasDB) {
                Map<String, Object> cv = new HashMap<>();
                cv.put("codCarrera",  c.getCodigo());
                cv.put("nombre",      c.getNombre());
                cv.put("descripcion", c.getDescripcion());
                carreras.add(cv);
            }

            model.put("carreras",       carreras);
            model.put("successMessage", req.queryParams("successMessage"));
            model.put("errorMessage",   req.queryParams("errorMessage"));

            return new ModelAndView(model, "admin/carreras/carrerasDashboard.mustache");
        }, new MustacheTemplateEngine());

        // FORMULARIO CREAR
        get("/admin/carreras/agregar", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            model.put("successMessage", req.queryParams("successMessage"));
            model.put("errorMessage",   req.queryParams("errorMessage"));

            return new ModelAndView(model, "admin/carreras/agregarCarrera.mustache");
        }, new MustacheTemplateEngine());

        // CREAR
        post("/admin/carreras/agregar", (req, res) -> {
            String nombre      = req.queryParams("nombre");
            String descripcion = req.queryParams("descripcion");

            if (nombre == null || nombre.isEmpty()) {
                res.redirect("/admin/carreras/agregar?errorMessage=El nombre es obligatorio.");
                return null;
            }

            try {
                Carrera carreraExistente = Carrera.findFirst("nombre = ?", nombre);
                if (carreraExistente != null) {
                    res.redirect("/admin/carreras/agregar?errorMessage=Ya existe una carrera con ese nombre.");
                    return null;
                }

                Carrera carrera = new Carrera();
                carrera.setNombre(nombre);
                carrera.setDescripcion(descripcion);
                carrera.saveIt();

                res.redirect("/admin/carreras?successMessage=Carrera creada correctamente.");
                return null;

            } catch (Exception e) {
                e.printStackTrace();
                res.redirect("/admin/carreras/agregar?errorMessage=Error al crear la carrera: " + e.getMessage());
                return null;
            }
        });

        // FORMULARIO EDITAR
        get("/admin/carreras/:id/edit", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            int codCarrera = Integer.parseInt(req.params(":id"));

            Carrera carrera = Carrera.findFirst("cod_carrera = ?", codCarrera);
            if (carrera == null) {
                res.redirect("/admin/carreras?errorMessage=Carrera no encontrada.");
                return null;
            }

            model.put("codCarrera",   carrera.getCodigo());
            model.put("nombre",       carrera.getNombre());
            model.put("descripcion",  carrera.getDescripcion());
            model.put("errorMessage", req.queryParams("errorMessage"));

            return new ModelAndView(model, "admin/carreras/editarCarrera.mustache");
        }, new MustacheTemplateEngine());

        // EDITAR
        post("/admin/carreras/:id/edit", (req, res) -> {
            int codCarrera = Integer.parseInt(req.params(":id"));

            Carrera carrera = Carrera.findFirst("cod_carrera = ?", codCarrera);
            if (carrera == null) {
                res.redirect("/admin/carreras?errorMessage=Carrera no encontrada.");
                return null;
            }

            String nombre      = req.queryParams("nombre");
            String descripcion = req.queryParams("descripcion");

            if (nombre == null || nombre.isEmpty()) {
                res.redirect("/admin/carreras/" + codCarrera + "/edit?errorMessage=El nombre es obligatorio.");
                return null;
            }

            try {
                Base.exec(
                    "UPDATE Carrera SET nombre = ?, descripcion = ? WHERE cod_carrera = ?",
                    nombre, descripcion, codCarrera
                );

                res.redirect("/admin/carreras?successMessage=Carrera actualizada correctamente.");
                return null;

            } catch (Exception e) {
                e.printStackTrace();
                res.redirect("/admin/carreras/" + codCarrera + "/edit?errorMessage=Error al actualizar: " + e.getMessage());
                return null;
            }
        });

        // CONFIRMAR ELIMINAR
        get("/admin/carreras/:id/delete", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            int codCarrera = Integer.parseInt(req.params(":id"));

            Carrera carrera = Carrera.findFirst("cod_carrera = ?", codCarrera);
            if (carrera == null) {
                res.redirect("/admin/carreras?errorMessage=Carrera no encontrada.");
                return null;
            }

            // Verificar si tiene planes asociados
            PlanDeEstudios planAsociado = PlanDeEstudios.findFirst("cod_carrera = ?", codCarrera);

            model.put("codCarrera",   carrera.getCodigo());
            model.put("nombre",       carrera.getNombre());
            model.put("descripcion",  carrera.getDescripcion());
            model.put("tienePlanes",  planAsociado != null);

            return new ModelAndView(model, "admin/carreras/eliminarCarrera.mustache");
        }, new MustacheTemplateEngine());

        // ELIMINAR
        post("/admin/carreras/:id/delete", (req, res) -> {
            int codCarrera = Integer.parseInt(req.params(":id"));

            Carrera carrera = Carrera.findFirst("cod_carrera = ?", codCarrera);
            if (carrera == null) {
                res.redirect("/admin/carreras?errorMessage=Carrera no encontrada.");
                return null;
            }

            // Bloquear si tiene planes asociados
            PlanDeEstudios planAsociado = PlanDeEstudios.findFirst("cod_carrera = ?", codCarrera);
            if (planAsociado != null) {
                res.redirect("/admin/carreras?errorMessage=No se puede eliminar una carrera con planes asociados.");
                return null;
            }

            try {
                Base.exec("DELETE FROM Carrera WHERE cod_carrera = ?", codCarrera);
                res.redirect("/admin/carreras?successMessage=Carrera eliminada correctamente.");
                return null;

            } catch (Exception e) {
                e.printStackTrace();
                res.redirect("/admin/carreras?errorMessage=Error al eliminar la carrera.");
                return null;
            }
        });
        
        // MANEJO DE ERRORES
        // 404 (ejemplo: ir a una ruta que no existe)
        
        notFound((req, res) -> {
            res.type("text/html");
            logger.warn("404 - Ruta no encontrada: {}", req.url());
            return "<h1>404 - Pagina no encontrada</h1><p>La ruta <b>" + req.url() + "</b> no existe.</p><a href='/dashboard'>Volver al inicio</a>";
        });

        // 500
        internalServerError((req, res) -> {
            res.type("text/html");
            logger.error("500 - Error interno en: {}", req.url());
            return "<h1>500 - Error interno del servidor</h1><p>Ocurrió un error inesperado. Intente más tarde.</p><a href='/dashboard'>Volver al inicio</a>";
        });

        // Excepciones no capturadas
        exception(Exception.class, (e, req, res) -> {
            logger.error("Excepción no manejada en {}: {}", req.url(), e.getMessage(), e);
            res.status(500);
            res.type("text/html");
            res.body("<h1>500 - Error interno del servidor</h1><p>Ocurrió un error inesperado. Intente más tarde.</p><a href='/dashboard'>Volver al inicio</a>");
        });

        // Acceso no autorizado (ejemplo: intenta entrar a admin sin estar logueado)
        exception(spark.HaltException.class, (e, req, res) -> {
            logger.warn("Acceso detenido en {}: status {}", req.url(), e.statusCode());
        });

    // ASIGNAR MATERIAS A DOCENTES
    // VER MATERIAS DEL DOCENTE
    get("/admin/docentes/:id/materias", (req, res) -> {
        Map<String, Object> model = new HashMap<>();
        int codigoProfesor = Integer.parseInt(req.params(":id"));

        Docente docente = Docente.findFirst("codigo_profesor = ?", codigoProfesor);
        if (docente == null) {
            res.redirect("/admin/docentes?errorMessage=Docente no encontrado.");
            return null;
        }

        Persona persona = Persona.findFirst("dni = ?", docente.getInteger("dni"));

        // Materias asignadas
        List<PeriodoAcademico> asignacionesDB = PeriodoAcademico.where("codigo_profesor = ?", codigoProfesor);
        List<Map<String, Object>> asignaciones = new ArrayList<>();
        for (PeriodoAcademico pa : asignacionesDB) {
            Map<String, Object> av = new HashMap<>();
            av.put("id",    pa.getId());
            av.put("fecha", pa.getFecha());
            av.put("cargo", pa.getCargo().replace("_", " "));
            Materia materia = Materia.findFirst("cod_materia = ?", pa.getCodMateria());
            av.put("nombreMateria", materia != null ? materia.getNombre() : "Sin materia");
            asignaciones.add(av);
        }

        // Materias disponibles (sin asignar a este docente)
        List<Materia> todasMaterias = Materia.findAll();
        List<Map<String, Object>> materiasDisponibles = new ArrayList<>();
        for (Materia m : todasMaterias) {
            PeriodoAcademico ya = PeriodoAcademico.findFirst(
                "codigo_profesor = ? AND cod_materia = ?", codigoProfesor, m.getCodMateria()
            );
            if (ya == null) {
                Map<String, Object> mv = new HashMap<>();
                mv.put("codMateria", m.getCodMateria());
                mv.put("nombre",     m.getNombre());
                materiasDisponibles.add(mv);
            }
        }

        model.put("codigoProfesor",     codigoProfesor);
        model.put("nombreDocente",      persona != null ? persona.getString("nombre") + " " + persona.getString("apellido") : "Sin nombre");
        model.put("asignaciones",       asignaciones);
        model.put("sinAsignaciones",    asignaciones.isEmpty());
        model.put("materiasDisponibles", materiasDisponibles);
        model.put("sinMaterias",        materiasDisponibles.isEmpty());
        model.put("successMessage",     req.queryParams("successMessage"));
        model.put("errorMessage",       req.queryParams("errorMessage"));

        return new ModelAndView(model, "admin/docentes/materiasDocente.mustache");
    }, new MustacheTemplateEngine());

    // ASIGNAR MATERIA AL DOCENTE
    post("/admin/docentes/:id/materias/agregar", (req, res) -> {
        int codigoProfesor = Integer.parseInt(req.params(":id"));
        String codMateriaStr = req.queryParams("cod_materia");
        String fecha         = req.queryParams("fecha");
        String cargo         = req.queryParams("cargo");

        if (codMateriaStr == null || codMateriaStr.isEmpty() || fecha.isEmpty() || cargo.isEmpty()) {
            res.redirect("/admin/docentes/" + codigoProfesor + "/materias?errorMessage=Todos los campos son obligatorios.");
            return null;
        }

        try {
            int codMateria = Integer.parseInt(codMateriaStr);

            PeriodoAcademico existente = PeriodoAcademico.findFirst(
                "codigo_profesor = ? AND cod_materia = ?", codigoProfesor, codMateria
            );
            if (existente != null) {
                res.redirect("/admin/docentes/" + codigoProfesor + "/materias?errorMessage=Ese docente ya está asignado a esa materia.");
                return null;
            }

            PeriodoAcademico pa = new PeriodoAcademico();
            pa.setCodigoProfesor(codigoProfesor);
            pa.setCodMateria(codMateria);
            pa.setFecha(fecha);
            pa.setCargo(cargo);
            pa.saveIt();

            res.redirect("/admin/docentes/" + codigoProfesor + "/materias?successMessage=Materia asignada correctamente.");
            return null;

        } catch (Exception e) {
            e.printStackTrace();
            res.redirect("/admin/docentes/" + codigoProfesor + "/materias?errorMessage=Error al asignar materia.");
            return null;
        }
    });

    // QUITAR MATERIA DEL DOCENTE
    post("/admin/docentes/:id/materias/:asignacionId/delete", (req, res) -> {
        int codigoProfesor = Integer.parseInt(req.params(":id"));
        int asignacionId   = Integer.parseInt(req.params(":asignacionId"));

        try {
            Base.exec("DELETE FROM PeriodoAcademico WHERE id = ? AND codigo_profesor = ?", asignacionId, codigoProfesor);
            res.redirect("/admin/docentes/" + codigoProfesor + "/materias?successMessage=Materia quitada correctamente.");
            return null;

        } catch (Exception e) {
            e.printStackTrace();
            res.redirect("/admin/docentes/" + codigoProfesor + "/materias?errorMessage=Error al quitar la materia.");
            return null;
        }
    });

    //! MATERIAS 
    // GENERAL
    get("/admin/materias", (req, res) -> {
        Map<String, Object> model = new HashMap<>();
        List<Materia> materiasDB = Materia.findAll();
        List<Map<String, Object>> materias = new ArrayList<>();

        for (Materia m : materiasDB) {
            Map<String, Object> mv = new HashMap<>();
            mv.put("codMateria",          m.getCodMateria());
            mv.put("nombre",      m.getNombre());
            mv.put("descripcion", m.getDescripcion());

            PlanDeEstudios plan = PlanDeEstudios.findFirst("cod_plan = ?", m.getCodPlan());
            mv.put("nombrePlan", plan != null ? "Plan " + plan.getAño() : "Sin plan");

            materias.add(mv);
        }

        model.put("materias",       materias);
        model.put("successMessage", req.queryParams("successMessage"));
        model.put("errorMessage",   req.queryParams("errorMessage"));

        return new ModelAndView(model, "admin/materias/materiasDashboard.mustache");
    }, new MustacheTemplateEngine());

    // FORMULARIO CREAR
    get("/admin/materias/agregar", (req, res) -> {
        Map<String, Object> model = new HashMap<>();

        List<PlanDeEstudios> planesDB = PlanDeEstudios.findAll();
        List<Map<String, Object>> planes = new ArrayList<>();
        for (PlanDeEstudios p : planesDB) {
            Map<String, Object> pv = new HashMap<>();
            pv.put("codPlan",    p.getCod());
            pv.put("nombrePlan", "Plan " + p.getAño());
            planes.add(pv);
        }

        model.put("planes",         planes);
        model.put("sinPlanes",      planes.isEmpty());
        model.put("errorMessage",   req.queryParams("errorMessage"));

        return new ModelAndView(model, "admin/materias/agregarMateria.mustache");
    }, new MustacheTemplateEngine());

    // AGREGAR
    post("/admin/materias/agregar", (req, res) -> {
        String nombre      = req.queryParams("nombre");
        String codigo      = req.queryParams("cod_materia");
        String descripcion = req.queryParams("descripcion");
        String codPlanStr  = req.queryParams("cod_plan");

        if (nombre == null || nombre.isEmpty() || codigo == null || codigo.isEmpty() || codPlanStr == null || codPlanStr.isEmpty()) {
            res.redirect("/admin/materias/agregar?errorMessage=Nombre, codigo y plan son obligatorios.");
            return null;
        }

        try {
            int codPlan = Integer.parseInt(codPlanStr);
            int codMat = Integer.parseInt(codigo);

            Materia materia = new Materia();
            materia.setNombre(nombre);
            materia.setDescripcion(descripcion);
            materia.setCodMateria(codMat);
            materia.setCodPlan(codPlan);
            materia.saveIt();

            res.redirect("/admin/materias?successMessage=Materia creada correctamente.");
            return null;

        } catch (Exception e) {
            e.printStackTrace();
            res.redirect("/admin/materias/agregar?errorMessage=Error al crear la materia: " + e.getMessage());
            return null;
        }
    });

    // FORMULARIO EDITAR
    get("/admin/materias/:id/edit", (req, res) -> {
        Map<String, Object> model = new HashMap<>();
        int codMateria = Integer.parseInt(req.params(":id"));

        Materia materia = Materia.findFirst("cod_materia = ?", codMateria);
        if (materia == null) {
            res.redirect("/admin/materias?errorMessage=Materia no encontrada.");
            return null;
        }

        List<PlanDeEstudios> planesDB = PlanDeEstudios.findAll();
        List<Map<String, Object>> planes = new ArrayList<>();
        for (PlanDeEstudios p : planesDB) {
            Map<String, Object> pv = new HashMap<>();
            pv.put("codPlan",    p.getCod());
            pv.put("nombrePlan", "Plan " + p.getAño());
            pv.put("selected",   p.getCod().equals(materia.getCodPlan()));
            planes.add(pv);
        }

        model.put("codMateria",   materia.getCodMateria());
        model.put("nombre",       materia.getNombre());
        model.put("Codigo",       materia.getCodMateria());
        model.put("descripcion",  materia.getDescripcion());
        model.put("planes",       planes);
        model.put("errorMessage", req.queryParams("errorMessage"));

        return new ModelAndView(model, "admin/materias/editarMateria.mustache");
    }, new MustacheTemplateEngine());

    // EDITAR
    post("/admin/materias/:id/edit", (req, res) -> {
        int codMateria = Integer.parseInt(req.params(":id"));

        Materia materia = Materia.findFirst("cod_materia = ?", codMateria);
        if (materia == null) {
            res.redirect("/admin/materias?errorMessage=Materia no encontrada.");
            return null;
        }

        String nombre      = req.queryParams("nombre");
        String descripcion = req.queryParams("descripcion");
        String codPlanStr  = req.queryParams("cod_plan");

        if (nombre == null || nombre.isEmpty() || codPlanStr == null || codPlanStr.isEmpty()) {
            res.redirect("/admin/materias/" + codMateria + "/edit?errorMessage=Nombre, Codigo y Plan son obligatorios.");
            return null;
        }

        try {
            Base.exec(
                "UPDATE Materia SET nombre = ?, descripcion = ?, cod_plan = ? WHERE cod_materia = ?",
                nombre, descripcion, Integer.parseInt(codPlanStr), codMateria
            );

            res.redirect("/admin/materias?successMessage=Materia actualizada correctamente.");
            return null;

        } catch (Exception e) {
            e.printStackTrace();
            res.redirect("/admin/materias/" + codMateria + "/edit?errorMessage=Error al actualizar: " + e.getMessage());
            return null;
        }
    });

    // CONFIRMAR ELIMINAR
    get("/admin/materias/:id/delete", (req, res) -> {
        Map<String, Object> model = new HashMap<>();
        int codMateria = Integer.parseInt(req.params(":id"));

        Materia materia = Materia.findFirst("cod_materia = ?", codMateria);
        if (materia == null) {
            res.redirect("/admin/materias?errorMessage=Materia no encontrada.");
            return null;
        }

        model.put("codMateria",  materia.getCodMateria());
        model.put("nombre",      materia.getNombre());
        model.put("descripcion", materia.getDescripcion());

        return new ModelAndView(model, "admin/materias/eliminarMateria.mustache");
    }, new MustacheTemplateEngine());

    // ELIMINAR
    post("/admin/materias/:id/delete", (req, res) -> {
        int codMateria = Integer.parseInt(req.params(":id"));

        Materia materia = Materia.findFirst("cod_materia = ?", codMateria);
        if (materia == null) {
            res.redirect("/admin/materias?errorMessage=Materia no encontrada.");
            return null;
        }

        try {
            Base.openTransaction();
            Base.exec("DELETE FROM PeriodoAcademico WHERE cod_materia = ?", codMateria);
            Base.exec("DELETE FROM Materia WHERE cod_materia = ?", codMateria);
            Base.commitTransaction();

            res.redirect("/admin/materias?successMessage=Materia eliminada correctamente.");
            return null;

        } catch (Exception e) {
            Base.rollbackTransaction();
            e.printStackTrace();
            res.redirect("/admin/materias?errorMessage=Error al eliminar la materia.");
            return null;
        }
    });

    // LISTAR
    get("admin/materias/listado", (req, res) -> {
        Map<String, Object> model = new HashMap<>();
        List<Materia> materiasDB = Materia.findAll();
        List<Map<String, Object>> materias = new ArrayList<>();

        for(Materia materia : materiasDB){
            Map<String, Object> materiaView = new HashMap<>();

            materiaView.put("codMateria", materia.getCodMateria());
            materiaView.put("nombre", materia.getNombre());
            materiaView.put("descripcion", materia.getDescripcion());

            PlanDeEstudios plan = PlanDeEstudios.findFirst("cod_plan = ?", materia.getCodPlan());
            materiaView.put("nombrePlan", plan != null ? "Plan " + plan.getAño() : "Sin plan");


            materias.add(materiaView);
        }

        model.put("materias", materias);
        return new ModelAndView(model, "admin/materias/listadoMaterias.mustache");
    }, new MustacheTemplateEngine());



    } // Fin del método main

    // HELPERS
    public static boolean esEmailValido(String email) {
        String regex = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";
        return email != null && email.matches(regex);
    }
    
    private static boolean isAdmin(Request req) {
        String rol = req.session().attribute("userRol");
        return "ADMINISTRADOR".equals(rol);
    }
    private static List<Map<String, Object>> getMateriasResponsable(Integer codigoProfesor) {
        List<PeriodoAcademico> periodos = PeriodoAcademico.where(
            "codigo_profesor = ? AND cargo = ?",
            codigoProfesor, "Responsable_de_Catedra"
        );
        List<Map<String, Object>> result = new ArrayList<>();
        for (PeriodoAcademico p : periodos) {
            Materia m = Materia.findFirst("cod_materia = ?", p.getCodMateria());
            if (m != null) {
                Map<String, Object> mv = new HashMap<>();
                mv.put("cod_materia", m.getCodMateria());
                mv.put("nombre", m.getNombre());
                result.add(mv);
            }
        }
        return result;
    }
 
    private static boolean isDocente(Request req) {
        String rol = req.session().attribute("userRol");
        return "DOCENTE".equals(rol);
    }
 
    // ---------------------------------------------------------------
    // RUTAS DOCENTE — Exámenes Finales
    // ---------------------------------------------------------------
 
    private static void registrarRutasExamenes() {
 
        // Proteger todas las rutas /docente/* para que solo docentes accedan
        before("/docente/*", (req, res) -> {
            Boolean loggedIn = req.session().attribute("loggedIn");
            if (loggedIn == null || !loggedIn) {
                res.redirect("/login");
                halt();
            }
            if (!isDocente(req)) {
                res.redirect("/dashboard?error=Solo los docentes pueden acceder a esta sección.");
                halt();
            }
        });
 
        // ---- Helper interno: materias del docente donde es Responsable_de_Catedra ----
        // Se usa en el GET crear, GET editar y validación del POST.
 
        // GET: listar exámenes del docente logueado
        get("/docente/examenes", (req, res) -> {
            Integer userId = req.session().attribute("userId");
            Docente docente = Docente.findFirst("user_id = ?", userId);
            if (docente == null) {
                res.redirect("/dashboard?error=No se encontró el perfil de docente.");
                return null;
            }
 
            List<ExamenFinal> examenes = ExamenFinal.where(
                "codigo_profesor = ?", docente.getCodigoProfesor()
            );
 
            List<Map<String, Object>> examenesView = new ArrayList<>();
            for (ExamenFinal e : examenes) {
                Map<String, Object> ev = new HashMap<>();
                ev.put("id", e.getId());
                ev.put("fecha", e.getFecha());
                ev.put("horario", e.getHorario());
                ev.put("aula", e.getAula());
                ev.put("observaciones", e.getObservaciones());
                Materia m = Materia.findFirst("cod_materia = ?", e.getCodMateria());
                ev.put("nombreMateria", m != null ? m.getNombre() : "Materia desconocida");
                examenesView.add(ev);
            }
 
            Map<String, Object> model = new HashMap<>();
            model.put("examenes", examenesView);
 
            String success = req.queryParams("successMessage");
            String error   = req.queryParams("errorMessage");
            if (success != null) model.put("successMessage", success);
            if (error   != null) model.put("errorMessage", error);
 
            return new ModelAndView(model, "docente/examenesDashboard.mustache");
        }, new MustacheTemplateEngine());
 
        // GET: formulario crear examen
        get("/docente/examenes/crear", (req, res) -> {
            Integer userId = req.session().attribute("userId");
            Docente docente = Docente.findFirst("user_id = ?", userId);
            if (docente == null) {
                res.redirect("/dashboard?error=No se encontró el perfil de docente.");
                return null;
            }
 
            // Solo materias donde el docente es Responsable_de_Catedra
            List<Map<String, Object>> materiasView = getMateriasResponsable(docente.getCodigoProfesor());
 
            Map<String, Object> model = new HashMap<>();
            model.put("materias", materiasView);
            if (materiasView.isEmpty()) {
                model.put("errorMessage", "No tenés materias asignadas como Responsable de Cátedra.");
            }
            String error = req.queryParams("errorMessage");
            if (error != null) model.put("errorMessage", error);
 
            return new ModelAndView(model, "docente/crearExamen.mustache");
        }, new MustacheTemplateEngine());
 
        // POST: guardar nuevo examen
        post("/docente/examenes/crear", (req, res) -> {
            Integer userId = req.session().attribute("userId");
            Docente docente = Docente.findFirst("user_id = ?", userId);
            if (docente == null) {
                res.redirect("/dashboard");
                return null;
            }
 
            String codMateriaStr = req.queryParams("cod_materia");
            String fecha         = req.queryParams("fecha");
            String horario       = req.queryParams("horario");
            String aula          = req.queryParams("aula");
            String observaciones = req.queryParams("observaciones");
 
            if (codMateriaStr == null || fecha == null || horario == null || aula == null
                    || codMateriaStr.isEmpty() || fecha.isEmpty() || horario.isEmpty() || aula.isEmpty()) {
                res.redirect("/docente/examenes/crear?errorMessage=Todos los campos obligatorios deben completarse.");
                return null;
            }
 
            Integer codMateria = Integer.parseInt(codMateriaStr);
 
            // Verificar que la materia elegida le pertenece al docente como Responsable
            PeriodoAcademico periodo = PeriodoAcademico.findFirst(
                "codigo_profesor = ? AND cod_materia = ? AND cargo = ?",
                docente.getCodigoProfesor(), codMateria, "Responsable_de_Catedra"
            );
            if (periodo == null) {
                res.redirect("/docente/examenes/crear?errorMessage=No tenés permiso para crear un examen en esa materia.");
                return null;
            }
 
            // Verificar duplicado
            if (ExamenFinal.existeDuplicado(codMateria, docente.getCodigoProfesor(), fecha, null)) {
                res.redirect("/docente/examenes/crear?errorMessage=Ya existe un examen para esa materia en esa fecha.");
                return null;
            }
 
            try {
                ExamenFinal examen = new ExamenFinal();
                examen.setCodMateria(codMateria);
                examen.setCodigoProfesor(docente.getCodigoProfesor());
                examen.setFecha(fecha);
                examen.setHorario(horario);
                examen.setAula(aula);
                examen.setObservaciones(observaciones != null ? observaciones : "");
                examen.saveIt();
                res.redirect("/docente/examenes?successMessage=Examen creado correctamente.");
            } catch (Exception e) {
                res.redirect("/docente/examenes/crear?errorMessage=Error al crear el examen: " + e.getMessage());
            }
            return null;
        });
 
        // GET: formulario editar examen
        get("/docente/examenes/:id/editar", (req, res) -> {
            Integer userId = req.session().attribute("userId");
            Docente docente = Docente.findFirst("user_id = ?", userId);
            if (docente == null) {
                res.redirect("/dashboard");
                return null;
            }
 
            Integer id = Integer.parseInt(req.params(":id"));
            ExamenFinal examen = ExamenFinal.findFirst(
                "id = ? AND codigo_profesor = ?", id, docente.getCodigoProfesor()
            );
            if (examen == null) {
                res.redirect("/docente/examenes?errorMessage=Examen no encontrado.");
                return null;
            }
 
            // Solo materias donde es Responsable, marcando la seleccionada
            List<Map<String, Object>> materiasView = getMateriasResponsable(docente.getCodigoProfesor());
            for (Map<String, Object> mv : materiasView) {
                mv.put("seleccionada", mv.get("cod_materia").equals(examen.getCodMateria()));
            }
 
            Map<String, Object> model = new HashMap<>();
            model.put("id", examen.getId());
            model.put("fecha", examen.getFecha());
            model.put("horario", examen.getHorario());
            model.put("aula", examen.getAula());
            model.put("observaciones", examen.getObservaciones());
            model.put("materias", materiasView);
            String error = req.queryParams("errorMessage");
            if (error != null) model.put("errorMessage", error);
 
            return new ModelAndView(model, "docente/editarExamen.mustache");
        }, new MustacheTemplateEngine());
 
        // POST: guardar cambios del examen
        post("/docente/examenes/:id/editar", (req, res) -> {
            Integer userId = req.session().attribute("userId");
            Docente docente = Docente.findFirst("user_id = ?", userId);
            if (docente == null) {
                res.redirect("/dashboard");
                return null;
            }
 
            Integer id = Integer.parseInt(req.params(":id"));
            ExamenFinal examen = ExamenFinal.findFirst(
                "id = ? AND codigo_profesor = ?", id, docente.getCodigoProfesor()
            );
            if (examen == null) {
                res.redirect("/docente/examenes?errorMessage=Examen no encontrado.");
                return null;
            }
 
            String codMateriaStr = req.queryParams("cod_materia");
            String fecha         = req.queryParams("fecha");
            String horario       = req.queryParams("horario");
            String aula          = req.queryParams("aula");
            String observaciones = req.queryParams("observaciones");
 
            if (codMateriaStr == null || fecha == null || horario == null || aula == null
                    || fecha.isEmpty() || horario.isEmpty() || aula.isEmpty()) {
                res.redirect("/docente/examenes/" + id + "/editar?errorMessage=Todos los campos obligatorios deben completarse.");
                return null;
            }
 
            Integer codMateria = Integer.parseInt(codMateriaStr);
 
            // Verificar que la materia pertenece al docente como Responsable
            PeriodoAcademico periodo = PeriodoAcademico.findFirst(
                "codigo_profesor = ? AND cod_materia = ? AND cargo = ?",
                docente.getCodigoProfesor(), codMateria, "Responsable_de_Catedra"
            );
            if (periodo == null) {
                res.redirect("/docente/examenes/" + id + "/editar?errorMessage=No tenés permiso para asignar esa materia.");
                return null;
            }
 
            // Verificar duplicado excluyendo el examen actual
            if (ExamenFinal.existeDuplicado(codMateria, docente.getCodigoProfesor(), fecha, id)) {
                res.redirect("/docente/examenes/" + id + "/editar?errorMessage=Ya existe un examen para esa materia en esa fecha.");
                return null;
            }
 
            try {
                examen.setCodMateria(codMateria);
                examen.setFecha(fecha);
                examen.setHorario(horario);
                examen.setAula(aula);
                examen.setObservaciones(observaciones != null ? observaciones : "");
                examen.saveIt();
                res.redirect("/docente/examenes?successMessage=Examen actualizado correctamente.");
            } catch (Exception e) {
                res.redirect("/docente/examenes/" + id + "/editar?errorMessage=Error al guardar: " + e.getMessage());
            }
            return null;
        });
 
        // GET: confirmar eliminación
        get("/docente/examenes/:id/eliminar", (req, res) -> {
            Integer userId = req.session().attribute("userId");
            Docente docente = Docente.findFirst("user_id = ?", userId);
            if (docente == null) {
                res.redirect("/dashboard");
                return null;
            }
 
            Integer id = Integer.parseInt(req.params(":id"));
            ExamenFinal examen = ExamenFinal.findFirst(
                "id = ? AND codigo_profesor = ?", id, docente.getCodigoProfesor()
            );
 
            if (examen == null) {
                res.redirect("/docente/examenes?errorMessage=Examen no encontrado.");
                return null;
            }
 
            Materia m = Materia.findFirst("cod_materia = ?", examen.getCodMateria());
            Map<String, Object> model = new HashMap<>();
            model.put("id", examen.getId());
            model.put("nombreMateria", m != null ? m.getNombre() : "Materia desconocida");
            model.put("fecha", examen.getFecha());
            model.put("horario", examen.getHorario());
            model.put("aula", examen.getAula());
 
            return new ModelAndView(model, "docente/eliminarExamen.mustache");
        }, new MustacheTemplateEngine());
 
        post("/docente/examenes/:id/eliminar", (req, res) -> {
            Integer userId = req.session().attribute("userId");
            Docente docente = Docente.findFirst("user_id = ?", userId);
            if (docente == null) {
                res.redirect("/dashboard");
                return null;
            }
 
            Integer id = Integer.parseInt(req.params(":id"));
            ExamenFinal examen = ExamenFinal.findFirst(
                "id = ? AND codigo_profesor = ?", id, docente.getCodigoProfesor()
            );
 
            if (examen == null) {
                res.redirect("/docente/examenes?errorMessage=Examen no encontrado.");
                return null;
            }
 
            try {
                examen.delete();
                res.redirect("/docente/examenes?successMessage=Examen eliminado correctamente.");
            } catch (Exception e) {
                res.redirect("/docente/examenes?errorMessage=Error al eliminar: " + e.getMessage());
            }
            return null;
        });
    }
 


} // Fin de la clase App