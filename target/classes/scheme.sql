-- Elimina la tabla 'users' si ya existe para asegurar un inicio limpio
-- DROP TABLE IF EXISTS users;

-- Crea la tabla 'users' con los campos originales, adaptados para SQLite
CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE,
    password TEXT NOT NULL,
    rol TEXT NOT NULL DEFAULT 'UNASSIGNED'
        CHECK (rol IN ('ALUMNO', 'DOCENTE', 'ADMINISTRADOR', 'UNASSIGNED'))
);

-- Administrador (contraseña '123') 
INSERT OR IGNORE INTO users (name, password, rol) VALUES (
    'admin',
    '$2a$12$AoKGRGy5pfvc9LVM2rhN6uJabTr/R9SV8rF9CsuePFuoskRa.9k9K',
    'ADMINISTRADOR'
);

CREATE TABLE IF NOT EXISTS Persona (
    dni INTEGER PRIMARY KEY,
    nombre TEXT NOT NULL,
    apellido TEXT NOT NULL,
    fecha_nacimiento TEXT,
    telefono TEXT,
    direccion TEXT
);

CREATE TABLE IF NOT EXISTS Docente (
    dni INTEGER,
    codigo_profesor INTEGER PRIMARY KEY AUTOINCREMENT,
    email TEXT,
    user_id INTEGER UNIQUE,

    FOREIGN KEY (dni) REFERENCES Persona(dni),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS Estudiante (
    dni INTEGER PRIMARY KEY,
    nro_legajo INTEGER NOT NULL UNIQUE,
    email TEXT NOT NULL,
    user_id INTEGER UNIQUE,

    FOREIGN KEY (dni) REFERENCES Persona(dni),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS PlanDeEstudios (
    cod_plan INTEGER PRIMARY KEY AUTOINCREMENT,
    año INTEGER NOT NULL,
    vigencia INTEGER NOT NULL,
    años_total INTEGER NOT NULL,
    cantidad_materias_total INTEGER NOT NULL,
    cod_carrera INTEGER NOT NULL,
    FOREIGN KEY (cod_carrera) REFERENCES Carrera(cod_carrera)
);

CREATE TABLE IF NOT EXISTS Materia (
    cod_materia INTEGER PRIMARY KEY,
    nombre TEXT,
    descripcion TEXT,
    cod_plan INTEGER NOT NULL,

    FOREIGN KEY (cod_plan)
        REFERENCES PlanDeEstudios(cod_plan)
);

CREATE TABLE IF NOT EXISTS Carrera (
    cod_carrera INTEGER PRIMARY KEY AUTOINCREMENT,
    nombre TEXT,
    descripcion TEXT
);

CREATE TABLE IF NOT EXISTS PeriodoAcademico (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    codigo_profesor INTEGER NOT NULL,
    cod_materia INTEGER NOT NULL,
    fecha TEXT NOT NULL,
    cargo TEXT NOT NULL CHECK (cargo IN ('Responsable_de_Catedra', 'Jefe_de_Trabajos_Practicos', 'Ayudante')),

    FOREIGN KEY (codigo_profesor) REFERENCES Docente(codigo_profesor),
    FOREIGN KEY (cod_materia) REFERENCES Materia(cod_materia)
);


CREATE TABLE IF NOT EXISTS Admin (
    dni     INTEGER PRIMARY KEY,
    user_id INTEGER UNIQUE NOT NULL,

    FOREIGN KEY (dni)     REFERENCES Persona(dni),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS ExamenFinal (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    cod_materia      INTEGER NOT NULL,
    codigo_profesor  INTEGER NOT NULL,
    fecha            TEXT NOT NULL,
    horario          TEXT NOT NULL,
    aula             TEXT NOT NULL,
    observaciones    TEXT,
 
    FOREIGN KEY (cod_materia)     REFERENCES Materia(cod_materia),
    FOREIGN KEY (codigo_profesor) REFERENCES Docente(codigo_profesor),
    UNIQUE (cod_materia, codigo_profesor, fecha)
);
-- Usuario docente
INSERT OR IGNORE INTO users (name, password, rol) VALUES (
    'docente1',
    '$2a$12$AoKGRGy5pfvc9LVM2rhN6uJabTr/R9SV8rF9CsuePFuoskRa.9k9K',

    'DOCENTE'
);

-- Usuario alumno
INSERT OR IGNORE INTO users (name, password, rol) VALUES (
    'alumno1',
    '$2a$12$AoKGRGy5pfvc9LVM2rhN6uJabTr/R9SV8rF9CsuePFuoskRa.9k9K',
    'ALUMNO'
);

-- Persona del docente
INSERT OR IGNORE INTO Persona (dni, nombre, apellido, fecha_nacimiento, telefono, direccion) VALUES (
    11111111, 'Carlos', 'García', '1980-05-10', '351-1111111', 'Calle Falsa 123'
);

-- Persona del alumno
INSERT OR IGNORE INTO Persona (dni, nombre, apellido, fecha_nacimiento, telefono, direccion) VALUES (
    22222222, 'Ana', 'López', '2000-03-15', '351-2222222', 'Av. Siempre Viva 456'
);

-- Docente (user_id = 2 porque admin es 1, docente1 es 2)
INSERT OR IGNORE INTO Docente (dni, email, user_id) VALUES (
    11111111, 'carlos@universidad.edu.ar', 2
);

-- Estudiante (user_id = 3)
INSERT OR IGNORE INTO Estudiante (dni, nro_legajo, email, user_id) VALUES (
    22222222, 1001, 'ana@universidad.edu.ar', 3
);