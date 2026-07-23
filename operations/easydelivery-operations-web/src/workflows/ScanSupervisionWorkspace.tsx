import { useState } from 'react';
import { Badge, Card, Col, Drawer, Row, Space, Statistic, Table, Tag } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { api, type Session } from '../api/client';
import { useTranslation } from 'react-i18next';

interface TaskSummary {
    taskId: number;
    driverId: number;
    driverName: string;
    expectedCount: number;
    validCount: number;
    missingCount: number;
    wrongTaskCount: number;
    unknownCount: number;
    duplicateCount: number;
    extraCount: number;
    openSessionCount: number;
}

interface WaveSummary {
    waveId: number;
    waveCode: string;
    expectedCount: number;
    validCount: number;
    missingCount: number;
    wrongTaskCount: number;
    unknownCount: number;
    duplicateCount: number;
    extraCount: number;
    openSessionCount: number;
    tasks: TaskSummary[];
}

interface SupervisionData {
    stationId: number;
    serviceDate: string;
    totalExpectedCount: number;
    totalValidCount: number;
    totalMissingCount: number;
    totalWrongTaskCount: number;
    totalUnknownCount: number;
    totalDuplicateCount: number;
    totalExtraCount: number;
    totalOpenSessionCount: number;
    waves: WaveSummary[];
}

interface ScanSessionItem {
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

interface ScanEventItem {
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

export function ScanSupervisionWorkspace({ session, station, serviceDate }: { session: Session; station: string; serviceDate: string }) {
    const { t } = useTranslation();
    const [selectedTaskId, setSelectedTaskId] = useState<number | null>(null);
    const [selectedSessionId, setSelectedSessionId] = useState<number | null>(null);

    const supervisionQuery = useQuery({
        queryKey: ['scan-supervision', station, serviceDate],
        queryFn: () => api<SupervisionData>(`/ops/v1/scan-supervision?serviceDate=${serviceDate}`, session, {}, station),
        enabled: Boolean(station && serviceDate),
    });

    const sessionsQuery = useQuery({
        queryKey: ['scan-sessions', station, selectedTaskId, serviceDate],
        queryFn: () => api<ScanSessionItem[]>(`/ops/v1/scan-sessions?taskId=${selectedTaskId}&serviceDate=${serviceDate}`, session, {}, station),
        enabled: Boolean(station && selectedTaskId),
    });

    const eventsQuery = useQuery({
        queryKey: ['scan-session-events', station, selectedSessionId],
        queryFn: () => api<ScanEventItem[]>(`/ops/v1/scan-sessions/${selectedSessionId}/events`, session, {}, station),
        enabled: Boolean(station && selectedSessionId),
    });

    const data = supervisionQuery.data;

    return (
        <Space direction="vertical" size="large" style={{ width: '100%' }}>
            <Card title={t('supervision.title')}>
                {data && (
                    <Row gutter={16}>
                        <Col span={3}>
                            <Statistic title={t('supervision.expected')} value={data.totalExpectedCount} />
                        </Col>
                        <Col span={3}>
                            <Statistic title={t('supervision.valid')} value={data.totalValidCount} valueStyle={{ color: '#3f8600' }} />
                        </Col>
                        <Col span={3}>
                            <Statistic title={t('supervision.missing')} value={data.totalMissingCount} valueStyle={{ color: data.totalMissingCount > 0 ? '#cf1322' : 'inherit' }} />
                        </Col>
                        <Col span={3}>
                            <Statistic title={t('supervision.wrongTask')} value={data.totalWrongTaskCount} valueStyle={{ color: data.totalWrongTaskCount > 0 ? '#fa8c16' : 'inherit' }} />
                        </Col>
                        <Col span={3}>
                            <Statistic title={t('supervision.unknown')} value={data.totalUnknownCount} valueStyle={{ color: data.totalUnknownCount > 0 ? '#fa8c16' : 'inherit' }} />
                        </Col>
                        <Col span={3}>
                            <Statistic title={t('supervision.duplicate')} value={data.totalDuplicateCount} />
                        </Col>
                        <Col span={3}>
                            <Statistic title={t('supervision.openSessions')} value={data.totalOpenSessionCount} />
                        </Col>
                    </Row>
                )}
            </Card>

            <Card title={t('supervision.wavesTitle')}>
                {data?.waves.map((wave) => (
                    <Card key={wave.waveId} type="inner" title={`${t('supervision.wave')}: ${wave.waveCode}`} style={{ marginBottom: 16 }}>
                        <Table
                            rowKey="taskId"
                            pagination={false}
                            dataSource={wave.tasks}
                            columns={[
                                { title: t('supervision.driver'), dataIndex: 'driverName', key: 'driverName' },
                                { title: t('supervision.expected'), dataIndex: 'expectedCount', key: 'expectedCount' },
                                { title: t('supervision.valid'), dataIndex: 'validCount', key: 'validCount', render: (v) => <Tag color="green">{v}</Tag> },
                                { title: t('supervision.missing'), dataIndex: 'missingCount', key: 'missingCount', render: (v) => v > 0 ? <Tag color="red">{v}</Tag> : <span>0</span> },
                                { title: t('supervision.wrongTask'), dataIndex: 'wrongTaskCount', key: 'wrongTaskCount', render: (v) => v > 0 ? <Tag color="orange">{v}</Tag> : <span>0</span> },
                                { title: t('supervision.unknown'), dataIndex: 'unknownCount', key: 'unknownCount', render: (v) => v > 0 ? <Tag color="orange">{v}</Tag> : <span>0</span> },
                                { title: t('supervision.duplicate'), dataIndex: 'duplicateCount', key: 'duplicateCount' },
                                { title: t('supervision.openSessions'), dataIndex: 'openSessionCount', key: 'openSessionCount', render: (v) => v > 0 ? <Badge count={v} /> : <span>0</span> },
                                {
                                    title: t('common.actions'),
                                    key: 'actions',
                                    render: (_, record) => (
                                        <a onClick={() => setSelectedTaskId(record.taskId)}>{t('supervision.viewSessions')}</a>
                                    ),
                                },
                            ]}
                        />
                    </Card>
                ))}
            </Card>

            <Drawer
                title={t('supervision.sessionsDrawerTitle')}
                width={700}
                open={Boolean(selectedTaskId)}
                onClose={() => setSelectedTaskId(null)}
            >
                <Table
                    rowKey="sessionId"
                    dataSource={sessionsQuery.data ?? []}
                    loading={sessionsQuery.isLoading}
                    columns={[
                        { title: 'Session ID', dataIndex: 'sessionId', key: 'sessionId' },
                        { title: t('supervision.driver'), dataIndex: 'driverName', key: 'driverName' },
                        { title: t('supervision.status'), dataIndex: 'sessionStatus', key: 'sessionStatus', render: (s) => <Tag color={s === 'SUBMITTED' ? 'blue' : s === 'APPROVED' ? 'green' : 'default'}>{s}</Tag> },
                        { title: t('supervision.valid'), dataIndex: 'validCount', key: 'validCount' },
                        { title: t('supervision.wrongTask'), dataIndex: 'wrongTaskCount', key: 'wrongTaskCount' },
                        {
                            title: t('common.actions'),
                            key: 'actions',
                            render: (_, record) => (
                                <a onClick={() => setSelectedSessionId(record.sessionId)}>{t('supervision.viewEvents')}</a>
                            ),
                        },
                    ]}
                />
            </Drawer>

            <Drawer
                title={t('supervision.eventsDrawerTitle')}
                width={750}
                open={Boolean(selectedSessionId)}
                onClose={() => setSelectedSessionId(null)}
            >
                <Table
                    rowKey="eventId"
                    dataSource={eventsQuery.data ?? []}
                    loading={eventsQuery.isLoading}
                    columns={[
                        { title: t('supervision.trackingNo'), dataIndex: 'trackingNo', key: 'trackingNo' },
                        {
                            title: t('supervision.resultCode'),
                            dataIndex: 'resultCode',
                            key: 'resultCode',
                            render: (code) => {
                                const color = code === 'EXPECTED' ? 'green' : code === 'WRONG_TASK' ? 'volcano' : code === 'UNKNOWN' ? 'orange' : 'default';
                                return <Tag color={color}>{code}</Tag>;
                            },
                        },
                        { title: t('supervision.scannedAt'), dataIndex: 'scannedAt', key: 'scannedAt' },
                        {
                            title: t('supervision.correctTaskHint'),
                            key: 'hint',
                            render: (_, record) => (
                                record.correctDriverName ? (
                                    <span>{record.correctDriverName} (Task #{record.correctTaskId})</span>
                                ) : '-'
                            ),
                        },
                    ]}
                />
            </Drawer>
        </Space>
    );
}
