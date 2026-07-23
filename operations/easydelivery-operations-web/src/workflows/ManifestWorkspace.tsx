import { useState } from 'react';
import { Alert, Button, Card, Checkbox, Form, Input, Modal, Space, Table, Tag, message } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, type Session } from '../api/client';
import { inboundDecision, scanPayload } from './payloads';
import { useTranslation } from 'react-i18next';

type Row = Record<string, unknown>;
type Detail = { manifest: { id: number; status: string; manifestNo: string }; items: Row[] };

export function ManifestWorkspace({ session, station }: { session: Session; station: string }) {
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
    const error = list.error ?? detail.error ?? action.error;

    return <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        {error && <Alert type="error" message={error.message} />}
        <Card title={t('manifest.title')}>
            <Table<Row> rowKey={(row) => String(row.id)} dataSource={list.data ?? []} loading={list.isLoading}
                onRow={(row) => ({ onClick: () => setSelectedId(Number(row.id)) })}
                columns={['id', 'external_manifest_no', 'status', 'expected_count', 'received_count', 'discrepancy_count']
                    .map((key) => ({ title: t(key === 'status' ? 'common.status' : `field.${key}`, { defaultValue: key }), dataIndex: key }))} pagination={false} />
        </Card>
        <Modal open={Boolean(selectedId)} width={900} footer={null} onCancel={() => setSelectedId(undefined)}
            title={`Manifest ${detail.data?.manifest.manifestNo ?? selectedId}`}>
            {detail.data && <Space direction="vertical" style={{ width: '100%' }}>
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
                <Table<Row> rowKey={(row) => String(row.id)} dataSource={detail.data.items}
                    columns={[
                        ...['expected_tracking_no', 'receipt_status', 'parcel_status', 'discrepancy_reason']
                            .map((key) => ({ title: t(`field.${key}`, { defaultValue: key }), dataIndex: key })),
                        { title: t('common.action'), render: (_, row) => inboundDecision(String(row.receipt_status))
                            ? <Button size="small" onClick={() => setDiscrepancy(row)}>{t('manifest.resolve')}</Button> : null },
                    ]} pagination={false} />
            </Space>}
        </Modal>
        <Modal open={Boolean(discrepancy)} footer={null} onCancel={() => setDiscrepancy(undefined)}
            title={t('manifest.resolveTitle', { status: String(discrepancy?.receipt_status ?? '') })}>
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
    </Space>;
}
