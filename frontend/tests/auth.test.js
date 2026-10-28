import test from "node:test";
import assert from "node:assert/strict";
import { registerStudent } from "../src/services/auth.js";

const datos = {
  nombre: " Santi ",
  email: " santi@example.com ",
  password: "ClavePrueba123!",
};

test("envía el JSON acordado y acepta un registro correcto", async (t) => {
  const usuario = {
    id: 1,
    nombre: "Santi",
    email: "santi@example.com",
  };

  t.mock.method(globalThis, "fetch", async (url, options) => {
    assert.equal(url, "/api/auth/register");
    assert.equal(options.method, "POST");
    assert.equal(options.headers["Content-Type"], "application/json");

    assert.deepEqual(JSON.parse(options.body), {
      nombre: "Santi",
      email: "santi@example.com",
      password: datos.password,
    });

    return Response.json(usuario, { status: 201 });
  });

  assert.deepEqual(await registerStudent(datos), usuario);
});

test("muestra los errores de validación del servidor", async (t) => {
  t.mock.method(globalThis, "fetch", async () =>
    Response.json(
      {
        message: "Datos inválidos.",
        errors: {
          password: "La contraseña debe tener al menos 8 caracteres.",
        },
      },
      { status: 400 }
    )
  );

  await assert.rejects(
    registerStudent(datos),
    /Contraseña: La contraseña debe tener al menos 8 caracteres/
  );
});

test("gestiona el correo duplicado aunque la respuesta esté vacía", async (t) => {
  t.mock.method(
    globalThis,
    "fetch",
    async () => new Response(null, { status: 409 })
  );

  await assert.rejects(
    registerStudent(datos),
    /Ya existe una cuenta con ese correo/
  );
});

test("no confunde una página de login con un registro correcto", async (t) => {
  t.mock.method(
    globalThis,
    "fetch",
    async () => new Response("<html>Login</html>", { status: 200 })
  );

  await assert.rejects(
    registerStudent(datos),
    /No se ha podido completar el registro/
  );
});

test("muestra un error cuando falla la conexión", async (t) => {
  t.mock.method(globalThis, "fetch", async () => {
    throw new TypeError("fetch failed");
  });

  await assert.rejects(
    registerStudent(datos),
    /No se ha podido conectar con el servicio de registro/
  );
});