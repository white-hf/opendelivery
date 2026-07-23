import { useMemo, useState } from 'react';
import { Alert, Button, Card, Form, Input, Modal, Select, Space, Table, Tag, Tooltip, Upload, notification } from 'antd';
import { UploadOutlined, QuestionCircleOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, type Session } from '../api/client';
import { areaPayload, type AreaForm } from './areaPayload';
import { useTranslation } from 'react-i18next';
import { AreaMapEditor } from './AreaMapEditor';
import { areaGeoJsonSummary, parseAreaGeoJson } from './areaGeometry';

type AreaRow = {
    id: number; area_code: string; area_name: string; area_level: number; status: string;
    version_id?: number; version_no?: number; version_status?: string; geo_json?: string;
    primary_driver_id?: number; primary_driver_name?: string;
};
type PreferenceRow = { id: number; driver_id: number; driver_code: string; driver_name: string; priority: number; status: string };
type DriverRow = { id?: number; driver_id?: number; driver_code?: string; credential_id?: string; display_name?: string; driver_name?: string };
type VersionRow = { id: number; version_no: number; status: string; change_reason: string; created_at: string; geo_json: string };

export function AreaWorkspace({ session, station }: { session: Session; station: string }) {
    const { t } = useTranslation();
    const [notice, noticeContext] = notification.useNotification();
    const cache = useQueryClient();
    const [open, setOpen] = useState(false);
    const [editingArea, setEditingArea] = useState<AreaRow>();
    const [selectedArea, setSelectedArea] = useState<AreaRow>();
    const [viewingArea, setViewingArea] = useState<AreaRow>();
    const [stateArea, setStateArea] = useState<AreaRow>();
    const [preferenceArea, setPreferenceArea] = useState<AreaRow>();
    const [form] = Form.useForm<AreaForm>();
    const [preferenceForm] = Form.useForm();
    const [stateForm] = Form.useForm<{ reason: string }>();
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
    const versions = useQuery({
        queryKey: ['area-versions', station, viewingArea?.id],
        queryFn: () => api<VersionRow[]>(`/ops/v1/delivery-areas/${viewingArea!.id}/versions`, session, {}, station),
        enabled: Boolean(viewingArea),
    });
    const overviewGeoJson = useMemo(() => {
        const rows = selectedArea ? [selectedArea] : (list.data ?? []).filter((row) => row.status === 'ACTIVE');
        const features = rows.flatMap((row) => {
            try { return row.geo_json ? [{ type: 'Feature', properties: { areaCode: row.area_code }, geometry: JSON.parse(row.geo_json) }] : []; }
            catch { return []; }
        });
        return features.length ? JSON.stringify({ type: 'FeatureCollection', features }) : undefined;
    }, [list.data, selectedArea]);
    const action = useMutation({
        mutationFn: ({ path, body, method = 'POST' }: { path: string; body?: unknown; method?: string }) => api(path, session, {
            method, body: body === undefined ? undefined : JSON.stringify(body),
        }, station),
        onSuccess: async () => {
            notice.success({ message: t('areas.success'), placement: 'topRight', duration: 4 });
            setOpen(false);
            setEditingArea(undefined);
            form.resetFields();
            await cache.invalidateQueries({ queryKey: ['areas', station] });
        },
        onError: (caught) => notice.error({ message: t('common.operationFailed'), description: caught.message, placement: 'topRight', duration: 6 }),
    });
    const stateAction = useMutation({
        mutationFn: ({ area, reason }: { area: AreaRow; reason: string }) => api(
            area.status === 'ACTIVE' ? `/ops/v1/delivery-areas/${area.id}` : `/ops/v1/delivery-areas/${area.id}/activate`,
            session, { method: area.status === 'ACTIVE' ? 'DELETE' : 'POST', body: JSON.stringify({ reason }) }, station),
        onSuccess: async () => {
            notice.success({ message: t('areas.success'), placement: 'topRight' });
            setStateArea(undefined); stateForm.resetFields(); setSelectedArea(undefined);
            await cache.invalidateQueries({ queryKey: ['areas', station] });
        },
        onError: (caught) => notice.error({ message: t('common.operationFailed'), description: caught.message, placement: 'topRight' }),
    });
    const error = list.error ?? drivers.error ?? preferences.error;
    const savePreference = useMutation({
        mutationFn: (values: { driverId: number; priority?: number; reason: string }) => api(
            `/ops/v1/delivery-areas/${preferenceArea!.id}/driver-preferences`, session,
            { method: 'POST', body: JSON.stringify(values) }, station),
        onSuccess: async () => {
            notice.success({ message: t('preferences.success'), placement: 'topRight', duration: 4 }); preferenceForm.resetFields();
            await Promise.all([preferences.refetch(), cache.invalidateQueries({ queryKey: ['areas', station] })]);
        },
        onError: (caught) => notice.error({ message: t('common.operationFailed'), description: caught.message, placement: 'topRight', duration: 6 }),
    });
    const deletePreference = useMutation({
        mutationFn: (preferenceId: number) => api(
            `/ops/v1/delivery-areas/${preferenceArea!.id}/driver-preferences/${preferenceId}`, session,
            { method: 'DELETE' }, station),
        onSuccess: async () => {
            notice.success({ message: '解绑司机成功', placement: 'topRight', duration: 4 });
            await Promise.all([preferences.refetch(), cache.invalidateQueries({ queryKey: ['areas', station] })]);
        },
        onError: (caught) => notice.error({ message: t('common.operationFailed'), description: caught.message, placement: 'topRight', duration: 6 }),
    });
    const displayedError = error;

    return <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        {noticeContext}
        {displayedError && <Alert type="error" message={displayedError.message} />}
        <Card title={t('areas.title')} extra={
            <Space>
                {selectedArea && <Button size="small" onClick={() => setSelectedArea(undefined)}>查看全部区域地图</Button>}
                <Button type="primary" onClick={() => {
                    setEditingArea(undefined); form.resetFields(); form.setFieldValue('areaLevel', 1); setOpen(true);
                }}>{t('areas.add')}</Button>
            </Space>
        }>
            <div className="area-map-container" style={{ marginBottom: 16 }}>
                <AreaMapEditor station={station} value={overviewGeoJson} readOnly />
            </div>
            <Table<AreaRow>
                rowKey="id"
                dataSource={list.data ?? []}
                loading={list.isLoading}
                pagination={false}
                rowClassName={(row) => selectedArea?.id === row.id ? 'area-table-row-selected' : 'area-table-row'}
                onRow={(row) => ({
                    onMouseEnter: () => setSelectedArea(row),
                    onClick: () => setSelectedArea(row),
                })}
                columns={[
                    { title: t('areas.code'), dataIndex: 'area_code', width: 140 },
                    { title: t('areas.name'), dataIndex: 'area_name', width: 220 },
                    { title: '责任司机', render: (_, row) => row.primary_driver_name ? <Tag color="blue">{row.primary_driver_name}</Tag> : <Tag color="red">无责任司机</Tag> },
                    { title: t('common.status'), render: (_, row) => <Tag color={row.status === 'ACTIVE' ? 'green' : 'default'}>{row.status === 'ACTIVE' ? '启用中' : '已停用'}</Tag>, width: 100 },
                    { title: t('common.action'), render: (_, row) => <Space onClick={(e) => e.stopPropagation()}>
                        <Button size="small" onClick={() => setViewingArea(row)}>{t('common.view')}</Button>
                        {row.status === 'ACTIVE' && <Button size="small" onClick={async () => {
                            setEditingArea(row);
                            try {
                                const prefs = await api<PreferenceRow[]>(`/ops/v1/delivery-areas/${row.id}/driver-preferences`, session, {}, station);
                                const activePrefs = (prefs ?? []).filter((p) => p.status === 'ACTIVE').sort((a, b) => a.priority - b.priority);
                                const initialDriverIds = activePrefs.length > 0 ? activePrefs.map((p) => p.driver_id) : (row.primary_driver_id ? [row.primary_driver_id] : []);
                                form.setFieldsValue({
                                    areaCode: row.area_code, areaName: row.area_name, areaLevel: row.area_level,
                                    driverIds: initialDriverIds,
                                    geoJson: row.geo_json ?? '', changeReason: '',
                                });
                            } catch {
                                form.setFieldsValue({
                                    areaCode: row.area_code, areaName: row.area_name, areaLevel: row.area_level,
                                    driverIds: row.primary_driver_id ? [row.primary_driver_id] : [],
                                    geoJson: row.geo_json ?? '', changeReason: '',
                                });
                            }
                            setOpen(true);
                        }}>{t('common.edit')}</Button>}
                        <Button size="small" danger={row.status === 'ACTIVE'} onClick={() => {
                            stateForm.resetFields(); setStateArea(row);
                        }}>{row.status === 'ACTIVE' ? t('common.delete') : t('areas.reactivate')}</Button>
                    </Space> },
                ]}
            />
        </Card>

        <Modal title={editingArea ? t('areas.editTitle', { code: editingArea.area_code }) : t('areas.dialog')} width={650} open={open} onCancel={() => setOpen(false)} destroyOnHidden
            styles={{ body: { maxHeight: 'calc(100vh - 210px)', overflowY: 'auto' } }}
            footer={[
                <Button key="cancel" onClick={() => setOpen(false)}>{t('common.cancel')}</Button>,
                <Button key="create" type="primary" htmlType="submit" form="delivery-area-form" loading={action.isPending}>{editingArea ? t('common.save') : t('areas.create')}</Button>,
            ]}>
            <Form<AreaForm> id="delivery-area-form" form={form} layout="vertical" initialValues={{ areaLevel: 1 }} onFinish={(values) => {
                try {
                    const payload=areaPayload(values);
                    action.mutate(editingArea ? {
                        path: `/ops/v1/delivery-areas/${editingArea.id}`, method: 'PUT',
                        body: { areaName: payload.areaName, areaLevel: payload.areaLevel, driverIds: payload.driverIds, geoJson: payload.geoJson, changeReason: payload.changeReason },
                    } : { path: '/ops/v1/delivery-areas', body: payload });
                }
                catch { notice.error({ message: t('areas.invalidJson'), placement: 'topRight', duration: 6 }); }
            }}>
                <Space style={{ display: 'flex' }} align="start">
                    <Form.Item name="areaCode" label={t('areas.code')} rules={[{ required: true, whitespace: true }]} style={{ width: 160 }}>
                        <Input placeholder="如 DT-01" disabled={Boolean(editingArea)} />
                    </Form.Item>
                    <Form.Item name="areaName" label={t('areas.name')} rules={[{ required: true, whitespace: true }]} style={{ width: 220 }}>
                        <Input placeholder="如 市中心区域" />
                    </Form.Item>
                    <Form.Item name="areaLevel" hidden initialValue={1}>
                        <Input hidden />
                    </Form.Item>
                </Space>

                <Form.Item
                    name="driverIds"
                    label={
                        <span>
                            责任司机&nbsp;
                            <Tooltip title="首位为主责任人，可多选添加备选司机">
                                <QuestionCircleOutlined style={{ color: '#8c8c8c' }} />
                            </Tooltip>
                        </span>
                    }
                    rules={[{ required: true, type: 'array', min: 1, message: '请选择责任司机' }]}
                >
                    <Select
                        mode="multiple"
                        maxTagCount="responsive"
                        popupMatchSelectWidth={false}
                        placeholder="选择责任司机（首选为主责任人）"
                        options={(drivers.data ?? []).map((driver) => ({
                            value: driver.driver_id ?? driver.id!,
                            label: driver.driver_name ?? driver.display_name ?? driver.driver_code ?? String(driver.driver_id ?? driver.id)
                        }))}
                    />
                </Form.Item>

                <Form.Item
                    name="geoJson"
                    label={
                        <span>
                            区域 GeoJSON 边界数据&nbsp;
                            <Upload accept=".geojson,.json,application/geo+json,application/json" showUploadList={false} beforeUpload={async (file) => {
                                try {
                                    const text = await file.text();
                                    parseAreaGeoJson(text);
                                    const summary = areaGeoJsonSummary(text);
                                    form.setFieldValue('geoJson', text);
                                    await form.validateFields(['geoJson']);
                                    notice.success({
                                        message: t('areas.fileLoaded', { name: file.name }),
                                        description: t('areas.importSummary', summary), placement: 'topRight', duration: 6,
                                    });
                                } catch {
                                    notice.error({ message: t('areas.invalidJson'), placement: 'topRight', duration: 6 });
                                }
                                return false;
                            }}>
                                <Button size="small" icon={<UploadOutlined />}>导入文件</Button>
                            </Upload>
                        </span>
                    }
                    rules={[
                        { required: true, whitespace: true, message: '请粘贴或导入 GeoJSON 边界数据' },
                        { validator: async (_, value) => { if (value) parseAreaGeoJson(value); } },
                    ]}
                >
                    <Input.TextArea rows={6} placeholder="请粘贴由 geojson.io 等工具导出的 GeoJSON 边界数据" />
                </Form.Item>

                <Form.Item name="changeReason" label={t('areas.reason')} rules={[{ required: true, whitespace: true, message: '请输入变更原因' }]}>
                    <Input placeholder="简要说明新增或调整原因" />
                </Form.Item>
            </Form>
        </Modal>

        <Modal title={t('areas.detailTitle', { code: viewingArea?.area_code })} width={800} open={Boolean(viewingArea)}
            footer={<Button onClick={() => setViewingArea(undefined)}>{t('common.close')}</Button>}
            onCancel={() => setViewingArea(undefined)} destroyOnHidden>
            {viewingArea && <>
                <Space wrap style={{ marginBottom: 12 }}>
                    <Tag>{viewingArea.area_name}</Tag>
                    <Tag color={viewingArea.status === 'ACTIVE' ? 'green' : 'default'}>{viewingArea.status}</Tag>
                </Space>
                <AreaMapEditor station={station} value={viewingArea.geo_json} readOnly />
                <Table<VersionRow> rowKey="id" size="small" loading={versions.isLoading} dataSource={versions.data ?? []}
                    pagination={false} style={{ marginTop: 16 }} columns={[
                        { title: t('areas.version'), dataIndex: 'version_no' },
                        { title: t('common.status'), dataIndex: 'status' },
                        { title: t('areas.reason'), dataIndex: 'change_reason' },
                        { title: t('areas.createdAt'), dataIndex: 'created_at' },
                    ]} />
            </>}
        </Modal>

        <Modal title={stateArea?.status === 'ACTIVE' ? t('areas.deactivateTitle') : t('areas.reactivateTitle')}
            open={Boolean(stateArea)} onCancel={() => setStateArea(undefined)} destroyOnHidden
            footer={[
                <Button key="cancel" onClick={() => setStateArea(undefined)}>{t('common.cancel')}</Button>,
                <Button key="confirm" danger={stateArea?.status === 'ACTIVE'} type="primary" loading={stateAction.isPending}
                    onClick={() => stateForm.submit()}>{t('common.confirm')}</Button>,
            ]}>
            <Alert type="warning" showIcon message={stateArea?.status === 'ACTIVE' ? t('areas.deactivateWarning') : t('areas.reactivateWarning')} />
            <Form form={stateForm} layout="vertical" onFinish={(values) => stateArea && stateAction.mutate({ area: stateArea, reason: values.reason })}>
                <Form.Item name="reason" label={t('common.reason')} rules={[{ required: true, whitespace: true }]} style={{ marginTop: 16 }}>
                    <Input.TextArea rows={3} />
                </Form.Item>
            </Form>
        </Modal>

        <Modal title={t('preferences.title', { area: preferenceArea?.area_code })} open={Boolean(preferenceArea)} footer={null}
            onCancel={() => setPreferenceArea(undefined)} destroyOnHidden>
            <Form form={preferenceForm} layout="vertical" initialValues={{ priority: 100 }} onFinish={savePreference.mutate}>
                <Form.Item name="driverId" label={t('dispatch.driver')} rules={[{ required: true }]}>
                    <Select options={(drivers.data ?? []).map((driver) => ({
                        value: driver.driver_id ?? driver.id!,
                        label: driver.driver_name ?? driver.display_name ?? driver.driver_code ?? String(driver.driver_id ?? driver.id)
                    }))} />
                </Form.Item>
                <Form.Item name="priority" label={t('preferences.priority')} rules={[{ required: true }]}>
                    <Input placeholder="优先级数值（1为最高）" />
                </Form.Item>
                <Form.Item name="reason" label={t('common.reason')} rules={[{ required: true, whitespace: true }]}><Input.TextArea rows={2} /></Form.Item>
                <Button htmlType="submit" type="primary" loading={savePreference.isPending}>{t('preferences.save')}</Button>
            </Form>
            <Table<PreferenceRow> rowKey="id" dataSource={preferences.data ?? []} loading={preferences.isLoading} pagination={false}
                style={{ marginTop: 20 }} columns={[
                    { title: t('dispatch.driver'), render: (_, row) => row.driver_name ?? row.driver_code },
                    { title: t('preferences.priority'), dataIndex: 'priority' },
                    { title: t('common.status'), dataIndex: 'status' },
                    { title: t('common.action'), render: (_, row) => (
                        <Button size="small" danger onClick={() => deletePreference.mutate(row.id)} loading={deletePreference.isPending}>
                            解绑/删除
                        </Button>
                    ) },
                ]} />
        </Modal>
    </Space>;
}
