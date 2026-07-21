import { expect, test } from '@playwright/test';

test('operator follows the business-day journey into a visible parcel map', async ({ page }) => {
    await page.goto('/');
    await page.getByPlaceholder('Username').fill('opsadmin');
    await page.getByPlaceholder('Password').fill('test');
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page.getByText('Today’s operating journey')).toBeVisible();
    await expect(page.getByText('Expected orders')).toBeVisible();
    await expect(page.getByText('Next actions')).toBeVisible();
    await page.getByRole('menuitem', { name: '3 Dispatch' }).click();
    await expect(page.getByLabel('Parcel planning map')).toBeVisible();
    await expect(page.getByText('Locatable 69')).toBeVisible();
    await expect(page.getByText('Displayed 69')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Fit all' })).toBeEnabled({ timeout: 15_000 });
    await expect(page.locator('.planning-map .gm-style')).toBeVisible({ timeout: 15_000 });
    const parcelMarkers=page.locator('.planning-map [title^="DEMO-R02-YHZ"]');
    expect(await parcelMarkers.count()).toBeGreaterThan(0);
    await page.locator('.planning-map [title="DEMO-R02-YHZ-00067"]').click({ force: true });
    await expect(page.getByText('parcel_id', { exact: true })).toBeVisible();
    await page.screenshot({ path: 'artifacts/control-tower-map.png', fullPage: true });
});

test('station switch removes old control-tower context', async ({ page }) => {
    await page.goto('/');
    await page.getByPlaceholder('Username').fill('opsadmin');
    await page.getByPlaceholder('Password').fill('test');
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page.getByRole('heading', { name: 'Halifax Last Mile Station' })).toBeVisible();
    await page.locator('.ant-select[aria-label="Station"]').click();
    await page.locator('.ant-select-item-option[title="YYZ-01"]').click();
    await expect(page.getByRole('heading', { name: 'Toronto Last Mile Station' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Halifax Last Mile Station' })).not.toBeVisible();
    await page.locator('.ant-select[aria-label="Station"]').click();
    await page.locator('.ant-select-item-option[title="YVR-01"]').click();
    await expect(page.getByRole('heading', { name: 'Vancouver Last Mile Station' })).toBeVisible();
    await page.screenshot({ path: 'artifacts/control-tower-vancouver.png', fullPage: true });
});
