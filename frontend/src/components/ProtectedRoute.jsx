import { Navigate } from "react-router";
import { useAuth } from "../auth/AuthContext.js";

export default function ProtectedRoute({ children }) {
  const { user, status } = useAuth();

  if (status === "checking") {
    return <p role="status">Comprobando sesión…</p>;
  }

  if (status === "error") {
    return null;
  }

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  return children;
}