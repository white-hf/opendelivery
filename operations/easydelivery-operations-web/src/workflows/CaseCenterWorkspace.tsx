import { useState } from 'react';
import { Button, Card, Drawer, Form, Input, Popconfirm, Space, Spin, Table, Tabs, Tag, Typography, message } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, type Session } from '../api/client';
import { useTranslation } from 'react-i18next';

interface CaseItem {
    id: number;
    case_code: string;
    case_type: string;
    title: string;
    description: string;
    status: string;
    created_at: string;
}

interface OutboxItem {
    id: number;
    aggregate_type: string;
    aggregate_id: number;
    event_type: string;
    event_key: string;
    status: string;
    attempt_count: number;
    last_error: string | null;
    created_at: string;
}

interface AuditLogItem {
    id: number;
    operator_user_id: number | null;
    action_code: string;
    resource_type: string;
    resource_id: string;
    outcome: string;
    reason_text: string | null;
    occurred_at: string;
}

export function CaseCenterWorkspace({ session, station }: { session: Session; station: string }) {
    const { t } = useTranslation();
    const queryClient = useQueryClient();
    const [selectedCaseId, setSelectedCaseId] = useState<number | null>(null);
    const [actionForm] = Form.useForm();

    const casesQuery = useQuery({
        queryKey: ['ops-cases', station],
        queryFn: () => api<CaseItem[]>('/ops/v1/cases', session, {}, station),
    });

    const outboxQuery = useQuery({
        queryKey: ['ops-outbox', station],
        queryFn: () => api<OutboxItem[]>('/ops/v1/outbox?limit=50', session, {}, station),
    });

    const auditQuery = useQuery({
        queryKey: ['ops-audit-logs', station],
        queryFn: () => api<AuditLogItem[]>('/ops/v1/audit-logs?limit=50', session, {}, station),
    });

    const replayMutation = useMutation({
        mutationFn: (eventId: number) => api(`/ops/v1/outbox/${eventId}/replay`, session, { method: 'POST' }, station),
        onSuccess: async () => {
            message.success(t('cases.replayedSuccess'));
            await queryClient.invalidateQueries({ queryKey: ['ops-outbox', station] });
        },
        onError: (err: Error) => message.error(err.message),
    });

    const actionMutation = useMutation({
        mutationFn: (values: { notes: string; newStatus?: string }) =>
            api(`/ops/v1/cases/${selectedCaseId}/actions`, session, { method: 'POST', body: JSON.stringify(values) }, station),
        onSuccess: async () => {
            message.success(t('cases.actionLogged'));
            setSelectedCaseId(null);
            actionForm.resetFields();
            await queryClient.invalidateQueries({ queryKey: ['ops-cases', station] });
        },
        onError: (err: Error) => message.error(err.message),
    });

    if (casesQuery.isLoading) return <Spin />;

    return (
        <Space direction="vertical" size="large" style={{ width: '100%' }}>
            <Card title={t('cases.title')}>
                <Tabs
                    items={[
                        {
                            key: 'cases',
                            label: t('cases.tabCases'),
                            children: (
                                <Table<CaseItem>
                                    rowKey="id"
                                    dataSource={casesQuery.data ?? []}
                                    columns={[
                                        { title: 'Case ID', dataIndex: 'case_code', render: (v, r) => v || `CASE-${r.id}` },
                                        { title: t('cases.type'), dataIndex: 'case_type', render: (v) => <Tag color="blue">{v}</Tag> },
                                        { title: t('cases.caseTitle'), dataIndex: 'title' },
                                        { title: t('cases.status'), dataIndex: 'status', render: (v) => <Tag color={v === 'CLOSED' ? 'default' : 'orange'}>{v}</Tag> },
                                        { title: t('cases.createdAt'), dataIndex: 'created_at' },
                                        {
                                            title: t('common.actions'),
                                            render: (_, record) => (
                                                <Button size="small" type="primary" onClick={() => setSelectedCaseId(record.id)}>
                                                    {t('cases.logAction')}
                                                </Button>
                                            ),
                                        },
                                    ]}
                                />
                            ),
                        },
                        {
                            key: 'outbox',
                            label: t('cases.tabOutbox'),
                            children: (
                                <Table<OutboxItem>
                                    rowKey="id"
                                    dataSource={outboxQuery.data ?? []}
                                    loading={outboxQuery.isLoading}
                                    columns={[
                                        { title: 'Event ID', dataIndex: 'id' },
                                        { title: t('cases.eventType'), dataIndex: 'event_type' },
                                        { title: t('cases.eventKey'), dataIndex: 'event_key' },
                                        {
                                            title: t('cases.status'),
                                            dataIndex: 'status',
                                            render: (status) => {
                                                const color = status === 'ACKNOWLEDGED' ? 'green' : status === 'DEAD_LETTER' ? 'red' : status === 'RETRY' ? 'volcano' : 'blue';
                                                return <Tag color={color}>{status}</Tag>;
                                            },
                                        },
                                        { title: t('cases.attempts'), dataIndex: 'attempt_count' },
                                        { title: t('cases.lastError'), dataIndex: 'last_error', render: (v) => v ? <Typography.Text type="danger" ellipsis={{ tooltip: v }}>{v}</Typography.Text> : '-' },
                                        {
                                            title: t('common.actions'),
                                            render: (_, record) => (
                                                record.status === 'DEAD_LETTER' || record.status === 'RETRY' ? (
                                                    <Popconfirm
                                                        title={t('cases.confirmReplay')}
                                                        onConfirm={() => replayMutation.mutate(record.id)}
                                                    >
                                                        <Button size="small" danger loading={replayMutation.isPending}>
                                                            {t('cases.replay')}
                                                        </Button>
                                                    </Popconfirm>
                                                ) : null
                                            ),
                                        },
                                    ]}
                                />
                            ),
                        },
                        {
                            key: 'audit',
                            label: t('cases.tabAudit'),
                            children: (
                                <Table<AuditLogItem>
                                    rowKey="id"
                                    dataSource={auditQuery.data ?? []}
                                    loading={auditQuery.isLoading}
                                    columns={[
                                        { title: 'Log ID', dataIndex: 'id' },
                                        { title: t('cases.actionCode'), dataIndex: 'action_code', render: (v) => <Tag color="geekblue">{v}</Tag> },
                                        { title: t('cases.resourceType'), dataIndex: 'resource_type' },
                                        { title: t('cases.resourceId'), dataIndex: 'resource_id' },
                                        { title: t('cases.outcome'), dataIndex: 'outcome', render: (v) => <Tag color="green">{v}</Tag> },
                                        { title: t('cases.occurredAt'), dataIndex: 'occurred_at' },
                                    ]}
                                />
                            ),
                        },
                    ]}
                />
            </Card>

            <Drawer open={Boolean(selectedCaseId)} onClose={() => setSelectedCaseId(null)} width={480} title={t('cases.logAction')}>
                <Form form={actionForm} layout="vertical" onFinish={(values) => actionMutation.mutate(values)} initialValues={{ actionType: 'OPERATOR_NOTE' }}>
                    <Form.Item name="actionType" label={t('cases.actionType')} rules={[{ required: true }]}>
                        <Input />
                    </Form.Item>
                    <Form.Item name="notes" label={t('cases.notes')} rules={[{ required: true }]}>
                        <Input.TextArea rows={4} placeholder="Log resolution or operational notes" />
                    </Form.Item>
                    <Form.Item name="newStatus" label={t('cases.newStatus')}>
                        <Input placeholder="Optional status update (e.g. RESOLVED, CLOSED)" />
                    </Form.Item>
                    <Button block type="primary" htmlType="submit" loading={actionMutation.isPending}>
                        {t('common.submit')}
                    </Button>
                </Form>
            </Drawer>
        </Space>
    );
}
