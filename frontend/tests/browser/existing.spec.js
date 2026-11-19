import { test } from '@playwright/test';
import catalogo from '../catalog.browser.mjs';
import ejemplares from '../ejemplares.browser.mjs';
import renovacion from '../renewLoan.browser.mjs';
import espera from '../waitlist.browser.mjs';

for (const [name, scenario] of Object.entries({ catalogo, ejemplares, renovacion, espera })) {
  test(`escenario existente con API simulada: ${name}`, async ({ page, baseURL }) => {
    await scenario(page, baseURL);
  });
}
