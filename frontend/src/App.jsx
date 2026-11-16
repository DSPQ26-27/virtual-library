import { useState } from "react";
import { Link, Navigate, Route, Routes } from "react-router";
import { useAuth } from "./auth/AuthContext.js";
import LoginForm from "./components/LoginForm.jsx";
import CatalogPage from "./components/CatalogPage.jsx";
import EjemplaresPage from "./components/EjemplaresPage.jsx";
import ProtectedRoute from "./components/ProtectedRoute.jsx";
import RegisterForm from "./components/RegisterForm.jsx";
import BookForm from "./components/BookForm.jsx";
import StaffRoute from "./components/StaffRoute.jsx";
import { canManageCatalog } from "./auth/roles.js";
import "./App.css";

function App() {
  const {
    user,
    status,
    sessionError,
    busy,
    logout,
    retrySession,
  } = useAuth();

  const [logoutError, setLogoutError] = useState("");

  async function handleLogout() {
    setLogoutError("");

    try {
      await logout();
    } catch (error) {
      setLogoutError(error.message);
    }
  }

  const authenticated = status === "ready" && user !== null;

  return (
    <main>
      <h1>Biblioteca Virtual</h1>
      <p>Aplicación de gestión de biblioteca universitaria.</p>

      <nav className="app-nav" aria-label="Navegación principal">
        {authenticated ? (
          <>
            <Link to="/catalogo">Catálogo</Link>
            {canManageCatalog(user) && <Link to="/admin/libros/nuevo">Añadir libro</Link>}
            <span>{user.nombre}</span>
            <button
              type="button"
              disabled={busy}
              onClick={handleLogout}
            >
              {busy ? "Cerrando sesión…" : "Cerrar sesión"}
            </button>
          </>
        ) : (
          <>
            <Link to="/login">Iniciar sesión</Link>
            <Link to="/registro">Crear cuenta</Link>
          </>
        )}
      </nav>

      {status === "error" && (
        <section className="app-notice" aria-label="Estado de conexión">
          <p role="alert">{sessionError}</p>
          <button
            type="button"
            disabled={busy}
            onClick={retrySession}
          >
            Reintentar comprobación
          </button>
        </section>
      )}

      {logoutError && (
        <p className="app-error" role="alert">
          {logoutError}
        </p>
      )}

      <Routes>
        <Route
          path="/"
          element={<Navigate to="/login" replace />}
        />

        <Route path="/login" element={<LoginForm />} />

        <Route
          path="/registro"
          element={
            authenticated
              ? <Navigate to="/catalogo" replace />
              : <RegisterForm />
          }
        />

        <Route
          path="/catalogo"
          element={
            <ProtectedRoute>
              <CatalogPage />
            </ProtectedRoute>
          }
        />

        <Route
          path="/libros/:id/ejemplares"
          element={
            <ProtectedRoute>
              <EjemplaresPage />
            </ProtectedRoute>
          }
        />

        <Route
          path="/admin/libros/nuevo"
          element={
            <StaffRoute>
              <BookForm />
            </StaffRoute>
          }
        />

        <Route
          path="/admin/libros/:id/editar"
          element={
            <StaffRoute>
              <BookForm />
            </StaffRoute>
          }
        />

        <Route
          path="*"
          element={
            <section>
              <h2>Página no encontrada</h2>
              <Link to="/">Volver al inicio</Link>
            </section>
          }
        />
      </Routes>
    </main>
  );
}

export default App;
