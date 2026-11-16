# Validación de la gestión del catálogo

Issue #53 (US05 #5). Comprueba que la gestión del catálogo hecha en la
Issue #51 (backend) y en la Issue #52 (interfaz) funciona como se espera.

## Resultado de los tests

| Parte | Comando | Resultado |
|---|---|---|
| Backend | `.\mvnw.cmd test` | 77 tests, 0 fallos |
| Frontend | `node --test tests/*.test.js` | 69 tests, 0 fallos |

Los tests de la gestión del catálogo están en:

- `backend/src/test/java/com/biblioteca/backend/catalogo/LibroGestionIntegrationTests.java` (19 tests)
- `frontend/tests/catalogAdmin.test.js` (19 tests)

## Criterios comprobados

| Criterio | Cómo se comprueba | Resultado |
|---|---|---|
| Un bibliotecario puede crear un libro | Test `unBibliotecarioCreaUnLibroConTodosSusDatos` y prueba manual | ✅ |
| Un administrador puede crear, editar y retirar | Tests `unAdministradorTambienPuedeCrear` y `unAdministradorPuedeEditarYRetirar` | ✅ |
| Un estudiante no puede modificar el catálogo (403) | Test `unEstudiantePuedeVerLaFichaPeroNoModificarElCatalogo` y prueba manual | ✅ |
| Sin sesión no se puede usar la API (401) | Test `sinSesionDevuelve401` | ✅ |
| Sin token CSRF no se puede modificar (403) | Test `sinTokenCsrfDevuelve403` | ✅ |
| Los datos no válidos devuelven 400 con un mensaje por campo | Test `losDatosNoValidosDevuelven400ConUnMensajePorCampo` y prueba manual | ✅ |
| Los campos opcionales vacíos se guardan como null | Test `losCamposOpcionalesVaciosSeGuardanComoNull` | ✅ |
| No se puede repetir un ISBN, aunque se escriba con otro formato (409) | Tests `unIsbnDuplicadoConOtroFormatoDevuelve409` y `editarConElIsbnDeOtroLibroDevuelve409` | ✅ |
| Editar un libro manteniendo su propio ISBN no da error | Test `editarConservandoSuPropioIsbnNoEsDuplicado` | ✅ |
| Editar cambia los datos pero conserva el origen | Test `editarActualizaLosDatosYConservaElOrigen` | ✅ |
| Editar un libro de Open Library conserva su origen y sus claves | Test `editarUnLibroDeOpenLibraryConservaSuOrigenYSusClaves` y prueba manual (libro id 5) | ✅ |
| Un libro que no existe devuelve 404 y un id no numérico 400 | Tests `unLibroInexistenteDevuelve404` y `unIdNoNumericoDevuelve400` | ✅ |
| Retirar es lógico: el libro y sus ejemplares siguen en la base de datos | Test `retirarEsLogicoYConservaElLibroYSusEjemplares` y prueba manual (libro id 100005) | ✅ |
| Retirar un libro de Open Library también es lógico | Test `retirarUnLibroDeOpenLibraryEsLogicoYConservaSusDatos` | ✅ |
| Un libro retirado no aparece en el catálogo ni en las categorías | Test `unLibroRetiradoDesapareceDelCatalogoYDeLasCategorias` y prueba manual | ✅ |
| Retirar dos veces no cambia la fecha de retirada | Test `retirarDosVecesNoCambiaLaFechaDeRetirada` | ✅ |
| La interfaz usa los mismos mensajes de validación que el backend | Tests de `catalogAdmin.test.js` (`valida con los mismos mensajes que el backend`, etc.) | ✅ |
| Solo bibliotecarios y administradores ven las opciones de gestión | Test `solo bibliotecarios y administradores pueden gestionar el catálogo` y prueba manual | ✅ |

## Pruebas manuales

Hechas con la aplicación completa en local (PostgreSQL en Docker, backend en
el puerto 8080 y frontend en http://localhost:5173).

| Prueba | Resultado |
|---|---|
| Un bibliotecario ve "Añadir libro", "Editar" y "Retirar" | ✅ |
| Un estudiante no ve esas opciones y en `/admin/libros/nuevo` ve "No tienes permiso" | ✅ |
| Crear un libro nuevo desde el formulario | ✅ |
| El formulario muestra los errores de cada campo | ✅ |
| Un ISBN repetido muestra el error en el campo ISBN | ✅ |
| Editar un libro y ver los cambios en el catálogo | ✅ |
| Retirar pide confirmación antes de hacerlo | ✅ |
| El libro retirado desaparece del catálogo | ✅ |
| En la base de datos el libro retirado sigue existiendo con `retirado = true` (id 100005) | ✅ |
| Editar un libro real de Open Library (id 5): cambia la editorial, pero `origen`, `openlibrary_key` y `openlibrary_work_key` no cambian | ✅ |

Después de la prueba del libro id 5 se volvió a dejar su editorial original.

## Historial de préstamos

La tabla de préstamos todavía no existe (llega en el Sprint 3). Retirar un
libro no ejecuta ningún `DELETE`: solo marca `retirado = true` y guarda la
fecha de retirada. Por eso, cuando existan los préstamos, el historial de un
libro retirado se conservará.
