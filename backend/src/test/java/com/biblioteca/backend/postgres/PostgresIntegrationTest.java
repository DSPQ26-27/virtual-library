package com.biblioteca.backend.postgres;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/no_conectar",
    "spring.datasource.username=no_conectar",
    "spring.datasource.password=no_conectar",
    "spring.datasource.driver-class-name=org.postgresql.Driver",
    "spring.flyway.url=jdbc:postgresql://127.0.0.1:1/no_conectar",
    "spring.flyway.user=no_conectar",
    "spring.flyway.password=no_conectar",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@Import(PostgresTestConfiguration.class)
public @interface PostgresIntegrationTest {}
