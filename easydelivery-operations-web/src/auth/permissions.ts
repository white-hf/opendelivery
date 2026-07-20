export const PAGE_KEYS = ['dashboard', 'manifests', 'dispatch', 'cases', 'callbacks', 'closeout'] as const;
export type PageKey = typeof PAGE_KEYS[number];

const ROLE_PAGES: Record<string, readonly PageKey[]> = {
    ADMIN: PAGE_KEYS,
    SUPERVISOR: PAGE_KEYS,
    INBOUND: ['dashboard', 'manifests', 'cases', 'closeout'],
    DISPATCHER: ['dashboard', 'dispatch', 'cases', 'closeout'],
};

export function allowedPages(roles: readonly string[]): PageKey[] {
    const permitted = new Set(roles.flatMap((role) => ROLE_PAGES[role] ?? []));
    return PAGE_KEYS.filter((page) => permitted.has(page));
}
