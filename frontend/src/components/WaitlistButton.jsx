import { useRef, useState } from "react";
import { joinWaitlist } from "../services/waitlist.js";
import { useAuth } from "../auth/AuthContext.js";

export default function WaitlistButton({ book }) {
  const { expireSession } = useAuth();
  const [state, setState] = useState({ phase: "idle", message: "" });
  const sending = useRef(false);

  async function apuntarse() {
    if (sending.current) return;
    sending.current = true;
    setState({ phase: "sending", message: "" });
    try {
      await joinWaitlist(book.id);
      setState({ phase: "done", message: "Te has apuntado. Puedes consultar la solicitud en Mis reservas." });
    } catch (error) {
      if (error.status === 401) expireSession();
      setState({ phase: "error", message: error.message });
    } finally {
      sending.current = false;
    }
  }

  if (book.disponible) return null;
  return (
    <div className="catalogo__gestion">
      <button type="button" className="catalogo__actualizar" onClick={apuntarse}
        disabled={state.phase === "sending" || state.phase === "done"}
        aria-label={`Apuntarme a la lista de espera de «${book.titulo}»`}>
        {state.phase === "sending" ? "Enviando…" : "Apuntarme a la lista de espera"}
      </button>
      <p role={state.phase === "error" ? "alert" : "status"}>{state.message}</p>
    </div>
  );
}
