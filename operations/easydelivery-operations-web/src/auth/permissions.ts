export const PAGE_KEYS = ['dashboard','orders','dispatch','manifests','scanning','handover','delivery','closeout','cases','areas','drivers','stations','callbacks'] as const;
export type PageKey = typeof PAGE_KEYS[number];

const ROLE_PAGES: Record<string, readonly PageKey[]> = {
    ADMIN: PAGE_KEYS,
    SUPERVISOR: PAGE_KEYS,
    INBOUND: ['dashboard','orders','manifests','scanning','handover','delivery','closeout','cases'],
    DISPATCHER: ['dashboard','orders','dispatch','delivery','closeout','cases','areas','drivers'],
};

export function allowedPages(roles: readonly string[]): PageKey[] {
    const permitted = new Set(roles.flatMap((role) => ROLE_PAGES[role] ?? []));
    return PAGE_KEYS.filter((page) => permitted.has(page));
}
