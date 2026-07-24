import { expect, test } from '@playwright/test';

test('operator can filter dispatch workspace parcels by SLA dimension', async ({ page }) => {
    await page.goto('/');
    await page.getByPlaceholder('Username').fill('opsadmin');
    await page.getByPlaceholder('Password').fill('test');
    await page.getByRole('button', { name: 'Sign in' }).click();
    
    await expect(page.getByText('Today’s operating journey')).toBeVisible();
    await page.getByRole('menuitem', { name: '3 Dispatch' }).click();
    await page.getByText('2. Assign parcels', { exact: true }).click();
    
    // 1. Verify SLA Dimension Filter exists
    await expect(page.getByText('⚡ SLA 时效维度:')).toBeVisible();
    const slaSelect = page.locator('.ant-select', { hasText: '全部待派包裹' });
    await expect(slaSelect).toBeVisible();
    
    // 2. Open SLA dropdown and switch to "Today Due / Express"
    await slaSelect.click();
    await page.locator('.ant-select-item-option', { hasText: '仅特快件/今日到期' }).click();
    
    // 3. Verify Map and queried parcel tags react
    await expect(page.getByText(/Queried/i)).toBeVisible();
    
    // 4. Switch to "Standard"
    await page.locator('.ant-select', { hasText: '仅特快件/今日到期' }).click();
    await page.locator('.ant-select-item-option', { hasText: '仅常规标快件' }).click();
    
    // 5. Verify Legend shows Express Magenta tag
    await expect(page.getByText('⚡ 特快/加急件')).toBeVisible();
    
    await page.screenshot({ path: 'artifacts/sla-dispatch-filter.png', fullPage: true });
});
