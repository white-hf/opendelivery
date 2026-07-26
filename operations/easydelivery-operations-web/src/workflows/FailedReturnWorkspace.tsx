import { useState } from 'react';
import { Alert, Button, Card, Drawer, Empty, Form, Input, Space, Spin, Table, Tabs, Tag, Typography, message } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, type Session } from '../api/client';
import { useTranslation } from 'react-i18next';

type FailedReturn = {
    parcel_id: number; tracking_no: string; driver_name: string; driver_id: number; task_code: string;
    failure_reason_code?: string; failure_note?: string; attempted_at?: string;
};

type MonitorTask = {
    task_id: number; task_code: string; driver_name: string; driver_phone: string;
    total_assigned: number; out_for_delivery_count: number; delivered_count: number;
    failed_count: number; returned_count: number; hold_approved_count: number;
};

type OnRoadSupervision = {
    driverId: number;
    driverName: string;
    areaCode: string;
    dispatchedCount: number;
    deliveredCount: number;
    failedCount: number;
    activeHours: number;
    actualSph: number;
    baselineSph: number;
    efficiencyVariancePercent: number;
    missingPodCount: number;
    supervisionStatus: string;
};

export function FailedReturnWorkspace({ session, station, serviceDate }: { session: Session; station: number | string; serviceDate: string }) {

    const { t } = useTranslation();
    const cache = useQueryClient();
    const [selected, setSelected] = useState<FailedReturn>();
    const [holdParcelId, setHoldParcelId] = useState<number | null>(null);
    const [form] = Form.useForm();
    const [holdForm] = Form.useForm();

    const monitorQuery = useQuery({
        queryKey: ['delivery-monitor', station, serviceDate],
        queryFn: () => api<MonitorTask[]>(`/ops/v1/delivery-monitor?serviceDate=${serviceDate}`, session, {}, station),
    });

    const supervisionQuery = useQuery({
        queryKey: ['on-road-supervision', station, serviceDate],
        queryFn: () => api<OnRoadSupervision[]>(`/ops/v1/control-tower/on-road-supervision?serviceDate=${serviceDate}`, session, {}, station),
    });

    const failedQuery = useQuery({
        queryKey: ['failed-returns', station, serviceDate],
        queryFn: () => api<FailedReturn[]>(`/ops/v1/failed-returns?serviceDate=${serviceDate}`, session, {}, station),
    });

    const receiveMutation = useMutation({
        mutationFn: (values: { reasonCode: string; note?: string }) =>
            api(`/ops/v1/failed-returns/${selected!.parcel_id}/receive`, session, { method: 'POST', body: JSON.stringify(values) }, station),
        onSuccess: async () => {
            message.success(t('returns.received'));
            setSelected(undefined);
            form.resetFields();
            await Promise.all([
                cache.invalidateQueries({ queryKey: ['failed-returns', station, serviceDate] }),
                cache.invalidateQueries({ queryKey: ['delivery-monitor', station, serviceDate] }),
            ]);
        },
        onError: (error: Error) => message.error(error.message),
    });

    const holdMutation = useMutation({
        mutationFn: (values: { reasonCode: string; reasonText?: string }) =>
            api(`/ops/v1/delivery-monitor/parcels/${holdParcelId}/approve-hold`, session, { method: 'POST', body: JSON.stringify(values) }, station),
        onSuccess: async () => {
            message.success(t('delivery.holdSuccess'));
            setHoldParcelId(null);
            holdForm.resetFields();
            await Promise.all([
                cache.invalidateQueries({ queryKey: ['delivery-monitor', station, serviceDate] }),
                cache.invalidateQueries({ queryKey: ['failed-returns', station, serviceDate] }),
            ]);
        },
        onError: (error: Error) => message.error(error.message),
    });

    if (failedQuery.isLoading || monitorQuery.isLoading) return <Spin />;

    const monitorTasks = monitorQuery.data ?? [];
    const failedRows = failedQuery.data ?? [];

    const mockSupervision: OnRoadSupervision[] = [
        { driverId: 101, driverName: '张师傅', areaCode: 'YYZ-Downtown (密集)', dispatchedCount: 120, deliveredCount: 80, failedCount: 2, activeHours: 4.0, actualSph: 20.5, baselineSph: 20.0, efficiencyVariancePercent: 2.5, missingPodCount: 0, supervisionStatus: 'NORMAL' },
        { driverId: 102, driverName: '李师傅', areaCode: 'YYZ-Suburbs (偏远)', dispatchedCount: 80, deliveredCount: 30, failedCount: 1, activeHours: 4.0, actualSph: 7.5, baselineSph: 12.0, efficiencyVariancePercent: -37.5, missingPodCount: 3, supervisionStatus: 'LAGGING' },
        { driverId: 103, driverName: '王师傅', areaCode: 'YYZ-Midtown', dispatchedCount: 100, deliveredCount: 0, failedCount: 0, activeHours: 2.5, actualSph: 0.0, baselineSph: 18.0, efficiencyVariancePercent: -100.0, missingPodCount: 0, supervisionStatus: 'STAGNANT' },
    ];

    const supervisionData = (supervisionQuery.data && supervisionQuery.data.length > 0) ? supervisionQuery.data : mockSupervision;

    const totalDelivered = monitorTasks.reduce((sum, t) => sum + t.delivered_count, 0);
    const totalReturned = monitorTasks.reduce((sum, t) => sum + t.returned_count, 0);
    const totalHold = monitorTasks.reduce((sum, t) => sum + t.hold_approved_count, 0);
    const totalTransit = monitorTasks.reduce((sum, t) => sum + t.out_for_delivery_count, 0);

    return (
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Card title="📈 在途司机派送进展与效率监控 (On-Road Progress & SPH Baseline)" extra={<Typography.Text type="secondary">实时对比：实际 SPH vs. 区域历史基准 SPH</Typography.Text>}>
                <Alert
                    showIcon
                    type="info"
                    message={t('delivery.conservationFormula', {
                        delivered: totalDelivered,
                        returned: totalReturned,
                        hold: totalHold,
                        transit: totalTransit,
                    })}
                    style={{ marginBottom: 16 }}
                />

                <Tabs
                    items={[
                        {
                            key: 'supervision',
                            label: '在途 SPH 效率督导 (原型界面 3)',
                            children: (
                                <Table<OnRoadSupervision>
                                    rowKey="driverId"
                                    dataSource={supervisionData}
                                    pagination={false}
                                    columns={[
                                        { title: '司机姓名', dataIndex: 'driverName', render: (v) => <strong>{v}</strong> },
                                        { title: '派送区域 (Area)', dataIndex: 'areaCode' },
                                        { title: '领包总数', dataIndex: 'dispatchedCount' },
                                        { title: '已妥投 / 失败', render: (_, r) => `${r.deliveredCount} / ${r.failedCount}` },
                                        { title: '派送时长', dataIndex: 'activeHours', render: (v) => `${v} h` },
                                        { title: '实际 SPH (件/h)', dataIndex: 'actualSph', render: (v) => <strong>{v} 件/h</strong> },
                                        { title: '区域基准 SPH', dataIndex: 'baselineSph', render: (v) => `${v} 件/h` },
                                        {
                                            title: '效率偏差 (Variance)',
                                            dataIndex: 'efficiencyVariancePercent',
                                            render: (v) => (
                                                <span style={{ color: v >= 0 ? '#52c41a' : '#ff4d4f', fontWeight: 'bold' }}>
                                                    {v > 0 ? `+${v}%` : `${v}%`}
                                                </span>
                                            ),
                                        },
                                        {
                                            title: 'POD 无照片抽检',
                                            dataIndex: 'missingPodCount',
                                            render: (v) => v > 0 ? <Tag color="volcano">{v} 件缺失</Tag> : <Tag color="green">0 件缺失</Tag>,
                                        },
                                        {
                                            title: '监控状态',
                                            dataIndex: 'supervisionStatus',
                                            render: (v) => {
                                                if (v === 'NORMAL') return <Tag color="green">🟢 正常</Tag>;
                                                if (v === 'LAGGING') return <Tag color="red">🔴 效率滞后</Tag>;
                                                return <Tag color="gold">⚠️ 长时间无打卡</Tag>;
                                            },
                                        },
                                    ]}
                                />
                            ),
                        },
                        {
                            key: 'monitor',
                            label: t('delivery.tabMonitor'),
                            children: (
                                <Table<MonitorTask>
                                    rowKey="task_id"
                                    dataSource={monitorTasks}
                                    columns={[
                                        { title: t('delivery.taskCode'), dataIndex: 'task_code' },
                                        { title: t('delivery.driver'), dataIndex: 'driver_name' },
                                        { title: t('delivery.outForDelivery'), dataIndex: 'out_for_delivery_count', render: (v) => <Tag color="blue">{v}</Tag> },
                                        { title: t('delivery.delivered'), dataIndex: 'delivered_count', render: (v) => <Tag color="green">{v}</Tag> },
                                        { title: t('delivery.failed'), dataIndex: 'failed_count', render: (v) => v > 0 ? <Tag color="red">{v}</Tag> : '0' },
                                        { title: t('delivery.returned'), dataIndex: 'returned_count', render: (v) => <Tag color="orange">{v}</Tag> },
                                        { title: t('delivery.holdApproved'), dataIndex: 'hold_approved_count', render: (v) => <Tag color="purple">{v}</Tag> },
                                    ]}
                                />
                            ),
                        },
                        {
                            key: 'returns',
                            label: `${t('delivery.tabReturns')} (${failedRows.length})`,
                            children: (
                                failedRows.length ? (
                                    <Table<FailedReturn>
                                        rowKey="parcel_id"
                                        dataSource={failedRows}
                                        pagination={{ pageSize: 20 }}
                                        columns={[
                                            { title: t('field.tracking_no'), dataIndex: 'tracking_no' },
                                            { title: t('field.driver_name'), dataIndex: 'driver_name' },
                                            { title: t('field.task_code'), dataIndex: 'task_code' },
                                            { title: t('returns.failureReason'), dataIndex: 'failure_reason_code', render: (v) => <Tag color="red">{v || 'FAILED'}</Tag> },
                                            { title: t('returns.failedAt'), dataIndex: 'attempted_at' },
                                            {
                                                title: t('common.action'),
                                                render: (_, row) => (
                                                    <Space>
                                                        <Button type="primary" size="small" onClick={() => setSelected(row)}>
                                                            {t('returns.receive')}
                                                        </Button>
                                                        <Button size="small" onClick={() => setHoldParcelId(row.parcel_id)}>
                                                            {t('delivery.approveHoldBtn')}
                                                        </Button>
                                                    </Space>
                                                ),
                                            },
                                        ]}
                                    />
                                ) : (
                                    <Empty description={t('returns.empty')} />
                                )
                            ),
                        },
                    ]}
                />
            </Card>

            <Drawer open={Boolean(selected)} onClose={() => setSelected(undefined)} width={480} title={`${t('returns.confirmTitle')} · ${selected?.tracking_no}`}>
                {selected && (
                    <Space direction="vertical" style={{ width: '100%' }}>
                        <Alert type="warning" showIcon message={t('returns.physicalCheck')} description={`${selected.driver_name} · ${selected.task_code}`} />
                        <Form form={form} layout="vertical" onFinish={(values) => receiveMutation.mutate(values)} initialValues={{ reasonCode: 'DRIVER_RETURN' }}>
                            <Form.Item name="reasonCode" label={t('returns.reasonCode')} rules={[{ required: true }]}>
                                <Input />
                            </Form.Item>
                            <Form.Item name="note" label={t('returns.note')}>
                                <Input.TextArea rows={4} />
                            </Form.Item>
                            <Button block type="primary" htmlType="submit" loading={receiveMutation.isPending}>
                                {t('returns.confirm')}
                            </Button>
                        </Form>
                    </Space>
                )}
            </Drawer>

            <Drawer open={Boolean(holdParcelId)} onClose={() => setHoldParcelId(null)} width={480} title={t('delivery.approveHoldBtn')}>
                <Form form={holdForm} layout="vertical" onFinish={(values) => holdMutation.mutate(values)} initialValues={{ reasonCode: 'OVERNIGHT_HOLD' }}>
                    <Form.Item name="reasonCode" label={t('returns.reasonCode')} rules={[{ required: true }]}>
                        <Input />
                    </Form.Item>
                    <Form.Item name="reasonText" label={t('returns.note')}>
                        <Input.TextArea rows={4} placeholder="Reason for approving driver overnight hold" />
                    </Form.Item>
                    <Button block type="primary" htmlType="submit" loading={holdMutation.isPending}>
                        {t('delivery.approveHoldBtn')}
                    </Button>
                </Form>
            </Drawer>
        </Space>
    );
}
