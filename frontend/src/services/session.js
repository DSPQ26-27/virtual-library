async function request(path, { method = "GET", data, token } = {}) {
  const headers = { Accept: "application/json" };

  if (data !== undefined) {
    headers["Content-Type"] = "application/json";
  }

  if (token) {
    headers["X-CSRF-TOKEN"] = token;
  }

  try {
    const response = await fetch(`/api/auth/${path}`, {
      method,
      headers,
      credentials: "include",
      cache: "no-store",
      redirect: "error",
      signal: AbortSignal.timeout(10000),
      ...(data !== undefined ? { body: JSON.stringify(data) } : {}),
    });

    const text = await response.text();
    let body = null;

    try {
      body = JSON.parse(text);
    } catch {
      // Una respuesta vacía o HTML no cumple el contrato JSON.
    }

    return { status: response.status, body };
  } catch (error) {
    throw new Error(
      error.name === "TimeoutError" || error.name === "AbortError"
        ? "El servidor tarda demasiado en responder. Inténtalo más tarde."
        : "No se ha podido conectar con el servicio de autenticación."
    );
  }
}

function isUser(body) {
  return (
    body &&
    Number.isSafeInteger(body.id) &&
    body.id > 0 &&
    typeof body.nombre === "string" &&
    typeof body.email === "string" &&
    Array.isArray(body.roles) &&
    body.roles.every((role) => typeof role === "string")
  );
}

async function csrfToken() {
  const { status, body } = await request("csrf");

  if (
    status !== 200 ||
    body?.headerName !== "X-CSRF-TOKEN" ||
    typeof body?.token !== "string" ||
    !body.token
  ) {
    throw new Error(
      "No se ha podido preparar la operación. Inténtalo de nuevo."
    );
  }

  return body.token;
}

export async function getCurrentUser() {
  const { status, body } = await request("me");

  if (status === 401) return null;

  if (status === 200 && isUser(body)) return body;

  throw new Error("No se ha podido comprobar tu sesión.");
}

export async function login({ email, password }) {
  const token = await csrfToken();

  const { status, body } = await request("login", {
    method: "POST",
    token,
    data: {
      email: email.trim(),
      password,
    },
  });

  if (status === 200 && isUser(body)) return body;

  if (status === 401) {
    throw new Error("Correo o contraseña incorrectos.");
  }

  if (status === 400) {
    throw new Error("Revisa el correo y la contraseña.");
  }

  if (status === 403) {
    throw new Error(
      "No se ha podido validar la solicitud. Inténtalo de nuevo."
    );
  }

  if (status === 429) {
    throw new Error(
      "Demasiados intentos. Espera antes de volver a intentarlo."
    );
  }

  throw new Error("No se ha podido iniciar sesión.");
}

export async function logout() {
  const token = await csrfToken();

  const { status } = await request("logout", {
    method: "POST",
    token,
  });

  if (status !== 204 && status !== 401) {
    throw new Error(
      "No se ha podido cerrar la sesión. Inténtalo de nuevo."
    );
  }
}