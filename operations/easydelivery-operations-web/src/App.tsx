import { lazy, Suspense, useState } from 'react';
import {
    Alert, Badge, Button, Card, DatePicker, Form, Input, Layout, Menu, Select, Space, Spin, Table, Typography, App as AntdApp
} from 'antd';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api, type Session } from './api/client';
import { allowedPages, type PageKey } from './auth/permissions';
import { useAuth } from './auth/session';
import { useTranslation } from 'react-i18next';
import { changeLocale, SUPPORTED_LOCALES, type SupportedLocale } from './i18n';
import dayjs from 'dayjs';
import { TodayWorkspace } from './workflows/TodayWorkspace';
import { ShipmentDetailDrawer } from './workflows/ShipmentDetailDrawer';

const AreaWorkspace = lazy(() => import('./workflows/AreaWorkspace').then((module) => ({ default: module.AreaWorkspace })));
const DispatchWorkspace = lazy(() => import('./workflows/DispatchWorkspace').then((module) => ({ default: module.DispatchWorkspace })));
const DispatchReassignWorkspace = lazy(() => import('./workflows/DispatchReassignWorkspace').then((module) => ({ default: module.DispatchReassignWorkspace })));
const ArrivalWorkspace = lazy(() => import('./workflows/ArrivalWorkspace').then((module) => ({ default: module.ArrivalWorkspace })));
const OrderReadinessWorkspace = lazy(() => import('./workflows/OrderReadinessWorkspace').then((module) => ({ default: module.OrderReadinessWorkspace })));
const FailedReturnWorkspace = lazy(() => import('./workflows/FailedReturnWorkspace').then((module) => ({ default: module.FailedReturnWorkspace })));
const ScanSupervisionWorkspace = lazy(() => import('./workflows/ScanSupervisionWorkspace').then((module) => ({ default: module.ScanSupervisionWorkspace })));
const HandoverApprovalWorkspace = lazy(() => import('./workflows/HandoverApprovalWorkspace').then((module) => ({ default: module.HandoverApprovalWorkspace })));
const DayCloseWorkspace = lazy(() => import('./workflows/DayCloseWorkspace').then((module) => ({ default: module.DayCloseWorkspace })));
const CaseCenterWorkspace = lazy(() => import('./workflows/CaseCenterWorkspace').then((module) => ({ default: module.CaseCenterWorkspace })));


const { Header, Sider, Content } = Layout;

export function App() {
    const auth = useAuth();
    return (
        <AntdApp>
            {auth.session ? <Workspace /> : <Login />}
        </AntdApp>
    );
}

function Login() {
    const auth = useAuth();
    const { t, i18n } = useTranslation();
    const [error, setError] = useState('');

    return <main className="login">
        <Card title={<Space><i className="fa-solid fa-truck-fast" style={{ color: '#1677ff' }}></i><span>{t('app.title')}</span><span style={{ fontSize: '12px', background: '#1677ff', color: '#fff', padding: '2px 8px', borderRadius: '10px' }}>v0.9.0</span></Space>}>




            <Select aria-label={t('locale.label')} value={i18n.language as SupportedLocale} style={{ width: '100%', marginBottom: 16 }}
                onChange={(value: SupportedLocale) => void changeLocale(value)}
                options={SUPPORTED_LOCALES.map((value) => ({ value, label: value }))} />
            <Form onFinish={async (values) => {
                try {
                    setError('');
                    await auth.login(values.username, values.password);
                } catch (caught) {
                    setError(caught instanceof Error ? caught.message : t('auth.failed'));
                }
            }}>
                <Form.Item name="username" rules={[{ required: true }]}>
                    <Input placeholder={t('auth.username')} />
                </Form.Item>
                <Form.Item name="password" rules={[{ required: true }]}>
                    <Input.Password placeholder={t('auth.password')} />
                </Form.Item>
                {error && <Alert type="error" message={error} />}
                <Button htmlType="submit" type="primary" block>{t('auth.signIn')}</Button>
            </Form>
        </Card>
    </main>;
}

const PAGE_ICONS: Record<string, string> = {
    dashboard: 'fa-solid fa-chart-line',
    orders: 'fa-solid fa-boxes-packing',
    dispatch: 'fa-solid fa-route',
    'dispatch-reassign': 'fa-solid fa-boxes-stacked',
    manifests: 'fa-solid fa-file-invoice',
    scanning: 'fa-solid fa-barcode',
    handover: 'fa-solid fa-signature',
    delivery: 'fa-solid fa-location-dot',
    closeout: 'fa-solid fa-calendar-check',
    cases: 'fa-solid fa-circle-exclamation',
    areas: 'fa-solid fa-draw-polygon',
    drivers: 'fa-solid fa-id-card',
    stations: 'fa-solid fa-warehouse',
    callbacks: 'fa-solid fa-network-wired',
};

function Workspace() {
    const { session, logout } = useAuth();
    const { t, i18n } = useTranslation();
    const queryClient = useQueryClient();
    const pages = allowedPages(session!.user.roles);
    const initial = new URLSearchParams(location.hash.slice(1));
    const [page, setPage] = useState<PageKey>(pages.includes(initial.get('page') as PageKey) ? initial.get('page') as PageKey : 'dashboard');
    const [filter, setFilter] = useState(initial.get('filter') ?? '');
    const [serviceDate, setServiceDate] = useState(initial.get('date') ?? dayjs().format('YYYY-MM-DD'));
    const [stationId, setStationId] = useState<number>(session!.user.stationId ?? 1);
    const [searchTrackingNo, setSearchTrackingNo] = useState<string | null>(null);

    const stations = useQuery({
        queryKey: ['stations'],
        queryFn: () => api<Array<{ id: number; station_code: string; station_name: string }>>('/ops/v1/stations', session!),
    });
    const navigation = useQuery({
        queryKey: ['control-tower', stationId, serviceDate],
        queryFn: () => api<{ stages: Array<{ target: PageKey; blockers: number }> }>(`/ops/v1/control-tower?serviceDate=${serviceDate}`, session!, {}, stationId),
        enabled: Boolean(stationId)
    });

    function changeStation(nextStationId: number) {
        setStationId(nextStationId);
        queryClient.removeQueries({ predicate: (query) => query.queryKey[0] !== 'stations' });
    }
    function navigate(nextPage: PageKey, nextFilter = '') {
        setPage(nextPage);
        setFilter(nextFilter);
        location.hash = new URLSearchParams({ page: nextPage, date: serviceDate, ...(nextFilter ? { filter: nextFilter } : {}) }).toString();
    }
    const daily: PageKey[] = ['dashboard', 'orders', 'dispatch', 'dispatch-reassign', 'manifests', 'scanning', 'handover', 'delivery', 'closeout'];
    const exception: PageKey[] = ['cases'];
    const configuration: PageKey[] = ['areas', 'drivers', 'stations', 'callbacks'];
    const blocker = (key: PageKey) => navigation.data?.stages.find(stage => stage.target === key)?.blockers ?? 0;
    const available = new Set<PageKey>(['dashboard', 'orders', 'dispatch', 'dispatch-reassign', 'manifests', 'scanning', 'handover', 'delivery', 'closeout', 'cases', 'areas', 'drivers', 'stations']);

    const item = (key: PageKey, index?: number, displayIndex?: number | string) => ({
        key,
        disabled: !available.has(key),
        label: (
            <span className="menu-label">
                <i className={PAGE_ICONS[key] ?? 'fa-solid fa-circle'} style={{ fontSize: '13px', width: '18px', opacity: 0.85 }}></i>
                {(displayIndex != null || index != null) && <span>{displayIndex ?? (index! + 1)}</span>}{' '}
                <em>{t(`nav.${key}`)}{!available.has(key) ? ` · ${t('common.planned')}` : ''}</em>
                {blocker(key) > 0 && available.has(key) && <Badge count={blocker(key)} overflowCount={99} />}
            </span>
        )
    });


    const menuItems = [
        {
            type: 'group' as const,
            label: t('nav.group.daily'),
            children: [
                item('dashboard', 0),
                item('orders', 1),
                {
                    key: 'dispatch-parent',
                    label: (
                        <span className="menu-label">
                            <i className="fa-solid fa-route" style={{ fontSize: '13px', width: '18px', opacity: 0.85 }}></i>
                            <span>3</span>
                            <em>{t('nav.dispatch.group')}</em>
                        </span>
                    ),
                    children: [
                        item('dispatch', undefined),
                        item('dispatch-reassign', undefined),
                    ],
                },
                item('manifests', 3, 4),
                item('scanning', 4),
                item('handover', 5),
                item('delivery', 6),
                item('closeout', 7),
            ].filter((m: any) => {
                if (m.children) return true;
                return pages.includes(m.key as PageKey);
            })
        },
        { type: 'group' as const, label: t('nav.group.exceptions'), children: exception.filter(k => pages.includes(k)).map(key => item(key)) },
        { type: 'group' as const, label: t('nav.group.configuration'), children: configuration.filter(k => pages.includes(k)).map(key => item(key)) }
    ];



    return <Layout className="shell">
        <Sider width={230}>
            <Typography.Title level={4} className="brand" style={{ display: 'flex', alignItems: 'center', gap: '8px', padding: '16px 20px', margin: 0 }}>
                <i className="fa-solid fa-truck-fast" style={{ color: '#1677ff' }}></i>
                <span>OpenDelivery</span>
                <span style={{ fontSize: '11px', background: '#1677ff', padding: '2px 6px', borderRadius: '4px', color: '#ffffff', fontWeight: 'bold' }}>v0.9.0</span>





            </Typography.Title>
            <Menu
                theme="dark"
                selectedKeys={[page]}
                onClick={(event) => navigate(event.key as PageKey)}
                items={menuItems}
            />
        </Sider>
        <Layout>
            <Header className="top" style={{ padding: '0 20px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', background: '#fff', borderBottom: '1px solid #e8e8e8' }}>
                <Space size="middle">
                    <Select
                        aria-label={t('station.label')}
                        value={stationId}
                        onChange={changeStation}
                        options={(stations.data ?? []).map((item) => ({
                            value: item.id,
                            label: `${item.station_code} - ${item.station_name}`,
                            title: item.station_code,
                        }))}
                        style={{ width: 220 }}
                    />

                    <DatePicker value={dayjs(serviceDate)} onChange={(_, dateString) => setServiceDate(typeof dateString === 'string' ? dateString : serviceDate)} />
                </Space>

                {/* 全局快捷搜索框 */}
                <div style={{ width: 320 }}>
                    <Input
                        prefix={<i className="fa-solid fa-magnifying-glass" style={{ color: '#bfbfbf', marginRight: 4 }}></i>}
                        placeholder="🔍 全局搜索：单号 / 追踪号 / 电话"
                        allowClear
                        onPressEnter={(e: any) => {
                            const val = e.target.value?.trim();
                            if (val) setSearchTrackingNo(val);
                        }}
                        style={{ borderRadius: 18 }}
                    />
                </div>

                <Space size="middle">
                    <Select value={i18n.language as SupportedLocale} onChange={(value: SupportedLocale) => void changeLocale(value)}
                        options={SUPPORTED_LOCALES.map((value) => ({ value, label: value }))} />
                    <span style={{ fontWeight: 600 }}><i className="fa-solid fa-user-circle" style={{ marginRight: 6, color: '#1677ff' }}></i>{session?.user.username}</span>
                    <Button type="text" danger onClick={() => void logout()}><i className="fa-solid fa-right-from-bracket" style={{ marginRight: 4 }}></i>{t('auth.signOut')}</Button>
                </Space>
            </Header>
            <Content className="body">
                <Page page={page} station={stationId} serviceDate={serviceDate} filter={filter} onNavigate={navigate} />
            </Content>

            {/* 运单与包裹详情 Drawer */}
            <ShipmentDetailDrawer
                trackingNo={searchTrackingNo}
                station={stationId}
                session={session!}
                onClose={() => setSearchTrackingNo(null)}
            />
        </Layout>
    </Layout>;
}


const SystemConfigWorkspace = lazy(() => import('./workflows/SystemConfigWorkspace').then((module) => ({ default: module.SystemConfigWorkspace })));

function Page({ page, station,serviceDate,filter,onNavigate }: { page: PageKey; station: number|string;serviceDate:string;filter:string;onNavigate:(page:PageKey,filter?:string)=>void }) {
    const { session } = useAuth();
    let content;
    if (page === 'areas') content = <AreaWorkspace key={station} session={session!} station={station} />;
    else if (page === 'drivers' || page === 'stations') content = <SystemConfigWorkspace key={station} session={session!} station={station} />;
    else if (page === 'manifests') content = <ArrivalWorkspace session={session!} station={station} serviceDate={serviceDate} onNavigate={onNavigate}/>;
    else if (page === 'dispatch') content = <DispatchWorkspace key={`${station}-${serviceDate}-${filter}`} session={session!} station={station} initialDate={serviceDate} initialFilter={filter}/>;
    else if (page === 'dispatch-reassign') content = <DispatchReassignWorkspace key={`${station}-${serviceDate}`} session={session!} station={station} serviceDate={serviceDate}/>;
    else if(page==='dashboard')content=<TodayWorkspace session={session!} station={station} serviceDate={serviceDate} onNavigate={onNavigate}/>;
    else if(page==='orders')content=<OrderReadinessWorkspace session={session!} station={station} serviceDate={serviceDate} initialFilter={filter}/>;
    else if(page==='delivery')content=<FailedReturnWorkspace session={session!} station={station} serviceDate={serviceDate}/>;
    else if(page==='scanning')content=<ScanSupervisionWorkspace session={session!} station={station} serviceDate={serviceDate}/>;

    else if(page==='handover')content=<HandoverApprovalWorkspace session={session!} station={station} serviceDate={serviceDate}/>;
    else if(page==='closeout')content=<DayCloseWorkspace session={session!} station={station} serviceDate={serviceDate}/>;
    else if(page==='cases')content=<CaseCenterWorkspace session={session!} station={station}/>;
    else content = <ReadPage page={page} station={station} session={session!} />;
    return <Suspense fallback={<Spin />}>{content}</Suspense>;
}


function ReadPage({ page, station, session }: { page: PageKey; station: number | string; session: Session }) {

    const { t } = useTranslation();
    const path = page === 'cases' ? '/ops/v1/cases' : null;
    const query = useQuery({
        queryKey: [page, station],
        queryFn: () => api<unknown>(path!, session, {}, station),
        enabled: Boolean(path && station),
    });

    if (!path) return <Card title={t(`nav.${page}`)}><Alert type="info" showIcon message={t('common.planned')} description={t('common.notReady')}/></Card>;
    if (query.isLoading) return <Spin />;
    if (query.error) return <Alert type="error" message={query.error.message} />;
    const rows = Array.isArray(query.data) ? query.data as Record<string, unknown>[] : [];
    const keys = rows.length ? Object.keys(rows[0]) : [];
    return <Card title={page}>
        <Table<Record<string, unknown>>
            rowKey={(row) => String(row.id ?? row.caseNo ?? row.tracking_no)}
            dataSource={rows}
            columns={keys.slice(0, 8).map((key) => ({ title: t(`field.${key}`, { defaultValue: key }), dataIndex: key }))}
            pagination={false}
        />
    </Card>;
}
