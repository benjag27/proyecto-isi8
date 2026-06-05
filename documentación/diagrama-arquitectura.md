
graph TD
    A[Browser] -->|HTTP Request| B[Spark Java - Router]
    B -->|Delega| C[Servicio - Lógica de negocio]
    C -->|Consulta| D[ActiveJDBC]
    D -->|JDBC| E[(SQLite - Base de Datos)]
    E -->|Resultados| D
    D -->|Objetos Java| C
    C -->|Datos procesados| B
    B -->|Datos + nombre del template| F[Mustache - Renderiza HTML]
    F -->|HTTP Response HTML| A
