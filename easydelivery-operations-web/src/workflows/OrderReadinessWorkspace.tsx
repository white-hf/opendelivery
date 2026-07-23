import { useCallback, useMemo, useState, useEffect } from 'react';
import { Alert, Button, Card, Col, Drawer, Empty, Form, Input, InputNumber, Row, Select, Space, Statistic, Table, Tag, Typography, message, Badge } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, type Session } from '../api/client';
import { PlanningMap, type PlanningParcel } from './PlanningMap';
import { useTranslation } from 'react-i18next';

type Area={id:number;area_code:string;area_name:string;status:string;version_id?:number;version_status?:string};
type Wave={id?:number;wave_id?:number;wave_code?:string;waveCode?:string;service_date:string;status:string;wave_status?:string};

export function OrderReadinessWorkspace({session,station,serviceDate,initialFilter}:{session:Session;station:string;serviceDate:string;initialFilter?:string}){
 const {t}=useTranslation();const cache=useQueryClient();
 const [selected,setSelected]=useState<Set<number>>(new Set());
 const [focus,setFocus]=useState<PlanningParcel>();
 const [filter,setFilter]=useState(initialFilter??'all');
 const [waveId,setWaveId]=useState<number|undefined>();
 const [sidebarOpen,setSidebarOpen]=useState(true);
 const [searchQuery,setSearchQuery]=useState('');
 const [selectedZoneCode,setSelectedZoneCode] = useState<string | undefined>();

 // 1. Get Waves for Cascade filtering
 const waves = useQuery({
   queryKey: ['dispatch-waves-list', station, serviceDate],
   queryFn: () => api<Wave[]>(`/ops/v1/dispatch/waves?limit=100`, session, {}, station).then(res => {
     return (res ?? []).filter(w => w.service_date === serviceDate);
   })
 });

 // Automatically select first wave if available - defense for wave_id vs id
 useEffect(() => {
   if (waves.data && waves.data.length > 0 && !waveId) {
     const firstWave = waves.data[0];
     setWaveId(firstWave.wave_id ?? firstWave.id);
   }
 }, [waves.data, waveId]);

 // 2. Query parcels with waveId parameter
 const parcels = useQuery({
   queryKey: ['planning-parcels', station, serviceDate, waveId],
   queryFn: () => api<PlanningParcel[]>(`/ops/v1/planning/parcels?serviceDate=${serviceDate}&limit=50000${waveId ? `&waveId=${waveId}` : ''}`, session, {}, station)
 });

 const areas = useQuery({
   queryKey: ['areas', station],
   queryFn: () => api<Area[]>('/ops/v1/delivery-areas', session, {}, station)
 });

 // Automatically select first region if available
 useEffect(() => {
   if (areas.data && areas.data.length > 0 && !selectedZoneCode) {
     setSelectedZoneCode(areas.data[0].area_code);
   }
 }, [areas.data, selectedZoneCode]);

 const all = useMemo(() => parcels.data ?? [], [parcels.data]);

 // Exact Tracking No B+Tree indexed direct exact lookup
 const filteredBySearch = useMemo(() => {
   if (!searchQuery.trim()) return all;
   return all.filter(p => p && (p.tracking_no === searchQuery.trim() || p.tracking_no?.startsWith(searchQuery.trim())));
 }, [all, searchQuery]);

 // Geographic zoning selection - default first active zone selected
 const filteredByZone = useMemo(() => {
   if (!selectedZoneCode) return [];
   return filteredBySearch.filter(p => p && p.area_code === selectedZoneCode);
 }, [filteredBySearch, selectedZoneCode]);

 const missing = useMemo(() => filteredByZone.filter(p => p && p.exception_code === 'MISSING_GEOCODE'), [filteredByZone]);
 const unmatched = useMemo(() => filteredByZone.filter(p => p && p.exception_code === 'UNMATCHED_AREA'), [filteredByZone]);
 const ready = useMemo(() => filteredByZone.filter(p => p && !p.exception_code), [filteredByZone]);

 const visible = useMemo(() => {
   if (filter === 'missing-geocode') return missing;
   if (filter === 'unmatched-area') return unmatched;
   if (filter === 'ready') return ready;
   return filteredByZone;
 }, [filteredByZone, filter, missing, ready, unmatched]);

 const toggle = useCallback((id:number) => setSelected(current => {
   const next = new Set(current);
   if (next.has(id)) next.delete(id);
   else next.add(id);
   return next;
 }), []);

 const action = useMutation({
   mutationFn: ({path,body}:{path:string;body:unknown}) => api(path,session,{method:'POST',body:JSON.stringify(body)},station),
   onSuccess: async () => {
     message.success(t('orders.resolved'));
     setFocus(undefined);
     await Promise.all([
       cache.invalidateQueries({queryKey:['planning-parcels',station,serviceDate,waveId]}),
       cache.invalidateQueries({queryKey:['control-tower',station,serviceDate]})
     ]);
   },
   onError: (error:Error) => message.error(error.message)
 });

 // SLA & Attribute based fast wave assignment mutation
 const batchAssignWave = useMutation({
   mutationFn: (targetWaveId: number) => {
     return api(`/ops/v1/planning/waves/${targetWaveId}/assignments`, session, {
       method: 'POST',
       body: JSON.stringify({ parcelIds: Array.from(selected) })
     }, station);
   },
   onSuccess: async () => {
     message.success(`Successfully batch reassigned ${selected.size} parcels to Selected Wave`);
     setSelected(new Set());
     await cache.invalidateQueries({queryKey:['planning-parcels',station,serviceDate,waveId]});
   },
   onError: (err: Error) => message.error(err.message)
 });

 return (
   <div className="order-readiness-workspace-container" style={{ display: 'flex', flexDirection: 'column', gap: '12px', height: 'calc(100vh - 120px)', position: 'relative' }}>
     {(parcels.error || areas.error || waves.error) && (
       <Alert type="error" showIcon message={(parcels.error ?? areas.error ?? waves.error)?.message} />
     )}

     {/* Compact Indicator & Cascading Wave Selector Toolbar */}
     <Card size="small" style={{ padding: '4px 8px', borderRadius: '10px', boxShadow: '0 1px 4px rgba(0,0,0,0.05)' }}>
       <Row justify="space-between" align="middle" gutter={12}>
         <Col>
           <Space size="large" wrap>
             <span style={{ fontWeight: 600, fontSize: '15px' }}>{t('orders.title')}</span>
             <Space wrap>
               <span style={{ color: '#667085' }}>{t('orders.activeWave')}:</span>
               <Select
                 value={waveId}
                 onChange={val => { setWaveId(val); setFilter('all'); }}
                 style={{ width: 240 }}
                 placeholder={t('orders.noWave')}
                 options={(waves.data ?? []).map(w => ({ 
                   value: w.wave_id ?? w.id, 
                   label: `${w.wave_code ?? w.waveCode ?? 'WAVE-DEMO'} (${w.wave_status ?? w.status ?? 'DRAFT'})` 
                 }))}
               />
               
               {/* Geographic Zoning selection - Operational Naming */}
               <span style={{ color: '#667085', marginLeft: '12px' }}>{t('orders.geographicZoning', {defaultValue: '选择区域'} )}:</span>
               <Select
                 value={selectedZoneCode}
                 onChange={val => setSelectedZoneCode(val)}
                 style={{ width: 180 }}
                 options={(areas.data ?? []).map(a => ({ value: a.area_code, label: `${a.area_code} - ${a.area_name}` }))}
               />
             </Space>
           </Space>
         </Col>
         <Col>
           <Space size="small" wrap>
             {[
               ['all', all.length, 'orders.expected', 'default'],
               ['ready', ready.length, 'orders.ready', 'green'],
               ['missing-geocode', missing.length, 'orders.missing', 'volcano'],
               ['unmatched-area', unmatched.length, 'orders.unmatched', 'warning']
             ].map(([code, count, label, color]) => (
               <Tag
                 key={String(code)}
                 color={filter === code ? 'blue' : String(color)}
                 style={{ cursor: 'pointer', padding: '4px 10px', fontSize: '12px', fontWeight: filter === code ? 'bold' : 'normal' }}
                 onClick={() => setFilter(String(code))}
               >
                 {t(String(label))}: {Number(count)}
               </Tag>
             ))}
             <Button 
               size="small" 
               type={sidebarOpen ? "default" : "primary"} 
               onClick={() => setSidebarOpen(!sidebarOpen)}
             >
               {sidebarOpen ? t('orders.hideQueue') : t('orders.showQueue')}
             </Button>
           </Space>
         </Col>
       </Row>
     </Card>

     {/* 100% Full-Screen Map Base Layout with Floating side panel */}
     <div style={{ flex: 1, position: 'relative', borderRadius: '12px', overflow: 'hidden', border: '1px solid #e8edf3' }}>
       {/* Big Google Map Background */}
       <div style={{ position: 'absolute', inset: 0 }}>
         <PlanningMap station={station} parcels={visible} selected={selected} onToggle={toggle} onSelect={setFocus} />
       </div>

       {/* Floating Sidebar Drawer Panel */}
       {sidebarOpen && (
         <div style={{
           position: 'absolute',
           top: '12px',
           right: '12px',
           bottom: '12px',
           width: '420px',
           background: 'rgba(255, 255, 255, 0.96)',
           backdropFilter: 'blur(10px)',
           boxShadow: '-4px 0 16px rgba(0,0,0,0.1)',
           borderRadius: '12px',
           zIndex: 10,
           display: 'flex',
           flexDirection: 'column',
           overflow: 'hidden',
           border: '1px solid rgba(224, 224, 224, 0.6)'
         }}>
           {/* Sidebar Title */}
           <div style={{ padding: '12px 16px', borderBottom: '1px solid #f0f0f0', display: 'flex', justifyContent: 'space-between', alignSelf: 'stretch', alignItems: 'center', background: '#fafbfc' }}>
             <Typography.Text strong style={{ fontSize: '14px' }}>
               {t('orders.queue')} <Badge count={visible.length} overflowCount={9999} style={{ backgroundColor: '#1677ff', marginLeft: '6px' }} />
             </Typography.Text>
             {selected.size > 0 && (
               <Space>
                 <Button 
                   size="small" 
                   type="primary" 
                   danger
                   onClick={() => {
                     const firstWave = waves.data?.[0]?.wave_id ?? waves.data?.[0]?.id;
                     if (firstWave) batchAssignWave.mutate(firstWave);
                   }}
                 >
                   Cut-off ({selected.size})
                 </Button>
                 <Button size="small" onClick={() => setSelected(new Set())}>Clear</Button>
               </Space>
             )}
           </div>

           {/* Exact Query Input: Single B+Tree Indexed Matcher (No wildcard like % or fuzzy scans) */}
           <div style={{ padding: '10px 16px', borderBottom: '1px solid #e8edf3' }}>
             <Input.Search
               placeholder={t('orders.searchPlaceholder')}
               allowClear
               size="small"
               value={searchQuery}
               onChange={e => setSearchQuery(e.target.value)}
             />
           </div>

           {/* Quick Attribute Filter Splitting Toolbar */}
           <div style={{ padding: '8px 12px', background: '#f5f7fa', borderBottom: '1px solid #e8edf3', display: 'flex', gap: '6px', overflowX: 'auto' }}>
             <Button size="small" style={{ fontSize: '11px' }} onClick={() => {
               const expressIds = all.filter(p => p && (p.tracking_no?.includes('EXPRESS') || p.promised_date === serviceDate)).map(p => p.parcel_id);
               setSelected(new Set(expressIds));
               message.info(`Auto-selected ${expressIds.length} Express items. Click "Cut-off" to re-assign.`);
             }}>SLA: Express</Button>
             <Button size="small" style={{ fontSize: '11px' }} onClick={() => {
               const unmatchedIds = unmatched.map(p => p.parcel_id);
               setSelected(new Set(unmatchedIds));
               message.info(`Auto-selected ${unmatchedIds.length} unmatched area items.`);
             }}>All Unmatched</Button>
             <Button size="small" style={{ fontSize: '11px' }} onClick={() => {
               const missingIds = missing.map(p => p.parcel_id);
               setSelected(new Set(missingIds));
               message.info(`Auto-selected ${missingIds.length} missing coordinate items.`);
             }}>All Missing</Button>
           </div>

           {/* Table Queue List */}
           <div style={{ flex: 1, overflowY: 'auto', padding: '4px' }}>
             <Table<PlanningParcel>
               size="small"
               rowKey="parcel_id"
               dataSource={visible}
               pagination={{ pageSize: 50, size: 'small', showSizeChanger: false }}
               onRow={row => ({ onClick: () => setFocus(row) })}
               rowClassName={record => record && selected.has(rowKeySelector(record)) ? 'row-selected-highlight' : ''}
               columns={[
                 {
                   title: t('field.tracking_no'), 
                   dataIndex: 'tracking_no',
                   render: (val, record) => (
                     <Space size="small">
                       <input 
                         type="checkbox" 
                         checked={record && selected.has(record.parcel_id)} 
                         onChange={(e) => {
                           e.stopPropagation();
                           if (record) toggle(record.parcel_id);
                         }} 
                       />
                       <span>{val}</span>
                     </Space>
                   )
                 },
                 { title: t('orders.postal'), dataIndex: 'postal_code', width: 85 },
                 {
                   title: t('dispatch.exception'), 
                   dataIndex: 'exception_code', 
                   width: 110,
                   render: value => value ? <Tag color="orange" style={{ margin: 0, fontSize: '10px' }}>{value}</Tag> : <Tag color="green" style={{ margin: 0, fontSize: '10px' }}>READY</Tag>
                 }
               ]}
             />
           </div>
         </div>
       )}
     </div>

     {/* Resolve Exception Drawer */}
     <Drawer width={520} open={!!focus} onClose={() => setFocus(undefined)} title={`${t('orders.detail')} · ${focus?.tracking_no}`}>
       {focus ? (
         <Space direction="vertical" style={{ width: '100%' }} size="middle">
           <Card size="small" style={{ background: '#fafbfc', borderRadius: '8px' }}>
             <p style={{ fontSize: '15px', marginBottom: '8px' }}><b>{focus.recipient_name as string}</b></p>
             <p style={{ color: '#667085', marginBottom: '12px' }}>{focus.address_line1 as string}, {focus.city as string} {focus.postal_code as string}</p>
             <Tag color={focus.exception_code ? "orange" : "green"}>{focus.exception_code ?? 'READY'}</Tag>
           </Card>
           
           {focus.exception_code === 'MISSING_GEOCODE' && (
             <Card title={t('orders.coordinateFix')} size="small">
               <Form layout="vertical" onFinish={values => action.mutate({ path: `/ops/v1/parcels/${focus.parcel_id}/area-match`, body: { ...values, providerCode: 'OPERATOR', precisionCode: 'MANUAL', confidence: 1, normalizedAddress: String(focus.address_line1 ?? ''), reason: values.reason } })}>
                 <Form.Item name="longitude" label={t('orders.longitude')} rules={[{ required: true }]}><InputNumber style={{ width: '100%' }} min={-180} max={180} precision={7} /></Form.Item>
                 <Form.Item name="latitude" label={t('orders.latitude')} rules={[{ required: true }]}><InputNumber style={{ width: '100%' }} min={-90} max={90} precision={7} /></Form.Item>
                 <Form.Item name="reason" label={t('common.reason')} rules={[{ required: true }]}><Input.TextArea /></Form.Item>
                 <Button block type="primary" htmlType="submit">{t('orders.geocodeAndMatch')}</Button>
               </Form>
             </Card>
           )}
           
           {focus.exception_code && (
             <Card title={t('orders.manualArea')} size="small">
               <Form layout="vertical" onFinish={values => action.mutate({ path: `/ops/v1/parcels/${focus.parcel_id}/area-override`, body: values })}>
                 <Form.Item name="areaVersionId" label={t('dispatch.area')} rules={[{ required: true }]}>
                   <Select options={(areas.data ?? []).filter(a => a.status === 'ACTIVE' && a.version_status === 'PUBLISHED').map(a => ({ value: a.version_id, label: `${a.area_code} · ${a.area_name}` }))} />
                 </Form.Item>
                 <Form.Item name="reason" label={t('common.reason')} rules={[{ required: true }]}><Input.TextArea /></Form.Item>
                 <Button block htmlType="submit">{t('orders.applyOverride')}</Button>
               </Form>
             </Card>
           )}
         </Space>
       ) : <Empty />}
     </Drawer>
   </div>
 );
}

function rowKeySelector(record: PlanningParcel) {
  return record ? record.parcel_id : 0;
}
