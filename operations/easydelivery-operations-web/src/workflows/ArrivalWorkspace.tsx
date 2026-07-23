import { useMemo, useState } from 'react';
import { Alert, Button, Card, DatePicker, Descriptions, Drawer, Form, Input, InputNumber, Modal, Select, Space, Table, Tabs, Tag, message } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { useTranslation } from 'react-i18next';
import { api, type Session } from '../api/client';
import { ManifestWorkspace } from './ManifestWorkspace';
import { aggregateEqualsDetail, parcelsOfUnit } from './arrivalCoverage';

type Trip={id:number;external_trip_no:string;vehicle_plate?:string;seal_no?:string;expected_at?:string;arrived_at?:string;status:string;unit_count:number;expected_piece_count:number;linked_piece_count:number};
type Unit={id:number;external_unit_no:string;unit_type:string;expected_piece_count?:number;status:string;linked_piece_count:number;driver_count:number;wave_count:number;declared_piece_count:number;scanned_piece_count:number;exception_piece_count:number};
type UnitParcel={unit_id:number;parcel_id:number;tracking_no:string;parcel_status:string;link_source:string;item_status?:string;task_code?:string;driver_name?:string};
type Unlinked={unit_id:number;external_unit_no:string;tracking_no:string;parcel_status:string;station_code?:string};
type Detail={trip:Trip&Record<string,unknown>;units:Unit[];parcels:UnitParcel[];unlinkedDeclarations:Unlinked[]};
type Area={id:number;area_code:string;area_name:string;version_id?:number;version_status?:string};
const nextTrip:Record<string,string>={EXPECTED:'ARRIVED',ARRIVED:'UNLOADING',UNLOADING:'READY_FOR_SCAN',READY_FOR_SCAN:'CLOSED'};
const nextUnit:Record<string,string>={EXPECTED:'ARRIVED',ARRIVED:'OPENED',OPENED:'CLEARED'};

export function ArrivalWorkspace({session,station,serviceDate}:{session:Session;station:string;serviceDate:string}){
 const {t}=useTranslation();const cache=useQueryClient();
 const [createOpen,setCreateOpen]=useState(false);const [tripId,setTripId]=useState<number>();const [unitOpen,setUnitOpen]=useState(false);
 const [fillUnit,setFillUnit]=useState<Unit>();const [selectedUnit,setSelectedUnit]=useState<number>();
 const trips=useQuery({queryKey:['arrival-trips',station,serviceDate],queryFn:()=>api<Trip[]>(`/ops/v1/arrival-trips?serviceDate=${serviceDate}`,session,{},station)});
 const detail=useQuery({queryKey:['arrival-trip',station,tripId],enabled:!!tripId,queryFn:()=>api<Detail>(`/ops/v1/arrival-trips/${tripId}`,session,{},station)});
 const areas=useQuery({queryKey:['delivery-areas',station],queryFn:()=>api<Area[]>('/ops/v1/delivery-areas',session,{},station)});
 const areaOptions=useMemo(()=>(areas.data??[]).filter(a=>a.version_status==='PUBLISHED'&&a.version_id!=null).map(a=>({value:a.version_id as number,label:`${a.area_code} · ${a.area_name}`})),[areas.data]);
 const unitLabels=useMemo(()=>new Map((detail.data?.units??[]).map(u=>[u.id,u.external_unit_no])),[detail.data]);
 const refresh=async()=>Promise.all([cache.invalidateQueries({queryKey:['arrival-trips',station,serviceDate]}),cache.invalidateQueries({queryKey:['arrival-trip',station,tripId]})]);
 const command=useMutation({mutationFn:({path,body}:{path:string;body:unknown})=>api(path,session,{method:'POST',body:JSON.stringify(body)},station),onSuccess:async()=>{message.success(t('arrival.saved'));setCreateOpen(false);setUnitOpen(false);setFillUnit(undefined);await refresh();},onError:(e:Error)=>message.error(e.message)});
 const unitParcels=parcelsOfUnit(detail.data?.parcels,selectedUnit);
 const countsMatch=detail.data?aggregateEqualsDetail(detail.data.units,detail.data.parcels):true;
 const tripProgressText: Record<string, string> = {
    EXPECTED: '🚚 确认卡车到港 / Confirm Arrival',
    ARRIVED: '📦 开始卸货 / Start Unloading',
    UNLOADING: '⚡ 启动并放行清点扫码 / Ready for Scan',
    READY_FOR_SCAN: '✅ 结束到货并关闭批次 / Close Arrival Batch'
  };
 const arrival=<Space direction="vertical" style={{width:'100%'}} size="middle">{trips.error&&<Alert type="error" showIcon message={trips.error.message}/>}<Card title={t('arrival.title')}><Alert type="info" showIcon message={t('arrival.boundary')}/><Table<Trip> rowKey="id" dataSource={trips.data??[]} pagination={false} onRow={row=>({onClick:()=>{setTripId(row.id);setSelectedUnit(undefined);}})} columns={[{title:t('arrival.tripNo'),dataIndex:'external_trip_no'},{title:t('arrival.vehicle'),dataIndex:'vehicle_plate'},{title:t('arrival.expected'),dataIndex:'expected_at'},{title:t('common.status'),dataIndex:'status',render:v=><Tag>{v}</Tag>},{title:t('arrival.units'),dataIndex:'unit_count'},{title:t('arrival.pieces'),render:(_,r)=>`${r.linked_piece_count}/${r.expected_piece_count}`}]}/></Card></Space>;
 return <><Tabs items={[{key:'arrival',label:t('arrival.tab'),children:arrival},{key:'manifest',label:t('arrival.legacyManifest'),children:<ManifestWorkspace session={session} station={station}/>}]} />
  <Drawer width={860} open={!!tripId} onClose={()=>setTripId(undefined)} title={`${t('arrival.detail')} · ${detail.data?.trip.external_trip_no??''}`}>{detail.error&&<Alert type="error" message={detail.error.message}/>} {detail.data&&<Space direction="vertical" style={{width:'100%'}}>
   <Descriptions bordered size="small" column={2} items={Object.entries(detail.data.trip).slice(0,10).map(([key,value])=>({key,label:key,children:String(value??'—')}))}/>
   {!countsMatch&&<Alert type="error" showIcon message={t('arrival.mismatch')}/>}
   {nextTrip[detail.data.trip.status]&&<Button type="primary" size="large" style={{ background: detail.data.trip.status === 'EXPECTED' ? '#389e0d' : undefined, borderColor: detail.data.trip.status === 'EXPECTED' ? '#389e0d' : undefined, height: '40px', fontWeight: 'bold' }} onClick={()=>command.mutate({path:`/ops/v1/arrival-trips/${tripId}/state`,body:{targetStatus:nextTrip[detail.data!.trip.status],reason:'Operations arrival progression'}})}>{tripProgressText[detail.data.trip.status]}</Button>}
   <Table<Unit> rowKey="id" pagination={false} dataSource={detail.data.units} rowClassName={u=>u.id===selectedUnit?'ant-table-row-selected':''} onRow={u=>({onClick:()=>setSelectedUnit(u.id===selectedUnit?undefined:u.id)})} columns={[
    {title:t('arrival.unitNo'),dataIndex:'external_unit_no'},
    {title:t('arrival.unitType'),dataIndex:'unit_type'},
    {title:t('common.status'),dataIndex:'status',render:v=><Tag>{v}</Tag>},
    {title:t('arrival.coverage'),render:(_,u)=><Space size={4} wrap><Tag>{t('arrival.declared')} {u.declared_piece_count}</Tag><Tag color="blue">{t('arrival.linked')} {u.linked_piece_count}</Tag><Tag color="green">{t('arrival.scanned')} {u.scanned_piece_count}</Tag><Tag color={u.exception_piece_count>0?'red':'default'}>{t('arrival.exception')} {u.exception_piece_count}</Tag></Space>},
    {title:t('arrival.drivers'),dataIndex:'driver_count'},
    {title:t('common.action'),render:(_,u)=><Space size={4}><Button size="small" onClick={e=>{e.stopPropagation();setFillUnit(u);}}>{t('arrival.areaFill')}</Button>{nextUnit[u.status]&&<Button size="small" onClick={e=>{e.stopPropagation();command.mutate({path:`/ops/v1/handling-units/${u.id}/state`,body:{targetStatus:nextUnit[u.status],reason:'Operations unit progression'}});}}>{nextUnit[u.status]}</Button>}</Space>}
   ]}/>
   {detail.data.unlinkedDeclarations.length>0&&<Alert type="warning" showIcon message={`${t('arrival.unlinked')} (${detail.data.unlinkedDeclarations.length})`} description={detail.data.unlinkedDeclarations.map(u=>`${u.tracking_no}${u.station_code?` · ${u.station_code}`:''}`).join(', ')}/>}
   <Card size="small" title={`${t('arrival.parcels')}${selectedUnit?` · ${unitLabels.get(selectedUnit)??''}`:''}`}><Table<UnitParcel> rowKey="parcel_id" size="small" pagination={false} dataSource={unitParcels} columns={[
    {title:t('field.tracking_no'),dataIndex:'tracking_no'},
    {title:t('common.status'),dataIndex:'parcel_status',render:v=><Tag>{v}</Tag>},
    {title:t('arrival.linkSource'),dataIndex:'link_source',render:v=><Tag>{v}</Tag>},
    ...(selectedUnit===undefined?[{title:t('arrival.unitNo'),dataIndex:'unit_id',render:(v:number)=>unitLabels.get(v)??v}]:[]),
    {title:t('arrival.task'),dataIndex:'task_code',render:v=>v??'—'},
    {title:t('arrival.driver'),dataIndex:'driver_name',render:v=>v??'—'}
   ]}/></Card>
  </Space>}</Drawer>
  <Drawer width={480} open={createOpen} onClose={()=>setCreateOpen(false)} title={t('arrival.create')}><Form layout="vertical" onFinish={v=>command.mutate({path:'/ops/v1/arrival-trips',body:{...v,expectedAt:v.expectedAt?.toISOString()}})}><Form.Item name="externalTripNo" label={t('arrival.tripNo')} extra={t('arrival.autoHint')}><Input/></Form.Item><Form.Item name="vehiclePlate" label={t('arrival.vehicle')}><Input/></Form.Item><Form.Item name="sealNo" label={t('arrival.seal')}><Input/></Form.Item><Form.Item name="expectedAt" label={t('arrival.expected')} initialValue={dayjs(`${serviceDate}T08:00:00`)}><DatePicker showTime style={{width:'100%'}}/></Form.Item><Form.Item name="note" label={t('common.reason')}><Input.TextArea/></Form.Item><Button block type="primary" htmlType="submit" loading={command.isPending}>{t('common.save')}</Button></Form></Drawer>
  <Drawer width={480} open={unitOpen} onClose={()=>setUnitOpen(false)} title={t('arrival.addUnit')}><Form layout="vertical" onFinish={v=>command.mutate({path:`/ops/v1/arrival-trips/${tripId}/handling-units`,body:{...v,trackingNumbers:String(v.trackingNumbers??'').split(/[,\n]/).map(x=>x.trim()).filter(Boolean)}})}><Form.Item name="externalUnitNo" label={t('arrival.unitNo')} rules={[{required:true}]}><Input/></Form.Item><Form.Item name="unitType" label={t('arrival.unitType')} initialValue="PALLET"><Select options={['PALLET','CAGE','BAG','LOOSE'].map(value=>({value,label:value}))}/></Form.Item><Form.Item name="expectedPieceCount" label={t('arrival.expectedPieces')}><InputNumber min={0} style={{width:'100%'}}/></Form.Item><Form.Item name="trackingNumbers" label={t('arrival.trackingList')}><Input.TextArea rows={6}/></Form.Item><Form.Item name="reason" label={t('common.reason')} rules={[{required:true}]}><Input.TextArea/></Form.Item><Button block type="primary" htmlType="submit" loading={command.isPending}>{t('common.save')}</Button></Form></Drawer>
  <Modal open={!!fillUnit} onCancel={()=>setFillUnit(undefined)} title={`${t('arrival.areaFill')} · ${fillUnit?.external_unit_no??''}`} footer={null} destroyOnClose>
   <Alert type="info" showIcon message={t('arrival.areaFillHelp')} style={{marginBottom:12}}/>
   <Form layout="vertical" onFinish={v=>command.mutate({path:`/ops/v1/handling-units/${fillUnit!.id}/area-fill`,body:{areaVersionIds:v.areaVersionIds,reason:v.reason}})}>
    <Form.Item name="areaVersionIds" label={t('arrival.selectAreas')} rules={[{required:true}]}><Select mode="multiple" options={areaOptions} optionFilterProp="label" placeholder={t('arrival.selectAreas')}/></Form.Item>
    <Form.Item name="reason" label={t('common.reason')} rules={[{required:true}]}><Input.TextArea/></Form.Item>
    <Button block type="primary" htmlType="submit" loading={command.isPending}>{t('common.save')}</Button>
   </Form>
  </Modal>
 </>;
}
