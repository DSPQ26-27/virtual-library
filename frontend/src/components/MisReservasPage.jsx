import { useEffect, useRef, useState } from "react";
import { Link } from "react-router";
import { useAuth } from "../auth/AuthContext.js";
import { cancelWaitlist, getMyWaitlist } from "../services/waitlist.js";
import { formatDueDate } from "../services/loans.js";
import "./CatalogPage.css";

export default function MisReservasPage() {
  const { expireSession } = useAuth();
  const [revision, setRevision] = useState(0);
  const [state, setState] = useState({ phase: "loading", items: [], message: "" });
  const [busy, setBusy] = useState(null);
  const [notice, setNotice] = useState("");
  const sending = useRef(false);

  useEffect(() => {
    const controller = new AbortController();
    getMyWaitlist({ signal: controller.signal }).then((items) => {
      if (!controller.signal.aborted) setState({ phase: "ready", items, message: "" });
    }).catch((error) => {
      if (controller.signal.aborted) return;
      if (error.status === 401) expireSession();
      setState({ phase: "error", items: [], message: error.message });
    });
    return () => controller.abort();
  }, [revision, expireSession]);

  function actualizar() {
    setState({ phase: "loading", items: [], message: "" });
    setRevision((value) => value + 1);
  }

  async function cancelar(id) {
    if (sending.current) return;
    sending.current = true;
    setBusy(id);
    setNotice("");
    try {
      await cancelWaitlist(id);
      setNotice("Solicitud cancelada.");
    } catch (error) {
      if (error.status === 401) expireSession();
      setNotice(error.message);
    } finally {
      sending.current = false;
      setBusy(null);
      actualizar();
    }
  }

  return (
    <section className="catalogo" aria-labelledby="reservas-titulo">
      <header className="catalogo__cabecera">
        <h2 id="reservas-titulo">Mis reservas</h2>
        <button type="button" className="catalogo__actualizar"
          disabled={state.phase === "loading" || busy !== null} onClick={actualizar}>Actualizar</button>
      </header>
      <p role="status">{notice}</p>
      {state.phase === "loading" && <p role="status">Cargando reservas…</p>}
      {state.phase === "error" && <p role="alert">{state.message}</p>}
      {state.phase === "ready" && state.items.length === 0 && <p>No tienes solicitudes de reserva.</p>}
      <ul className="catalogo__lista">
        {state.items.map((item) => (
          <li key={item.id}>
            <article className="catalogo__libro">
              <h3>{item.titulo}</h3>
              <p>Solicitada el {formatDueDate(item.fechaSolicitud)}</p>
              <p>Estado: {item.estado}</p>
              {item.estado === "PENDIENTE" && (
                <button type="button" className="catalogo__actualizar" disabled={busy !== null}
                  onClick={() => cancelar(item.id)} aria-label={`Cancelar reserva de «${item.titulo}»`}>
                  {busy === item.id ? "Cancelando…" : "Cancelar reserva"}
                </button>
              )}
              {item.estado === "ASIGNADA" && <Link to="/mis-prestamos">Consultar préstamo asignado</Link>}
            </article>
          </li>
        ))}
      </ul>
    </section>
  );
}
