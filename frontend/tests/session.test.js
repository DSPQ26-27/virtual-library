import test from "node:test";
import assert from "node:assert/strict";
import {
  getCurrentUser,
  login,
  logout,
} from "../src/services/session.js";

const user = {
  id: 1,
  nombre: "Santi",
  email: "santi@example.com",
  roles: ["STUDENT"],
};

const csrf = {
  headerName: "X-CSRF-TOKEN",
  token: "token-de-prueba",
};

test("401 en me representa una sesión anónima", async (t) => {
  t.mock.method(
    globalThis,
    "fetch",
    async () => new Response(null, { status: 401 })
  );

  assert.equal(await getCurrentUser(), null);
});

test("recupera el usuario confirmado por el servidor", async (t) => {
  t.mock.method(globalThis, "fetch", async (url, options) => {
    assert.equal(url, "/api/auth/me");
    assert.equal(options.credentials, "include");

    return Response.json(user);
  });

  assert.deepEqual(await getCurrentUser(), user);
});

test("una página HTML no se interpreta como sesión válida", async (t) => {
  t.mock.method(
    globalThis,
    "fetch",
    async () => new Response("<html>Login</html>")
  );

  await assert.rejects(
    getCurrentUser(),
    /comprobar tu sesión/
  );
});

test("un error del servidor no se confunde con una sesión anónima", async (t) => {
  t.mock.method(
    globalThis,
    "fetch",
    async () => new Response(null, { status: 500 })
  );

  await assert.rejects(
    getCurrentUser(),
    /comprobar tu sesión/
  );
});

test("login envía cookies, CSRF y los campos acordados", async (t) => {
  const calls = [];

  t.mock.method(globalThis, "fetch", async (url, options) => {
    calls.push(url);

    assert.equal(options.credentials, "include");

    if (url.endsWith("/csrf")) {
      return Response.json(csrf);
    }

    assert.equal(url, "/api/auth/login");
    assert.equal(options.method, "POST");
    assert.equal(options.headers["X-CSRF-TOKEN"], csrf.token);

    assert.deepEqual(JSON.parse(options.body), {
      email: "santi@example.com",
      password: "clave-prueba",
    });

    return Response.json(user);
  });

  const result = await login({
    email: " santi@example.com ",
    password: "clave-prueba",
  });

  assert.deepEqual(result, user);

  assert.deepEqual(calls, [
    "/api/auth/csrf",
    "/api/auth/login",
  ]);
});

test("rechaza las credenciales incorrectas", async (t) => {
  t.mock.method(globalThis, "fetch", async (url) => {
    if (url.endsWith("/csrf")) {
      return Response.json(csrf);
    }

    return new Response(null, { status: 401 });
  });

  await assert.rejects(
    login({
      email: user.email,
      password: "incorrecta",
    }),
    /Correo o contraseña incorrectos/
  );
});

test("no envía el login sin un token CSRF válido", async (t) => {
  const stub = t.mock.method(
    globalThis,
    "fetch",
    async () => Response.json({})
  );

  await assert.rejects(
    login({
      email: user.email,
      password: "clave",
    }),
    /preparar la operación/
  );

  assert.equal(stub.mock.callCount(), 1);
});

test("logout solicita CSRF y espera confirmación", async (t) => {
  t.mock.method(globalThis, "fetch", async (url, options) => {
    if (url.endsWith("/csrf")) {
      return Response.json(csrf);
    }

    assert.equal(url, "/api/auth/logout");
    assert.equal(options.method, "POST");
    assert.equal(options.headers["X-CSRF-TOKEN"], csrf.token);

    return new Response(null, { status: 204 });
  });

  await logout();
});

test("no confirma logout cuando el servidor lo rechaza", async (t) => {
  t.mock.method(globalThis, "fetch", async (url) => {
    if (url.endsWith("/csrf")) {
      return Response.json(csrf);
    }

    return new Response(null, { status: 403 });
  });

  await assert.rejects(
    logout(),
    /No se ha podido cerrar/
  );
});