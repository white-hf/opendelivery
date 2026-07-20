import { useState } from 'react';
import { Alert, Button, Card, Form, Input, InputNumber, Modal, Select, Space, Table, Tag, Typography, Upload, message } from 'antd';
import { UploadOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, type Session } from '../api/client';
import { areaPayload, type AreaForm } from './areaPayload';
import { useTranslation } from 'react-i18next';
import { AreaMapEditor } from './AreaMapEditor';
import { parseAreaGeoJson } from './areaGeometry';

type AreaRow = {
    id: number; area_code: string; area_name: string; area_level: number; status: string;
    version_id?: number; version_no?: number; version_status?: string; geo_json?: string;
};
type PreferenceRow = { id: number; driver_id: number; driver_code: string; driver_name: string; priority: number; status: string };
type DriverRow = { id: number; driver_code: string; display_name: string };

export function AreaWorkspace({ session, station }: { session: Session; station: string }) {
    const { t } = useTranslation();
    const cache = useQueryClient();
    const [open, setOpen] = useState(false);
    const [preferenceArea, setPreferenceArea] = useState<AreaRow>();
    const [form] = Form.useForm<AreaForm>();
    const [preferenceForm] = Form.useForm();
    const geoJson = Form.useWatch('geoJson', form);
    const list = useQuery({
        queryKey: ['areas', station],
        queryFn: () => api<AreaRow[]>('/ops/v1/delivery-areas', session, {}, station),
        enabled: Boolean(station),
    });
    const drivers = useQuery({
        queryKey: ['dispatch-drivers', station],
        queryFn: () => api<DriverRow[]>('/ops/v1/dispatch/drivers', session, {}, station), enabled: Boolean(station),
    });
    const preferences = useQuery({
        queryKey: ['area-preferences', station, preferenceArea?.id],
        queryFn: () => api<PreferenceRow[]>(`/ops/v1/delivery-areas/${preferenceArea!.id}/driver-preferences`, session, {}, station),
        enabled: Boolean(preferenceArea),
    });
    const action = useMutation({
        mutationFn: ({ path, body }: { path: string; body?: unknown }) => api(path, session, {
            method: 'POST', body: body === undefined ? undefined : JSON.stringify(body),
        }, station),
        onSuccess: async () => {
            message.success(t('areas.success'));
            setOpen(false);
            form.resetFields();
            await cache.invalidateQueries({ queryKey: ['areas', station] });
        },
    });
    const error = list.error ?? drivers.error ?? preferences.error ?? action.error;
    const savePreference = useMutation({
        mutationFn: (values: { driverId: number; priority?: number; reason: string }) => api(
            `/ops/v1/delivery-areas/${preferenceArea!.id}/driver-preferences`, session,
            { method: 'POST', body: JSON.stringify(values) }, station),
        onSuccess: async () => {
            message.success(t('preferences.success')); preferenceForm.resetFields();
            await preferences.refetch();
        },
    });
    const displayedError = error ?? savePreference.error;

    return <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        {displayedError && <Alert type="error" message={displayedError.message} />}
        <Card title={t('areas.title')} extra={<Button type="primary" onClick={() => setOpen(true)}>{t('areas.import')}</Button>}>
            <Typography.Paragraph type="secondary">
                {t('areas.help')}
            </Typography.Paragraph>
            <Table<AreaRow> rowKey="id" dataSource={list.data ?? []} loading={list.isLoading} pagination={false}
                columns={[
                    { title: t('areas.code'), dataIndex: 'area_code' }, { title: t('areas.name'), dataIndex: 'area_name' },
                    { title: t('areas.level'), dataIndex: 'area_level' },
                    { title: t('areas.version'), render: (_, row) => row.version_no ?? t('areas.draft') },
                    { title: t('common.status'), render: (_, row) => { const status=row.version_status ?? row.status; return <Tag>{t(`status.${status}`, { defaultValue: status })}</Tag>; } },
                    { title: t('common.action'), render: (_, row) => <Space>
                        <Button size="small" onClick={() => setPreferenceArea(row)}>{t('preferences.button')}</Button>
                        {row.version_status === 'DRAFT' && <Button size="small" onClick={() => action.mutate({
                            path: `/ops/v1/delivery-areas/${row.id}/versions/${row.version_id}/validate`,
                        })}>{t('areas.validate')}</Button>}
                        {row.version_status === 'VALIDATED' && <Button size="small" type="primary" onClick={() => action.mutate({
                            path: `/ops/v1/delivery-areas/${row.id}/versions/${row.version_id}/publish`,
                            body: { reason: 'Approved in delivery area workspace' },
                        })}>{t('areas.publish')}</Button>}
                    </Space> },
                ]} />
        </Card>
        <Modal title={t('areas.dialog')} width={900} open={open} footer={null} onCancel={() => setOpen(false)} destroyOnHidden>
            <Form<AreaForm> form={form} layout="vertical" initialValues={{ areaLevel: 1 }} onFinish={(values) => {
                try { action.mutate({ path: '/ops/v1/delivery-areas', body: areaPayload(values) }); }
                catch { message.error(t('areas.invalidJson')); }
            }}>
                <Space.Compact block>
                <Form.Item name="areaCode" label={t('areas.code')} rules={[{ required: true, whitespace: true }]} style={{ width: '50%' }}><Input placeholder="DT-01" /></Form.Item>
                    <Form.Item name="areaLevel" label={t('areas.level')} rules={[{ required: true }]} style={{ width: '50%' }}><InputNumber min={1} max={9} style={{ width: '100%' }} /></Form.Item>
                </Space.Compact>
                <Form.Item name="areaName" label={t('areas.name')} rules={[{ required: true, whitespace: true }]}><Input placeholder="Downtown core" /></Form.Item>
                <AreaMapEditor station={station} value={geoJson} onChange={(next) => form.setFieldValue('geoJson', next)} />
                <Upload accept=".geojson,.json,application/geo+json,application/json" showUploadList={false} beforeUpload={async (file) => {
                    try {
                        const text = await file.text();
                        parseAreaGeoJson(text);
                        form.setFieldValue('geoJson', text);
                        await form.validateFields(['geoJson']);
                        message.success(t('areas.fileLoaded', { name: file.name }));
                    } catch {
                        message.error(t('areas.invalidJson'));
                    }
                    return false;
                }}><Button icon={<UploadOutlined />}>{t('areas.chooseFile')}</Button></Upload>
                <Form.Item name="geoJson" label={t('areas.geometry')} rules={[
                    { required: true, whitespace: true },
                    { validator: async (_, value) => { if (value) parseAreaGeoJson(value); } },
                ]}><Input.TextArea rows={6} placeholder={t('areas.geoJsonPlaceholder')} /></Form.Item>
                <Form.Item name="changeReason" label={t('areas.reason')} rules={[{ required: true, whitespace: true }]}><Input.TextArea rows={2} /></Form.Item>
                <Button type="primary" htmlType="submit" loading={action.isPending}>{t('areas.create')}</Button>
            </Form>
        </Modal>
        <Modal title={t('preferences.title', { area: preferenceArea?.area_code })} open={Boolean(preferenceArea)} footer={null}
            onCancel={() => setPreferenceArea(undefined)} destroyOnHidden>
            <Form form={preferenceForm} layout="vertical" initialValues={{ priority: 100 }} onFinish={savePreference.mutate}>
                <Form.Item name="driverId" label={t('dispatch.driver')} rules={[{ required: true }]}>
                    <Select options={(drivers.data ?? []).map((driver) => ({ value: driver.id, label: driver.display_name ?? driver.driver_code }))} />
                </Form.Item>
                <Form.Item name="priority" label={t('preferences.priority')} rules={[{ required: true }]}>
                    <InputNumber min={1} max={1000} style={{ width: '100%' }} />
                </Form.Item>
                <Form.Item name="reason" label={t('common.reason')} rules={[{ required: true, whitespace: true }]}><Input.TextArea rows={2} /></Form.Item>
                <Button htmlType="submit" type="primary" loading={savePreference.isPending}>{t('preferences.save')}</Button>
            </Form>
            <Table<PreferenceRow> rowKey="id" dataSource={preferences.data ?? []} loading={preferences.isLoading} pagination={false}
                style={{ marginTop: 20 }} columns={[
                    { title: t('dispatch.driver'), render: (_, row) => row.driver_name ?? row.driver_code },
                    { title: t('preferences.priority'), dataIndex: 'priority' },
                    { title: t('common.status'), dataIndex: 'status' },
                ]} />
        </Modal>
    </Space>;
}
