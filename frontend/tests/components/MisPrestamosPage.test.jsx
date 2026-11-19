import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, it, vi } from 'vitest';
import MisPrestamosPage from '../../src/components/MisPrestamosPage.jsx';
import { getMyLoans, renewLoan, returnLoan } from '../../src/services/loans.js';

const sesion = vi.hoisted(() => ({ expireSession: vi.fn() }));
vi.mock('../../src/auth/AuthContext.js', () => ({ useAuth: () => sesion }));
vi.mock('../../src/services/loans.js', () => ({
  getMyLoans: vi.fn(), renewLoan: vi.fn(), returnLoan: vi.fn(), formatDueDate: date => date,
}));
beforeEach(() => vi.resetAllMocks());
const activo = { id: 1, titulo: 'Libro', estado: 'ACTIVO', fechaPrestamo: '2026-01-01', fechaVencimiento: '2026-01-31', numeroRenovaciones: 0 };

it('muestra carga y las listas vacías', async () => {
  let resolver;
  getMyLoans.mockReturnValue(new Promise(resolve => { resolver = resolve; }));
  render(<MisPrestamosPage />);
  expect(screen.getByText('Cargando préstamos…')).toBeVisible();
  expect(screen.getByRole('button', { name: 'Actualizar' })).toBeDisabled();
  resolver({ activos: [], historial: [] });
  expect(await screen.findByText('No tienes préstamos activos.')).toBeVisible();
  expect(screen.getByText('Todavía no tienes devoluciones.')).toBeVisible();
});

it('muestra error y permite reintentar la consulta', async () => {
  getMyLoans.mockRejectedValueOnce(new Error('Sin conexión')).mockResolvedValueOnce({ activos: [], historial: [] });
  const user = userEvent.setup(); render(<MisPrestamosPage />);
  expect(await screen.findByRole('alert')).toHaveTextContent('Sin conexión');
  await user.click(screen.getByRole('button', { name: 'Reintentar' }));
  expect(await screen.findByText('No tienes préstamos activos.')).toBeVisible();
});

it('tras devolver actualiza la interfaz y conserva el historial', async () => {
  getMyLoans.mockResolvedValueOnce({ activos: [activo], historial: [] })
    .mockResolvedValueOnce({ activos: [], historial: [{ ...activo, estado: 'DEVUELTO', fechaDevolucion: '2026-01-15' }] });
  returnLoan.mockResolvedValue({ fechaDevolucion: '2026-01-15' });
  const user = userEvent.setup(); render(<MisPrestamosPage />);
  await user.click(await screen.findByRole('button', { name: 'Devolver «Libro»' }));
  await user.click(screen.getByRole('button', { name: 'Sí, devolver' }));
  expect(await screen.findByText('No tienes préstamos activos.')).toBeVisible();
  expect(screen.getByText('DEVUELTO')).toBeVisible();
  expect(returnLoan).toHaveBeenCalledWith(1);
  expect(renewLoan).not.toHaveBeenCalled();
});
