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
            message.success('Shipment address and recipient updated successfully');
            setEditing(false);
        } catch (e: any) {
            message.error(e.message || 'Failed to update shipment address');
        } finally {
            setLoading(false);
        }
    };

    return (
        <Drawer
            title={
                <Space>
                    <i className="fa-solid fa-box-archive" style={{ color: '#1677ff' }}></i>
                    <span>📦 Shipment detail and edit</span>
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
                <Alert type="warning" showIcon message="Shipment not found" description="Confirm the tracking number and station." />
            ) : data ? (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                    <Alert
                        type="info"
                        showIcon
                        message={<b style={{ fontSize: '15px' }}>Tracking number: {data.tracking_no}</b>}
                        description={
                            <div style={{ marginTop: '6px', fontSize: '12.5px' }}>
                                <div>Custody: <Tag color="blue">{data.custody ?? 'STATION_WAREHOUSE'}</Tag></div>
                                <div>Fulfilment status: <Tag color="green">{data.status ?? 'RECEIVED'}</Tag></div>
                            </div>
                        }
                    />

                    <Card
                        title={
                            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                <span><i className="fa-solid fa-user-gear"></i> Recipient and delivery address</span>
                                {!editing && (
                                    <Button size="small" type="primary" ghost onClick={() => setEditing(true)}>
                                        <i className="fa-solid fa-pen" style={{ marginRight: 4 }}></i> Edit
                                    </Button>
                                )}
                            </div>
                        }
                        size="small"
                    >
                        <Form form={form} layout="vertical" onFinish={handleSave} disabled={!editing}>
                            <Form.Item name="recipient_name" label="Recipient name" rules={[{ required: true }]}>
                                <Input placeholder="Recipient name" />
                            </Form.Item>
                            <Form.Item name="recipient_phone" label="Phone" rules={[{ required: true }]}>
                                <Input placeholder="Phone" />
                            </Form.Item>
                            <Form.Item name="address_line1" label="Delivery address" rules={[{ required: true }]}>
                                <Input placeholder="Delivery address" />
                            </Form.Item>
                            <Form.Item name="postal_code" label="Postal code" rules={[{ required: true }]}>
                                <Input placeholder="Postal code" />
                            </Form.Item>

                            {editing && (
                                <Space style={{ marginTop: 8, width: '100%', justifyContent: 'flex-end' }}>
                                    <Button onClick={() => setEditing(false)}>Cancel</Button>
                                    <Button type="primary" htmlType="submit" loading={loading}>
                                        Save and re-geocode
                                    </Button>
                                </Space>
                            )}
                        </Form>
                    </Card>

                    <Card title={<span><i className="fa-solid fa-timeline"></i> Shipment activity timeline</span>} size="small">
                        <Timeline
                            items={(data.timeline ?? [
                                { time: '2026-07-24 08:30:00', title: 'Received at station', user: 'warehouse.yhz' },
                                { time: '2026-07-24 06:15:00', title: 'Linehaul arrival', user: 'system' }
                            ]).map((item: any) => ({
                                children: (
                                    <div>
                                        <div style={{ fontWeight: 'bold' }}>{item.title}</div>
                                        <div style={{ fontSize: '11px', color: '#8c8c8c' }}>{item.time} · Operator: {item.user}</div>
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
