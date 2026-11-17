import { useEffect, useRef, useState } from "react";
import { useAuth } from "../auth/AuthContext.js";
import { formatDueDate, getMyLoans, returnLoan } from "../services/loans.js";
import "./MisPrestamosPage.css";

function ListaPrestamos({ prestamos, historial, confirmando, setConfirmando, devolver, enviando }) {
  if (!prestamos.length) return <p>{historial ? "Todavía no tienes devoluciones." : "No tienes préstamos activos."}</p>;
  return <ul className="mis-prestamos__lista">{prestamos.map((prestamo) => (
    <li key={prestamo.id}><article className="mis-prestamos__tarjeta">
      <h4>{prestamo.titulo}</h4>
      <p><strong>Fecha de préstamo:</strong> {formatDueDate(prestamo.fechaPrestamo)}</p>
      {historial ? <p><strong>Fecha de devolución:</strong> {formatDueDate(prestamo.fechaDevolucion)}</p> :
        <p><strong>Fecha de vencimiento:</strong> {formatDueDate(prestamo.fechaVencimiento)}</p>}
      <p><strong>Estado:</strong> {prestamo.estado}</p>
      {!historial && (confirmando === prestamo.id ? <div className="mis-prestamos__acciones">
        <p>¿Devolver «{prestamo.titulo}»?</p>
        <button type="button" disabled={enviando} onClick={() => devolver(prestamo)}>{enviando ? "Devolviendo…" : "Sí, devolver"}</button>
        <button type="button" disabled={enviando} onClick={() => setConfirmando(null)}>Cancelar</button>
      </div> : <button type="button" disabled={enviando} onClick={() => setConfirmando(prestamo.id)}
        aria-label={`Devolver «${prestamo.titulo}»`}>Devolver</button>)}
    </article></li>
  ))}</ul>;
}

export default function MisPrestamosPage() {
  const { expireSession } = useAuth();
  const [revision, setRevision] = useState(0);
  const [state, setState] = useState({ phase: "loading", data: null, message: "" });
  const [confirmando, setConfirmando] = useState(null);
  const [enviando, setEnviando] = useState(false);
  const [aviso, setAviso] = useState({ kind: "", message: "" });
  const ocupado = useRef(false);
  const mounted = useRef(false);

  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    async function cargar() {
      setState({ phase: "loading", data: null, message: "" });
      try {
        const data = await getMyLoans({ signal: controller.signal });
        if (active) setState({ phase: "ready", data, message: "" });
      } catch (error) {
        if (!active || controller.signal.aborted) return;
        if (error.status === 401) { expireSession(); return; }
        setState({ phase: "error", data: null, message: error.message || "No se han podido cargar tus préstamos." });
      }
    }
    cargar();
    return () => { active = false; controller.abort(); };
  }, [revision, expireSession]);

  async function devolver(prestamo) {
    if (ocupado.current) return;
    ocupado.current = true;
    setEnviando(true);
    setAviso({ kind: "", message: "" });
    try {
      const resultado = await returnLoan(prestamo.id);
      if (!mounted.current) return;
      setAviso({ kind: "success", message: `Devolución de «${prestamo.titulo}» registrada el ${formatDueDate(resultado.fechaDevolucion)}.` });
    } catch (error) {
      if (!mounted.current) return;
      if (error.status === 401) { expireSession(); return; }
      setAviso({ kind: "error", message: error.message || "No se ha podido confirmar la devolución." });
    } finally {
      if (mounted.current) {
        setConfirmando(null);
        setState({ phase: "loading", data: null, message: "" });
        setRevision((n) => n + 1);
        setEnviando(false);
      }
      ocupado.current = false;
    }
  }

  return <section className="mis-prestamos" aria-labelledby="mis-prestamos-titulo">
    <header className="mis-prestamos__cabecera"><h2 id="mis-prestamos-titulo">Mis préstamos</h2>
      <button type="button" onClick={() => setRevision((n) => n + 1)} disabled={state.phase === "loading" || enviando}>
        {state.phase === "error" ? "Reintentar" : "Actualizar"}
      </button>
    </header>
    {state.phase === "loading" && <p role="status">Cargando préstamos…</p>}
    {state.phase === "error" && <p role="alert" className="mis-prestamos__error">{state.message}</p>}
    {aviso.message && <p role={aviso.kind === "error" ? "alert" : "status"} className={aviso.kind === "error" ? "mis-prestamos__error" : ""}>{aviso.message}</p>}
    {state.phase === "ready" && <>
      <section aria-labelledby="prestamos-activos"><h3 id="prestamos-activos">Préstamos activos</h3>
        <ListaPrestamos prestamos={state.data.activos} historial={false} confirmando={confirmando}
          setConfirmando={setConfirmando} devolver={devolver} enviando={enviando} /></section>
      <section aria-labelledby="prestamos-historial"><h3 id="prestamos-historial">Historial</h3>
        <ListaPrestamos prestamos={state.data.historial} historial /></section>
    </>}
  </section>;
}
