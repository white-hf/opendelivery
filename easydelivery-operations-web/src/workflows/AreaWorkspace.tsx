import { useState } from 'react';
import { Alert, Button, Card, Form, Input, InputNumber, Modal, Space, Table, Tag, Typography, message } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, type Session } from '../api/client';
import { areaPayload, type AreaForm } from './areaPayload';

type AreaRow = {
    id: number; area_code: string; area_name: string; area_level: number; status: string;
    version_id?: number; version_no?: number; version_status?: string; geo_json?: string;
};

export function AreaWorkspace({ session, station }: { session: Session; station: string }) {
    const cache = useQueryClient();
    const [open, setOpen] = useState(false);
    const [form] = Form.useForm<AreaForm>();
    const list = useQuery({
        queryKey: ['areas', station],
        queryFn: () => api<AreaRow[]>('/ops/v1/delivery-areas', session, {}, station),
        enabled: Boolean(station),
    });
    const action = useMutation({
        mutationFn: ({ path, body }: { path: string; body?: unknown }) => api(path, session, {
            method: 'POST', body: body === undefined ? undefined : JSON.stringify(body),
        }, station),
        onSuccess: async () => {
            message.success('Area operation completed');
            setOpen(false);
            form.resetFields();
            await cache.invalidateQueries({ queryKey: ['areas', station] });
        },
    });
    const error = list.error ?? action.error;

    return <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        {error && <Alert type="error" message={error.message} />}
        <Card title="Delivery areas" extra={<Button type="primary" onClick={() => setOpen(true)}>Import GeoJSON</Button>}>
            <Typography.Paragraph type="secondary">
                Published polygons define reusable planning areas for this station. Import GeoJSON from geojson.io, validate it, then publish it.
            </Typography.Paragraph>
            <Table<AreaRow> rowKey="id" dataSource={list.data ?? []} loading={list.isLoading} pagination={false}
                columns={[
                    { title: 'Code', dataIndex: 'area_code' }, { title: 'Name', dataIndex: 'area_name' },
                    { title: 'Level', dataIndex: 'area_level' },
                    { title: 'Version', render: (_, row) => row.version_no ?? 'Draft' },
                    { title: 'Status', render: (_, row) => <Tag>{row.version_status ?? row.status}</Tag> },
                    { title: 'Action', render: (_, row) => <Space>
                        {row.version_status === 'DRAFT' && <Button size="small" onClick={() => action.mutate({
                            path: `/ops/v1/delivery-areas/${row.id}/versions/${row.version_id}/validate`,
                        })}>Validate</Button>}
                        {row.version_status === 'VALIDATED' && <Button size="small" type="primary" onClick={() => action.mutate({
                            path: `/ops/v1/delivery-areas/${row.id}/versions/${row.version_id}/publish`,
                            body: { reason: 'Approved in delivery area workspace' },
                        })}>Publish</Button>}
                    </Space> },
                ]} />
        </Card>
        <Modal title="Import delivery area" open={open} footer={null} onCancel={() => setOpen(false)} destroyOnHidden>
            <Form<AreaForm> form={form} layout="vertical" initialValues={{ areaLevel: 1 }} onFinish={(values) => {
                try { action.mutate({ path: '/ops/v1/delivery-areas', body: areaPayload(values) }); }
                catch { message.error('GeoJSON is not valid JSON'); }
            }}>
                <Space.Compact block>
                    <Form.Item name="areaCode" label="Area code" rules={[{ required: true, whitespace: true }]} style={{ width: '50%' }}><Input placeholder="DT-01" /></Form.Item>
                    <Form.Item name="areaLevel" label="Level" rules={[{ required: true }]} style={{ width: '50%' }}><InputNumber min={1} max={9} style={{ width: '100%' }} /></Form.Item>
                </Space.Compact>
                <Form.Item name="areaName" label="Area name" rules={[{ required: true, whitespace: true }]}><Input placeholder="Downtown core" /></Form.Item>
                <Form.Item name="geoJson" label="GeoJSON Feature, Polygon, or MultiPolygon" rules={[{ required: true, whitespace: true }]}><Input.TextArea rows={9} /></Form.Item>
                <Form.Item name="changeReason" label="Change reason" rules={[{ required: true, whitespace: true }]}><Input.TextArea rows={2} /></Form.Item>
                <Button type="primary" htmlType="submit" loading={action.isPending}>Create draft</Button>
            </Form>
        </Modal>
    </Space>;
}
