/** Formulario vacío: en un input, "sin valor" es "" (no null). */
export const EMPTY_BOOK = {
  isbn: "", titulo: "", autor: "", editorial: "", categoria: "",
  anioPublicacion: "", descripcion: "", portadaUrl: "",
};

const ISBN = /^(?:\d[- ]?){9}[\dXx]$|^(?:\d[- ]?){12}\d$/;
const URL_PORTADA = /^https?:\/\/\S+$/;
const MAXIMOS = { titulo: 255, autor: 255, editorial: 150, categoria: 100, descripcion: 5000, portadaUrl: 1000 };

/** Ficha del backend -> valores del formulario (null -> ""). */
export function toFormValues(book) {
  const values = { ...EMPTY_BOOK };
  for (const name of Object.keys(EMPTY_BOOK)) {
    values[name] = book?.[name] == null ? "" : String(book[name]);
  }
  return values;
}

/** Valores del formulario -> JSON para la API (recorta; opcionales vacíos -> null; año -> número). */
export function toRequest(values) {
  const opcional = (value) => {
    const text = String(value ?? "").trim();
    return text === "" ? null : text;
  };
  const anio = opcional(values.anioPublicacion);
  return {
    isbn: opcional(values.isbn),
    titulo: String(values.titulo ?? "").trim(),
    autor: String(values.autor ?? "").trim(),
    editorial: opcional(values.editorial),
    categoria: opcional(values.categoria),
    anioPublicacion: anio === null ? null : Number(anio),
    descripcion: opcional(values.descripcion),
    portadaUrl: opcional(values.portadaUrl),
  };
}

/** Mismas reglas y mensajes que LibroRequest del backend (#51). Devuelve {campo: mensaje}. */
export function validateBook(values) {
  const book = toRequest(values);
  const errors = {};

  if (!book.titulo) errors.titulo = "El título es obligatorio.";
  if (!book.autor) errors.autor = "El autor es obligatorio.";
  if (book.isbn && !ISBN.test(book.isbn)) errors.isbn = "ISBN no válido: debe tener 10 o 13 dígitos.";
  if (book.anioPublicacion !== null &&
      (!Number.isInteger(book.anioPublicacion) || book.anioPublicacion < 0 || book.anioPublicacion > 2100)) {
    errors.anioPublicacion = "Año de publicación no válido.";
  }
  if (book.portadaUrl && !URL_PORTADA.test(book.portadaUrl)) {
    errors.portadaUrl = "La portada debe ser una URL http o https.";
  }
  for (const [name, max] of Object.entries(MAXIMOS)) {
    if (!errors[name] && book[name] && book[name].length > max) {
      errors[name] = `No puede superar ${max} caracteres.`;
    }
  }
  return errors;
}
