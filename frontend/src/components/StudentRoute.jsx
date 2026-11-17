import { Link } from "react-router";
import { useAuth } from "../auth/AuthContext.js";
import { isStudent } from "../auth/roles.js";
import ProtectedRoute from "./ProtectedRoute.jsx";

export default function StudentRoute({ children }) {
  const { user } = useAuth();
  return <ProtectedRoute>{isStudent(user) ? children : (
    <section role="alert"><h2>No tienes permiso</h2>
      <p>Solo los estudiantes pueden consultar sus préstamos.</p>
      <Link to="/catalogo">Volver al catálogo</Link>
    </section>
  )}</ProtectedRoute>;
}
