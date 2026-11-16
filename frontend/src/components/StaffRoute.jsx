import { Link } from "react-router";
import { useAuth } from "../auth/AuthContext.js";
import { canManageCatalog } from "../auth/roles.js";
import ProtectedRoute from "./ProtectedRoute.jsx";

/** Como ProtectedRoute, pero además exige ser bibliotecario o administrador (#52). */
export default function StaffRoute({ children }) {
  const { user } = useAuth();

  return (
    <ProtectedRoute>
      {canManageCatalog(user) ? children : (
        <section role="alert">
          <h2>No tienes permiso</h2>
          <p>Solo bibliotecarios y administradores pueden gestionar el catálogo.</p>
          <Link to="/catalogo">Volver al catálogo</Link>
        </section>
      )}
    </ProtectedRoute>
  );
}
