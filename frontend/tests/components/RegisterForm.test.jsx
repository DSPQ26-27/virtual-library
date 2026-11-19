import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import RegisterForm from '../../src/components/RegisterForm.jsx';
import { registerStudent } from '../../src/services/auth.js';

vi.mock('../../src/services/auth.js', () => ({ registerStudent: vi.fn() }));

describe('Registro de estudiantes', () => {
  beforeEach(() => vi.resetAllMocks());

  async function rellenar(user) {
    await user.type(screen.getByLabelText('Nombre'), '  Ana  ');
    await user.type(screen.getByLabelText('Correo electrónico'), 'ana@example.com');
    await user.type(screen.getByLabelText('Contraseña'), 'clave1234');
  }

  it('muestra progreso y confirma el registro limpiando los campos', async () => {
    let resolver;
    registerStudent.mockReturnValue(new Promise((resolve) => { resolver = resolve; }));
    const user = userEvent.setup();
    render(<RegisterForm />);
    await rellenar(user);
    await user.click(screen.getByRole('button', { name: 'Crear cuenta' }));
    expect(screen.getByRole('button', { name: 'Creando cuenta…' })).toBeDisabled();
    expect(registerStudent).toHaveBeenCalledWith({ nombre: 'Ana', email: 'ana@example.com', password: 'clave1234' });
    resolver({});
    expect(await screen.findByText('Tu cuenta se ha creado correctamente.')).toBeVisible();
    expect(screen.getByLabelText('Nombre')).toHaveValue('');
    expect(screen.getByLabelText('Contraseña')).toHaveValue('');
  });

  it('muestra el error de registro y permite corregirlo', async () => {
    registerStudent.mockRejectedValue(new Error('Correo ya registrado'));
    const user = userEvent.setup();
    render(<RegisterForm />);
    await rellenar(user);
    await user.click(screen.getByRole('button', { name: 'Crear cuenta' }));
    expect(await screen.findByText('Correo ya registrado')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Crear cuenta' })).toBeEnabled();
    await user.type(screen.getByLabelText('Nombre'), 'x');
    expect(screen.queryByText('Correo ya registrado')).not.toBeInTheDocument();
  });
});
