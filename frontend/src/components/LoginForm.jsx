import { useState } from "react";
import { Link, Navigate } from "react-router";
import { useAuth } from "../auth/AuthContext.js";
import "./RegisterForm.css";

export default function LoginForm() {
  const { user, status, busy, login } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");

  async function handleSubmit(event) {
    event.preventDefault();

    if (busy) return;

    setError("");

    try {
      await login({ email, password });
      setPassword("");
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : "No se ha podido iniciar sesión."
      );
    }
  }

  if (status === "checking") {
    return <p role="status">Comprobando sesión…</p>;
  }

  if (status === "ready" && user) {
    return <Navigate to="/catalogo" replace />;
  }

  return (
    <section className="registro" aria-labelledby="login-titulo">
      <h2 id="login-titulo">Iniciar sesión</h2>

      <p className="registro__intro">
        Accede con tu correo y contraseña.
      </p>

      <form onSubmit={handleSubmit}>
        <fieldset className="registro__campos" disabled={busy}>
          <legend className="registro__legend">
            Datos de acceso
          </legend>

          <div className="registro__campo">
            <label htmlFor="login-email">Correo electrónico</label>
            <input
              id="login-email"
              name="email"
              type="email"
              autoComplete="username"
              value={email}
              onChange={(event) => {
                setEmail(event.target.value);
                setError("");
              }}
              required
            />
          </div>

          <div className="registro__campo">
            <label htmlFor="login-password">Contraseña</label>
            <input
              id="login-password"
              name="password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(event) => {
                setPassword(event.target.value);
                setError("");
              }}
              required
            />
          </div>

          <button className="registro__boton" type="submit">
            {busy ? "Accediendo…" : "Iniciar sesión"}
          </button>
        </fieldset>

        <div role="alert" aria-atomic="true">
          {error && (
            <p className="registro__mensaje registro__mensaje--error">
              {error}
            </p>
          )}
        </div>
      </form>

      <p>
        ¿Todavía no tienes cuenta?{" "}
        <Link to="/registro">Regístrate</Link>
      </p>
    </section>
  );
}