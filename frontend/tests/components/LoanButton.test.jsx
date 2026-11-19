import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, it, vi } from 'vitest';
import LoanButton from '../../src/components/LoanButton.jsx';
import { requestLoan } from '../../src/services/loans.js';

vi.mock('../../src/services/loans.js', () => ({ requestLoan: vi.fn() }));
beforeEach(() => vi.resetAllMocks());
const libro = { id: 12, titulo: 'Libro de prueba', disponible: true };

it('oculta la acción si no hay ejemplares', () => {
  render(<LoanButton book={{ ...libro, disponible: false }} onLoaned={vi.fn()} onError={vi.fn()} />);
  expect(screen.queryByRole('button')).not.toBeInTheDocument();
});

it('pide confirmación y permite cancelar sin enviar', async () => {
  const user = userEvent.setup();
  render(<LoanButton book={libro} onLoaned={vi.fn()} onError={vi.fn()} />);
  await user.click(screen.getByRole('button', { name: /Pedir préstamo/ }));
  expect(screen.getByRole('alert')).toHaveTextContent('30 días');
  await user.click(screen.getByRole('button', { name: 'Cancelar' }));
  expect(requestLoan).not.toHaveBeenCalled();
});

it('deshabilita acciones mientras solicita y notifica el resultado', async () => {
  let resolver;
  requestLoan.mockReturnValue(new Promise((resolve) => { resolver = resolve; }));
  const onLoaned = vi.fn();
  const user = userEvent.setup();
  render(<LoanButton book={libro} onLoaned={onLoaned} onError={vi.fn()} />);
  await user.click(screen.getByRole('button', { name: /Pedir préstamo/ }));
  await user.click(screen.getByRole('button', { name: 'Sí, pedir' }));
  expect(screen.getByRole('button', { name: 'Solicitando…' })).toBeDisabled();
  expect(screen.getByRole('button', { name: 'Cancelar' })).toBeDisabled();
  resolver({ id: 99 });
  expect(await screen.findByRole('button', { name: /Pedir préstamo/ })).toBeEnabled();
  expect(onLoaned).toHaveBeenCalledWith({ id: 99 }, libro);
});

it('notifica el error sin anunciar éxito', async () => {
  const error = new Error('Sin ejemplares');
  requestLoan.mockRejectedValue(error);
  const onLoaned = vi.fn(); const onError = vi.fn(); const user = userEvent.setup();
  render(<LoanButton book={libro} onLoaned={onLoaned} onError={onError} />);
  await user.click(screen.getByRole('button', { name: /Pedir préstamo/ }));
  await user.click(screen.getByRole('button', { name: 'Sí, pedir' }));
  expect(await screen.findByRole('button', { name: /Pedir préstamo/ })).toBeEnabled();
  expect(onError).toHaveBeenCalledWith(error, libro);
  expect(onLoaned).not.toHaveBeenCalled();
});
