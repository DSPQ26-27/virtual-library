# Cobertura del backend con JaCoCo

Desde `backend`, ejecutar la suite habitual y generar el informe:

```powershell
.\mvnw.cmd test
Invoke-Item .\target\site\jacoco\index.html
```

También se puede utilizar `mvn test` con Maven instalado. El informe XML está en
`target/site/jacoco/jacoco.xml`, el CSV en `target/site/jacoco/jacoco.csv` y los
datos de ejecución en `target/jacoco.exec`. El informe incluye todas las clases
de producción, sin exclusiones configuradas. La cobertura de esta orden procede
de las pruebas unitarias y de integración habituales con H2; las clases `*IT`
de PostgreSQL no se incluyen por defecto.

En la vista de código del HTML, el verde indica líneas ejecutadas y el rojo,
líneas sin ejecutar. El amarillo indica una línea ejecutada con cobertura de
ramas parcial. Una línea verde no demuestra por sí sola que se hayan comprobado
todos los resultados o combinaciones. JaCoCo mide ramas de bytecode, no todas
las combinaciones lógicas posibles ni el comportamiento de PostgreSQL.

Cada ejecución de pruebas reemplaza `target/jacoco.exec` para evitar que una
ejecución anterior infle las cifras. El informe de `mvn test` se genera después
de Surefire; no se ha configurado ningún umbral que pueda fallar la compilación.
Si se omiten las pruebas, un informe previo puede quedar en `target`: no lo
interprete como resultado de esa ejecución.

## PostgreSQL y combinación futura

La ejecución PostgreSQL está documentada en `PRUEBAS-POSTGRESQL.md`. Como la
configuración actual usa el mismo archivo de ejecución, **ejecutarla sobrescribe
la cobertura H2**. Para combinar ambas suites en una fase posterior, configurar
archivos de datos separados para H2 y PostgreSQL, ejecutar ambas contra el mismo
build sin recompilar las clases entre ejecuciones y usar el objetivo oficial
`jacoco:merge` antes de generar un informe sobre el archivo combinado. Validar
el número de tests de cada suite y mantener también los informes separados. No
se ha activado esta combinación ni se ha ejecutado PostgreSQL para este informe.

## CI futura

En una fase posterior, GitHub Actions puede ejecutar la suite H2 y publicar como
artefactos el HTML y XML; otra tarea, con Docker, puede ejecutar los `*IT`
PostgreSQL y publicar sus datos separados. La combinación debe ser explícita,
mantener los mismos binarios y no establecer umbrales antes de revisar una
línea base y acordar objetivos para las reglas críticas.
