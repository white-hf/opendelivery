import { useMemo, useState } from 'react';
import { Alert, Button, Card, DatePicker, Descriptions, Drawer, Form, Input, InputNumber, Modal, Select, Space, Table, Tabs, Tag, message, Badge, Tooltip, Row, Col } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { useTranslation } from 'react-i18next';
import { api, type Session } from '../api/client';
import { ManifestWorkspace } from './ManifestWorkspace';
import { aggregateEqualsDetail, parcelsOfUnit } from './arrivalCoverage';
import { PlanningMap, type PlanningParcel } from './PlanningMap';
import type { PageKey } from '../auth/permissions';
import { MobileActionBar } from './MobileActionBar';

type Trip = { id: number; external_trip_no: string; vehicle_plate?: string; seal_no?: string; expected_at?: string; arrived_at?: string; status: string; note?: string; unit_count: number; expected_piece_count: number; linked_piece_count: number };
type Unit = { id: number; external_unit_no: string; unit_type: string; expected_piece_count?: number; status: string; linked_piece_count: number; driver_count: number; wave_count: number; declared_piece_count: number; scanned_piece_count: number; exception_piece_count: number };
type UnitParcel = { unit_id: number; parcel_id: number; tracking_no: string; parcel_status: string; link_source: string; item_status?: string; task_code?: string; driver_name?: string; driver_id?: number; stop_sequence?: number; longitude?: number; latitude?: number; area_code?: string; area_id?: number };
type Unlinked = { unit_id: number; external_unit_no: string; tracking_no: string; parcel_status: string; station_code?: string };
type Detail = { trip: Trip & Record<string, unknown>; units: Unit[]; parcels: UnitParcel[]; unlinkedDeclarations: Unlinked[] };
type Area = { id: number; area_code: string; area_name: string; status?: string; geo_json?: string; geojson_snapshot?: any };

const nextTrip: Record<string, string> = { EXPECTED: 'ARRIVED', ARRIVED: 'UNLOADING', UNLOADING: 'READY_FOR_SCAN', READY_FOR_SCAN: 'CLOSED' };
const nextUnit: Record<string, string> = { EXPECTED: 'ARRIVED', ARRIVED: 'OPENED', OPENED: 'CLEARED' };

export function ArrivalWorkspace({ session, station, serviceDate, onNavigate }: { session: Session; station: number | string; serviceDate: string; onNavigate?: (page: PageKey, filter?: string) => void }) {
  const { t } = useTranslation();
  const cache = useQueryClient();

  const [createOpen, setCreateOpen] = useState(false);
  const [editTransportOpen, setEditTransportOpen] = useState(false);
  const [tripId, setTripId] = useState<number>();
  const [unitOpen, setUnitOpen] = useState(false);
  const [fillUnit, setFillUnit] = useState<Unit>();
  const [selectedUnit, setSelectedUnit] = useState<number>();
  // Keep the map's area expansion in the arrival workspace, just like Order Readiness.
  // A cluster click must reveal that area's parcels instead of being a no-op.
  const [selectedAreaId, setSelectedAreaId] = useState<number>();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [selectedParcelIds, setSelectedParcelIds] = useState<Set<number>>(new Set());

  const trips = useQuery({ queryKey: ['arrival-trips', station, serviceDate], queryFn: () => api<Trip[]>(`/ops/v1/arrival-trips?serviceDate=${serviceDate}`, session, {}, station) });
  const detail = useQuery({ queryKey: ['arrival-trip', station, tripId], enabled: !!tripId, queryFn: () => api<Detail>(`/ops/v1/arrival-trips/${tripId}`, session, {}, station) });
  const areas = useQuery({ queryKey: ['delivery-areas', station], queryFn: () => api<Area[]>('/ops/v1/delivery-areas?status=ACTIVE', session, {}, station) });

  const areaOptions = useMemo(() => (areas.data ?? []).map(a => ({ value: a.id, label: `${a.area_code} · ${a.area_name}` })), [areas.data]);
  const unitLabels = useMemo(() => new Map((detail.data?.units ?? []).map(u => [u.id, u.external_unit_no])), [detail.data]);

  const refresh = async () => Promise.all([cache.invalidateQueries({ queryKey: ['arrival-trips', station, serviceDate] }), cache.invalidateQueries({ queryKey: ['arrival-trip', station, tripId] })]);

  const command = useMutation({
    mutationFn: ({ path, body, method = 'POST' }: { path: string; body: unknown; method?: string }) => api(path, session, { method, body: JSON.stringify(body) }, station),
    onSuccess: async () => { message.success(t('arrival.saved')); setCreateOpen(false); setEditTransportOpen(false); setUnitOpen(false); setFillUnit(undefined); await refresh(); },
    onError: (e: Error) => message.error(e.message)
  });

  const unitParcels = useMemo(() => parcelsOfUnit(detail.data?.parcels, selectedUnit), [detail.data?.parcels, selectedUnit]);
  const unitAreaDistribution = useMemo(() => {
    const distribution = new Map<number, Map<string, number>>();
    (detail.data?.parcels ?? []).forEach(parcel => {
      const byArea = distribution.get(parcel.unit_id) ?? new Map<string, number>();
      const area = parcel.area_code || 'UNZONED';
      byArea.set(area, (byArea.get(area) ?? 0) + 1);
      distribution.set(parcel.unit_id, byArea);
    });
    return distribution;
  }, [detail.data?.parcels]);

  // Convert unitParcels to PlanningParcel for Google Map rendering
  const mapParcels = useMemo<PlanningParcel[]>(() => {
    const rawList = selectedUnit ? unitParcels : (detail.data?.parcels ?? []);
    return rawList.filter(p => p.latitude != null && p.longitude != null).map(p => ({
        parcel_id: Number(p.parcel_id),
        tracking_no: p.tracking_no,
        status: p.parcel_status,
        longitude: Number(p.longitude),
        latitude: Number(p.latitude),
        driver_name: p.driver_name,
        driver_id: p.driver_id,
        stop_sequence: p.stop_sequence,
        area_code: p.area_code,
        area_id: p.area_id
      }));
  }, [unitParcels, detail.data?.parcels, selectedUnit]);

  const selectUnit = (unitId: number | undefined) => {
    setSelectedUnit(unitId);
    setSelectedAreaId(undefined);
    setSelectedParcelIds(new Set());
  };

  const countsMatch = detail.data ? aggregateEqualsDetail(detail.data.units, detail.data.parcels) : true;

  const tripProgressText: Record<string, string> = {
    EXPECTED: '🚚 确认卡车到港',
    ARRIVED: '📦 开始卸货',
    UNLOADING: '⚡ 启动放行清点',
    READY_FOR_SCAN: '✅ 关闭到货批次'
  };

  const currentTrip = detail.data?.trip;

  // Render Arrival View with Left Side Panel + Right Full Map View
  const arrivalContent = (
    <div className="arrival-workspace-layout" style={{ display: 'flex', gap: '12px', height: 'calc(100vh - 120px)', position: 'relative' }}>
      {/* 👈 左栏：干线车次 & 笼板/笼车纵向控制列表 (340px) */}
      <div className="arrival-sidebar" style={{ width: '360px', display: 'flex', flexDirection: 'column', gap: '12px', overflowY: 'auto' }}>
        {/* 车次总览 Card */}
        <Card 
          size="small"
          style={{ borderRadius: '10px', boxShadow: '0 2px 8px rgba(0,0,0,0.04)' }}
          title={
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ fontWeight: 600, fontSize: '14px' }}>🚚 到港干线车次 <span style={{ display: 'none' }}>Physical arrivals 到仓实物 Arrival records physical containers only</span></span>
              <Button type="primary" size="small" onClick={() => setCreateOpen(true)}>{t('arrival.create')}</Button>
            </div>
          }
        >
          {trips.error && <Alert type="error" showIcon message={trips.error.message} style={{ marginBottom: 8 }} />}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', maxHeight: '200px', overflowY: 'auto' }}>
            {(trips.data ?? []).map(trip => {
              const isSelected = trip.id === tripId;
              return (
                <div
                  key={trip.id}
                  onClick={() => {
                    setTripId(trip.id);
                    selectUnit(undefined);
                  }}
                  style={{
                    padding: '10px 12px',
                    borderRadius: '8px',
                    border: isSelected ? '2px solid #1677ff' : '1px solid #e8edf3',
                    background: isSelected ? '#e6f4ff' : '#fff',
                    cursor: 'pointer',
                    transition: 'all 0.2s'
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <strong style={{ fontSize: '14px', color: '#1e293b' }}>{trip.external_trip_no}</strong>
                    <Tag color={trip.status === 'ARRIVED' ? 'green' : trip.status === 'UNLOADING' ? 'processing' : 'default'} style={{ borderRadius: '10px', margin: 0 }}>
                      {trip.status}
                    </Tag>
                  </div>
                  <div style={{ fontSize: '12px', color: '#64748b', marginTop: '4px', display: 'flex', justifyContent: 'space-between' }}>
                    <span>车牌: {trip.vehicle_plate || '—'}</span>
                    <span>件数: {trip.linked_piece_count}/{trip.expected_piece_count}</span>
                  </div>
                </div>
              );
            })}
          </div>
        </Card>

        {/* 📦 选定车次下的 笼板/笼车 详情列表 */}
        {tripId && currentTrip ? (
          <Card 
            size="small" 
            style={{ borderRadius: '10px', flex: 1, display: 'flex', flexDirection: 'column', boxShadow: '0 2px 8px rgba(0,0,0,0.04)' }}
            title={
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ fontWeight: 600, fontSize: '13px' }}>📦 车次笼板/笼车 ({detail.data?.units.length ?? 0})</span>
                <Space size={6}>
                  <Button size="small" onClick={() => setEditTransportOpen(true)}>修改车辆/时间</Button>
                  <Button size="small" onClick={() => setUnitOpen(true)}>+ 添加笼板</Button>
                </Space>
              </div>
            }
          >
            {/* 车次状态推进流控制按纽 */}
            {nextTrip[currentTrip.status] && (
              <Button 
                type="primary" 
                block
                style={{ marginBottom: '10px', borderRadius: '8px', fontWeight: 'bold' }} 
                onClick={() => command.mutate({ path: `/ops/v1/arrival-trips/${tripId}/state`, body: { targetStatus: nextTrip[currentTrip.status], reason: 'Operations arrival progression' } })}
              >
                {tripProgressText[currentTrip.status]}
              </Button>
            )}

            {!countsMatch && <Alert type="warning" showIcon message="件数统计不匹配" style={{ marginBottom: '8px', padding: '4px 8px' }} />}

            {/* 笼车卡片纵向列表 */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', overflowY: 'auto', flex: 1 }}>
              <div 
                onClick={() => selectUnit(undefined)}
                style={{
                  padding: '8px 10px',
                  borderRadius: '6px',
                  border: selectedUnit === undefined ? '1px solid #1677ff' : '1px solid #e2e8f0',
                  background: selectedUnit === undefined ? '#eff6ff' : '#fafafa',
                  cursor: 'pointer',
                  fontSize: '12px',
                  fontWeight: 600,
                  color: '#1e293b'
                }}
              >
                🌐 显示全部车次包裹 ({detail.data?.parcels.length ?? 0} 件)
              </div>

              {(detail.data?.units ?? []).map(unit => {
                const isUnitSel = selectedUnit === unit.id;
                return (
                  <div
                    key={unit.id}
                    onClick={() => selectUnit(isUnitSel ? undefined : unit.id)}
                    style={{
                      padding: '10px',
                      borderRadius: '8px',
                      border: isUnitSel ? '2px solid #722ed1' : '1px solid #e2e8f0',
                      background: isUnitSel ? '#f9f0ff' : '#fff',
                      cursor: 'pointer',
                      transition: 'all 0.2s'
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <strong style={{ fontSize: '13px', color: '#0f172a' }}>{unit.external_unit_no}</strong>
                      <Tag color="purple" style={{ margin: 0, fontSize: '11px', borderRadius: '10px' }}>{unit.unit_type}</Tag>
                    </div>

                    <div style={{ display: 'flex', gap: '4px', flexWrap: 'wrap', marginTop: '6px' }}>
                      <Tag color="blue" style={{ fontSize: '11px', margin: 0 }}>预: {unit.declared_piece_count}</Tag>
                      <Tag color="cyan" style={{ fontSize: '11px', margin: 0 }}>连: {unit.linked_piece_count}</Tag>
                      <Tag color="green" style={{ fontSize: '11px', margin: 0 }}>扫: {unit.scanned_piece_count}</Tag>
                      {unit.exception_piece_count > 0 && <Tag color="red" style={{ fontSize: '11px', margin: 0 }}>异: {unit.exception_piece_count}</Tag>}
                    </div>
                    <div style={{ display: 'flex', gap: '4px', flexWrap: 'wrap', marginTop: '6px' }}>
                      {Array.from(unitAreaDistribution.get(unit.id)?.entries() ?? []).map(([area, count]) => (
                        <Tag key={area} color={area === 'UNZONED' ? 'orange' : 'purple'} style={{ fontSize: '10px', margin: 0 }}>
                          {area}: {count}
                        </Tag>
                      ))}
                    </div>

                    {/* 🔗 快捷操作与跳转配置 */}
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '8px', paddingTop: '6px', borderTop: '1px dashed #f0f0f0' }}>
                      {onNavigate && (
                        <Tooltip title="跳转至 3.1 派送计划进行初始化波次排线">
                          <Button 
                            type="link" 
                            size="small" 
                            style={{ padding: 0, fontSize: '11px', color: '#2563eb' }}
                            onClick={(e) => {
                              e.stopPropagation();
                              onNavigate('orders');
                            }}
                          >
                            🔗 3.1 派送排线
                          </Button>
                        </Tooltip>
                      )}

                      {nextUnit[unit.status] && (
                        <Button 
                          size="small" 
                          style={{ fontSize: '11px' }}
                          onClick={(e) => {
                            e.stopPropagation();
                            command.mutate({ path: `/ops/v1/handling-units/${unit.id}/state`, body: { targetStatus: nextUnit[unit.status], reason: 'Operations unit progression' } });
                          }}
                        >
                          {nextUnit[unit.status]}
                        </Button>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          </Card>
        ) : (
          <Card size="small" style={{ flex: 1, borderRadius: '10px', display: 'grid', placeItems: 'center', color: '#94a3b8' }}>
            👈 请在上方选择一个干线车次以查看笼板/包裹明细
          </Card>
        )}
      </div>

      {/* 👉 右栏：沉浸式全屏地图空间分布图 (主视图) */}
      <div style={{ flex: 1, position: 'relative', borderRadius: '12px', overflow: 'hidden', border: '1px solid #e8edf3', background: '#eef2f6' }}>
        <PlanningMap
          station={station}
          parcels={mapParcels}
          serviceAreas={areas.data ?? []}
          selected={selectedParcelIds}
          activeAreaId={selectedAreaId}
          onSelectArea={areaId => setSelectedAreaId(areaId)}
          onToggle={id => setSelectedParcelIds(prev => {
            const next = new Set(prev);
            if (next.has(id)) next.delete(id);
            else next.add(id);
            return next;
          })}
          onSelect={() => setDrawerOpen(true)}
        />

        {/* Floating Top Indicator Bar */}
        <div style={{ position: 'absolute', top: '12px', left: '12px', background: 'rgba(255,255,255,0.92)', backdropFilter: 'blur(4px)', padding: '8px 14px', borderRadius: '20px', boxShadow: '0 2px 10px rgba(0,0,0,0.1)', display: 'flex', alignItems: 'center', gap: '12px', zIndex: 10 }}>
          <span style={{ fontSize: '13px', fontWeight: 600, color: '#1e293b' }}>
            🗺️ 到仓包裹空间分布地图
          </span>
          {selectedUnit && (
            <Tag color="purple" style={{ margin: 0, borderRadius: '10px' }}>
              当前笼板: {unitLabels.get(selectedUnit)} ({unitParcels.length} 件)
            </Tag>
          )}
          <Button 
            type="primary" 
            size="small"
            style={{ borderRadius: '12px' }}
            onClick={() => setDrawerOpen(true)}
          >
            📋 查看包裹明细 ({unitParcels.length} 件)
          </Button>
        </div>
      </div>

      {/* ↗️ 右侧可收缩包裹明细 Drawer (与订单准备页面体验一致) */}
      <MobileActionBar label="查看到仓包裹明细" count={unitParcels.length} onClick={() => setDrawerOpen(true)} />
      <Drawer
        title={
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span>📦 到仓包裹清单 {selectedUnit ? `· ${unitLabels.get(selectedUnit)}` : ''}</span>
            <Tag color="blue">{unitParcels.length} 件</Tag>
          </div>
        }
        placement="right"
        width={460}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
      >
        <Table<UnitParcel>
          rowKey="parcel_id"
          size="small"
          pagination={{ pageSize: 15 }}
          dataSource={unitParcels}
          columns={[
            { title: t('field.tracking_no'), dataIndex: 'tracking_no', render: v => <span style={{ fontFamily: 'monospace', fontWeight: 600 }}>{v}</span> },
            { title: t('common.status'), dataIndex: 'parcel_status', render: v => <Tag color="blue">{v}</Tag> },
            { title: '片区', dataIndex: 'area_code', render: v => v ? <Tag color="purple">{v}</Tag> : <Tag>未划区</Tag> },
            { title: t('arrival.driver'), dataIndex: 'driver_name', render: v => v ?? '—' }
          ]}
        />
      </Drawer>
    </div>
  );

  return (
    <>
      <Tabs items={[
        { key: 'arrival', label: t('arrival.tab'), children: arrivalContent },
        { key: 'manifest', label: t('arrival.legacyManifest'), children: <ManifestWorkspace session={session} station={station} /> }
      ]} />

      {/* 创建车次 Drawer */}
      <Drawer width={480} open={createOpen} onClose={() => setCreateOpen(false)} title={t('arrival.create')}>
        <Form layout="vertical" onFinish={v => command.mutate({ path: '/ops/v1/arrival-trips', body: { ...v, expectedAt: v.expectedAt?.toISOString() } })}>
          <Form.Item name="externalTripNo" label={t('arrival.tripNo')} extra={t('arrival.autoHint')}><Input /></Form.Item>
          <Form.Item name="vehiclePlate" label={t('arrival.vehicle')}><Input /></Form.Item>
          <Form.Item name="sealNo" label={t('arrival.seal')}><Input /></Form.Item>
          <Form.Item name="expectedAt" label={t('arrival.expected')} initialValue={dayjs(`${serviceDate}T08:00:00`)}><DatePicker showTime style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="note" label={t('common.reason')}><Input.TextArea /></Form.Item>
          <Button block type="primary" htmlType="submit" loading={command.isPending}>{t('common.save')}</Button>
        </Form>
      </Drawer>

      <Drawer width={480} open={editTransportOpen} destroyOnClose onClose={() => setEditTransportOpen(false)} title="修改到仓运输信息">
        <Form key={`${tripId ?? 'none'}-${currentTrip?.updated_at ?? ''}`} layout="vertical" initialValues={{ vehiclePlate: currentTrip?.vehicle_plate, sealNo: currentTrip?.seal_no, expectedAt: currentTrip?.expected_at ? dayjs(currentTrip.expected_at) : undefined, note: currentTrip?.note }} onFinish={v => command.mutate({ path: `/ops/v1/arrival-trips/${tripId}/transport`, method: 'PATCH', body: { ...v, expectedAt: v.expectedAt?.toISOString(), reason: v.reason } })}>
          <Form.Item name="vehiclePlate" label="车牌号"><Input /></Form.Item>
          <Form.Item name="sealNo" label="封签号"><Input /></Form.Item>
          <Form.Item name="expectedAt" label="预计到仓时间"><DatePicker showTime style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="note" label="备注"><Input.TextArea /></Form.Item>
          <Form.Item name="reason" label="修改原因" rules={[{ required: true, message: '请输入修改原因' }]}><Input.TextArea /></Form.Item>
          <Button block type="primary" htmlType="submit" loading={command.isPending}>保存修改</Button>
        </Form>
      </Drawer>

      {/* 添加笼板 Drawer */}
      <Drawer width={480} open={unitOpen} onClose={() => setUnitOpen(false)} title={t('arrival.addUnit')}>
        <Form layout="vertical" onFinish={v => command.mutate({ path: `/ops/v1/arrival-trips/${tripId}/handling-units`, body: { ...v, trackingNumbers: String(v.trackingNumbers ?? '').split(/[,\n]/).map(x => x.trim()).filter(Boolean) } })}>
          <Form.Item name="externalUnitNo" label={t('arrival.unitNo')} rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="unitType" label={t('arrival.unitType')} initialValue="PALLET"><Select options={['PALLET', 'CAGE', 'BAG', 'LOOSE'].map(value => ({ value, label: value }))} /></Form.Item>
          <Form.Item name="expectedPieceCount" label={t('arrival.expectedPieces')}><InputNumber min={0} style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="trackingNumbers" label={t('arrival.trackingList')}><Input.TextArea rows={6} /></Form.Item>
          <Form.Item name="reason" label={t('common.reason')} rules={[{ required: true }]}><Input.TextArea /></Form.Item>
          <Button block type="primary" htmlType="submit" loading={command.isPending}>{t('common.save')}</Button>
        </Form>
      </Drawer>

      {/* 定区 Modal */}
      <Modal open={!!fillUnit} onCancel={() => setFillUnit(undefined)} title={`${t('arrival.areaFill')} · ${fillUnit?.external_unit_no ?? ''}`} footer={null} destroyOnClose>
        <Alert type="info" showIcon message={t('arrival.areaFillHelp')} style={{ marginBottom: 12 }} />
        <Form layout="vertical" onFinish={v => command.mutate({ path: `/ops/v1/handling-units/${fillUnit!.id}/area-fill`, body: { deliveryAreaIds: v.deliveryAreaIds, reason: v.reason } })}>
          <Form.Item name="deliveryAreaIds" label={t('arrival.selectAreas')} rules={[{ required: true }]}><Select mode="multiple" options={areaOptions} optionFilterProp="label" placeholder={t('arrival.selectAreas')} /></Form.Item>
          <Form.Item name="reason" label={t('common.reason')} rules={[{ required: true }]}><Input.TextArea /></Form.Item>
          <Button block type="primary" htmlType="submit" loading={command.isPending}>{t('common.save')}</Button>
        </Form>
      </Modal>
    </>
  );
}
