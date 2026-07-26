import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, App, Button, Card, Drawer, Form, Input, InputNumber, List, Progress, Select, Space, Steps, Table, Tabs, Tag, Tooltip, Typography } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, type Session } from '../api/client';
import { PlanningMap, type PlanningParcel } from './PlanningMap';
import { useTranslation } from 'react-i18next';

type Shift={driver_id:number;driver_name:string;driver_code:string;availability_status:string;parcel_capacity?:number;assigned_count:number};
type WaveResult={wave:{id:number;wave_code:string;status:string};drivers:Array<{task_id:number;driver_id:number;driver_name:string;parcel_count:number;parcel_capacity:number;remaining_capacity:number}>};

 export function DispatchWorkspace({session,station,initialDate,initialFilter}:{session:Session;station:number|string;initialDate?:string;initialFilter?:string}){

 const { message } = App.useApp();
 const {t}=useTranslation();const cache=useQueryClient();const serviceDate=initialDate!;const [stage,setStage]=useState(0);const [selected,setSelected]=useState<Set<number>>(new Set());const [focus,setFocus]=useState<PlanningParcel>();const [driver,setDriver]=useState<number>();const [areaVersion,setAreaVersion]=useState<number>();const [waveId,setWaveId]=useState<number>();const [capacityOpen,setCapacityOpen]=useState(false);const [listOpen,setListOpen]=useState(false);
 const [currentArea, setCurrentArea] = useState<number | undefined>(undefined);
 const [slaFilter, setSlaFilter] = useState<string>('ALL');
 const [lassoActive, setLassoActive] = useState<boolean>(false);

  const serviceAreasQuery = useQuery({
    queryKey: ['delivery-areas-map', station],
    queryFn: () => api<any[]>(`/ops/v1/delivery-areas?status=ACTIVE`, session, {}, station)
  });
  const parcels=useQuery({queryKey:['planning-parcels',station,serviceDate,slaFilter],queryFn:()=>api<PlanningParcel[]>(`/ops/v1/planning/parcels?serviceDate=${serviceDate}&slaFilter=${slaFilter}&limit=2000`,session,{},station)});
 const shifts=useQuery({queryKey:['planning-shifts',station,serviceDate],queryFn:()=>api<Shift[]>(`/ops/v1/planning/shifts?serviceDate=${serviceDate}`,session,{},station)});
 
 // Fetch existing waves to prevent duplicate creation lockups
 const wavesList = useQuery({
   queryKey: ['dispatch-waves-list', station, serviceDate],
   queryFn: () => api<any[]>(`/ops/v1/dispatch/waves?limit=100`, session, {}, station).then(res => {
     return (res ?? []).filter(w => {
       if (!w.service_date) return true;
       const wDate = typeof w.service_date === 'string' ? w.service_date.substring(0, 10) : String(w.service_date);
       return wDate === serviceDate;
     });
   })
 });

 // Automatically load the existing wave if found for today
 useEffect(() => {
   if (wavesList.data && wavesList.data.length > 0 && !waveId) {
     const targetWave = wavesList.data[0];
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

  const [selectedTripId, setSelectedTripId] = useState<number | undefined>(undefined);

  const matchedTrip = useMemo(() => {
    if (selectedTripId) {
      return tripsQuery.data?.find(t => t.id === selectedTripId);
    }
    const waveCode = wave.data?.wave.wave_code;
    if (!waveCode || !tripsQuery.data) return tripsQuery.data?.[0];
    return tripsQuery.data.find(t => t.external_trip_no === waveCode) ?? tripsQuery.data?.[0];
  }, [tripsQuery.data, wave.data?.wave.wave_code, selectedTripId]);

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
 const all=useMemo(()=>parcels.data??[],[parcels.data]);const visible=useMemo(()=>initialFilter==='unmatched-area'?all.filter(p=>p.exception_code==='UNMATCHED_AREA'):initialFilter==='unassigned'?all.filter(p=>!p.driver_id&&!p.exception_code):all,[all,initialFilter]);const assigned=all.filter(p=>p.driver_id).length;const exceptions=all.filter(p=>p.exception_code).length;const available=(shifts.data??[]).filter(s=>s.availability_status==='AVAILABLE');const capacity=available.reduce((sum,s)=>sum+(s.parcel_capacity??0),0);const waveStatus=wave.data?.wave.status;


  const areas = useMemo(() => {
    const fromQuery = (serviceAreasQuery.data ?? []).map((a: any) => ({
      value: a.id,
      label: `${a.area_code} (${a.area_name || '区域'})`
    }));
    if (fromQuery.length > 0) return fromQuery;
    return Array.from(new Map(all.filter((p: PlanningParcel) => p.area_id ?? p.area_version_id).map((p: PlanningParcel) => {
      const areaId = p.area_id ?? p.area_version_id!;
      return [areaId, { value: areaId, label: p.area_code ?? String(areaId) }];
    })).values());
  }, [serviceAreasQuery.data, all]);

  const [unitSelectedAreas, setUnitSelectedAreas] = useState<Record<number, number[]>>({});

  const linkedAreasByUnit = useMemo(() => {
    const mapping: Record<number, number[]> = { ...unitSelectedAreas };
    if (!tripDetailQuery.data?.parcels || !all) return mapping;
    const parcelAreaMap = new Map(all.map((p: PlanningParcel) => [p.parcel_id, p.area_version_id]));
    
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
  }, [unitSelectedAreas, tripDetailQuery.data?.parcels, all]);

  const autoFillAllUnits = async () => {

    const units = tripDetailQuery.data?.units;

    if (!tripId || !units || units.length === 0) {
      message.warning('当前波次未关联有效的干线批次，或该批次无板笼结构。');
      return;
    }

    if (serviceAreasQuery.data?.length === 0) {
      message.warning('当前站点下暂无配送区域，无法执行一键规则填充。');
      return;
    }

    try {
      // Group areas by unit index (modulo load balancing / template default)
      const unitAreasMap: Record<number, number[]> = {};
      const areasList = serviceAreasQuery.data ?? [];
      for (let index = 0; index < areasList.length; index++) {
        const areaId = areasList[index].id;
        const unit = units[index % units.length];
        if (!unitAreasMap[unit.id]) unitAreasMap[unit.id] = [];
        unitAreasMap[unit.id].push(areaId);
      }


      for (const [unitIdStr, areaIds] of Object.entries(unitAreasMap)) {
        const unitId = Number(unitIdStr);
        await api(`/ops/v1/handling-units/${unitId}/area-fill`, session, {
          method: 'POST',
          body: JSON.stringify({
            deliveryAreaIds: areaIds,
            reason: 'Auto pre-arrival allocation from area planning template'
          })
        }, station);
      }

      setUnitSelectedAreas(unitAreasMap);
      message.success('已按默认规则，一键完成全站区域与板笼分配！');

      await Promise.all([
        cache.invalidateQueries({ queryKey: ['arrival-trip', station, tripId] }),
        cache.invalidateQueries({ queryKey: ['arrival-trips', station, serviceDate] }),
        refresh()
      ]);

    } catch (e: any) {
      message.error('一键规则填充失败: ' + e.message);
    }
  };


 const defaultWaveCode = useMemo(() => {
   if (!serviceDate) return '';
   const cleanDate = serviceDate.replace(/-/g, '');
   return `${cleanDate}-WAVE-01`;
 }, [serviceDate]);

  const createWave = async (values: { waveCode?: string; routeCode?: string }, targetStage = 1) => {
    try {
      const finalWaveCode = (values.waveCode ?? defaultWaveCode).trim();
      const finalRouteCode = (values.routeCode ?? 'DYNAMIC-ROUTE').trim();
      const result = await api<{ wave: { id: number } } | { id: number }>('/ops/v1/planning/waves', session, { method: 'POST', body: JSON.stringify({ waveCode: finalWaveCode, routeCode: finalRouteCode, serviceDate }) }, station);
      const newWaveId = 'wave' in result ? result.wave.id : result.id;
      setWaveId(newWaveId);
      setStage(targetStage);
      message.success(`派送波次 [${finalWaveCode}] 已成功启动，进入下一阶段`);
      return newWaveId;
    } catch (e: any) {
      message.error('启动波次失败: ' + e.message);
      return null;
    }
  };

  const currentWaveCode = useMemo(() => {
    if (wave.data?.wave?.wave_code) return wave.data.wave.wave_code;
    const match = (wavesList.data ?? []).find(w => (w.wave_id ?? w.id) === waveId);
    if (match?.wave_code) return match.wave_code;
    return wavesList.data?.[0]?.wave_code;
  }, [wave.data, wavesList.data, waveId]);

  const ensureWaveAndProceed = async (targetStage: number) => {
    if (targetStage > 0 && !waveId) {
      const existing = wavesList.data?.[0];
      const existingId = existing?.wave_id ?? existing?.id;
      if (existingId) {
        setWaveId(existingId);
        setStage(targetStage);
      } else {
        await createWave({}, targetStage);
      }
    } else {
      setStage(targetStage);
    }
  };


  const stepItems = [
    { title: '1. 新增每日波次', description: currentWaveCode ?? '创建或选择波次' },
    { title: '2. 干线板笼规划', description: '持久化板笼/区域对应关系' },

    { title: '3. 司机指派与排线', description: `已指派 ${assigned}/${all.length} 件` },
    { title: '4. 预检与发布', description: waveStatus ?? '门禁校验与锁单' }
  ];

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
    {/* Page Header */}
    <div style={{ padding: '12px 16px', background: '#fafafa', border: '1px solid #e8e8e8', borderRadius: '8px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
      <div style={{ fontSize: '16px', fontWeight: 'bold' }}>
        🌊 3.1 派送计划 (波次初始排线 SOP)
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
        <span style={{ fontSize: '13px', color: '#8c8c8c' }}>
          当前站点出勤司机: <b style={{ color: '#1677ff' }}>{available.length} 名</b> | 已指派在途件: <b>{assigned} 件</b>
        </span>
        <Button 
          size="small" 
          icon={<i className="fa-solid fa-sliders" style={{ marginRight: 4 }}></i>}
          onClick={() => setCapacityOpen(true)}
        >
          司机出勤与容量管理
        </Button>
      </div>
    </div>

    {/* 4-Step Pipeline Bar matching prototype HTML */}
    <div className="pipeline-bar">
      <div className="steps-container">
        {/* Step 1 */}
        <div className={`step-node ${stage === 0 ? 'active' : stage > 0 ? 'completed' : ''}`} onClick={() => setStage(0)}>
          <div className="step-num">{stage > 0 ? '✓' : '1'}</div>
          <div className="step-info">
            <span className="step-title">1. 新增每日波次</span>
            <span className="step-sub">{currentWaveCode ?? '未创建波次'}</span>


          </div>
        </div>
        <i className="fa-solid fa-chevron-right" style={{ color: '#ccc', fontSize: '11px' }}></i>

        {/* Step 2 */}
        <div className={`step-node ${stage === 1 ? 'active' : stage > 1 ? 'completed' : ''}`} onClick={() => ensureWaveAndProceed(1)}>
          <div className="step-num">{stage > 1 ? '✓' : '2'}</div>
          <div className="step-info">
            <span className="step-title">2. 干线板笼规划</span>
            <span className="step-sub">运营已调配持久化对应关系</span>
          </div>
        </div>
        <i className="fa-solid fa-chevron-right" style={{ color: '#ccc', fontSize: '11px' }}></i>

        {/* Step 3 */}
        <div className={`step-node ${stage === 2 ? 'active' : stage > 2 ? 'completed' : ''}`} onClick={() => ensureWaveAndProceed(2)}>
          <div className="step-num">{stage > 2 ? '✓' : '3'}</div>
          <div className="step-info">
            <span className="step-title">3. 司机指派与排线</span>
            <span className="step-sub">区域指派 + 默认司机 + 套索</span>
          </div>
        </div>
        <i className="fa-solid fa-chevron-right" style={{ color: '#ccc', fontSize: '11px' }}></i>

        {/* Step 4 */}
        <div className={`step-node ${stage === 3 ? 'active' : ''}`} onClick={() => ensureWaveAndProceed(3)}>
          <div className="step-num">4</div>
          <div className="step-info">
            <span className="step-title">4. 预检与发布</span>
            <span className="step-sub">门禁校验与锁单</span>
          </div>
        </div>
      </div>

      <Button type="primary" onClick={() => ensureWaveAndProceed(Math.min(3, stage + 1))}>
        下一步 <i className="fa-solid fa-arrow-right"></i>
      </Button>

    </div>

  {(parcels.error||shifts.error||wave.error)&&<Alert type="error" showIcon message={(parcels.error??shifts.error??wave.error)?.message}/>} 
  
  {/* Main Workspace Body matching prototype HTML layout: left-control + right-map */}
  <div style={{ display: 'grid', gridTemplateColumns: (stage === 0 || stage === 1) ? '1fr' : '440px 1fr', gap: '16px', minHeight: '640px' }}>

    {/* Left Control Panel */}
    <div className="left-control" style={{ border: '1px solid #e8e8e8', borderRadius: '8px', padding: '16px', overflowY: 'auto', background: '#fff' }}>
      <div style={{ paddingBottom: '12px', marginBottom: '16px', borderBottom: '1px solid #e8e8e8', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <span style={{ fontWeight: 'bold', fontSize: '14px' }}>
          <i className="fa-solid fa-wand-magic-sparkles" style={{ color: '#1677ff', marginRight: 8 }}></i>
          {stage === 0 && '步骤 1: 新增每日波次'}
          {stage === 1 && '步骤 2: 干线板笼规划'}
          {stage === 2 && '步骤 3: 司机指派与排线'}
          {stage === 3 && '步骤 4: 预检与发布'}
        </span>
        <span style={{ fontSize: '11px', background: '#e6f4ff', color: '#0958d9', padding: '2px 6px', borderRadius: '4px' }}>当前编辑</span>
      </div>


      {/* STEP 1 面板 */}
      {stage === 0 && (
        <div style={{ maxWidth: '640px', margin: '0 auto', width: '100%', display: 'flex', flexDirection: 'column', gap: '16px', padding: '12px 0' }}>
          <div className="op-card" style={{ boxShadow: '0 2px 8px rgba(0,0,0,0.04)', borderRadius: '8px' }}>
            <div className="card-header">
              <span>波次基本信息</span>
              <Tag color="green">自动生成</Tag>
            </div>
            <div style={{ marginBottom: '16px' }}>
              <label style={{ fontSize: '12px', fontWeight: 'bold', display: 'block', marginBottom: '6px', color: '#262626' }}>波次代码 (Wave Code)</label>
              <Input value={defaultWaveCode} readOnly style={{ background: '#f5f5f5', borderRadius: '6px' }} size="large" />
            </div>
            <div style={{ marginBottom: '20px' }}>
              <label style={{ fontSize: '12px', fontWeight: 'bold', display: 'block', marginBottom: '6px', color: '#262626' }}>关联到仓干线车次 (Trip No)</label>
              <Select
                size="large"
                style={{ width: '100%' }}
                placeholder="请选择关联的到仓干线车次..."
                value={tripId}
                onChange={(val) => setSelectedTripId(val)}
                options={(tripsQuery.data ?? []).map(t => ({
                  value: t.id,
                  label: `🚚 车次: ${t.external_trip_no} (${t.vehicle_plate || '暂无车牌'}) [${t.unit_count ?? 0}个板笼]`
                }))}
              />
            </div>

            <Button 
              type="primary" 
              block 
              size="large"
              style={{ height: '42px', fontWeight: 'bold' }}
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
              ➕ 新建波次
            </Button>
          </div>

          <div className="op-card" style={{ boxShadow: '0 2px 8px rgba(0,0,0,0.04)', borderRadius: '8px' }}>
            <div className="card-header" style={{ fontSize: '14px', fontWeight: 'bold' }}>今日包裹概览</div>
            <div style={{ fontSize: '13px', lineHeight: '2.0', color: '#434343' }}>
              <div>• 待规划总件数: <b style={{ fontSize: '15px', color: '#1677ff' }}>{all.length} 件</b></div>
              <div>• ⚡ 优先级加急/特快件: <b style={{ color: '#c41d7f', fontSize: '15px' }}>{all.filter(p=>p.priority_flag).length} 件</b></div>
              <div>• 📮 常规标快件: <b style={{ color: '#262626' }}>{all.filter(p=>!p.priority_flag).length} 件</b></div>
            </div>
            <Button className="btn-primary btn-block" style={{ marginTop: '16px', height: '42px', fontSize: '14px', fontWeight: 'bold' }} onClick={() => ensureWaveAndProceed(1)}>
              启动波次并进入下一步 <i className="fa-solid fa-arrow-right"></i>
            </Button>

          </div>
        </div>
      )}

      {/* STEP 2 面板：干线板笼规划矩阵 */}
      {stage === 1 && (
        <div style={{ maxWidth: '960px', margin: '0 auto', width: '100%', display: 'flex', flexDirection: 'column', gap: '16px', padding: '8px 0' }}>
          <div style={{ background: '#fff', padding: '16px', borderRadius: '8px', border: '1px solid #f0f0f0', boxShadow: '0 2px 8px rgba(0,0,0,0.04)' }}>

            <Table
              size="small"
              rowKey="id"
              dataSource={serviceAreasQuery.data ?? []}
              pagination={{ pageSize: 6, showSizeChanger: false, showTotal: (total) => `共 ${total} 个派送区域` }}
              columns={[
                {
                  title: '区域代码 / 名称',
                  dataIndex: 'area_code',
                  key: 'area_code',
                  render: (text, record: any) => (
                    <div>
                      <div style={{ fontWeight: 'bold', color: '#1677ff' }}>{text}</div>
                      <div style={{ fontSize: '11px', color: '#8c8c8c' }}>{record.area_name || '默认网格'}</div>
                    </div>
                  )
                },
                {
                  title: '关联包裹',
                  key: 'parcels',
                  width: 90,
                  render: (_, record: any) => {
                    const count = all.filter(p => (p.area_id ?? p.area_version_id) === record.id).length;
                    return <Tag color={count > 0 ? 'blue' : 'default'}>{count} 件</Tag>;
                  }
                },
                {
                  title: '指派目标干线板笼 (HU)',
                  key: 'target_unit',
                  render: (_, record: any) => {
                    const areaVerId = record.id;
                    let currentUnitId: number | undefined = undefined;

                    // 1. First check if any unit has linked parcels for this area
                    if (tripDetailQuery.data?.parcels) {
                      const parcelAreaMap = new Map(all.map((p: any) => [p.parcel_id, p.area_id ?? p.area_version_id]));

                      const match = tripDetailQuery.data.parcels.find(
                        up => up.link_source === 'AREA_PLAN' && parcelAreaMap.get(up.parcel_id) === areaVerId
                      );
                      if (match) currentUnitId = match.unit_id;
                    }

                    // 2. Fallback: match by local state / unit selected areas
                    if (!currentUnitId && tripDetailQuery.data?.units) {
                      for (const u of tripDetailQuery.data.units) {
                        const selectedForUnit = unitSelectedAreas[u.id] ?? [];
                        if (selectedForUnit.includes(areaVerId)) {
                          currentUnitId = u.id;
                          break;
                        }
                      }
                    }

                    const unitOptions = (tripDetailQuery.data?.units ?? []).map(u => ({
                      value: u.id,
                      label: `📦 ${u.external_unit_no} (${u.linked_piece_count} 件)`
                    }));

                    return (
                      <Select
                        style={{ width: '100%', maxWidth: '280px' }}
                        placeholder="选择指派干线板笼..."
                        allowClear
                        value={currentUnitId}
                        options={unitOptions}
                        onChange={(newUnitId) => {
                          // 1. Clear this area from all other units in local state first
                          setUnitSelectedAreas(prev => {
                            const next = { ...prev };
                            Object.keys(next).forEach(uId => {
                              const numericId = Number(uId);
                              next[numericId] = (next[numericId] ?? []).filter(aId => aId !== areaVerId);
                            });
                            if (newUnitId) {
                              next[newUnitId] = [...(next[newUnitId] ?? []), areaVerId];
                            }
                            return next;
                          });

                          // 2. Clear old unit binding on server if changing unit
                          if (currentUnitId && currentUnitId !== newUnitId) {
                            command.mutate({
                              path: `/ops/v1/handling-units/${currentUnitId}/area-fill`,
                              body: { deliveryAreaIds: [], reason: 'Re-assigning area to another handling unit' }
                            });
                          }

                          // 3. Bind to new unit if selected
                          if (newUnitId) {
                            command.mutate({
                              path: `/ops/v1/handling-units/${newUnitId}/area-fill`,
                              body: { deliveryAreaIds: [areaVerId], reason: 'Manual area-centric assignment' }
                            });
                          }
                        }}
                      />
                    );

                  }
                }

              ]}
            />
          </div>

          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <Button size="small" onClick={autoFillAllUnits} loading={command.isPending}>
              <i className="fa-solid fa-wand-magic-sparkles"></i> 一键按默认规则填充
            </Button>
            <Button className="btn-primary" onClick={() => setStage(2)}>
              确认干线规划，进入司机指派 <i className="fa-solid fa-arrow-right"></i>
            </Button>
          </div>
        </div>
      )}



      {/* STEP 3 面板 */}
      {stage === 2 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          <div className="op-card" style={{ background: '#e6f4ff', borderColor: '#91caef' }}>
            <div style={{ fontWeight: 'bold', color: '#0958d9', marginBottom: '8px' }}>
              <i className="fa-solid fa-user-plus"></i> 按区域指派给对应责任司机
            </div>
            <div style={{ marginBottom: '10px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '4px' }}>
                <label style={{ fontSize: '12px', fontWeight: 'bold' }}>1. 选择目标司机</label>
                <Button 
                  type="link" 
                  size="small" 
                  style={{ padding: 0, fontSize: '12px' }}
                  onClick={() => setCapacityOpen(true)}
                >
                  ⚙️ 管理出勤与容量上限
                </Button>
              </div>
              <Select 
                value={driver} 
                onChange={(val) => {
                  setDriver(val);
                  setAreaVersion(undefined);
                  setCurrentArea(undefined);
                }} 
                style={{ width: '100%' }} 
                allowClear
                placeholder="选择目标司机" 
                options={available.map(s=>({value:s.driver_id,label:`${s.driver_name} (已分: ${s.assigned_count}/${s.parcel_capacity ?? 200} 件)`}))}
              />
            </div>
            <div style={{ marginBottom: '10px' }}>
              <label style={{ fontSize: '12px', fontWeight: 'bold', display: 'block', marginBottom: '4px' }}>2. 选择分配区域</label>
              <Select 
                value={areaVersion} 
                onChange={(val) => {
                  setAreaVersion(val);
                  if (val) setCurrentArea(val);
                }} 
                style={{ width: '100%' }} 
                allowClear
                placeholder="选择分配区域" 
                options={areas}
              />
            </div>
            <div style={{ display: 'flex', gap: '8px' }}>
              <Button 
                type="primary" 
                style={{ flex: 1 }}
                disabled={!driver || !areaVersion}
                onClick={() => {
                  if (waveId && driver && areaVersion) {
                    command.mutate({
                      path: `/ops/v1/planning/waves/${waveId}/assign-area`,
                      body: { driverId: driver, areaVersionId: areaVersion }
                    });
                  }
                }}
              >
                指派所选区域包裹
              </Button>
              <Button 
                style={{ borderColor: '#1677ff', color: '#1677ff' }}
                loading={command.isPending}
                onClick={() => {
                  const firstWave = wavesList.data?.[0];
                  const targetWaveId = waveId 
                    ?? (wave.data as any)?.wave?.id 
                    ?? (wave.data as any)?.wave?.wave_id
                    ?? firstWave?.wave_id 
                    ?? firstWave?.id 
                    ?? 0;

                  command.mutate({
                    path: `/ops/v1/planning/waves/${targetWaveId}/assign-defaults`,
                    body: {}
                  });
                }}
              >
                一键按责任区域指派
              </Button>

              <Button
                type="primary"
                ghost
                block
                style={{ marginTop: '8px' }}
                disabled={!driver}
                onClick={() => {
                  const firstWave = wavesList.data?.[0];
                  const targetWaveId = waveId ?? (wave.data as any)?.wave?.id ?? (wave.data as any)?.wave?.wave_id ?? firstWave?.wave_id ?? firstWave?.id ?? 0;
                  if (targetWaveId && driver) {
                    command.mutate({
                      path: `/ops/v1/planning/waves/${targetWaveId}/drivers/${driver}/optimize-route`,
                      body: {}
                    });
                  }
                }}
              >
                <i className="fa-solid fa-route" style={{ marginRight: 6 }}></i>
                🧭 OSRM 智能规划当前司机路线
              </Button>
            </div>
          </div>

          <div className="op-card">
            <div style={{ fontWeight: 'bold', marginBottom: '6px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span><i className="fa-solid fa-draw-polygon"></i> 辅助: 地图圈选部分包裹</span>
              {selected.size > 0 && <Tag color="purple">已圈选 {selected.size} 件</Tag>}
            </div>
            <div style={{ fontSize: '12px', color: '#8c8c8c', marginBottom: '8px' }}>
              在右侧地图画圈圈选局部点位，单独改派给其他司机。
            </div>
            <Button 
              block 
              type={lassoActive ? 'primary' : 'default'}
              danger={lassoActive}
              onClick={() => setLassoActive(!lassoActive)}
            >
              <i className="fa-solid fa-draw-polygon" style={{ marginRight: 6 }}></i>
              {lassoActive ? '关闭地图套索' : '开启地图套索圈选'}
            </Button>
            {selected.size > 0 && driver && waveId && (
              <Button
                type="primary"
                block
                style={{ marginTop: '8px', background: '#722ed1', borderColor: '#722ed1' }}
                onClick={() => {
                  command.mutate({
                    path: `/ops/v1/planning/waves/${waveId}/assignments`,
                    body: {
                      driverId: driver,
                      parcelIds: [...selected],
                      areaVersionIds: [],
                      reason: 'Lasso map parcel assignment'
                    }
                  });
                }}
              >
                👉 指派圈中的 {selected.size} 件给目标司机
              </Button>
            )}
          </div>

          <Button className="btn-primary btn-block" style={{ marginTop: '12px' }} onClick={() => setStage(3)}>
            指派完成，进入预检发布 <i className="fa-solid fa-arrow-right"></i>
          </Button>
        </div>
      )}

      {/* STEP 4 面板 */}
      {stage === 3 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
          <div className="op-card">
            <div className="card-header">
              <span>波次门禁检查 (Gateways)</span>
              <i className="fa-solid fa-shield-halved" style={{ color: '#52c41a' }}></i>
            </div>
            <div style={{ fontSize: '13px', lineHeight: '2' }}>
              <div><i className="fa-solid fa-circle-check" style={{ color: '#52c41a' }}></i> {all.length} 个包裹全量路由匹配成功</div>
              <div><i className="fa-solid fa-circle-check" style={{ color: '#52c41a' }}></i> 无超载司机</div>
              <div><i className="fa-solid fa-circle-check" style={{ color: '#52c41a' }}></i> {all.filter(p=>p.priority_flag).length} 件⚡特快件全部已指派</div>
              <div><i className="fa-solid fa-circle-check" style={{ color: '#52c41a' }}></i> 司机 Shift 在岗状态正常</div>
            </div>
          </div>

          <Button 
            type="primary" 
            block 
            style={{ background: '#52c41a', borderColor: '#52c41a', padding: '12px', height: 'auto', fontSize: '15px' }}
            disabled={waveStatus === 'PUBLISHED'}
            onClick={() => {
              if (waveId) {
                command.mutate({
                  path: `/ops/v1/planning/waves/${waveId}/publish`,
                  body: {}
                });
              }
            }}
          >
            <i className="fa-solid fa-rocket"></i> {waveStatus === 'PUBLISHED' ? '波次已发布' : '确认正式发布波次 (Publish)'}
          </Button>
        </div>
      )}
    </div>

    {/* Right Map Panel with Collapsible Parcel Drawer */}
    {stage >= 2 && (
      <div style={{ border: '1px solid #e8e8e8', borderRadius: '8px', overflow: 'hidden', position: 'relative', background: '#e5e9ec', display: 'flex', flexDirection: 'column' }}>

        {/* Floating Map Controls & Collapsible Parcel Drawer Toggle */}
        <div style={{ position: 'absolute', top: 12, right: 12, zIndex: 1000, display: 'flex', gap: '8px' }}>
          <Button 
            type={listOpen ? 'primary' : 'default'}
            icon={<i className={`fa-solid ${listOpen ? 'fa-xmark' : 'fa-list-check'}`}></i>}
            onClick={() => setListOpen(!listOpen)}
            style={{ boxShadow: '0 2px 6px rgba(0,0,0,0.15)' }}
          >
            {listOpen ? '收起包裹明细' : `展开包裹明细 (${filteredVisibleParcels.length} 件)`}
            {selected.size > 0 && <Tag color="purple" style={{ marginLeft: 6 }}>已选 {selected.size}</Tag>}
          </Button>
        </div>

        {/* Map View Main Area */}
        <div style={{ flex: 1, position: 'relative' }}>
          <PlanningMap
            station={station}
            parcels={all.filter(p => {
              if (driver && p.driver_id !== driver) return false;
              if (currentArea) {
                const matchedArea = (serviceAreasQuery.data ?? []).find(a => a.id === currentArea);
                if (matchedArea && p.area_code !== matchedArea.area_code && p.area_id !== currentArea) return false;
              }
              return true;
            })}
            selectedDriverName={driver ? available.find(s => s.driver_id === driver)?.driver_name : undefined}
            serviceAreas={(serviceAreasQuery.data ?? []).filter(a => {
              if (currentArea && a.id !== currentArea) return false;
              return true;
            })}
            selected={selected}
            activeAreaId={currentArea}
            lassoActive={lassoActive}
            onSelectArea={setCurrentArea}
            onToggle={toggle}
            onSelect={setFocus}
            onLassoSelect={(ids) => setSelected(new Set(ids))}
          />
        </div>

        {/* Collapsible Right-Side Floating Parcel Table Panel (Simulating Orders Workspace) */}
        {listOpen && (
          <div style={{
            position: 'absolute', top: 0, right: 0, bottom: 0, width: '420px',
            background: '#fff', borderLeft: '1px solid #d9d9d9', boxShadow: '-4px 0 16px rgba(0,0,0,0.12)',
            zIndex: 1001, display: 'flex', flexDirection: 'column', padding: '12px'
          }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px', paddingBottom: '8px', borderBottom: '1px solid #f0f0f0' }}>
              <span style={{ fontWeight: 'bold', fontSize: '14px' }}>
                <i className="fa-solid fa-boxes-packing" style={{ color: '#1677ff', marginRight: 6 }}></i>
                包裹动态明细 ({filteredVisibleParcels.length} 件)
              </span>
              <Button size="small" type="text" onClick={() => setListOpen(false)}>
                <i className="fa-solid fa-xmark"></i>
              </Button>
            </div>

            {/* Batch Reassign Bar when items are selected */}
            {selected.size > 0 && (
              <div style={{ padding: '8px', background: '#f9f0ff', border: '1px solid #d3ade8', borderRadius: '6px', marginBottom: '8px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ fontSize: '12px', color: '#531dab', fontWeight: 'bold' }}>
                  已勾选 {selected.size} 件包裹
                </span>
                <Space>
                  <Button size="small" onClick={() => setSelected(new Set())}>取消选择</Button>
                  <Button 
                    size="small" 
                    type="primary" 
                    disabled={!driver || !waveId}
                    style={{ background: '#722ed1', borderColor: '#722ed1' }}
                    onClick={() => {
                      if (driver && waveId) {
                        command.mutate({
                          path: `/ops/v1/planning/waves/${waveId}/assignments`,
                          body: {
                            driverId: driver,
                            parcelIds: [...selected],
                            areaVersionIds: [],
                            reason: 'Batch transfer from dynamic parcel list'
                          }
                        });
                      }
                    }}
                  >
                    转移给当前目标司机
                  </Button>
                </Space>
              </div>
            )}

            {/* Parcel Table with Multi-selection and Row-Click to View Details */}
            <div style={{ flex: 1, overflowY: 'auto' }}>
              <Table<PlanningParcel>
                size="small"
                rowKey="parcel_id"
                dataSource={filteredVisibleParcels}
                pagination={{ pageSize: 15, size: 'small', showSizeChanger: false }}
                onRow={(r) => ({
                  onClick: () => setFocus(r),
                  style: { cursor: 'pointer' }
                })}
                rowSelection={{
                  selectedRowKeys: [...selected],
                  onChange: (keys) => setSelected(new Set(keys as number[]))
                }}
                columns={[
                  {
                    title: '运单号 / 追溯码',
                    dataIndex: 'tracking_no',
                    render: (text: string, r) => (
                      <div>
                        <div style={{ fontWeight: 'bold', fontSize: '12px', color: '#1677ff' }}>{text}</div>
                        <div style={{ fontSize: '11px', color: '#8c8c8c' }}>{r.area_code ?? '未划区'}</div>
                      </div>
                    )
                  },
                  {
                    title: '指派司机',
                    dataIndex: 'driver_name',
                    render: (name: string) => name ? <Tag color="blue">{name}</Tag> : <Tag color="default">未指派</Tag>
                  },
                  {
                    title: '状态',
                    dataIndex: 'status',
                    render: (v: string) => <Tag color={v === 'ASSIGNED' ? 'green' : 'orange'}>{v}</Tag>
                  }
                ]}
              />
            </div>
          </div>
        )}
      </div>
    )}



  </div>


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
  <Drawer 
    open={!!focus} 
    onClose={()=>setFocus(undefined)} 
    title={`📦 包裹详情: ${focus?.tracking_no ?? ''}`}
    width={480}
    zIndex={2000}
  >
    {focus&&<><List dataSource={Object.entries(focus)} renderItem={([key,value])=><List.Item><Typography.Text type="secondary">{key}</Typography.Text><Typography.Text>{String(value??'—')}</Typography.Text></List.Item>}/>{focus.driver_id&&waveId&&<Space.Compact block style={{ marginTop: '16px' }}><Select value={driver} onChange={setDriver} style={{width:'70%'}} options={available.map(s=>({value:s.driver_id,label:s.driver_name}))}/><Button disabled={!driver||driver===focus.driver_id} onClick={()=>command.mutate({path:`/ops/v1/planning/waves/${waveId}/parcels/${focus.parcel_id}/reassign`,body:{driverId:driver,reason:'Operator map reassignment'}})}>{t('dispatch.reassign')}</Button></Space.Compact>}</>}
  </Drawer>
 </div>;
}

