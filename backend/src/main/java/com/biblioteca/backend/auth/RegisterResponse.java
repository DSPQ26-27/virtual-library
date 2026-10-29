package com.biblioteca.backend.auth;

/** DTO de salida del registro: nunca incluye la contraseña ni su hash. */
public record RegisterResponse(long id, String nombre, String email) {}
