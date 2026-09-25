import AxeBuilder from '@axe-core/playwright';
import {expect, test} from '@playwright/test';

test('authenticated catalog browse and search is keyboard accessible', async ({page}) => {
  await page.goto('/modern/catalog');
  await expect(page.getByRole('heading', {name: 'Demo catalog'})).toBeVisible();
  await expect(page.getByText('Tiny Gizmo')).toBeVisible();
  await expect(page.locator('[data-experience="modern"]')).toHaveCount(0);

  const accessibility = await new AxeBuilder({page}).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  expect(accessibility.violations).toEqual([]);

  const query = page.getByRole('textbox', {name: 'Search products'});
  await query.focus();
  await query.fill('gizmo');
  await query.press('Enter');
  await expect(page.getByRole('heading', {name: 'Search results for “gizmo”'})).toBeVisible();

  const productButton = page.getByRole('button', {name: /Tiny Gizmo/});
  await productButton.focus();
  await productButton.press('Enter');
  await expect(page.locator('#product-detail h2')).toBeFocused();
});

test('disabled catalog route falls back to legacy OFBiz', async ({page}) => {
  await page.goto('/modern/catalog?scenario=disabled');
  await expect(page).toHaveURL(/\/auth\/legacy$/);
  await expect(page.getByRole('heading', {name: 'Legacy OFBiz'})).toBeVisible();
});

test('catalog service failure is sanitized and offers explicit fallback', async ({page}) => {
  await page.goto('/modern/catalog?scenario=failure');
  await expect(page.getByRole('heading', {name: 'Temporarily unavailable'})).toBeVisible();
  await expect(page.getByRole('alert')).toHaveText('The catalog service did not respond.');
  await expect(page.getByRole('link', {name: 'Continue to legacy OFBiz'})).toHaveAttribute('href', '/auth/legacy');
  await expect(page.getByText('upstream_unavailable')).toHaveCount(0);
});
