import { expect, test } from '@playwright/test';

test('operator can navigate to system config workbench and manage drivers', async ({ page }) => {
    await page.goto('/');
    await page.getByPlaceholder('Username').fill('opsadmin');
    await page.getByPlaceholder('Password').fill('test');
    await page.getByRole('button', { name: 'Sign in' }).click();
    
    await expect(page.getByText('Today’s operating journey')).toBeVisible();
    
    // Navigate to Drivers configuration page
    await page.getByRole('menuitem', { name: /Drivers/i }).click();
    
    // Verify System Config Workbench UI
    await expect(page.getByText('⚙️ 站点与司机系统配置中心')).toBeVisible();
    await expect(page.getByText(/Stations & Service Areas/i)).toBeVisible();
    await expect(page.getByRole('button', { name: '+ 新增末端站点' })).toBeVisible();
    
    // Switch to Drivers Tab
    await page.getByRole('tab', { name: /Drivers/i }).click();
    await expect(page.getByRole('button', { name: '+ 新建司机账号' })).toBeVisible();
    
    await page.screenshot({ path: 'artifacts/system-config-workbench.png', fullPage: true });
});
