import { expect, test } from '@playwright/test';

// Read-only UI regression for the R03-C arrival batch workbench.
// Create/state flows with data assertions live in scripts/arrival-batch-e2e.sh (API level),
// so this spec never mutates the shared development database.
test('arrival batch workbench renders with auto batch-number guidance', async ({ page }) => {
    await page.goto('/');
    await page.getByPlaceholder('Username').fill('opsadmin');
    await page.getByPlaceholder('Password').fill('test');
    await page.getByRole('button', { name: 'Sign in' }).click();
    await page.getByRole('menuitem', { name: '4 Manifests' }).click();
    await expect(page.getByText('Physical arrivals')).toBeVisible();
    await expect(page.getByText(/Arrival records physical containers only/)).toBeVisible();
    await expect(page.getByRole('button', { name: 'Create arrival batch' })).toBeVisible();
    await page.getByRole('button', { name: 'Create arrival batch' }).click();
    const drawer = page.locator('.ant-drawer');
    await expect(drawer.getByText('Arrival batch no.')).toBeVisible();
    await expect(drawer.getByText('Leave blank to auto-generate the batch number')).toBeVisible();
    await expect(drawer.getByText('Vehicle plate')).toBeVisible();
    await page.screenshot({ path: 'artifacts/arrival-workbench.png', fullPage: true });
    await page.keyboard.press('Escape');
});

test('arrival workbench renders in zh-CN', async ({ page }) => {
    await page.addInitScript(() => localStorage.setItem('opendelivery.locale', 'zh-CN'));
    await page.goto('/');
    await page.getByPlaceholder('用户名').fill('opsadmin');
    await page.getByPlaceholder('密码').fill('test');
    await page.getByRole('button', { name: /登\s*录/ }).click();
    await page.getByRole('menuitem', { name: '4 到货清单' }).click();
    await expect(page.getByText('到仓实物')).toBeVisible();
    await page.getByRole('button', { name: '创建到仓批次' }).click();
    const drawer = page.locator('.ant-drawer');
    await expect(drawer.getByText('留空则自动生成批次号')).toBeVisible();
    await page.keyboard.press('Escape');
});
