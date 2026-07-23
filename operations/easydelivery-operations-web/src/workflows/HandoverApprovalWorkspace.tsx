import { useState } from 'react';
import { Alert, Button, Card, Drawer, Popconfirm, Space, Table, Tag, Typography, message } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, type Session } from '../api/client';
import { useTranslation } from 'react-i18next';

interface SessionItem {
    sessionId: number;
    taskId: number;
    driverId: number;
    driverName: string;
    sessionStatus: string;
    openedAt: string;
    submittedAt: string | null;
    expectedCount: number;
    scannedCount: number;
    discrepancyCount: number;
    validCount: number;
    wrongTaskCount: number;
    unknownCount: number;
    duplicateCount: number;
    extraCount: number;
}

interface EventItem {
    eventId: number;
    sessionId: number;
    parcelId: number | null;
    trackingNo: string;
    resultCode: string;
    deviceEventId: string;
    scannedAt: string;
    correctTaskId: number | null;
    correctDriverName: string | null;
}

export function HandoverApprovalWorkspace({ session, station, serviceDate }: { session: Session; station: string; serviceDate: string }) {
    const { t } = useTranslation();
    const queryClient = useQueryClient();
    const [selectedSessionId, setSelectedSessionId] = useState<number | null>(null);

    const sessionsQuery = useQuery({
        queryKey: ['handover-sessions', station, serviceDate],
        queryFn: () => api<SessionItem[]>(`/ops/v1/scan-sessions?serviceDate=${serviceDate}`, session, {}, station),
        enabled: Boolean(station && serviceDate),
    });

    const eventsQuery = useQuery({
        queryKey: ['handover-events', station, selectedSessionId],
        queryFn: () => api<EventItem[]>(`/ops/v1/scan-sessions/${selectedSessionId}/events`, session, {}, station),
        enabled: Boolean(station && selectedSessionId),
    });

    const approveMutation = useMutation({
        mutationFn: (sessionId: number) => api(`/ops/v1/scan-sessions/${sessionId}/approve`, session, { method: 'POST' }, station),
        onSuccess: async () => {
            message.success(t('handover.approved'));
            await queryClient.invalidateQueries({ queryKey: ['handover-sessions', station, serviceDate] });
        },
        onError: (err: Error) => message.error(err.message),
    });

    const rejectMutation = useMutation({
        mutationFn: (sessionId: number) => api(`/ops/v1/scan-sessions/${sessionId}/reject`, session, { method: 'POST' }, station),
        onSuccess: async () => {
            message.success(t('handover.rejected'));
            await queryClient.invalidateQueries({ queryKey: ['handover-sessions', station, serviceDate] });
        },
        onError: (err: Error) => message.error(err.message),
    });

    const items = sessionsQuery.data ?? [];
    const pendingCount = items.filter(s => s.sessionStatus === 'SUBMITTED').length;

    return (
        <Space direction="vertical" size="large" style={{ width: '100%' }}>
            <Card title={t('handover.title')}>
                <Alert
                    showIcon
                    type={pendingCount > 0 ? 'warning' : 'info'}
                    message={t('handover.summary', { count: pendingCount })}
                    description={t('handover.help')}
                    style={{ marginBottom: 16 }}
                />

                <Table
                    rowKey="sessionId"
                    dataSource={items}
                    loading={sessionsQuery.isLoading}
                    columns={[
                        { title: 'Session ID', dataIndex: 'sessionId', key: 'sessionId' },
                        { title: t('handover.driver'), dataIndex: 'driverName', key: 'driverName' },
                        {
                            title: t('handover.status'),
                            dataIndex: 'sessionStatus',
                            key: 'sessionStatus',
                            render: (status) => {
                                const color = status === 'APPROVED' ? 'green' : status === 'SUBMITTED' ? 'blue' : status === 'OPEN' ? 'orange' : 'default';
                                return <Tag color={color}>{status}</Tag>;
                            },
                        },
                        { title: t('handover.expected'), dataIndex: 'expectedCount', key: 'expectedCount' },
                        { title: t('handover.valid'), dataIndex: 'validCount', key: 'validCount', render: (v) => <Tag color="green">{v}</Tag> },
                        { title: t('handover.wrongTask'), dataIndex: 'wrongTaskCount', key: 'wrongTaskCount', render: (v) => v > 0 ? <Tag color="volcano">{v}</Tag> : '0' },
                        {
                            title: t('common.actions'),
                            key: 'actions',
                            render: (_, record) => (
                                <Space>
                                    <Button type="link" size="small" onClick={() => setSelectedSessionId(record.sessionId)}>
                                        {t('handover.viewEvents')}
                                    </Button>
                                    {record.sessionStatus === 'SUBMITTED' && (
                                        <>
                                            <Popconfirm
                                                title={t('handover.confirmApprove')}
                                                onConfirm={() => approveMutation.mutate(record.sessionId)}
                                            >
                                                <Button type="primary" size="small" loading={approveMutation.isPending}>
                                                    {t('handover.approve')}
                                                </Button>
                                            </Popconfirm>
                                            <Popconfirm
                                                title={t('handover.confirmReject')}
                                                onConfirm={() => rejectMutation.mutate(record.sessionId)}
                                            >
                                                <Button danger size="small" loading={rejectMutation.isPending}>
                                                    {t('handover.reject')}
                                                </Button>
                                            </Popconfirm>
                                        </>
                                    )}
                                </Space>
                            ),
                        },
                    ]}
                />
            </Card>

            <Drawer
                title={t('handover.eventsDrawerTitle')}
                width={750}
                open={Boolean(selectedSessionId)}
                onClose={() => setSelectedSessionId(null)}
            >
                <Table
                    rowKey="eventId"
                    dataSource={eventsQuery.data ?? []}
                    loading={eventsQuery.isLoading}
                    columns={[
                        { title: t('handover.trackingNo'), dataIndex: 'trackingNo', key: 'trackingNo' },
                        {
                            title: t('handover.resultCode'),
                            dataIndex: 'resultCode',
                            key: 'resultCode',
                            render: (code) => {
                                const color = code === 'EXPECTED' ? 'green' : code === 'WRONG_TASK' ? 'volcano' : code === 'UNKNOWN' ? 'orange' : 'default';
                                return <Tag color={color}>{code}</Tag>;
                            },
                        },
                        { title: t('handover.scannedAt'), dataIndex: 'scannedAt', key: 'scannedAt' },
                        {
                            title: t('handover.correctTaskHint'),
                            key: 'hint',
                            render: (_, record) => (
                                record.correctDriverName ? (
                                    <Typography.Text type="danger">{record.correctDriverName} (Task #{record.correctTaskId})</Typography.Text>
                                ) : '-'
                            ),
                        },
                    ]}
                />
            </Drawer>
        </Space>
    );
}
