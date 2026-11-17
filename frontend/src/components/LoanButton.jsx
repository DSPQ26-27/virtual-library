import { useRef, useState } from "react";
import { requestLoan } from "../services/loans.js";

/**
 * Pedir el préstamo de un libro, con confirmación (#59).
 * El resultado (éxito o error) lo muestra el catálogo con onLoaned y onError.
 */
export default function LoanButton({ book, onLoaned, onError }) {
  const [phase, setPhase] = useState("idle"); // idle | confirming | sending
  const sending = useRef(false);

  async function pedir() {
    if (sending.current) return; // evita una segunda petición mientras se procesa la primera
    sending.current = true;
    setPhase("sending");
    try {
      const prestamo = await requestLoan(book.id);
      onLoaned(prestamo, book);
    } catch (error) {
      onError(error, book);
    } finally {
      sending.current = false;
      setPhase("idle");
    }
  }

  if (!book.disponible) return null;

  if (phase === "idle") {
    return (
      <div className="catalogo__gestion">
        <button type="button" className="catalogo__actualizar"
          aria-label={`Pedir préstamo de «${book.titulo}»`}
          onClick={() => setPhase("confirming")}>
          Pedir préstamo
        </button>
      </div>
    );
  }

  return (
    <div className="catalogo__gestion">
      <p className="catalogo__confirmacion" role="alert">
        ¿Pedir «{book.titulo}»? Tendrás 30 días para devolverlo.
      </p>
      <button type="button" className="catalogo__actualizar"
        disabled={phase === "sending"} onClick={pedir}>
        {phase === "sending" ? "Solicitando…" : "Sí, pedir"}
      </button>
      <button type="button" className="catalogo__actualizar"
        disabled={phase === "sending"} onClick={() => setPhase("idle")}>
        Cancelar
      </button>
    </div>
  );
}
