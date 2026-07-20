import { useState } from 'react';
import { Alert, Button, Card, DatePicker, Form, Input, Select, Space, Table, message } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { api, type Session } from '../api/client';
import { dispatchDraftPayload, type DispatchDraftValues } from './payloads';
import { useTranslation } from 'react-i18next';

type Row = Record<string, unknown>;

export function DispatchWorkspace({ session, station }: { session: Session; station: string }) {
    const { t } = useTranslation();
    const cache = useQueryClient();
    const [selected, setSelected] = useState<string[]>([]);
    const candidates = useQuery({ queryKey: ['dispatch-candidates', station], queryFn: () => api<Row[]>('/ops/v1/dispatch/candidates', session, {}, station) });
    const drivers = useQuery({ queryKey: ['dispatch-drivers', station], queryFn: () => api<Row[]>('/ops/v1/dispatch/drivers', session, {}, station) });
    const waves = useQuery({ queryKey: ['dispatch-waves', station], queryFn: () => api<Row[]>('/ops/v1/dispatch/waves', session, {}, station) });
    const create = useMutation({
        mutationFn: async (values: DispatchDraftValues) => {
            const draft = await api<{ waveId: number }>('/ops/v1/dispatch/waves', session, {
                method: 'POST', body: JSON.stringify(dispatchDraftPayload(values, selected)),
            }, station);
            return api(`/ops/v1/dispatch/waves/${draft.waveId}/publish`, session, { method: 'POST' }, station);
        },
        onSuccess: async () => {
            setSelected([]); message.success(t('dispatch.success'));
            await Promise.all(['dispatch-candidates', 'dispatch-waves'].map((key) => cache.invalidateQueries({ queryKey: [key, station] })));
        },
    });
    const error = candidates.error ?? drivers.error ?? waves.error ?? create.error;

    return <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        {error && <Alert type="error" message={error.message} />}
        <Card title={t('dispatch.create')}>
            <Form layout="inline" initialValues={{ serviceDate: dayjs() }} onFinish={(values) => create.mutate({ ...values, serviceDate: values.serviceDate.format('YYYY-MM-DD') })}>
                <Form.Item name="waveCode" rules={[{ required: true }]}><Input placeholder={t('dispatch.waveCode')} /></Form.Item>
                <Form.Item name="serviceDate" rules={[{ required: true }]}><DatePicker /></Form.Item>
                <Form.Item name="routeCode"><Input placeholder={t('dispatch.routeCode')} /></Form.Item>
                <Form.Item name="driverId" rules={[{ required: true }]}><Select style={{ width: 180 }} placeholder={t('dispatch.driver')}
                    options={(drivers.data ?? []).map((row) => ({ value: row.id, label: row.display_name ?? row.driver_code }))} /></Form.Item>
                <Button type="primary" htmlType="submit" disabled={!selected.length} loading={create.isPending}>{t('dispatch.submit', { count: selected.length })}</Button>
            </Form>
        </Card>
        <Card title={t('dispatch.inventory')}>
            <Table<Row> rowKey={(row) => String(row.tracking_no)} dataSource={candidates.data ?? []} loading={candidates.isLoading}
                rowSelection={{ selectedRowKeys: selected, onChange: (keys) => setSelected(keys.map(String)) }}
                columns={['tracking_no', 'status', 'route_code', 'promised_date', 'recipient_name', 'postal_code']
                    .map((key) => ({ title: t(key === 'status' ? 'common.status' : `field.${key}`, { defaultValue: key }), dataIndex: key }))} pagination={false} />
        </Card>
        <Card title={t('dispatch.recent')}><Table<Row> rowKey={(row) => String(row.id)} dataSource={waves.data ?? []}
            columns={['wave_code', 'service_date', 'route_code', 'status', 'driver_id', 'parcel_count']
                .map((key) => ({ title: t(key === 'status' ? 'common.status' : `field.${key}`, { defaultValue: key }), dataIndex: key }))} pagination={false} /></Card>
    </Space>;
}
