import { useEffect, useState } from "react";
import { Link, useParams } from "react-router";
import { useAuth } from "../auth/AuthContext.js";
import { createBook, getBook, updateBook } from "../services/catalogAdmin.js";
import { EMPTY_BOOK, toFormValues, toRequest, validateBook } from "../services/bookValidation.js";
import "./RegisterForm.css";
import "./BookForm.css";

const CAMPOS = [
  { name: "titulo", label: "Título", required: true, maxLength: 255 },
  { name: "autor", label: "Autor", required: true, maxLength: 255 },
  { name: "isbn", label: "ISBN", maxLength: 17, help: "10 o 13 dígitos; se admiten guiones." },
  { name: "editorial", label: "Editorial", maxLength: 150 },
  { name: "categoria", label: "Categoría", maxLength: 100 },
  { name: "anioPublicacion", label: "Año de publicación", type: "number", min: 0, max: 2100 },
  { name: "portadaUrl", label: "URL de la portada", type: "url", maxLength: 1000,
    help: "Dirección http o https de la imagen." },
  { name: "descripcion", label: "Descripción", multiline: true, maxLength: 5000 },
];

/**
 * Crear (/admin/libros/nuevo) o editar (/admin/libros/:id/editar) un libro (#52).
 * La key crea un formulario nuevo y limpio para cada libro, sin resetear el estado a mano.
 */
export default function BookForm() {
  const { id } = useParams();
  return <BookFormContent key={id ?? "nuevo"} id={id} />;
}

function BookFormContent({ id }) {
  const editing = id !== undefined;
  const { expireSession } = useAuth();

  const [values, setValues] = useState(EMPTY_BOOK);
  const [fieldErrors, setFieldErrors] = useState({});
  const [phase, setPhase] = useState(editing ? "loading" : "ready");
  const [message, setMessage] = useState({ type: "", text: "" });
  const [retirado, setRetirado] = useState(false);

  // Al editar, cargar los datos del libro desde la API (al crear no hay nada que cargar).
  useEffect(() => {
    if (!editing) return undefined;

    let active = true;

    getBook(id)
      .then((book) => {
        if (!active) return;
        setValues(toFormValues(book));
        setRetirado(book.retirado);
        setPhase("ready");
      })
      .catch((error) => {
        if (!active) return;
        if (error.status === 401) {
          expireSession();
          return;
        }
        setMessage({ type: "error", text: error.message || "No se ha podido cargar el libro." });
        setPhase("loadError");
      });

    return () => {
      active = false;
    };
  }, [id, editing, expireSession]);

  function change(name, value) {
    setValues((current) => ({ ...current, [name]: value }));
    setFieldErrors((current) => ({ ...current, [name]: undefined }));
    setMessage({ type: "", text: "" });
  }

  async function handleSubmit(event) {
    event.preventDefault();
    if (phase === "saving") return;

    // 1. Validar en el navegador (el backend vuelve a validar)
    const errors = validateBook(values);
    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      setMessage({ type: "error", text: "Revisa los datos del libro." });
      return;
    }

    setPhase("saving");
    setMessage({ type: "", text: "" });

    try {
      // 2. PUT al editar, POST al crear
      const saved = editing
        ? await updateBook(id, toRequest(values))
        : await createBook(toRequest(values));

      setFieldErrors({});
      if (editing) {
        setValues(toFormValues(saved));
        setMessage({ type: "exito", text: "Cambios guardados correctamente." });
      } else {
        setValues(EMPTY_BOOK);
        setMessage({ type: "exito", text: `Libro «${saved.titulo}» creado correctamente.` });
      }
    } catch (error) {
      if (error.status === 401) {
        expireSession();
        return;
      }
      // 3. Errores del backend junto a cada campo (400, 409)
      setFieldErrors(error.fieldErrors ?? {});
      setMessage({ type: "error", text: error.message || "No se ha podido guardar el libro." });
    } finally {
      setPhase((current) => (current === "saving" ? "ready" : current));
    }
  }

  return (
    <section className="registro libro-form" aria-labelledby="libro-form-titulo">
      <h2 id="libro-form-titulo">{editing ? "Editar libro" : "Añadir libro"}</h2>
      <p className="registro__intro">
        {editing
          ? "Modifica los datos bibliográficos y guarda los cambios."
          : "Introduce los datos del nuevo libro. Título y autor son obligatorios."}
      </p>

      {phase === "loading" && <p role="status">Cargando libro…</p>}

      {phase === "loadError" && (
        <div role="alert">
          <p className="registro__mensaje registro__mensaje--error">{message.text}</p>
          <p className="libro-form__volver"><Link to="/catalogo">Volver al catálogo</Link></p>
        </div>
      )}

      {(phase === "ready" || phase === "saving") && (
        <form onSubmit={handleSubmit} noValidate>
          {editing && retirado && (
            <p className="registro__mensaje registro__mensaje--error libro-form__aviso">
              Este libro está retirado y no aparece en el catálogo.
            </p>
          )}

          <fieldset className="registro__campos" disabled={phase === "saving"}>
            <legend className="registro__legend">Datos bibliográficos</legend>

            {CAMPOS.map((campo) => {
              const fieldId = `libro-${campo.name}`;
              const error = fieldErrors[campo.name];
              const describedBy = [
                campo.help ? `${fieldId}-ayuda` : null,
                error ? `${fieldId}-error` : null,
              ].filter(Boolean).join(" ") || undefined;
              const common = {
                id: fieldId,
                name: campo.name,
                value: values[campo.name],
                maxLength: campo.maxLength,
                required: campo.required,
                "aria-invalid": error ? true : undefined,
                "aria-describedby": describedBy,
                onChange: (event) => change(campo.name, event.target.value),
              };

              return (
                <div className="registro__campo" key={campo.name}>
                  <label htmlFor={fieldId}>
                    {campo.label}{campo.required && <span aria-hidden="true"> *</span>}
                  </label>
                  {campo.multiline
                    ? <textarea {...common} rows={5} />
                    : <input {...common} type={campo.type ?? "text"} min={campo.min} max={campo.max} />}
                  {campo.help && <small id={`${fieldId}-ayuda`}>{campo.help}</small>}
                  {error && <p className="libro-form__error-campo" id={`${fieldId}-error`}>{error}</p>}
                </div>
              );
            })}

            <button className="registro__boton" type="submit">
              {phase === "saving" ? "Guardando…" : editing ? "Guardar cambios" : "Crear libro"}
            </button>
          </fieldset>

          <div aria-live="polite" aria-atomic="true">
            {message.type === "exito" && (
              <p className="registro__mensaje registro__mensaje--exito">{message.text}</p>
            )}
          </div>

          <div role="alert" aria-atomic="true">
            {message.type === "error" && (
              <p className="registro__mensaje registro__mensaje--error">{message.text}</p>
            )}
          </div>

          <p className="libro-form__volver"><Link to="/catalogo">Volver al catálogo</Link></p>
        </form>
      )}
    </section>
  );
}
