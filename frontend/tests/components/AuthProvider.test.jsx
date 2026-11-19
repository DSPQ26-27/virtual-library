import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, it, vi } from 'vitest';
import AuthProvider from '../../src/auth/AuthProvider.jsx';
import { useAuth } from '../../src/auth/AuthContext.js';
import { getCurrentUser, login, logout } from '../../src/services/session.js';

vi.mock('../../src/services/session.js', () => ({ getCurrentUser: vi.fn(), login: vi.fn(), logout: vi.fn() }));
beforeEach(() => vi.resetAllMocks());

function EstadoSesion() {
  const auth = useAuth();
  return <><p>{auth.status}</p><p>{auth.user?.nombre ?? 'Anónimo'}</p><p>{auth.sessionError}</p>
    <button onClick={() => auth.login({ email: 'a', password: 'b' })}>Entrar</button>
    <button onClick={() => auth.logout()}>Salir</button>
    <button onClick={auth.retrySession}>Reintentar</button></>;
}

it('recupera sesión, inicia y cierra sesión actualizando el estado', async () => {
  getCurrentUser.mockResolvedValue(null);
  login.mockResolvedValue({ nombre: 'Ana' }); logout.mockResolvedValue();
  const user = userEvent.setup();
  render(<AuthProvider><EstadoSesion /></AuthProvider>);
  expect(await screen.findByText('ready')).toBeVisible();
  await user.click(screen.getByRole('button', { name: 'Entrar' }));
  expect(await screen.findByText('Ana')).toBeVisible();
  await user.click(screen.getByRole('button', { name: 'Salir' }));
  expect(await screen.findByText('Anónimo')).toBeVisible();
});

it('permite reintentar tras un error de recuperación de sesión', async () => {
  getCurrentUser.mockRejectedValueOnce(new Error('Sin conexión')).mockResolvedValueOnce({ nombre: 'Ana' });
  const user = userEvent.setup();
  render(<AuthProvider><EstadoSesion /></AuthProvider>);
  expect(await screen.findByText('Sin conexión')).toBeVisible();
  await user.click(screen.getByRole('button', { name: 'Reintentar' }));
  expect(await screen.findByText('Ana')).toBeVisible();
  expect(screen.getByText('ready')).toBeVisible();
});
