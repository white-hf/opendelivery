import { useState } from 'react';
import { Alert, Button, Card, Col, Input, Popconfirm, Row, Space, Spin, Statistic, Tag, message } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, type Session } from '../api/client';
import { useTranslation } from 'react-i18next';

interface DayCloseData {
    stationId: number;
    businessDate: string;
    status: string;
    inboundCount: number;
    dispatchedCount: number;
    deliveredCount: number;
    driverReturnCount?: number;
    varianceCount: number;
    openCaseCount: number;
    signedOffBy?: number | null;
    signedOffAt?: string | null;
    carryoverReason?: string | null;
}

export function DayCloseWorkspace({ session, station, serviceDate }: { session: Session; station: string; serviceDate: string }) {
    const { t } = useTranslation();
    const queryClient = useQueryClient();
    const [note, setNote] = useState('');

    const dayCloseQuery = useQuery({
        queryKey: ['day-close', station, serviceDate],
        queryFn: () => api<DayCloseData>(`/ops/v1/day-close?serviceDate=${serviceDate}`, session, {}, station),
        enabled: Boolean(station && serviceDate),
    });

    const recalculateMutation = useMutation({
        mutationFn: () => api(`/ops/v1/day-close/recalculate?serviceDate=${serviceDate}`, session, { method: 'POST' }, station),
        onSuccess: async () => {
            message.success(t('dayClose.recalculated'));
            await queryClient.invalidateQueries({ queryKey: ['day-close', station, serviceDate] });
        },
        onError: (err: Error) => message.error(err.message),
    });

    const signOffMutation = useMutation({
        mutationFn: () => api(`/ops/v1/day-close/sign?serviceDate=${serviceDate}`, session, { method: 'POST', body: JSON.stringify({ note }) }, station),
        onSuccess: async () => {
            message.success(t('dayClose.signedOffSuccess'));
            setNote('');
            await queryClient.invalidateQueries({ queryKey: ['day-close', station, serviceDate] });
        },
        onError: (err: Error) => message.error(err.message),
    });

    if (dayCloseQuery.isLoading) return <Spin />;
    if (dayCloseQuery.error) {
        return <Alert type="error" showIcon message={dayCloseQuery.error.message} action={<Button onClick={() => void dayCloseQuery.refetch()}>{t('common.retry')}</Button>} />;
    }

    const data = dayCloseQuery.data ?? {
        stationId: 0,
        businessDate: serviceDate,
        status: 'OPEN',
        inboundCount: 0,
        dispatchedCount: 0,
        deliveredCount: 0,
        varianceCount: 0,
        openCaseCount: 0,
    };

    const isSigned = data.status === 'SIGNED_OFF';
    const isBalanced = data.varianceCount === 0 && data.openCaseCount === 0;

    return (
        <Space direction="vertical" size="large" style={{ width: '100%' }}>
            <Card
                title={t('dayClose.title')}
                extra={
                    <Space>
                        <Tag color={isSigned ? 'green' : isBalanced ? 'blue' : 'orange'}>
                            {data.status}
                        </Tag>
                        <Button
                            type="default"
                            disabled={isSigned}
                            loading={recalculateMutation.isPending}
                            onClick={() => recalculateMutation.mutate()}
                        >
                            {t('dayClose.recalculate')}
                        </Button>
                    </Space>
                }
            >
                <Alert
                    showIcon
                    type={isSigned ? 'success' : isBalanced ? 'info' : 'warning'}
                    message={isSigned ? t('dayClose.signedBanner') : isBalanced ? t('dayClose.balancedBanner') : t('dayClose.unbalancedBanner')}
                    style={{ marginBottom: 24 }}
                />

                <Row gutter={[16, 16]}>
                    <Col span={6}>
                        <Card size="small">
                            <Statistic title={t('dayClose.inbound')} value={data.inboundCount} />
                        </Card>
                    </Col>
                    <Col span={6}>
                        <Card size="small">
                            <Statistic title={t('dayClose.dispatched')} value={data.dispatchedCount} />
                        </Card>
                    </Col>
                    <Col span={6}>
                        <Card size="small">
                            <Statistic title={t('dayClose.delivered')} value={data.deliveredCount} valueStyle={{ color: '#3f8600' }} />
                        </Card>
                    </Col>
                    <Col span={6}>
                        <Card size="small">
                            <Statistic title={t('dayClose.variance')} value={data.varianceCount} valueStyle={{ color: data.varianceCount > 0 ? '#cf1322' : '#3f8600' }} />
                        </Card>
                    </Col>
                </Row>

                {!isSigned && (
                    <Card style={{ marginTop: 24 }} title={t('dayClose.signSectionTitle')}>
                        <Space direction="vertical" style={{ width: '100%' }}>
                            <Input.TextArea
                                rows={3}
                                placeholder={t('dayClose.notePlaceholder')}
                                value={note}
                                onChange={(e) => setNote(e.target.value)}
                            />
                            <Popconfirm
                                title={t('dayClose.confirmSign')}
                                onConfirm={() => signOffMutation.mutate()}
                            >
                                <Button type="primary" loading={signOffMutation.isPending}>
                                    {t('dayClose.signOffBtn')}
                                </Button>
                            </Popconfirm>
                        </Space>
                    </Card>
                )}
            </Card>
        </Space>
    );
}
