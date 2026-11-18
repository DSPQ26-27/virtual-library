# Panel del estudiante — #71

Mi biblioteca abre /mi-biblioteca y requiere una sesión de estudiante mediante
StudentRoute. MiBibliotecaPage reúne los componentes existentes MisPrestamosPage
y MisReservasPage.

El panel permite consultar préstamos activos, historial y reservas. Reutiliza
las acciones de renovar, devolver y cancelar reservas pendientes, junto con sus
confirmaciones y mensajes de carga, error y lista vacía.

Cada sección tiene su botón Actualizar y consulta su API. Al realizar una acción
se actualiza esa sección. Para consultar cambios en la otra, pulsar su botón
Actualizar. Las APIs y los estilos son los existentes.

El código nuevo se limita al componente de composición y al enlace y ruta de App.
Para verificar el frontend, ejecutar node --test tests/*.test.js, el lint y la
compilación de Vite desde frontend.
