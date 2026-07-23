import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, App, Button, Card, Drawer, Form, Input, InputNumber, List, Progress, Select, Space, Steps, Table, Tabs, Tag, Tooltip, Typography } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, type Session } from '../api/client';
import { PlanningMap, type PlanningParcel } from './PlanningMap';
import { useTranslation } from 'react-i18next';

type Shift={driver_id:number;driver_name:string;driver_code:string;availability_status:string;parcel_capacity?:number;assigned_count:number};
type WaveResult={wave:{id:number;wave_code:string;status:string};drivers:Array<{task_id:number;driver_id:number;driver_name:string;parcel_count:number;parcel_capacity:number;remaining_capacity:number}>};

export function DispatchWorkspace({session,station,initialDate,initialFilter}:{session:Session;station:string;initialDate?:string;initialFilter?:string}){
 const { message } = App.useApp();
 const {t}=useTranslation();const cache=useQueryClient();const serviceDate=initialDate!;const [stage,setStage]=useState(0);const [selected,setSelected]=useState<Set<number>>(new Set());const [focus,setFocus]=useState<PlanningParcel>();const [driver,setDriver]=useState<number>();const [areaVersion,setAreaVersion]=useState<number>();const [waveId,setWaveId]=useState<number>();const [capacityOpen,setCapacityOpen]=useState(false);const [listOpen,setListOpen]=useState(false);
 const [currentArea, setCurrentArea] = useState<number | undefined>(undefined);
 const parcels=useQuery({queryKey:['planning-parcels',station,serviceDate],queryFn:()=>api<PlanningParcel[]>(`/ops/v1/planning/parcels?serviceDate=${serviceDate}&limit=2000`,session,{},station)});
 const shifts=useQuery({queryKey:['planning-shifts',station,serviceDate],queryFn:()=>api<Shift[]>(`/ops/v1/planning/shifts?serviceDate=${serviceDate}`,session,{},station)});
 
 // Fetch existing waves to prevent duplicate creation lockups
 const wavesList = useQuery({
   queryKey: ['dispatch-waves-list', station, serviceDate],
   queryFn: () => api<any[]>(`/ops/v1/dispatch/waves?limit=100`, session, {}, station).then(res => {
     return (res ?? []).filter(w => w.service_date === serviceDate);
   })
 });

 // Automatically load the existing wave if found for today
 useEffect(() => {
   if (wavesList.data && wavesList.data.length > 0 && !waveId) {
     const activeDraftWave = wavesList.data.find(w => w.wave_status === 'DRAFT');
     const targetWave = activeDraftWave ?? wavesList.data[0];
     const targetId = targetWave.wave_id ?? targetWave.id;
     if (targetId) {
       setWaveId(targetId);
       setStage(1); // Auto transition to parcel assignment stage
     }
   }
 }, [wavesList.data, waveId]);

  // Inbound Trip & Unit types
  type Trip={id:number;external_trip_no:string;vehicle_plate?:string;seal_no?:string;expected_at?:string;arrived_at?:string;status:string;unit_count:number;expected_piece_count:number;linked_piece_count:number};
  type Unit={id:number;external_unit_no:string;unit_type:string;expected_piece_count?:number;status:string;linked_piece_count:number;driver_count:number;wave_count:number;declared_piece_count:number;scanned_piece_count:number;exception_piece_count:number};
  type UnitParcel={unit_id:number;parcel_id:number;tracking_no:string;parcel_status:string;link_source:string;item_status?:string;task_code?:string;driver_name?:string};
  type Unlinked={unit_id:number;external_unit_no:string;tracking_no:string;parcel_status:string;station_code?:string};
  type TripDetail={trip:Trip&Record<string,unknown>;units:Unit[];parcels:UnitParcel[];unlinkedDeclarations:Unlinked[]};

  const wave=useQuery({queryKey:['planning-wave',station,waveId],enabled:!!waveId,queryFn:()=>api<WaveResult>(`/ops/v1/planning/waves/${waveId}`,session,{},station)});

  // Inbound Trip / Pallet planning queries
  const tripsQuery = useQuery({
    queryKey: ['arrival-trips', station, serviceDate],
    queryFn: () => api<Trip[]>(`/ops/v1/arrival-trips?serviceDate=${serviceDate}`, session, {}, station)
  });

  const matchedTrip = useMemo(() => {
    const waveCode = wave.data?.wave.wave_code;
    if (!waveCode || !tripsQuery.data) return undefined;
    return tripsQuery.data.find(t => t.external_trip_no === waveCode);
  }, [tripsQuery.data, wave.data?.wave.wave_code]);

  const tripId = matchedTrip?.id;

  const tripDetailQuery = useQuery({
    queryKey: ['arrival-trip', station, tripId],
    enabled: !!tripId,
    queryFn: () => api<TripDetail>(`/ops/v1/arrival-trips/${tripId}`, session, {}, station)
  });
  const refresh=async()=>Promise.all([cache.invalidateQueries({queryKey:['planning-parcels',station,serviceDate]}),cache.invalidateQueries({queryKey:['planning-shifts',station,serviceDate]}),cache.invalidateQueries({queryKey:['planning-wave',station,waveId]}),cache.invalidateQueries({queryKey:['dispatch-waves-list',station,serviceDate]})]);
  const command=useMutation({
    mutationFn:({path,body}:{path:string;body:unknown})=>api<any>(path,session,{method:'POST',body:JSON.stringify(body)},station),
    onSuccess:async(res, variables)=>{
      let msg = t('dispatch.commandSuccess');
      if (res && typeof res.changedCount === 'number') {
        if (res.changedCount === 0) {
          if (variables.path.includes('assign-defaults')) {
            message.warning('⚡ 一键自动指派完成：但实际没有匹配到任何包裹！请检查：1. 司机是否设置了「配送区域」偏好 2. 该偏好区域内今天是否有未分配的有效包裹 3. 司机容量是否已满。', 8);
          } else {
            message.warning(`👉 指派操作完成：但实际分配包裹数量为 0 件。请确认该区域是否有处于待派送状态（RECEIVED/AT_STATION/SORTED）且未被其他任务占用的有效包裹。`, 8);
          }
        } else {
          msg += ` (成功处理了 ${res.changedCount} 件包裹，当前司机总计已分 ${res.assignedCount}/${res.capacity} 件)`;
          message.success(msg, 5);
        }
      } else {
        message.success(msg, 4);
      }
      setSelected(new Set());
      await refresh();
      if (tripId) await tripDetailQuery.refetch();
    },
    onError:(e:Error)=>message.error(`操作失败: ${e.message}`, 6)
  });
 const saveShift=useMutation({mutationFn:(value:{driverId:number;availabilityStatus:string;parcelCapacity:number})=>api('/ops/v1/planning/shifts',session,{method:'PUT',body:JSON.stringify({...value,serviceDate,note:'Operations planning'})},station),onSuccess:async()=>{message.success(t('dispatch.shiftSaved'));await refresh();}});
 const toggle=useCallback((id:number)=>setSelected(current=>{const next=new Set(current);if(next.has(id))next.delete(id);else next.add(id);return next;}),[]);
 const all=useMemo(()=>parcels.data??[],[parcels.data]);const visible=useMemo(()=>initialFilter==='unmatched-area'?all.filter(p=>p.exception_code==='UNMATCHED_AREA'):initialFilter==='unassigned'?all.filter(p=>!p.driver_id&&!p.exception_code):all,[all,initialFilter]);const areas=useMemo(()=>Array.from(new Map(all.filter(p=>p.area_version_id).map(p=>[p.area_version_id!,{value:p.area_version_id!,label:p.area_code??String(p.area_version_id)}])).values()),[all]);const assigned=all.filter(p=>p.driver_id).length;const exceptions=all.filter(p=>p.exception_code).length;const available=(shifts.data??[]).filter(s=>s.availability_status==='AVAILABLE');const capacity=available.reduce((sum,s)=>sum+(s.parcel_capacity??0),0);const waveStatus=wave.data?.wave.status;

  const linkedAreasByUnit = useMemo(() => {
    const mapping: Record<number, number[]> = {};
    if (!tripDetailQuery.data?.parcels || !all) return mapping;
    const parcelAreaMap = new Map(all.map(p => [p.parcel_id, p.area_version_id]));
    
    for (const up of tripDetailQuery.data.parcels) {
      if (up.link_source === 'AREA_PLAN') {
        const areaVerId = parcelAreaMap.get(up.parcel_id);
        if (areaVerId) {
          if (!mapping[up.unit_id]) {
            mapping[up.unit_id] = [];
          }
          if (!mapping[up.unit_id].includes(areaVerId)) {
            mapping[up.unit_id].push(areaVerId);
          }
        }
      }
    }
    return mapping;
  }, [tripDetailQuery.data?.parcels, all]);

  const autoFillAllUnits = async () => {
    const units = tripDetailQuery.data?.units;

    if (!tripId || !units || units.length === 0) {
      message.warning('当前波次未关联有效的干线批次，或该批次无板笼结构。目前开发阶段请重新创建新波次以自动绑定！');
      return;
    }

    if (areas.length === 0) {
      message.warning('当前站点下暂无配送小区，无法执行一键预装载。');
      return;
    }

    try {
      // Group areas by unit index (modulo load balancing)
      const unitAreasMap: Record<number, number[]> = {};
      for (let index = 0; index < areas.length; index++) {
        const areaVerId = areas[index].value;
        const unit = units[index % units.length];
        if (!unitAreasMap[unit.id]) unitAreasMap[unit.id] = [];
        unitAreasMap[unit.id].push(areaVerId);
      }

      // Call API once per unit with its full list of assigned areas!
      for (const [unitIdStr, areaIds] of Object.entries(unitAreasMap)) {
        const unitId = Number(unitIdStr);
        await api(`/ops/v1/handling-units/${unitId}/area-fill`, session, {
          method: 'POST',
          body: JSON.stringify({
            areaVersionIds: areaIds,
            reason: 'Auto pre-arrival allocation from dispatch wave planning'
          })
        }, station);
      }

      message.success('已根据配送区域分布，采用轮询均衡算法成功一键预分配所有板笼！');
      await Promise.all([
        cache.invalidateQueries({ queryKey: ['arrival-trip', station, tripId] }),
        cache.invalidateQueries({ queryKey: ['arrival-trips', station, serviceDate] }),
        refresh()
      ]);
    } catch (e: any) {
      message.error('一键预装载失败: ' + e.message);
    }
  };

 const defaultWaveCode = useMemo(() => {
   if (!serviceDate) return '';
   const cleanDate = serviceDate.replace(/-/g, '');
   return `${cleanDate}-WAVE-01`;
 }, [serviceDate]);

 const createWave=async(values:{waveCode?:string;routeCode?:string})=>{
   try {
     const finalWaveCode = (values.waveCode ?? defaultWaveCode).trim();
     const finalRouteCode = (values.routeCode ?? 'DYNAMIC-ROUTE').trim();
     const result=await api<{wave:{id:number}}|{id:number}>('/ops/v1/planning/waves',session,{method:'POST',body:JSON.stringify({waveCode: finalWaveCode, routeCode: finalRouteCode, serviceDate})},station);
     setWaveId('wave'in result?result.wave.id:result.id);
     setStage(1);
     message.success('派送规划波次已成功启动，进入包裹分配阶段');
   } catch (e: any) {
     message.error('启动波次失败，请重试');
   }
 };

 const stepItems=[{title:t('dispatch.stageCapacity'),description:t('dispatch.capacitySummary',{drivers:available.length,capacity})},{title:t('dispatch.stageAssign'),description:t('dispatch.assignmentSummary',{assigned,total:all.length})},{title: '3. 预检发布并生成派送任务', description: waveStatus ?? '尚未发布'}];
 const [shiftSearch, setShiftSearch] = useState('');
 const filteredShifts = useMemo(() => {
   const raw = shifts.data ?? [];
   if (!shiftSearch.trim()) return raw;
   const q = shiftSearch.toLowerCase().trim();
   return raw.filter(s => 
     s.driver_name?.toLowerCase().includes(q) || 
     s.driver_code?.toLowerCase().includes(q) ||
     String(s.driver_id).includes(q)
   );
 }, [shifts.data, shiftSearch]);

 const capacityTableColumns = [
   {
     title: '工号/ID',
     dataIndex: 'driver_id',
     key: 'driver_id',
     width: '15%',
     render: (id: number, r: Shift) => <Typography.Text type="secondary">{r.driver_code ?? id}</Typography.Text>
   },
   {
     title: '司机姓名',
     dataIndex: 'driver_name',
     key: 'driver_name',
     width: '30%',
     render: (name: string) => <strong style={{ color: '#101828' }}>{name}</strong>
   },
   {
     title: '出勤状态',
     dataIndex: 'availability_status',
     key: 'availability_status',
     width: '30%',
     render: (status: string, record: Shift) => (
       <Select
         size="small"
         value={status}
         onChange={(val) => saveShift.mutate({
           driverId: record.driver_id,
           availabilityStatus: val,
           parcelCapacity: record.parcel_capacity ?? 200
         })}
         style={{ width: 110 }}
         options={[
           { value: 'AVAILABLE', label: <Tag color="green" style={{ margin: 0 }}>AVAILABLE</Tag> },
           { value: 'UNAVAILABLE', label: <Tag color="default" style={{ margin: 0 }}>OFF-DUTY</Tag> }
         ]}
       />
     )
   },
   {
     title: '最大运力 (件)',
     dataIndex: 'parcel_capacity',
     key: 'parcel_capacity',
     width: '25%',
     render: (cap: number, record: Shift) => (
       <InputNumber
         size="small"
         min={1}
         max={1000}
         value={cap ?? 200}
         onBlur={(e) => {
           const val = Number(e.target.value);
           if (val > 0 && val !== cap) {
             saveShift.mutate({
               driverId: record.driver_id,
               availabilityStatus: record.availability_status,
               parcelCapacity: val
             });
           }
         }}
         onPressEnter={(e) => {
           const val = Number((e.target as HTMLInputElement).value);
           if (val > 0 && val !== cap) {
             saveShift.mutate({
               driverId: record.driver_id,
               availabilityStatus: record.availability_status,
               parcelCapacity: val
             });
             (e.target as HTMLInputElement).blur();
           }
         }}
         style={{ width: '100%' }}
       />
     )
   }
 ];

  const [readinessSearch, setReadinessSearch] = useState('');
  const [readinessStatus, setReadinessStatus] = useState<string>('ALL');

  const filteredReadinessShifts = useMemo(() => {
    let list = shifts.data ?? [];
    if (readinessStatus !== 'ALL') {
      list = list.filter(s => s.availability_status === readinessStatus);
    }
    if (!readinessSearch.trim()) return list;
    const q = readinessSearch.toLowerCase().trim();
    return list.filter(s =>
      s.driver_name?.toLowerCase().includes(q) ||
      s.driver_code?.toLowerCase().includes(q) ||
      String(s.driver_id).includes(q)
    );
  }, [shifts.data, readinessSearch, readinessStatus]);

  const readinessTableColumns = [
    {
      title: '工号/ID',
      dataIndex: 'driver_id',
      key: 'driver_id',
      width: '15%',
      render: (id: number, r: Shift) => <Typography.Text type="secondary">{r.driver_code ?? id}</Typography.Text>
    },
    {
      title: '司机姓名',
      dataIndex: 'driver_name',
      key: 'driver_name',
      width: '25%',
      render: (name: string) => <strong style={{ color: '#1d2939' }}>{name}</strong>
    },
    {
      title: '出勤状态',
      dataIndex: 'availability_status',
      key: 'availability_status',
      width: '20%',
      render: (status: string, record: Shift) => (
        <Select
          size="small"
          value={status}
          onChange={(val) => saveShift.mutate({
            driverId: record.driver_id,
            availabilityStatus: val,
            parcelCapacity: record.parcel_capacity ?? 200
          })}
          style={{ width: 120 }}
          options={[
            { value: 'AVAILABLE', label: <Tag color="green" style={{ margin: 0 }}>AVAILABLE</Tag> },
            { value: 'UNAVAILABLE', label: <Tag color="default" style={{ margin: 0 }}>OFF-DUTY</Tag> }
          ]}
        />
      )
    },
    {
      title: '运力负载 / 饱和度',
      key: 'utilization',
      width: '40%',
      render: (_: unknown, r: Shift) => {
        const cap = r.parcel_capacity ?? 0;
        const assignedCount = r.assigned_count ?? 0;
        const percent = cap > 0 ? Math.min(100, Math.round((assignedCount / cap) * 100)) : 0;
        const isOverloaded = assignedCount > cap;
        
        let progressStatus: 'success' | 'normal' | 'exception' = 'normal';
        if (isOverloaded) progressStatus = 'exception';
        else if (percent >= 90) progressStatus = 'success';

        return (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px' }}>
              <span style={{ fontWeight: 500 }}>
                已分 {assignedCount} / 容量 {cap > 0 ? cap : '—'} 件
              </span>
              <span style={{ color: isOverloaded ? '#d92d20' : '#475467' }}>
                {isOverloaded ? `超载 ${assignedCount - cap} 件` : `余 ${cap - assignedCount} 件`}
              </span>
            </div>
            <Progress 
              percent={percent} 
              size="small" 
              status={progressStatus} 
              strokeColor={isOverloaded ? '#d92d20' : undefined}
              showInfo={false}
              style={{ margin: 0 }}
            />
          </div>
        );
      }
    }
  ];

  const filteredVisibleParcels = useMemo(() => {
    let list = visible;
    if (currentArea) {
      list = list.filter(p => p.area_version_id === currentArea);
    }
    if (driver) {
      // Show unassigned parcels AND parcels assigned to THIS driver. Other drivers' parcels are filtered out.
      list = list.filter(p => !p.driver_id || p.driver_id === driver);
    }
    return list;
  }, [visible, currentArea, driver]);

 return <div className="planning-console">
  <Card className="planning-stage-card"><Steps current={stage} onChange={setStage} items={stepItems}/><Space wrap className="planning-stage-actions"><Tag color="blue">{station}</Tag><Tag>{serviceDate}</Tag><Button type="primary" onClick={()=>setCapacityOpen(true)}>{t('dispatch.manageCapacity')}</Button><Button onClick={()=>setListOpen(true)}>{t('dispatch.openWorklist')} ({visible.length})</Button></Space></Card>
  {(parcels.error||shifts.error||wave.error)&&<Alert type="error" showIcon message={(parcels.error??shifts.error??wave.error)?.message}/>} 
  {stage===0&&<Card 
     title={<div style={{ display: 'flex', alignItems: 'center', gap: '16px', width: '100%', flexWrap: 'wrap' }}>
       <span style={{ fontSize: '16px', fontWeight: 600 }}>{t('dispatch.stageCapacity')}</span>
       <Input.Search
         placeholder="输入姓名/工号/ID 极速筛选司机..."
         allowClear
         value={readinessSearch}
         onChange={e => setReadinessSearch(e.target.value)}
         style={{ width: 240 }}
         size="small"
       />
       <Select
         size="small"
         value={readinessStatus}
         onChange={setReadinessStatus}
         style={{ width: 140 }}
         options={[
           { value: 'ALL', label: '出勤状态: 全部' },
           { value: 'AVAILABLE', label: '出勤状态: AVAILABLE' },
           { value: 'UNAVAILABLE', label: '出勤状态: OFF-DUTY' }
         ]}
       />
     </div>} 
     extra={<Button type="primary" disabled={!available.length||capacity<all.length-exceptions} onClick={()=>setStage(1)}>{t('dispatch.continueAssign')}</Button>}
   >
     <Alert 
       showIcon 
       type={capacity>=all.length-exceptions?'success':'warning'} 
       message={t('dispatch.capacityReadiness',{drivers:available.length,capacity,required:all.length-exceptions})}
       style={{ marginBottom: '16px' }}
     />
     <Table
       rowKey="driver_id"
       size="middle"
       dataSource={filteredReadinessShifts}
       columns={readinessTableColumns}
       pagination={{
         pageSize: 15,
         showSizeChanger: true,
         pageSizeOptions: ['15', '30', '50', '100'],
         showTotal: (total) => `共 ${total} 位排班司机`,
         size: 'small'
       }}
     />
   </Card>}

   {stage===1&&<div className="planning-grid">
     <section className="planning-map-panel" style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
       <div style={{ background: '#fff', padding: '10px', borderRadius: '8px', border: '1px solid #d0d5dd', display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '8px' }}>
         <Space>
           <strong style={{ fontSize: '13px', color: '#344054' }}>📍 当前排程区域过滤:</strong>
           <Select 
             style={{ width: 220 }}
             placeholder="展示本站全部区域包裹 (不推荐)"
             allowClear
             value={currentArea}
             onChange={setCurrentArea}
             options={areas}
           />
         </Space>
         <Tag color={currentArea ? 'purple' : 'orange'}>
           {currentArea ? `该区域地图包裹数: ${filteredVisibleParcels.length} 件` : `🚨 警告: 正在显示全站 ${visible.length} 件包裹 (易造成浏览器卡顿)`}
         </Tag>
       </div>

       <PlanningMap station={station} parcels={filteredVisibleParcels} selected={selected} onToggle={toggle} onSelect={setFocus}/>
       
       <div className="map-legend" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
         <div>
           <Tag color="blue">{t('dispatch.unassigned')}</Tag>
           <Tag color="green">{t('dispatch.assigned')}</Tag>
           <Tag color="orange">{t('dispatch.dataException')}</Tag>
         </div>
         <span style={{ fontSize: '12px', color: '#667085' }}>💡 提示：在地图上直接点击包裹点，可多选/反选以进行划圈圈中分配</span>
       </div>
     </section>
      <aside className="planning-sidebar">
        {/* Wave Switcher & Creator at the top of the Sidebar */}
        <div style={{ background: '#fff', border: '1px solid #d0d5dd', padding: '12px', borderRadius: '8px', marginBottom: '12px' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
            <span style={{ fontSize: '13px', fontWeight: 600, color: '#344054' }}>📦 选择派送波次</span>
            <Button 
              type="primary" 
              size="small" 
              ghost
              style={{ fontSize: '12px' }}
              onClick={() => {
                const nextSeq = (wavesList.data ?? []).length + 1;
                const cleanDate = serviceDate.replace(/-/g, '');
                const defaultCode = `${cleanDate}-WAVE-0${nextSeq}`;
                const code = prompt("请输入新建派送波次编码 (同站唯一):", defaultCode);
                if (code && code.trim()) {
                  createWave({ waveCode: code.trim() });
                }
              }}
            >
              ➕ 新增波次
            </Button>
          </div>
          <Select
            style={{ width: '100%' }}
            placeholder="请选择或创建派送波次"
            value={waveId}
            onChange={(val) => {
              setWaveId(val);
              setStage(1);
            }}
            options={(wavesList.data ?? []).map(w => ({
              value: w.wave_id ?? w.id,
              label: `🌊 ${w.wave_code} (${w.wave_status === 'DRAFT' ? '草稿' : w.wave_status === 'PUBLISHED' ? '已发布' : w.wave_status})`
            }))}
          />
        </div>

        <Card 
          title={<div style={{ display: 'flex', alignItems: 'center', gap: '8px', justifyContent: 'space-between', width: '100%' }}>
            <strong>🚚 统一并轨控制台 (Planning Console)</strong>
            {waveId && <Tag color="cyan" style={{ margin: 0 }}>{waveStatus === 'DRAFT' ? '草稿' : waveStatus === 'PUBLISHED' ? '已发布' : waveStatus}</Tag>}
          </div>}
          styles={{ body: { padding: '8px' } }}
        >
          <Tabs
            defaultActiveKey="outbound"
            size="middle"
            items={[
              {
                key: 'outbound',
                label: <strong>🚚 司机指派 (Outbound)</strong>,
                children: !waveId ? (
                  <div style={{ padding: '32px 16px', textAlign: 'center', color: '#667085' }}>
                    <div style={{ fontSize: '32px', marginBottom: '12px' }}>🚚</div>
                    <p style={{ fontSize: '13px', margin: 0 }}>请在上方选择一个执行中的派送波次，或者点击「新增波次」开启今天的规划指派工作！</p>
                  </div>
                ) : (
                  <Space direction="vertical" size="middle" style={{ width: '100%', marginTop: '8px' }}>
                    {/* Simplified wave metrics bar */}
                    <div style={{ background: '#f8f9fa', padding: '10px 12px', borderRadius: '6px', border: '1px solid #e9ecef', fontSize: '13px', color: '#344054' }}>
                      今日规划包裹：<strong>{all.length - exceptions}</strong> 件 · 已分配：<strong>{assigned}</strong> 件
                    </div>

                    {/* 1. One-click automated area default assignment */}
                    <Tooltip title="根据司机在「配送区域」管理中维护的默认服务区域，将匹配该区域的待指派包裹自动一键批量分配给当前出勤的司机。">
                      <Button 
                        block 
                        type="primary" 
                        style={{ background: '#1890ff', borderColor: '#1890ff', height: '36px', fontWeight: 600 }}
                        disabled={waveStatus!=='DRAFT'} 
                        onClick={() => {
                          command.mutate({
                            path: `/ops/v1/planning/waves/${waveId}/assign-defaults`,
                            body: {}
                          });
                        }}
                      >
                        ⚡ 一键指派默认司机
                      </Button>
                    </Tooltip>

                    <div style={{ borderTop: '1px dashed #e4e7ec', margin: '4px 0' }} />

                    {/* 2. Manual Assign Section */}
                    <div style={{ background: '#f5f3ff', padding: '10px', borderRadius: '6px', border: '1px solid #ddd6fe' }}>
                      <span style={{ fontSize: '12px', fontWeight: 600, color: '#5b21b6', display: 'block', marginBottom: '6px' }}>✍️ 第一步：指定承接司机</span>
                      <Select 
                        value={driver} 
                        onChange={setDriver} 
                        style={{ width: '100%' }} 
                        allowClear
                        placeholder="选择司机" 
                        options={available.map(s=>({value:s.driver_id,label:`${s.driver_name} (已分: ${s.assigned_count} 件)`}))}
                      />
                    </div>

                    {/* Method A: Area Assign */}
                    <div style={{ background: '#fff', border: '1px solid #e4e7ec', padding: '10px', borderRadius: '6px' }}>
                      <span style={{ fontSize: '12px', fontWeight: 600, color: '#344054', display: 'block', marginBottom: '6px' }}>📦 方法 A：按配送区域指派</span>
                      <Space direction="vertical" style={{ width: '100%' }}>
                        <Select 
                          value={areaVersion} 
                          onChange={(val) => {
                            setAreaVersion(val);
                            if (val) setCurrentArea(val);
                          }} 
                          allowClear 
                          style={{ width: '100%' }} 
                          placeholder="选择物理区域 (Area)" 
                          options={areas}
                        />
                        <Tooltip title="将选定配送小区（Area）内所有尚未指派的包裹，一键全部指派给上方选定的司机。">
                          <Button 
                            block 
                            type="primary"
                            ghost
                            disabled={!driver||!areaVersion||waveStatus!=='DRAFT'} 
                            onClick={() => {
                              command.mutate({
                                path: `/ops/v1/planning/waves/${waveId}/assignments`,
                                body: {
                                  driverId: driver,
                                  parcelIds: [],
                                  areaVersionIds: [areaVersion],
                                  reason: 'Whole-area assignment'
                                }
                              });
                            }}
                          >
                            👉 一键指派该区域
                          </Button>
                        </Tooltip>
                      </Space>
                    </div>

                    {/* Method B: Map selection */}
                    <div style={{ background: '#fff', border: '1px solid #e4e7ec', padding: '10px', borderRadius: '6px' }}>
                      <span style={{ fontSize: '12px', fontWeight: 600, color: '#344054', display: 'block', marginBottom: '6px' }}>🗺️ 方法 B：地图框选指派</span>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: '12px', color: '#667085', marginBottom: '6px' }}>
                        <span>已勾选包裹数：<strong style={{ color: '#722ed1' }}>{selected.size}</strong> 件</span>
                        {selected.size > 0 && <Button size="small" type="link" danger style={{ padding: 0 }} onClick={() => setSelected(new Set())}>取消圈中</Button>}
                      </div>
                      <Space style={{ width: '100%' }} direction="vertical" size="small">
                        <Tooltip title="在右侧地图上圈中或在明细列表中勾选多个包裹后，点击此按钮一键批量指派给指定司机。">
                          <Button 
                            block 
                            type="primary" 
                            disabled={!driver||!selected.size||waveStatus!=='DRAFT'} 
                            onClick={() => {
                              command.mutate({
                                path: `/ops/v1/planning/waves/${waveId}/assignments`,
                                body: {
                                  driverId: driver,
                                  parcelIds: [...selected],
                                  areaVersionIds: [],
                                  reason: 'Map parcel assignment'
                                }
                              });
                            }}
                          >
                            🗺️ 指派圈中 ({selected.size} 件)
                          </Button>
                        </Tooltip>
                        <Tooltip title="将当前圈中或勾选的包裹批量从其当前司机名下移除，重新退回未指派包裹池中。">
                          <Button 
                            block 
                            danger
                            disabled={!selected.size||waveStatus!=='DRAFT'} 
                            onClick={() => {
                              command.mutate({
                                path: `/ops/v1/planning/waves/${waveId}/assignments`,
                                body: {
                                  driverId: 0,
                                  parcelIds: [...selected],
                                  areaVersionIds: [],
                                  reason: 'Resetting assignments'
                                }
                              });
                            }}
                          >
                            🗑️ 一键解除分配
                          </Button>
                        </Tooltip>
                      </Space>
                    </div>

                    <Button 
                      block 
                      type="primary" 
                      size="large" 
                      onClick={() => setStage(2)} 
                      style={{ marginTop: '8px', background: '#389e0d', borderColor: '#389e0d', height: '40px', fontWeight: 600 }}
                    >
                      完成分配，进入预检发布 ➡️
                    </Button>
                  </Space>
                )
              },
              {
                key: 'inbound',
                label: <strong>📦 干线板笼规划 (Inbound)</strong>,
                children: !waveId ? (
                  <div style={{ padding: '32px 16px', textAlign: 'center', color: '#667085' }}>
                    <div style={{ fontSize: '32px', marginBottom: '12px' }}>📦</div>
                    <p style={{ fontSize: '13px', margin: 0 }}>请在上方选择一个执行中的派送波次，系统将同步加载或一键自动补建该波次的到仓板笼数据结构！</p>
                  </div>
                ) : (
                  <Space direction="vertical" size="middle" style={{ width: '100%', marginTop: '8px' }}>
                    {/* Pre-sort area fill trigger */}
                    <Tooltip title="根据本波次所有未指派包裹所属的配送小区，采用轮询负载均衡算法将这些小区均匀地预分配绑定到各个到仓板笼（Pallet）中，指导上游物理打包。">
                      <Button 
                        block 
                        type="primary"
                        style={{ background: '#722ed1', borderColor: '#722ed1', height: '36px', fontWeight: 600 }}
                        loading={command.isPending}
                        onClick={autoFillAllUnits}
                      >
                        ⚡ 智能一键预装载
                      </Button>
                    </Tooltip>

                    <Alert 
                      showIcon 
                      type="info" 
                      message="在此关联小区后，上游分拣中心将按此映射规则进行物理大打包。物理卡车到港后，实扫将与之核对。" 
                      style={{ fontSize: '12px' }}
                    />

                    {/* Render Pre-planned Pallets */}
                    {tripDetailQuery.isPending && <div style={{ textAlign: 'center', padding: '20px' }}>加载板笼规划中...</div>}
                    {tripDetailQuery.data && (
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', maxHeight: '420px', overflowY: 'auto', paddingRight: '4px' }}>
                        {tripDetailQuery.data.units.map((u) => {
                          const isHovered = currentArea != null && (linkedAreasByUnit[u.id] ?? []).includes(currentArea);
                          return (
                            <Card 
                              key={u.id}
                              size="small"
                              style={{ 
                                border: isHovered ? '2px solid #722ed1' : '1px solid #d0d5dd',
                                background: isHovered ? '#f9f5ff' : '#ffffff',
                                cursor: 'pointer'
                              }}
                              onClick={() => {
                                const linked = linkedAreasByUnit[u.id] ?? [];
                                if (linked.length > 0) {
                                  setCurrentArea(linked[0]);
                                }
                              }}
                            >
                              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '6px' }}>
                                <strong style={{ color: '#101828' }}>📦 {u.external_unit_no.split('-').pop()}</strong>
                                <Tag color="purple">已划归: {u.linked_piece_count} 件</Tag>
                              </div>
                              <div style={{ fontSize: '12px', color: '#475467', marginBottom: '4px' }}>
                                🎯 规划关联小区 (Area):
                              </div>
                              <Select
                                mode="multiple"
                                style={{ width: '100%' }}
                                placeholder="选择关联配送小区"
                                value={linkedAreasByUnit[u.id] ?? []}
                                onChange={(val) => {
                                  command.mutate({
                                    path: `/ops/v1/handling-units/${u.id}/area-fill`,
                                    body: { areaVersionIds: val, reason: 'Manual pre-arrival planning area assign' }
                                  });
                                }}
                                options={areas}
                                optionFilterProp="label"
                              />
                            </Card>
                          );
                        })}
                      </div>
                    )}
                  </Space>
                )
              }
            ]}
          />
        </Card>
      </aside></div>}
  {stage===2&&<Card title={t('dispatch.stageRelease')} extra={<Button onClick={()=>setStage(1)}>{t('dispatch.backAssign')}</Button>}><Space direction="vertical" size="large" style={{width:'100%'}}><Alert showIcon type={exceptions||assigned<all.length-exceptions?'warning':'success'} message={t('dispatch.preflightSummary',{assigned,ready:all.length-exceptions,exceptions})}/><List header={t('dispatch.driverTasks')} bordered dataSource={wave.data?.drivers??[]} renderItem={item=><List.Item extra={<Tag color={item.remaining_capacity<0?'red':'green'}>{item.remaining_capacity} {t('dispatch.remaining')}</Tag>}><List.Item.Meta title={item.driver_name} description={`${item.parcel_count}/${item.parcel_capacity}`}/></List.Item>}/><Space><Button disabled={waveStatus!=='DRAFT'||assigned<all.length-exceptions} onClick={()=>command.mutate({path:`/ops/v1/planning/waves/${waveId}/freeze`,body:{reason:'Planning preflight approved'}})}>{t('dispatch.freeze')}</Button><Button type="primary" disabled={waveStatus!=='FROZEN'} onClick={()=>command.mutate({path:`/ops/v1/planning/waves/${waveId}/publish`,body:{reason:'Released to driver scan lists'}})}>{t('dispatch.publish')}</Button></Space></Space></Card>}
  <Drawer 
    title={<div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
      <span style={{ fontSize: '18px', fontWeight: 600 }}>{t('dispatch.manageCapacity')}</span>
      <Input.Search 
        placeholder="按姓名、工号、司机 ID 极速搜索..." 
        allowClear 
        value={shiftSearch}
        onChange={e => setShiftSearch(e.target.value)}
        onSearch={setShiftSearch}
        style={{ width: '100%', marginTop: '4px' }} 
      />
    </div>}
    width={640} 
    open={capacityOpen} 
    onClose={()=>setCapacityOpen(false)}
    styles={{ body: { padding: '12px' } }}
  >
    <Table
      rowKey="driver_id"
      size="small"
      dataSource={filteredShifts}
      columns={capacityTableColumns}
      pagination={{
        pageSize: 10,
        showSizeChanger: true,
        showTotal: (total) => `共 ${total} 位司机`,
        size: 'small'
      }}
      style={{ marginTop: '4px' }}
    />
  </Drawer>
  <Drawer title={t('dispatch.parcelList')} width={900} open={listOpen} onClose={()=>setListOpen(false)}><Table rowKey="parcel_id" size="small" dataSource={visible} pagination={{pageSize:20}} rowSelection={{selectedRowKeys:[...selected],onChange:keys=>setSelected(new Set(keys.map(Number)))}} onRow={row=>({onClick:()=>setFocus(row)})} columns={[{title:t('field.tracking_no'),dataIndex:'tracking_no'},{title:t('common.status'),dataIndex:'status'},{title:t('dispatch.area'),dataIndex:'area_code'},{title:t('dispatch.driver'),dataIndex:'driver_name'},{title:t('dispatch.exception'),dataIndex:'exception_code'}]}/></Drawer>
  <Drawer open={!!focus} onClose={()=>setFocus(undefined)} title={focus?.tracking_no}>{focus&&<><List dataSource={Object.entries(focus)} renderItem={([key,value])=><List.Item><Typography.Text type="secondary">{key}</Typography.Text><Typography.Text>{String(value??'—')}</Typography.Text></List.Item>}/>{focus.driver_id&&waveId&&<Space.Compact block><Select value={driver} onChange={setDriver} style={{width:'70%'}} options={available.map(s=>({value:s.driver_id,label:s.driver_name}))}/><Button disabled={!driver||driver===focus.driver_id} onClick={()=>command.mutate({path:`/ops/v1/planning/waves/${waveId}/parcels/${focus.parcel_id}/reassign`,body:{driverId:driver,reason:'Operator map reassignment'}})}>{t('dispatch.reassign')}</Button></Space.Compact>}</>}</Drawer>
 </div>;
}
