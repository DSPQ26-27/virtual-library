export async function registerStudent({ nombre, email, password }) {
  let response;

  try {
    response = await fetch("/api/auth/register", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
      },
      credentials: "omit",
      redirect: "error",
      signal: AbortSignal.timeout(10000),
      body: JSON.stringify({
        nombre: nombre.trim(),
        email: email.trim(),
        password,
      }),
    });
  } catch (error) {
    throw new Error(
      error.name === "TimeoutError"
        ? "El servidor tarda demasiado en responder. Inténtalo más tarde."
        : "No se ha podido conectar con el servicio de registro."
    );
  }

  let data = null;

  try {
    data = await response.json();
  } catch {
    // También puede llegar una respuesta vacía o que no sea JSON.
  }

  if (response.status === 201) {
    if (
      data &&
      data.id != null &&
      typeof data.nombre === "string" &&
      typeof data.email === "string"
    ) {
      return data;
    }

    throw new Error(
      "El servidor no ha confirmado correctamente los datos de la cuenta. " +
      "Comprueba si se creó antes de repetir el registro."
    );
  }

  const messages = {
    400: "Revisa los datos del formulario.",
    401: "El servidor no permite el registro en este momento.",
    403: "El servidor no permite el registro en este momento.",
    404: "El servicio de registro todavía no está disponible.",
    409: "Ya existe una cuenta con ese correo electrónico.",
  };

  if (response.status === 400 || response.status === 409) {
    const labels = {
      nombre: "Nombre",
      email: "Correo",
      password: "Contraseña",
    };

    const details = Object.entries(labels)
      .filter(([field]) => typeof data?.errors?.[field] === "string")
      .map(([field, label]) => `${label}: ${data.errors[field]}`);

    const message =
      typeof data?.message === "string"
        ? data.message
        : messages[response.status];

    throw new Error([message, ...details].join("\n"));
  }

  throw new Error(
    messages[response.status] ||
      "No se ha podido completar el registro. Inténtalo más tarde."
  );
}