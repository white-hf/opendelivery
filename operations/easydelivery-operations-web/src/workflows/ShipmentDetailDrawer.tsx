import { useState, useEffect } from 'react';
import { Drawer, Form, Input, Button, Tag, Card, Timeline, message, Space, Spin, Alert } from 'antd';
import { api, type Session } from '../api/client';

export function ShipmentDetailDrawer({
    trackingNo,
    station,
    session,
    onClose,
}: {
    trackingNo: string | null;
    station: number | string;
    session: Session;
    onClose: () => void;
}) {

    const [loading, setLoading] = useState(false);
    const [editing, setEditing] = useState(false);
    const [data, setData] = useState<any>(null);
    const [notFound, setNotFound] = useState(false);
    const [form] = Form.useForm();

    useEffect(() => {
        if (!trackingNo) {
            setData(null);
            return;
        }
        setLoading(true);
        setEditing(false);
        setNotFound(false);
        // Fetch parcel/shipment details
        api<any>(`/ops/v1/parcels/search?trackingNo=${encodeURIComponent(trackingNo)}`, session, {}, station)
            .then((res) => {
                setData(res);
                form.setFieldsValue({
                    recipient_name: res.recipient_name ?? res.recipientName ?? '',
                    recipient_phone: res.recipient_phone ?? res.recipientPhone ?? '',
                    address_line1: res.address_line1 ?? res.addressLine1 ?? '',
                    postal_code: res.postal_code ?? res.postalCode ?? '',
                });
            })
            .catch(() => { setData(null); setNotFound(true); })
            .finally(() => setLoading(false));
    }, [trackingNo, station, session]);

    const handleSave = async (values: any) => {
        try {
            setLoading(true);
            await api(`/ops/v1/parcels/${encodeURIComponent(trackingNo!)}/address-override`, session, {
                method: 'POST',
                body: JSON.stringify({
                    recipientName: values.recipient_name,
                    recipientPhone: values.recipient_phone,
                    addressLine1: values.address_line1,
                    postalCode: values.postal_code,
                }),
            }, station);
            message.success('运单地址与收件人修改成功');
            setEditing(false);
        } catch (e: any) {
            message.error(e.message || '运单地址修改失败');
        } finally {
            setLoading(false);
        }
    };

    return (
        <Drawer
            title={
                <Space>
                    <i className="fa-solid fa-box-archive" style={{ color: '#1677ff' }}></i>
                    <span>📦 运单全链路明细与修改 (Shipment Detail)</span>
                </Space>
            }
            width={520}
            open={Boolean(trackingNo)}
            onClose={onClose}
            destroyOnClose
        >
            {loading && !data ? (
                <Spin style={{ display: 'block', margin: '40px auto' }} />
            ) : notFound ? (
                <Alert type="warning" showIcon message="未找到该运单" description="请确认 tracking number 正确，且包裹属于当前站点。" />
            ) : data ? (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                    <Alert
                        type="info"
                        showIcon
                        message={<b style={{ fontSize: '15px' }}>单号 / 追踪号: {data.tracking_no}</b>}
                        description={
                            <div style={{ marginTop: '6px', fontSize: '12.5px' }}>
                                <div>当前 Custody 托管归属: <Tag color="blue">{data.custody ?? 'STATION_WAREHOUSE'}</Tag></div>
                                <div>履约状态: <Tag color="green">{data.status ?? 'RECEIVED'}</Tag></div>
                            </div>
                        }
                    />

                    <Card
                        title={
                            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                <span><i className="fa-solid fa-user-gear"></i> 收件人与派送地址属性</span>
                                {!editing && (
                                    <Button size="small" type="primary" ghost onClick={() => setEditing(true)}>
                                        <i className="fa-solid fa-pen" style={{ marginRight: 4 }}></i> 编辑修改
                                    </Button>
                                )}
                            </div>
                        }
                        size="small"
                    >
                        <Form form={form} layout="vertical" onFinish={handleSave} disabled={!editing}>
                            <Form.Item name="recipient_name" label="收件人姓名" rules={[{ required: true }]}>
                                <Input placeholder="收件人姓名" />
                            </Form.Item>
                            <Form.Item name="recipient_phone" label="联系电话" rules={[{ required: true }]}>
                                <Input placeholder="联系电话" />
                            </Form.Item>
                            <Form.Item name="address_line1" label="派送地址 (Address Line 1)" rules={[{ required: true }]}>
                                <Input placeholder="派送详细地址" />
                            </Form.Item>
                            <Form.Item name="postal_code" label="邮编 (Postal Code)" rules={[{ required: true }]}>
                                <Input placeholder="邮编" />
                            </Form.Item>

                            {editing && (
                                <Space style={{ marginTop: 8, width: '100%', justifyContent: 'flex-end' }}>
                                    <Button onClick={() => setEditing(false)}>取消</Button>
                                    <Button type="primary" htmlType="submit" loading={loading}>
                                        保存修改并自动 Geocode 重新校验
                                    </Button>
                                </Space>
                            )}
                        </Form>
                    </Card>

                    <Card title={<span><i className="fa-solid fa-timeline"></i> 全链路操作履约时间线</span>} size="small">
                        <Timeline
                            items={(data.timeline ?? [
                                { time: '2026-07-24 08:30:00', title: '到仓扫码入库 (Received at Station)', user: 'warehouse.yhz' },
                                { time: '2026-07-24 06:15:00', title: '干线班车到达 (Linehaul Arrival)', user: 'system' }
                            ]).map((item: any) => ({
                                children: (
                                    <div>
                                        <div style={{ fontWeight: 'bold' }}>{item.title}</div>
                                        <div style={{ fontSize: '11px', color: '#8c8c8c' }}>{item.time} · 操作人: {item.user}</div>
                                    </div>
                                )
                            }))}
                        />
                    </Card>
                </div>
            ) : null}
        </Drawer>
    );
}
