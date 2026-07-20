import { useState } from 'react';
import {
    Alert, Button, Card, Form, Input, Layout, Menu, Select, Space, Spin, Statistic, Table, Typography,
} from 'antd';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api, type Session } from './api/client';
import { allowedPages, type PageKey } from './auth/permissions';
import { useAuth } from './auth/session';
import { DispatchWorkspace } from './workflows/DispatchWorkspace';
import { ManifestWorkspace } from './workflows/ManifestWorkspace';

const { Header, Sider, Content } = Layout;

export function App() {
    const auth = useAuth();
    return auth.session ? <Workspace /> : <Login />;
}

function Login() {
    const auth = useAuth();
    const [error, setError] = useState('');

    return <main className="login">
        <Card title="OpenDelivery Operations">
            <Form onFinish={async (values) => {
                try {
                    setError('');
                    await auth.login(values.username, values.password);
                } catch (caught) {
                    setError(caught instanceof Error ? caught.message : 'Login failed');
                }
            }}>
                <Form.Item name="username" rules={[{ required: true }]}>
                    <Input placeholder="Username" />
                </Form.Item>
                <Form.Item name="password" rules={[{ required: true }]}>
                    <Input.Password placeholder="Password" />
                </Form.Item>
                {error && <Alert type="error" message={error} />}
                <Button htmlType="submit" type="primary" block>Sign in</Button>
            </Form>
        </Card>
    </main>;
}

function Workspace() {
    const { session, logout } = useAuth();
    const queryClient = useQueryClient();
    const pages = allowedPages(session!.user.roles);
    const [page, setPage] = useState<PageKey>(pages[0]);
    const [station, setStation] = useState(session!.user.stationCode ?? '');
    const stations = useQuery({
        queryKey: ['stations'],
        queryFn: () => api<Record<string, string>[]>('/ops/v1/stations', session!),
    });

    function changeStation(nextStation: string) {
        setStation(nextStation);
        queryClient.removeQueries({ predicate: (query) => query.queryKey[0] !== 'stations' });
    }

    return <Layout className="shell">
        <Sider>
            <Typography.Title level={4} className="brand">OpenDelivery</Typography.Title>
            <Menu
                theme="dark"
                selectedKeys={[page]}
                onClick={(event) => setPage(event.key as PageKey)}
                items={pages.map((key) => ({ key, label: key[0].toUpperCase() + key.slice(1) }))}
            />
        </Sider>
        <Layout>
            <Header className="top">
                <Space>
                    <Select
                        aria-label="Station"
                        value={station}
                        onChange={changeStation}
                        options={(stations.data ?? []).map((item) => ({
                            value: item.station_code,
                            label: item.station_code,
                        }))}
                    />
                    <span>{session!.user.displayName}</span>
                    <Button onClick={logout}>Sign out</Button>
                </Space>
            </Header>
            <Content className="content"><Page page={page} station={station} /></Content>
        </Layout>
    </Layout>;
}

function Page({ page, station }: { page: PageKey; station: string }) {
    const { session } = useAuth();
    if (page === 'manifests') return <ManifestWorkspace session={session!} station={station} />;
    if (page === 'dispatch') return <DispatchWorkspace session={session!} station={station} />;
    return <ReadPage page={page} station={station} session={session!} />;
}

function ReadPage({ page, station, session }: { page: PageKey; station: string; session: Session }) {
    const path = page === 'dashboard' ? '/ops/v1/readiness'
        : page === 'cases' ? '/ops/v1/cases' : null;
    const query = useQuery({
        queryKey: [page, station],
        queryFn: () => api<unknown>(path!, session, {}, station),
        enabled: Boolean(path && station),
    });

    if (!path) return <Card title={page}>This pilot-critical workspace is connected in the next vertical slice.</Card>;
    if (query.isLoading) return <Spin />;
    if (query.error) return <Alert type="error" message={query.error.message} />;
    if (page === 'dashboard') {
        const data = query.data as Record<string, number | boolean>;
        return <Space wrap>{Object.entries(data).map(([key, value]) =>
            <Card key={key}><Statistic title={key} value={String(value)} /></Card>)}</Space>;
    }
    const rows = Array.isArray(query.data) ? query.data as Record<string, unknown>[] : [];
    const keys = rows.length ? Object.keys(rows[0]) : [];
    return <Card title={page}>
        <Table<Record<string, unknown>>
            rowKey={(row) => String(row.id ?? row.caseNo ?? row.tracking_no)}
            dataSource={rows}
            columns={keys.slice(0, 8).map((key) => ({ title: key, dataIndex: key }))}
            pagination={false}
        />
    </Card>;
}
