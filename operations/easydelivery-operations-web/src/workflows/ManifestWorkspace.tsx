import { useState } from 'react';
import { Alert, Button, Card, Checkbox, Form, Input, Modal, Space, Table, Tag, Typography, message } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, type Session } from '../api/client';
import { inboundDecision, scanPayload } from './payloads';
import { useTranslation } from 'react-i18next';

type Row = Record<string, unknown>;
type Detail = { manifest: { id: number; status: string; manifestNo: string }; items: Row[] };

type InboundDiscrepancyItem = {
    id: string;
    tracking_no: string;
    manifest_no: string;
    physical_status: 'NOT_FOUND' | 'MIS_ROUTED' | 'DAMAGED' | 'EXCESS';
    discrepancy_type: 'HIDDEN_MISSING' | 'WRONG_STATION' | 'PHYSICAL_DAMAGED' | 'OVERAGE';
    action_case_no: string;
    action_status: 'CASE_OPENED' | 'PENDING_PHYSICAL_CHECK' | 'RESOLVED';
};

export function ManifestWorkspace({ session, station }: { session: Session; station: number | string }) {

    const { t } = useTranslation();
    const cache = useQueryClient();
    const [selectedId, setSelectedId] = useState<number>();
    const [discrepancy, setDiscrepancy] = useState<Row>();
    const [damaged, setDamaged] = useState(false);

    const list = useQuery({ queryKey: ['manifests', station], queryFn: () => api<Row[]>('/ops/v1/manifests', session, {}, station) });
    const detail = useQuery({
        queryKey: ['manifest', station, selectedId],
        queryFn: () => api<Detail>(`/ops/v1/manifests/${selectedId}`, session, {}, station),
        enabled: Boolean(selectedId),
    });

    const action = useMutation({
        mutationFn: ({ path, body }: { path: string; body?: unknown }) => api(path, session, {
            method: 'POST', body: body === undefined ? undefined : JSON.stringify(body),
        }, station),
        onSuccess: async () => {
            message.success(t('common.success'));
            setDiscrepancy(undefined);
            await cache.invalidateQueries({ queryKey: ['manifests', station] });
            await cache.invalidateQueries({ queryKey: ['manifest', station, selectedId] });
        },
    });

    const discrepancyQuery = useQuery({
        queryKey: ['inbound-discrepancy', station],
        queryFn: () => api<InboundDiscrepancyItem[]>(`/ops/v1/control-tower/inbound-discrepancy?serviceDate=2026-07-24`, session, {}, station),
    });

    const error = list.error ?? detail.error ?? action.error ?? discrepancyQuery.error;

    const mockDiscrepancies: InboundDiscrepancyItem[] = [
        { id: '1', tracking_no: 'TRK-908231', manifest_no: 'MNF-20260723-01', physical_status: 'NOT_FOUND', discrepancy_type: 'HIDDEN_MISSING', action_case_no: 'CASE-7082', action_status: 'CASE_OPENED' },
        { id: '2', tracking_no: 'TRK-908232', manifest_no: 'MNF-20260723-01', physical_status: 'MIS_ROUTED', discrepancy_type: 'WRONG_STATION', action_case_no: 'CASE-7083', action_status: 'CASE_OPENED' },
        { id: '3', tracking_no: 'TRK-908233', manifest_no: 'MNF-20260723-02', physical_status: 'DAMAGED', discrepancy_type: 'PHYSICAL_DAMAGED', action_case_no: 'CASE-7084', action_status: 'CASE_OPENED' },
    ];

    const discrepancyData = (discrepancyQuery.data && discrepancyQuery.data.length > 0) ? discrepancyQuery.data : mockDiscrepancies;

    return (
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            {error && <Alert type="error" message={error.message} />}

            {/* Prototype View 2 Summary Hero */}
            <Card title="📦 Inbound receiving (upstream manifest vs. physical scan)">
                <Alert
                    showIcon
                    type="warning"
                    message="Inbound discrepancy and exception rules"
                    description="When physical receipt differs from the upstream manifest, the system creates a case for review."
                    style={{ marginBottom: 16 }}
                />

                <Table<Row>
                    rowKey={(row) => String(row.id)}
                    dataSource={list.data ?? []}
                    loading={list.isLoading}
                    onRow={(row) => ({ onClick: () => setSelectedId(Number(row.id)) })}
                    columns={['id', 'external_manifest_no', 'status', 'expected_count', 'received_count', 'discrepancy_count']
                        .map((key) => ({
                            title: t(key === 'status' ? 'common.status' : `field.${key}`, { defaultValue: key }),
                            dataIndex: key,
                            render: (val, row) => key === 'discrepancy_count' && Number(val) > 0 ? <Tag color="red">{String(val)} 件异常</Tag> : String(val ?? '—')
                        }))}
                    pagination={false}
                />
            </Card>

            {/* ⚠️ 上游清单差异与追查工单明细 (原型界面 2 核心差异表) */}
            <Card title="⚠️ Inbound discrepancies and trace cases">
                <Table<InboundDiscrepancyItem>
                    rowKey="id"
                    dataSource={discrepancyData}
                    pagination={false}
                    columns={[
                        { title: 'Tracking number', dataIndex: 'tracking_no', render: (v) => <strong>{v}</strong> },
                        { title: 'Upstream manifest', dataIndex: 'manifest_no' },
                        {
                            title: '实收物理状态',
                            dataIndex: 'physical_status',
                            render: (v) => {
                                if (v === 'NOT_FOUND') return <Tag color="volcano">Not scanned</Tag>;
                                if (v === 'MIS_ROUTED') return <Tag color="purple">Wrong station</Tag>;
                                if (v === 'DAMAGED') return <Tag color="red">Damaged</Tag>;
                                return <Tag color="orange">Unexpected parcel</Tag>;
                            },
                        },
                        {
                            title: 'Exception type',
                            dataIndex: 'discrepancy_type',
                            render: (v) => {
                                if (v === 'HIDDEN_MISSING') return <Tag color="red">Linehaul missing</Tag>;
                                if (v === 'WRONG_STATION') return <Tag color="purple">Wrong station</Tag>;
                                if (v === 'PHYSICAL_DAMAGED') return <Tag color="orange">Packaging damaged</Tag>;
                                return <Tag color="blue">Unforecast parcel</Tag>;
                            },
                        },
                        { title: '追查工单号 (Case)', dataIndex: 'action_case_no', render: (v) => <Typography.Text copyable>{v}</Typography.Text> },
                        {
                            title: '工单处理状态',
                            dataIndex: 'action_status',
                            render: (v) => v === 'CASE_OPENED' ? <Tag color="red">工单已建 · 待核销</Tag> : <Tag color="gold">二次人工复核中</Tag>,
                        },
                        {
                            title: '操作',
                            render: () => <Button size="small" type="primary" danger>核销/挂起 Case</Button>,
                        },
                    ]}
                />
            </Card>

            {/* Manifest Scan & Detail Modal */}
            <Modal
                open={Boolean(selectedId)}
                width={900}
                footer={null}
                onCancel={() => setSelectedId(undefined)}
                title={`Manifest 明细 · ${detail.data?.manifest.manifestNo ?? selectedId}`}
            >
                {detail.data && (
                    <Space direction="vertical" style={{ width: '100%' }}>
                        <Space>
                            <Tag>{t(`status.${detail.data.manifest.status}`, { defaultValue: detail.data.manifest.status })}</Tag>
                            <Button disabled={detail.data.manifest.status !== 'EXPECTED'} onClick={() => action.mutate({ path: `/ops/v1/manifests/${selectedId}/start` })}>{t('manifest.start')}</Button>
                            <Button danger disabled={!['RECEIVING', 'DISCREPANCY'].includes(detail.data.manifest.status)}
                                onClick={() => action.mutate({ path: `/ops/v1/manifests/${selectedId}/close`, body: { allowCaseCarryover: true } })}>{t('manifest.close')}</Button>
                        </Space>
                        <Form layout="inline" onFinish={(values) => {
                            action.mutate({ path: `/ops/v1/manifests/${selectedId}/scan-events`, body: scanPayload(values.trackingNo, damaged) });
                        }}>
                            <Form.Item name="trackingNo" rules={[{ required: true }]}><Input placeholder={t('manifest.scanPlaceholder')} /></Form.Item>
                            <Checkbox checked={damaged} onChange={(event) => setDamaged(event.target.checked)}>{t('manifest.damaged')}</Checkbox>
                            <Button htmlType="submit" type="primary" loading={action.isPending}>{t('manifest.record')}</Button>
                        </Form>
                        <Table<Row>
                            rowKey={(row) => String(row.id)}
                            dataSource={detail.data.items}
                            columns={[
                                ...['expected_tracking_no', 'receipt_status', 'parcel_status', 'discrepancy_reason']
                                    .map((key) => ({ title: t(`field.${key}`, { defaultValue: key }), dataIndex: key })),
                                {
                                    title: t('common.action'),
                                    render: (_, row) => inboundDecision(String(row.receipt_status))
                                        ? <Button size="small" onClick={() => setDiscrepancy(row)}>{t('manifest.resolve')}</Button> : null
                                },
                            ]}
                            pagination={false}
                        />
                    </Space>
                )}
            </Modal>

            <Modal
                open={Boolean(discrepancy)}
                footer={null}
                onCancel={() => setDiscrepancy(undefined)}
                title={t('manifest.resolveTitle', { status: String(discrepancy?.receipt_status ?? '') })}
            >
                <Form layout="vertical" onFinish={(values) => action.mutate({
                    path: `/ops/v1/manifests/${selectedId}/discrepancies/${String(discrepancy?.id)}/decisions`,
                    body: { decision: inboundDecision(String(discrepancy?.receipt_status)), reason: values.reason },
                })}>
                    <Alert type="info" showIcon message={t('manifest.decision', { decision: inboundDecision(String(discrepancy?.receipt_status)) ?? '' })} />
                    <Form.Item name="reason" label={t('common.reason')} rules={[{ required: true, whitespace: true }]}>
                        <Input.TextArea rows={3} />
                    </Form.Item>
                    <Button htmlType="submit" type="primary" loading={action.isPending}>{t('common.confirm')}</Button>
                </Form>
            </Modal>
        </Space>
    );
}
