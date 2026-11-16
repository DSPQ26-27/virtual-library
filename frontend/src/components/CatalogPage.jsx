import { useEffect, useRef, useState } from "react";
import { Link } from "react-router";
import { useAuth } from "../auth/AuthContext.js";
import { getBooks, getCategories } from "../services/books.js";
import { retireBook } from "../services/catalogAdmin.js";
import { canManageCatalog } from "../auth/roles.js";
import "./CatalogPage.css";

export default function CatalogPage() {
  const { user, expireSession } = useAuth();
  // Gestión del catálogo (#52): solo bibliotecarios y administradores
  const staff = canManageCatalog(user);
  const [confirmando, setConfirmando] = useState(null);
  const [retirada, setRetirada] = useState({ phase: "idle", message: "" });
  const [draft, setDraft] = useState("");
  const [query, setQuery] = useState({ q: "", categoria: "", vista: "TODOS", page: 0, revision: 0 });
  const [state, setState] = useState({ query: null, phase: "loading", data: null, message: "" });
  const [categories, setCategories] = useState({ phase: "loading", items: [], message: "" });
  const [categoryAttempt, setCategoryAttempt] = useState(0);
  const summaryRef = useRef(null);
  const focusAfterPaging = useRef(false);
  // No mostrar una página anterior bajo filtros nuevos, incluso antes de ejecutar el efecto.
  const loading = state.query !== query || state.phase === "loading";
  const ready = !loading && state.phase === "ready";
  const failed = !loading && state.phase === "error";

  useEffect(() => {
    if (!loading && focusAfterPaging.current) {
      summaryRef.current?.focus();
      focusAfterPaging.current = false;
    }
  }, [loading]);

  useEffect(() => {
    const controller = new AbortController();
    let active = true;

    async function loadBooks() {
      try {
        const data = await getBooks({
          q: query.q,
          categoria: query.categoria,
          vista: query.vista,
          page: query.page,
          size: 20,
          sort: "titulo,asc",
          signal: controller.signal,
        });

        if (!active) return;
        if (data.totalPages > 0 && data.page >= data.totalPages) {
          // El catálogo puede reducirse mientras se navega o actualiza.
          setQuery((current) => current === query ? { ...current, page: data.totalPages - 1 } : current);
          return;
        }
        setState({ query, phase: "ready", data, message: "" });
      } catch (error) {
        if (!active) return;

        if (error.status === 401) {
          expireSession();
          return;
        }

        setState({
          query,
          phase: "error",
          data: null,
          message:
            error instanceof Error
              ? error.message
              : "No se ha podido cargar el catálogo.",
        });
      }
    }

    loadBooks();

    return () => {
      active = false;
      controller.abort();
    };
  }, [query, expireSession]);

  useEffect(() => {
    const controller = new AbortController();
    let active = true;

    async function loadCategories() {
      try {
        const items = await getCategories({ signal: controller.signal });
        if (active) setCategories({ phase: "ready", items, message: "" });
      } catch (error) {
        if (!active) return;
        if (error.status === 401) {
          expireSession();
          return;
        }
        setCategories((current) => ({
          ...current,
          phase: "error",
          message: error instanceof Error ? error.message : "No se pudieron cargar las categorías.",
        }));
      }
    }

    loadCategories();
    return () => {
      active = false;
      controller.abort();
    };
  }, [categoryAttempt, expireSession]);

  function reload() {
    setQuery((current) => ({ ...current, revision: current.revision + 1 }));
  }

  function search(event) {
    event.preventDefault();
    setQuery((current) => ({ ...current, q: draft.trim(), page: 0, revision: current.revision + 1 }));
  }

  function filter(name, value) {
    setQuery((current) => ({ ...current, [name]: value, page: 0 }));
  }

  function clearFilters() {
    setDraft("");
    setQuery((current) => ({ q: "", categoria: "", vista: "TODOS", page: 0, revision: current.revision + 1 }));
  }

  async function retirar(book) {
    setRetirada({ phase: "saving", message: "" });
    try {
      await retireBook(book.id); // DELETE /api/libros/{id} (retirada lógica)
      setConfirmando(null);
      setRetirada({ phase: "done", message: `«${book.titulo}» se ha retirado del catálogo.` });
      reload(); // el backend ya no devuelve el libro retirado
    } catch (error) {
      if (error.status === 401) {
        expireSession();
        return;
      }
      setRetirada({ phase: "error", message: error.message || "No se ha podido retirar el libro." });
    }
  }

  function changePage(page) {
    focusAfterPaging.current = true;
    setQuery((current) => ({ ...current, page }));
  }

  return (
    <section className="catalogo" aria-labelledby="catalogo-titulo">
      <header className="catalogo__cabecera">
        <h2 id="catalogo-titulo">Catálogo</h2>

        {staff && (
          <Link className="catalogo__actualizar catalogo__enlace" to="/admin/libros/nuevo">
            Añadir libro
          </Link>
        )}

        <button
          className="catalogo__actualizar"
          type="button"
          onClick={reload}
          disabled={loading}
        >
          {failed ? "Reintentar" : "Actualizar"}
        </button>
      </header>

      <div className="catalogo__filtros">
      <form className="catalogo__buscador" onSubmit={search} role="search" aria-label="Buscar en el catálogo">
        <label htmlFor="catalogo-busqueda">Buscar por título o autor</label>
        <div className="catalogo__entrada-busqueda">
          <input id="catalogo-busqueda" type="search" value={draft}
            onChange={(event) => setDraft(event.target.value)} aria-describedby="catalogo-ayuda" />
          <button type="submit" className="catalogo__actualizar catalogo__buscar">Buscar</button>
        </div>
        <p className="catalogo__ayuda" id="catalogo-ayuda">También puedes buscar por ISBN. Pulsa Buscar o Enter para aplicar el texto.</p>
      </form>

      <div className="catalogo__campo">
        <label htmlFor="catalogo-categoria">Categoría</label>
        <select id="catalogo-categoria" value={query.categoria}
          aria-describedby="catalogo-categoria-estado"
          disabled={categories.phase !== "ready"}
          onChange={(event) => filter("categoria", event.target.value)}>
          <option value="">Todas las categorías</option>
          {categories.items.map((category) => <option key={category} value={category}>{category}</option>)}
        </select>
        {query.categoria && <p className="catalogo__ayuda">Categoría seleccionada: {query.categoria}</p>}
      </div>
      <div id="catalogo-categoria-estado" className="catalogo__categorias-estado" role="status" aria-atomic="true">
        {categories.phase === "loading" && <p>Cargando categorías…</p>}
      </div>
      {categories.phase === "error" && (
        <div className="catalogo__categorias-estado">
          <p role="alert" className="catalogo__error">{categories.message} Puedes seguir buscando libros.</p>
          <button type="button" className="catalogo__actualizar" onClick={() => {
            setCategories((current) => ({ ...current, phase: "loading", message: "" }));
            setCategoryAttempt((current) => current + 1);
          }}>Reintentar categorías</button>
        </div>
      )}

      <fieldset className="catalogo__vistas">
        <legend>Vista del catálogo</legend>
        <div className="catalogo__opciones">
        {[["TODOS", "Todos"], ["BIBLIOTECA", "En biblioteca"], ["DISPONIBLES", "Disponibles"]].map(([value, label]) => (
          <label className="catalogo__opcion" key={value}>
            <input type="radio" name="catalogo-vista" value={value} checked={query.vista === value}
              onChange={() => filter("vista", value)} />{label}{" "}
          </label>
        ))}
        </div>
      </fieldset>
      <div className="catalogo__limpiar"><button type="button" className="catalogo__actualizar" onClick={clearFilters}>Limpiar filtros</button></div>
      </div>

      <p ref={summaryRef} tabIndex={-1} className="catalogo__resumen" role="status" aria-atomic="true">
        {loading ? "Cargando catálogo…" : ready
          ? `Total de libros encontrados: ${state.data.totalElements}${state.data.totalPages > 0 ? ` · Página ${state.data.page + 1} de ${state.data.totalPages}` : ""}`
          : failed ? "No se pudo actualizar el catálogo." : ""}
      </p>

      <div role="alert" aria-atomic="true">
        {failed && <p className="catalogo__error">{state.message}</p>}
      </div>

      <div aria-live="polite" aria-atomic="true">
        {retirada.phase === "done" && <p className="catalogo__exito">{retirada.message}</p>}
      </div>
      <div role="alert" aria-atomic="true">
        {retirada.phase === "error" && <p className="catalogo__error">{retirada.message}</p>}
      </div>

      <div aria-busy={loading}>
      {ready && state.data.content.length === 0 && (
        <p className="catalogo__vacio" role="status">
          {query.q || query.categoria || query.vista !== "TODOS"
            ? "No se encontraron resultados con estos filtros."
            : "Todavía no hay libros en el catálogo."}
        </p>
      )}

      {ready && state.data.content.length > 0 && (
        <ul className="catalogo__lista" aria-label="Libros del catálogo">
          {state.data.content.map((book) => (
            <li key={book.id}>
              <article className="catalogo__libro">
                <h3>{book.titulo}</h3>

                <p>
                  <strong>Autor:</strong> {book.autor}
                </p>

                <p
                  className={
                    book.disponible
                      ? "catalogo__estado catalogo__estado--disponible"
                      : "catalogo__estado catalogo__estado--ocupado"
                  }
                >
                  {book.disponible
                    ? "Disponible"
                    : "Sin ejemplares disponibles"}
                </p>

                <Link className="catalogo__actualizar catalogo__enlace" to={`/libros/${book.id}/ejemplares`}>
                  Ejemplares
                </Link>

                {staff && (
                  <div className="catalogo__gestion">
                    {confirmando === book.id ? (
                      <>
                        <p className="catalogo__confirmacion" role="alert">
                          ¿Retirar «{book.titulo}» del catálogo? No se borrará ni se perderán sus ejemplares.
                        </p>
                        <button
                          type="button"
                          className="catalogo__actualizar catalogo__retirar"
                          disabled={retirada.phase === "saving"}
                          onClick={() => retirar(book)}
                        >
                          {retirada.phase === "saving" ? "Retirando…" : "Sí, retirar"}
                        </button>
                        <button
                          type="button"
                          className="catalogo__actualizar"
                          disabled={retirada.phase === "saving"}
                          onClick={() => setConfirmando(null)}
                        >
                          Cancelar
                        </button>
                      </>
                    ) : (
                      <>
                        <Link
                          className="catalogo__actualizar catalogo__enlace"
                          to={`/admin/libros/${book.id}/editar`}
                          aria-label={`Editar «${book.titulo}»`}
                        >
                          Editar
                        </Link>
                        <button
                          type="button"
                          className="catalogo__actualizar catalogo__retirar"
                          aria-label={`Retirar «${book.titulo}»`}
                          onClick={() => {
                            setConfirmando(book.id);
                            setRetirada({ phase: "idle", message: "" });
                          }}
                        >
                          Retirar
                        </button>
                      </>
                    )}
                  </div>
                )}
              </article>
            </li>
          ))}
        </ul>
      )}
      </div>
      {(ready || loading) && state.data?.totalPages > 0 && (
        <nav className="catalogo__paginacion" aria-label="Paginación del catálogo">
          <button type="button" className="catalogo__actualizar" disabled={loading || state.data.page === 0}
            onClick={() => changePage(state.data.page - 1)}>Anterior</button>
          <span className="catalogo__pagina">{loading ? "Actualizando página…" : <>Página {state.data.page + 1} de {state.data.totalPages}<small>{state.data.size} libros por página</small></>}</span>
          <button type="button" className="catalogo__actualizar" disabled={loading || state.data.page + 1 >= state.data.totalPages}
            onClick={() => changePage(state.data.page + 1)}>Siguiente</button>
        </nav>
      )}
    </section>
  );
}
