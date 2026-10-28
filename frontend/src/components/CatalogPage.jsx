import { useEffect, useState } from "react";
import { useAuth } from "../auth/AuthContext.js";
import { getBooks } from "../services/books.js";
import "./CatalogPage.css";

export default function CatalogPage() {
  const { expireSession } = useAuth();
  const [attempt, setAttempt] = useState(0);

  const [state, setState] = useState({
    phase: "loading",
    books: [],
    message: "",
  });

  useEffect(() => {
    const controller = new AbortController();

    async function loadBooks() {
      try {
        const books = await getBooks({
          signal: controller.signal,
        });

        if (!controller.signal.aborted) {
          setState({
            phase: "ready",
            books,
            message: "",
          });
        }
      } catch (error) {
        if (controller.signal.aborted) return;

        if (error.status === 401) {
          expireSession();
          return;
        }

        setState({
          phase: "error",
          books: [],
          message:
            error instanceof Error
              ? error.message
              : "No se ha podido cargar el catálogo.",
        });
      }
    }

    loadBooks();

    return () => {
      controller.abort();
    };
  }, [attempt, expireSession]);

  function reload() {
    setState({
      phase: "loading",
      books: [],
      message: "",
    });

    setAttempt((value) => value + 1);
  }

  return (
    <section className="catalogo" aria-labelledby="catalogo-titulo">
      <header className="catalogo__cabecera">
        <h2 id="catalogo-titulo">Catálogo</h2>

        <button
          className="catalogo__actualizar"
          type="button"
          onClick={reload}
          disabled={state.phase === "loading"}
        >
          {state.phase === "error" ? "Reintentar" : "Actualizar"}
        </button>
      </header>

      {state.phase === "loading" && (
        <p role="status">Cargando catálogo…</p>
      )}

      {state.phase === "error" && (
        <p className="catalogo__error" role="alert">
          {state.message}
        </p>
      )}

      {state.phase === "ready" && state.books.length === 0 && (
        <p role="status">
          Todavía no hay libros en el catálogo.
        </p>
      )}

      {state.phase === "ready" && state.books.length > 0 && (
        <ul className="catalogo__lista" aria-label="Libros del catálogo">
          {state.books.map((book) => (
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
              </article>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}