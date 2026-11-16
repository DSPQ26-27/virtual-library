import { useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router";
import { useAuth } from "../auth/AuthContext.js";
import { canManageCatalog } from "../auth/roles.js";
import { getBook } from "../services/catalogAdmin.js";
import {
  createEjemplar, getEjemplares, retireEjemplar, updateEjemplar, validateEjemplar,
} from "../services/ejemplares.js";
import "./CatalogPage.css";
import "./RegisterForm.css";
import "./EjemplaresPage.css";

const EMPTY_EJEMPLAR = { codigo: "", estado: "DISPONIBLE" };

export default function EjemplaresPage() {
  const { id } = useParams();
  return <EjemplaresContent key={id} libroId={id} />;
}

function EjemplaresContent({ libroId }) {
  const { user, expireSession } = useAuth();
  const staff = canManageCatalog(user);
  const [revision, setRevision] = useState(0);
  const [state, setState] = useState({ revision: null, phase: "loading", book: null, data: null, message: "" });
  const [values, setValues] = useState(EMPTY_EJEMPLAR);
  const [editing, setEditing] = useState(null);
  const [fieldErrors, setFieldErrors] = useState({});
  const [confirming, setConfirming] = useState(null);
  const [busy, setBusy] = useState("");
  const [message, setMessage] = useState({ type: "", text: "" });
  const mounted = useRef(true);
  const working = useRef(false);
  const codigoRef = useRef(null);
  const loading = state.revision !== revision || state.phase === "loading";
  const ready = !loading && state.phase === "ready";

  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    let active = true;

    Promise.all([getBook(libroId), getEjemplares(libroId, { signal: controller.signal })])
      .then(([book, data]) => {
        if (active) setState({ revision, phase: "ready", book, data, message: "" });
      })
      .catch((error) => {
        if (!active) return;
        if (error.status === 401) {
          expireSession();
          return;
        }
        setState({ revision, phase: "error", book: null, data: null,
          message: error.message || "No se han podido cargar los ejemplares." });
      });

    return () => {
      active = false;
      controller.abort();
    };
  }, [libroId, revision, expireSession]);

  function resetForm() {
    setEditing(null);
    setValues(EMPTY_EJEMPLAR);
    setFieldErrors({});
  }

  function change(name, value) {
    setValues((current) => ({ ...current, [name]: value }));
    setFieldErrors((current) => ({ ...current, [name]: undefined }));
    setMessage({ type: "", text: "" });
  }

  function edit(ejemplar) {
    setEditing(ejemplar.id);
    setValues({ codigo: ejemplar.codigo, estado: ejemplar.estado });
    setFieldErrors({});
    setConfirming(null);
    setMessage({ type: "", text: "" });
    codigoRef.current?.focus();
  }

  function showError(error, fallback) {
    if (error.status === 401) {
      expireSession();
      return;
    }
    setFieldErrors(error.fieldErrors ?? {});
    setMessage({ type: "error", text: error.message || fallback });
  }

  async function save(event) {
    event.preventDefault();
    if (!staff || !ready || working.current) return;
    const errors = validateEjemplar(values);
    if (Object.keys(errors).length) {
      setFieldErrors(errors);
      setMessage({ type: "error", text: "Revisa los datos del ejemplar." });
      return;
    }

    working.current = true;
    setBusy("saving");
    setMessage({ type: "", text: "" });
    setFieldErrors({});
    try {
      const datos = { codigo: values.codigo.trim(), estado: values.estado };
      const saved = editing === null
        ? await createEjemplar(libroId, datos)
        : await updateEjemplar(libroId, editing, datos);
      if (!mounted.current) return;
      setMessage({ type: "success", text: editing === null
        ? `Ejemplar «${saved.codigo}» registrado correctamente.`
        : `Cambios del ejemplar «${saved.codigo}» guardados correctamente.` });
      resetForm();
      setConfirming(null);
      setRevision((current) => current + 1);
    } catch (error) {
      if (mounted.current) showError(error, "No se ha podido guardar el ejemplar.");
    } finally {
      working.current = false;
      if (mounted.current) setBusy("");
    }
  }

  async function retire(ejemplar) {
    if (!staff || !ready || working.current) return;
    working.current = true;
    setBusy("retiring");
    setMessage({ type: "", text: "" });
    try {
      await retireEjemplar(libroId, ejemplar.id);
      if (!mounted.current) return;
      if (editing === ejemplar.id) resetForm();
      setConfirming(null);
      setMessage({ type: "success", text: `Ejemplar «${ejemplar.codigo}» retirado correctamente.` });
      setRevision((current) => current + 1);
    } catch (error) {
      if (mounted.current) showError(error, "No se ha podido retirar el ejemplar.");
    } finally {
      working.current = false;
      if (mounted.current) setBusy("");
    }
  }

  return (
    <section className="catalogo ejemplares" aria-labelledby="ejemplares-titulo">
      <header className="catalogo__cabecera">
        <h2 id="ejemplares-titulo">Ejemplares{ready ? ` de «${state.book.titulo}»` : ""}</h2>
        <button type="button" className="catalogo__actualizar" disabled={loading || Boolean(busy)}
          onClick={() => {
            setConfirming(null);
            setRevision((current) => current + 1);
          }}>
          {!loading && state.phase === "error" ? "Reintentar" : "Actualizar"}
        </button>
      </header>

      <div role="status" aria-atomic="true">
        {loading && <p>Cargando ejemplares…</p>}
        {message.type === "success" && <p className="catalogo__exito">{message.text}</p>}
      </div>
      <div role="alert" aria-atomic="true">
        {!loading && state.phase === "error" && <p className="catalogo__error">{state.message}</p>}
        {message.type === "error" && <p className="catalogo__error">{message.text}</p>}
      </div>

      <div aria-busy={loading || Boolean(busy)}>
        {ready && (
          <>
            <p className="catalogo__resumen" role="status">
              Total de ejemplares: {state.data.total} · Disponibles: {state.data.disponibles}
            </p>
            <p className="catalogo__ayuda">El total incluye los ejemplares retirados.</p>
            {state.book.retirado && <p className="catalogo__error">Este libro está retirado del catálogo.</p>}

            {state.data.ejemplares.length === 0 ? (
              <p className="catalogo__vacio" role="status">Este libro todavía no tiene ejemplares.</p>
            ) : (
              <ul className="ejemplares__lista" aria-label="Ejemplares del libro">
                {state.data.ejemplares.map((ejemplar) => (
                  <li key={ejemplar.id} className="catalogo__libro">
                    <h3>Ejemplar {ejemplar.codigo}</h3>
                    <p><strong>Identificador:</strong> {ejemplar.id}</p>
                    <p><strong>Estado:</strong> {ejemplar.estado === "DISPONIBLE" ? "Disponible" : "Prestado"}</p>
                    {!ejemplar.activo && <p className="ejemplares__retirado">Retirado</p>}

                    {staff && ejemplar.activo && (
                      <div className="catalogo__gestion">
                        {confirming === ejemplar.id ? (
                          <>
                            <p className="catalogo__confirmacion" role="alert">
                              ¿Retirar el ejemplar «{ejemplar.codigo}»? Se conservará su registro.
                            </p>
                            <button type="button" className="catalogo__actualizar catalogo__retirar"
                              disabled={Boolean(busy)} onClick={() => retire(ejemplar)}>
                              {busy === "retiring" ? "Retirando…" : "Sí, retirar"}
                            </button>
                            <button type="button" className="catalogo__actualizar" disabled={Boolean(busy)}
                              onClick={() => setConfirming(null)}>Cancelar</button>
                          </>
                        ) : (
                          <>
                            <button type="button" className="catalogo__actualizar" disabled={Boolean(busy)}
                              aria-label={`Editar ejemplar ${ejemplar.codigo}`} onClick={() => edit(ejemplar)}>Editar</button>
                            <button type="button" className="catalogo__actualizar catalogo__retirar" disabled={Boolean(busy)}
                              aria-label={`Retirar ejemplar ${ejemplar.codigo}`} onClick={() => {
                                setConfirming(ejemplar.id);
                                setMessage({ type: "", text: "" });
                              }}>Retirar</button>
                          </>
                        )}
                      </div>
                    )}
                  </li>
                ))}
              </ul>
            )}

            {staff && (
              <form className="registro ejemplares__formulario" onSubmit={save} noValidate
                aria-labelledby="ejemplar-form-titulo">
                <h3 id="ejemplar-form-titulo">{editing === null ? "Registrar ejemplar" : `Editar ejemplar ${editing}`}</h3>
                <fieldset className="registro__campos" disabled={Boolean(busy)}>
                  <legend className="registro__legend">Datos del ejemplar</legend>
                  <div className="registro__campo">
                    <label htmlFor="ejemplar-codigo">Código del ejemplar</label>
                    <input ref={codigoRef} id="ejemplar-codigo" name="codigo" value={values.codigo} required maxLength={50}
                      aria-invalid={fieldErrors.codigo ? true : undefined}
                      aria-describedby={fieldErrors.codigo ? "ejemplar-codigo-error" : undefined}
                      onChange={(event) => change("codigo", event.target.value)} />
                    {fieldErrors.codigo && <p className="catalogo__error" id="ejemplar-codigo-error">{fieldErrors.codigo}</p>}
                  </div>
                  <div className="registro__campo">
                    <label htmlFor="ejemplar-estado">Estado</label>
                    <select id="ejemplar-estado" name="estado" value={values.estado} required
                      aria-invalid={fieldErrors.estado ? true : undefined}
                      aria-describedby={fieldErrors.estado ? "ejemplar-estado-error" : undefined}
                      onChange={(event) => change("estado", event.target.value)}>
                      <option value="DISPONIBLE">Disponible</option>
                      <option value="PRESTADO">Prestado</option>
                    </select>
                    {fieldErrors.estado && <p className="catalogo__error" id="ejemplar-estado-error">{fieldErrors.estado}</p>}
                  </div>
                  <div className="ejemplares__acciones">
                    <button className="registro__boton" type="submit">
                      {busy === "saving" ? "Guardando…" : editing === null ? "Registrar ejemplar" : "Guardar cambios"}
                    </button>
                    {editing !== null && <button className="catalogo__actualizar" type="button"
                      onClick={resetForm}>Cancelar edición</button>}
                  </div>
                </fieldset>
              </form>
            )}
          </>
        )}
      </div>
      <p className="ejemplares__volver"><Link to="/catalogo">Volver al catálogo</Link></p>
    </section>
  );
}
