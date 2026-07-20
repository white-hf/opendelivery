import { useState } from 'react';
import { Alert, Button, Card, Checkbox, Form, Input, Modal, Space, Table, Tag, message } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, type Session } from '../api/client';
import { scanPayload } from './payloads';

type Row = Record<string, unknown>;
type Detail = { manifest: { id: number; status: string; manifestNo: string }; items: Row[] };

export function ManifestWorkspace({ session, station }: { session: Session; station: string }) {
    const cache = useQueryClient();
    const [selectedId, setSelectedId] = useState<number>();
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
            message.success('Operation completed');
            await cache.invalidateQueries({ queryKey: ['manifests', station] });
            await cache.invalidateQueries({ queryKey: ['manifest', station, selectedId] });
        },
    });
    const error = list.error ?? detail.error ?? action.error;

    return <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        {error && <Alert type="error" message={error.message} />}
        <Card title="Inbound manifests">
            <Table<Row> rowKey={(row) => String(row.id)} dataSource={list.data ?? []} loading={list.isLoading}
                onRow={(row) => ({ onClick: () => setSelectedId(Number(row.id)) })}
                columns={['id', 'external_manifest_no', 'status', 'expected_count', 'received_count', 'discrepancy_count']
                    .map((key) => ({ title: key, dataIndex: key }))} pagination={false} />
        </Card>
        <Modal open={Boolean(selectedId)} width={900} footer={null} onCancel={() => setSelectedId(undefined)}
            title={`Manifest ${detail.data?.manifest.manifestNo ?? selectedId}`}>
            {detail.data && <Space direction="vertical" style={{ width: '100%' }}>
                <Space>
                    <Tag>{detail.data.manifest.status}</Tag>
                    <Button disabled={detail.data.manifest.status !== 'EXPECTED'} onClick={() => action.mutate({ path: `/ops/v1/manifests/${selectedId}/start` })}>Start receiving</Button>
                    <Button danger disabled={!['RECEIVING', 'DISCREPANCY'].includes(detail.data.manifest.status)}
                        onClick={() => action.mutate({ path: `/ops/v1/manifests/${selectedId}/close`, body: { allowCaseCarryover: true } })}>Close with case carryover</Button>
                </Space>
                <Form layout="inline" onFinish={(values) => {
                    action.mutate({ path: `/ops/v1/manifests/${selectedId}/scan-events`, body: scanPayload(values.trackingNo, damaged) });
                }}>
                    <Form.Item name="trackingNo" rules={[{ required: true }]}><Input placeholder="Scan tracking number" /></Form.Item>
                    <Checkbox checked={damaged} onChange={(event) => setDamaged(event.target.checked)}>Damaged</Checkbox>
                    <Button htmlType="submit" type="primary" loading={action.isPending}>Record scan</Button>
                </Form>
                <Table<Row> rowKey={(row) => String(row.id)} dataSource={detail.data.items}
                    columns={['expected_tracking_no', 'receipt_status', 'parcel_status', 'discrepancy_reason']
                        .map((key) => ({ title: key, dataIndex: key }))} pagination={false} />
            </Space>}
        </Modal>
    </Space>;
}
