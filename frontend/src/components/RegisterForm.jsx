import { useState } from "react";
import { registerStudent } from "../services/auth.js";
import "./RegisterForm.css";

export default function RegisterForm() {
  const [nombre, setNombre] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState("");
  const [exito, setExito] = useState("");

  function limpiarMensajes() {
    setError("");
    setExito("");
  }

  async function handleSubmit(event) {
    event.preventDefault();

    if (enviando) return;

    limpiarMensajes();

    const nombreLimpio = nombre.trim();

    if (nombreLimpio.length < 2 || nombreLimpio.length > 100) {
      setError("El nombre debe tener entre 2 y 100 caracteres.");
      return;
    }

    if (!password.trim() || password.length < 8) {
      setError(
        "La contraseña debe tener al menos 8 caracteres y no puede ser solo espacios."
      );
      return;
    }

    setEnviando(true);

    try {
      await registerStudent({
        nombre: nombreLimpio,
        email,
        password,
      });

      setNombre("");
      setEmail("");
      setPassword("");
      setExito("Tu cuenta se ha creado correctamente.");
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : "No se ha podido completar el registro."
      );
    } finally {
      setEnviando(false);
    }
  }

  return (
    <section className="registro" aria-labelledby="registro-titulo">
      <h2 id="registro-titulo">Registro de estudiantes</h2>

      <p className="registro__intro">
        Crea tu cuenta para acceder a la biblioteca.
      </p>

      <form onSubmit={handleSubmit}>
        <fieldset className="registro__campos" disabled={enviando}>
          <legend className="registro__legend">
            Datos de la cuenta
          </legend>

          <div className="registro__campo">
            <label htmlFor="registro-nombre">Nombre</label>
            <input
              id="registro-nombre"
              name="nombre"
              type="text"
              autoComplete="name"
              minLength={2}
              maxLength={100}
              value={nombre}
              onChange={(event) => {
                setNombre(event.target.value);
                limpiarMensajes();
              }}
              required
            />
          </div>

          <div className="registro__campo">
            <label htmlFor="registro-email">Correo electrónico</label>
            <input
              id="registro-email"
              name="email"
              type="email"
              autoComplete="email"
              maxLength={254}
              value={email}
              onChange={(event) => {
                setEmail(event.target.value);
                limpiarMensajes();
              }}
              required
            />
          </div>

          <div className="registro__campo">
            <label htmlFor="registro-password">Contraseña</label>
            <input
              id="registro-password"
              name="password"
              type="password"
              autoComplete="new-password"
              minLength={8}
              aria-describedby="registro-password-ayuda"
              value={password}
              onChange={(event) => {
                setPassword(event.target.value);
                limpiarMensajes();
              }}
              required
            />
            <small id="registro-password-ayuda">
              Utiliza al menos 8 caracteres.
            </small>
          </div>

          <button className="registro__boton" type="submit">
            {enviando ? "Creando cuenta…" : "Crear cuenta"}
          </button>
        </fieldset>

        <div aria-live="polite" aria-atomic="true">
          {exito && (
            <p className="registro__mensaje registro__mensaje--exito">
              {exito}
            </p>
          )}
        </div>

        <div role="alert" aria-atomic="true">
          {error && (
            <p className="registro__mensaje registro__mensaje--error">
              {error}
            </p>
          )}
        </div>
      </form>
    </section>
  );
}