import { lazy, Suspense, useState } from 'react';
import {
    Alert, Badge, Button, Card, DatePicker, Form, Input, Layout, Menu, Select, Space, Spin, Table, Typography,
} from 'antd';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api, type Session } from './api/client';
import { allowedPages, type PageKey } from './auth/permissions';
import { useAuth } from './auth/session';
import { useTranslation } from 'react-i18next';
import { changeLocale, SUPPORTED_LOCALES, type SupportedLocale } from './i18n';
import dayjs from 'dayjs';
import { TodayWorkspace } from './workflows/TodayWorkspace';

const AreaWorkspace = lazy(() => import('./workflows/AreaWorkspace').then((module) => ({ default: module.AreaWorkspace })));
const DispatchWorkspace = lazy(() => import('./workflows/DispatchWorkspace').then((module) => ({ default: module.DispatchWorkspace })));
const ArrivalWorkspace = lazy(() => import('./workflows/ArrivalWorkspace').then((module) => ({ default: module.ArrivalWorkspace })));
const OrderReadinessWorkspace = lazy(() => import('./workflows/OrderReadinessWorkspace').then((module) => ({ default: module.OrderReadinessWorkspace })));

const { Header, Sider, Content } = Layout;

export function App() {
    const auth = useAuth();
    return auth.session ? <Workspace /> : <Login />;
}

function Login() {
    const auth = useAuth();
    const { t, i18n } = useTranslation();
    const [error, setError] = useState('');

    return <main className="login">
        <Card title={t('app.title')}>
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

function Workspace() {
    const { session, logout } = useAuth();
    const { t, i18n } = useTranslation();
    const queryClient = useQueryClient();
    const pages = allowedPages(session!.user.roles);
    const initial=new URLSearchParams(location.hash.slice(1));
    const [page, setPage] = useState<PageKey>(pages.includes(initial.get('page') as PageKey)?initial.get('page') as PageKey:'dashboard');
    const [filter,setFilter]=useState(initial.get('filter')??'');
    const [serviceDate,setServiceDate]=useState(initial.get('date')??dayjs().format('YYYY-MM-DD'));
    const [station, setStation] = useState(session!.user.stationCode ?? '');
    const stations = useQuery({
        queryKey: ['stations'],
        queryFn: () => api<Record<string, string>[]>('/ops/v1/stations', session!),
    });
    const navigation=useQuery({queryKey:['control-tower',station,serviceDate],queryFn:()=>api<{stages:Array<{target:PageKey;blockers:number}>}>(`/ops/v1/control-tower?serviceDate=${serviceDate}`,session!,{},station),enabled:Boolean(station)});

    function changeStation(nextStation: string) {
        setStation(nextStation);
        queryClient.removeQueries({ predicate: (query) => query.queryKey[0] !== 'stations' });
    }
    function navigate(nextPage:PageKey,nextFilter=''){setPage(nextPage);setFilter(nextFilter);location.hash=new URLSearchParams({page:nextPage,date:serviceDate,...(nextFilter?{filter:nextFilter}:{})}).toString();}
    const daily:PageKey[]=['dashboard','orders','dispatch','manifests','scanning','handover','delivery','closeout'];
    const exception:PageKey[]=['cases'];const configuration:PageKey[]=['areas','drivers','stations','callbacks'];
    const blocker=(key:PageKey)=>navigation.data?.stages.find(stage=>stage.target===key)?.blockers??0;
    const available=new Set<PageKey>(['dashboard','orders','dispatch','manifests','cases','areas']);
    const item=(key:PageKey,index?:number)=>({key,disabled:!available.has(key),label:<span className="menu-label">{index!=null&&<span>{index+1}</span>}<em>{t(`nav.${key}`)}{!available.has(key)?` · ${t('common.planned')}`:''}</em>{blocker(key)>0&&available.has(key)&&<Badge count={blocker(key)} overflowCount={99}/>}</span>});
    const menuItems=[{type:'group' as const,label:t('nav.group.daily'),children:daily.filter(k=>pages.includes(k)).map((key,index)=>item(key,index))},{type:'group' as const,label:t('nav.group.exceptions'),children:exception.filter(k=>pages.includes(k)).map(key=>item(key))},{type:'group' as const,label:t('nav.group.configuration'),children:configuration.filter(k=>pages.includes(k)).map(key=>item(key))}];

    return <Layout className="shell">
        <Sider>
            <Typography.Title level={4} className="brand">OpenDelivery</Typography.Title>
            <Menu
                theme="dark"
                selectedKeys={[page]}
                onClick={(event) => navigate(event.key as PageKey)}
                items={menuItems}
            />
        </Sider>
        <Layout>
            <Header className="top">
                <Space>
                    <Select
                        aria-label={t('station.label')}
                        value={station}
                        onChange={changeStation}
                        options={(stations.data ?? []).map((item) => ({
                            value: item.station_code,
                            label: item.station_code,
                        }))}
                    />
                    <DatePicker value={dayjs(serviceDate)} onChange={value=>{if(value){const next=value.format('YYYY-MM-DD');setServiceDate(next);queryClient.removeQueries({predicate:q=>q.queryKey[0]!=='stations'});}}}/>
                    <Select aria-label={t('locale.label')} value={i18n.language as SupportedLocale} style={{ width: 100 }}
                        options={SUPPORTED_LOCALES.map((value) => ({ value, label: value }))}
                        onChange={async (value: SupportedLocale) => {
                            await changeLocale(value);
                            await api('/ops/auth/me/locale', session!, { method: 'PUT', body: JSON.stringify({ locale: value }) });
                        }} />
                    <span>{session!.user.displayName}</span>
                    <Button onClick={logout}>{t('auth.signOut')}</Button>
                </Space>
            </Header>
            <Content className="content"><Page page={page} station={station} serviceDate={serviceDate} filter={filter} onNavigate={navigate}/></Content>
        </Layout>
    </Layout>;
}

function Page({ page, station,serviceDate,filter,onNavigate }: { page: PageKey; station: string;serviceDate:string;filter:string;onNavigate:(page:PageKey,filter?:string)=>void }) {
    const { session } = useAuth();
    let content;
    if (page === 'areas') content = <AreaWorkspace key={station} session={session!} station={station} />;
    else if (page === 'manifests') content = <ArrivalWorkspace session={session!} station={station} serviceDate={serviceDate}/>;
    else if (page === 'dispatch') content = <DispatchWorkspace key={`${station}-${serviceDate}-${filter}`} session={session!} station={station} initialDate={serviceDate} initialFilter={filter}/>;
    else if(page==='dashboard')content=<TodayWorkspace session={session!} station={station} serviceDate={serviceDate} onNavigate={onNavigate}/>;
    else if(page==='orders')content=<OrderReadinessWorkspace session={session!} station={station} serviceDate={serviceDate} initialFilter={filter}/>;
    else content = <ReadPage page={page} station={station} session={session!} />;
    return <Suspense fallback={<Spin />}>{content}</Suspense>;
}

function ReadPage({ page, station, session }: { page: PageKey; station: string; session: Session }) {
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
