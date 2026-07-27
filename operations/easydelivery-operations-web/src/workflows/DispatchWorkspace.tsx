import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, App, Button, Card, Drawer, Form, Input, InputNumber, List, Progress, Select, Space, Steps, Table, Tabs, Tag, Tooltip, Typography } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, type Session } from '../api/client';
import { PlanningMap, type PlanningParcel } from './PlanningMap';
import { useTranslation } from 'react-i18next';
import { MobileActionBar } from './MobileActionBar';

type Shift={driver_id:number;driver_name:string;driver_code:string;availability_status:string;parcel_capacity?:number;assigned_count:number};
type WaveResult={wave:{id:number;wave_code:string;arrival_trip_id?:number;status:string};drivers:Array<{task_id:number;driver_id:number;driver_name:string;parcel_count:number;parcel_capacity:number;remaining_capacity:number}>};

 export function DispatchWorkspace({session,station,initialDate,initialFilter}:{session:Session;station:number|string;initialDate?:string;initialFilter?:string}){

 const { message } = App.useApp();
 const {t}=useTranslation();const cache=useQueryClient();const serviceDate=initialDate!;const [stage,setStage]=useState(0);const [selected,setSelected]=useState<Set<number>>(new Set());const [focus,setFocus]=useState<PlanningParcel>();const [driver,setDriver]=useState<number>();const [areaVersion,setAreaVersion]=useState<number>();const [waveId,setWaveId]=useState<number>();const [capacityOpen,setCapacityOpen]=useState(false);const [listOpen,setListOpen]=useState(false);const [driverDrawerOpen,setDriverDrawerOpen]=useState(false);
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

  // Load the existing wave context for today without skipping the SOP entry step.
  // Operators should always land on step 1 and explicitly advance when ready.
  useEffect(() => {
   if (wavesList.data && wavesList.data.length > 0 && !waveId) {
     const targetWave = wavesList.data[0];
     const targetId = targetWave.wave_id ?? targetWave.id;
     if (targetId) {
       setWaveId(targetId);
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
 const [tripSelectionTouched, setTripSelectionTouched] = useState(false);

  const matchedTrip = useMemo(() => {
    if (tripSelectionTouched) {
      if (!selectedTripId) return undefined;
      return tripsQuery.data?.find(t => t.id === selectedTripId);
    }
    if (wave.data?.wave.arrival_trip_id) {
      return tripsQuery.data?.find(t => t.id === wave.data!.wave.arrival_trip_id);
    }
    return undefined;
  }, [tripsQuery.data, wave.data?.wave.arrival_trip_id, selectedTripId, tripSelectionTouched]);

  const tripId = matchedTrip?.id;


  const tripDetailQuery = useQuery({
    queryKey: ['arrival-trip', station, tripId],
    enabled: !!tripId,
    queryFn: () => api<TripDetail>(`/ops/v1/arrival-trips/${tripId}`, session, {}, station)
  });
  const refresh=async()=>Promise.all([cache.invalidateQueries({queryKey:['planning-parcels',station,serviceDate]}),cache.invalidateQueries({queryKey:['planning-shifts',station,serviceDate]}),cache.invalidateQueries({queryKey:['planning-wave',station,waveId]}),cache.invalidateQueries({queryKey:['dispatch-waves-list',station,serviceDate]})]);
  const command=useMutation({
    mutationFn:({path,body,method='POST'}:{path:string;body:unknown;method?:'POST'|'PATCH'|'PUT'})=>api<any>(path,session,{method,body:JSON.stringify(body)},station),
    onSuccess:async(res, variables)=>{
      let msg = t('dispatch.commandSuccess');
      if (res && typeof res.changedCount === 'number') {
        if (res.changedCount === 0) {
          if (variables.path.includes('assign-defaults')) {
            message.warning('⚡ Auto-assignment completed but no parcels matched. Check driver area preferences, unassigned parcels, and capacity.', 8);
          } else {
            message.warning('👉 Assignment completed with zero parcels. Check that the area has eligible, unassigned parcels.', 8);
          }
        } else {
          msg += ` (${res.changedCount} parcels processed; driver total ${res.assignedCount}/${res.capacity})`;
          message.success(msg, 5);
        }
      } else {
        message.success(msg, 4);
      }
      setSelected(new Set());
      await refresh();
      if (tripId) await tripDetailQuery.refetch();
    },
    onError:(e:Error)=>message.error(`Operation failed: ${e.message}`, 6)
  });
 const saveShift=useMutation({mutationFn:(value:{driverId:number;availabilityStatus:string;parcelCapacity:number})=>api('/ops/v1/planning/shifts',session,{method:'PUT',body:JSON.stringify({...value,serviceDate,note:'Operations planning'})},station),onSuccess:async()=>{message.success(t('dispatch.shiftSaved'));await refresh();}});
 const toggle=useCallback((id:number)=>setSelected(current=>{const next=new Set(current);if(next.has(id))next.delete(id);else next.add(id);return next;}),[]);
 const all=useMemo(()=>parcels.data??[],[parcels.data]);const visible=useMemo(()=>initialFilter==='unmatched-area'?all.filter(p=>p.exception_code==='UNMATCHED_AREA'):initialFilter==='unassigned'?all.filter(p=>!p.driver_id&&!p.exception_code):all,[all,initialFilter]);const assigned=all.filter(p=>p.driver_id).length;const exceptions=all.filter(p=>p.exception_code).length;const available=(shifts.data??[]).filter(s=>s.availability_status==='AVAILABLE');const capacity=available.reduce((sum,s)=>sum+(s.parcel_capacity??0),0);const waveStatus=wave.data?.wave.status;


  const areas = useMemo(() => {
    const fromQuery = (serviceAreasQuery.data ?? []).map((a: any) => ({
      value: a.id,
      label: `${a.area_code} (${a.area_name || 'area'})`
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
      message.warning('The current wave has no valid arrival trip or handling-unit structure.');
      return;
    }

    if (serviceAreasQuery.data?.length === 0) {
      message.warning('This station has no delivery areas for default assignment.');
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
    message.success('Default area and handling-unit assignment completed.');

      await Promise.all([
        cache.invalidateQueries({ queryKey: ['arrival-trip', station, tripId] }),
        cache.invalidateQueries({ queryKey: ['arrival-trips', station, serviceDate] }),
        refresh()
      ]);

    } catch (e: any) {
    message.error('Default assignment failed: ' + e.message);
    }
  };


 const defaultWaveCode = useMemo(() => {
   if (!serviceDate) return '';
   const cleanDate = serviceDate.replace(/-/g, '');
   return `${cleanDate}-WAVE-01`;
 }, [serviceDate]);

  const createWave = async (values: { waveCode?: string; routeCode?: string; arrivalBatchNo?: string }, targetStage = 1) => {
    try {
      const finalWaveCode = (values.waveCode ?? defaultWaveCode).trim();
      const finalRouteCode = (values.routeCode ?? 'DYNAMIC-ROUTE').trim();
      const selectedTrip = tripsQuery.data?.find(t => t.id === selectedTripId);
      const arrivalBatchNo = values.arrivalBatchNo ?? selectedTrip?.external_trip_no;
      const result = await api<{ wave: { id: number } } | { id: number }>('/ops/v1/planning/waves', session, { method: 'POST', body: JSON.stringify({ waveCode: finalWaveCode, routeCode: finalRouteCode, serviceDate, arrivalBatchNo }) }, station);
      const newWaveId = 'wave' in result ? result.wave.id : result.id;
      setWaveId(newWaveId);
      setStage(targetStage);
      message.success(`Dispatch wave [${finalWaveCode}] started. Continue to the next stage.`);
      return newWaveId;
    } catch (e: any) {
      message.error('Failed to start dispatch wave: ' + e.message);
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
    { title: '1. Create daily wave', description: currentWaveCode ?? 'Create or select a wave' },
    { title: '2. Plan handling units', description: 'Persist unit-to-area mappings' },

    { title: '3. Assign drivers and routes', description: `${assigned}/${all.length} assigned` },
    { title: '4. Preflight and publish', description: waveStatus ?? 'Gate checks and lock' }
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
     title: 'Driver ID',
     dataIndex: 'driver_id',
     key: 'driver_id',
     width: '15%',
     render: (id: number, r: Shift) => <Typography.Text type="secondary">{r.driver_code ?? id}</Typography.Text>
   },
   {
     title: 'Driver name',
     dataIndex: 'driver_name',
     key: 'driver_name',
     width: '30%',
     render: (name: string) => <strong style={{ color: '#101828' }}>{name}</strong>
   },
   {
     title: 'Availability',
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
     title: 'Parcel capacity',
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
      title: 'Driver ID',
      dataIndex: 'driver_id',
      key: 'driver_id',
      width: '15%',
      render: (id: number, r: Shift) => <Typography.Text type="secondary">{r.driver_code ?? id}</Typography.Text>
    },
    {
      title: 'Driver name',
      dataIndex: 'driver_name',
      key: 'driver_name',
      width: '25%',
      render: (name: string) => <strong style={{ color: '#1d2939' }}>{name}</strong>
    },
    {
      title: 'Availability',
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
      title: 'Capacity load',
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
                Assigned {assignedCount} / capacity {cap > 0 ? cap : '—'}
              </span>
              <span style={{ color: isOverloaded ? '#d92d20' : '#475467' }}>
                {isOverloaded ? `Over capacity by ${assignedCount - cap}` : `${cap - assignedCount} remaining`}
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
        🌊 3.1 Dispatch planning (wave SOP)
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
        <span style={{ fontSize: '13px', color: '#8c8c8c' }}>
          Available drivers: <b style={{ color: '#1677ff' }}>{available.length}</b> | Assigned parcels: <b>{assigned}</b>
        </span>
        <Button 
          size="small" 
          icon={<i className="fa-solid fa-sliders" style={{ marginRight: 4 }}></i>}
          onClick={() => setCapacityOpen(true)}
        >
          Driver availability and capacity
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
            <span className="step-title">1. Create daily wave</span>
            <span className="step-sub">{currentWaveCode ?? 'No wave created'}</span>


          </div>
        </div>
        <i className="fa-solid fa-chevron-right" style={{ color: '#ccc', fontSize: '11px' }}></i>

        {/* Step 2 */}
        <div className={`step-node ${stage === 1 ? 'active' : stage > 1 ? 'completed' : ''}`} onClick={() => ensureWaveAndProceed(1)}>
          <div className="step-num">{stage > 1 ? '✓' : '2'}</div>
          <div className="step-info">
            <span className="step-title">2. Plan handling units</span>
            <span className="step-sub">Persist unit-to-area mapping</span>
          </div>
        </div>
        <i className="fa-solid fa-chevron-right" style={{ color: '#ccc', fontSize: '11px' }}></i>

        {/* Step 3 */}
        <div className={`step-node ${stage === 2 ? 'active' : stage > 2 ? 'completed' : ''}`} onClick={() => ensureWaveAndProceed(2)}>
          <div className="step-num">{stage > 2 ? '✓' : '3'}</div>
          <div className="step-info">
            <span className="step-title">3. Assign drivers and routes</span>
            <span className="step-sub">Area assignment and route tools</span>
          </div>
        </div>
        <i className="fa-solid fa-chevron-right" style={{ color: '#ccc', fontSize: '11px' }}></i>

        {/* Step 4 */}
        <div className={`step-node ${stage === 3 ? 'active' : ''}`} onClick={() => ensureWaveAndProceed(3)}>
          <div className="step-num">4</div>
          <div className="step-info">
            <span className="step-title">4. Preflight and publish</span>
            <span className="step-sub">Gate checks and lock</span>
          </div>
        </div>
      </div>

      <Button type="primary" onClick={() => ensureWaveAndProceed(Math.min(3, stage + 1))}>
        Next <i className="fa-solid fa-arrow-right"></i>
      </Button>

    </div>

  {(parcels.error||shifts.error||wave.error)&&<Alert type="error" showIcon message={(parcels.error??shifts.error??wave.error)?.message}/>} 
  
  {/* Main Workspace Body matching prototype HTML layout: left-control + right-map */}
  <div className="dispatch-workspace-body" style={{ display: 'grid', gridTemplateColumns: (stage === 0 || stage === 1) ? '1fr' : '440px 1fr', gap: '16px', minHeight: '640px' }}>

    {/* Left Control Panel */}
    <div className={`left-control ${stage >= 2 ? 'dispatch-driver-panel' : ''}`} style={{ border: '1px solid #e8e8e8', borderRadius: '8px', padding: '16px', overflowY: 'auto', background: '#fff' }}>
      <div style={{ paddingBottom: '12px', marginBottom: '16px', borderBottom: '1px solid #e8e8e8', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <span style={{ fontWeight: 'bold', fontSize: '14px' }}>
          <i className="fa-solid fa-wand-magic-sparkles" style={{ color: '#1677ff', marginRight: 8 }}></i>
          {stage === 0 && 'Step 1: Create daily wave'}
          {stage === 1 && 'Step 2: Plan handling units'}
          {stage === 2 && 'Step 3: Assign drivers and routes'}
          {stage === 3 && 'Step 4: Preflight and publish'}
        </span>
        <span style={{ fontSize: '11px', background: '#e6f4ff', color: '#0958d9', padding: '2px 6px', borderRadius: '4px' }}>Editing</span>
      </div>


      {/* STEP 1 panel */}
      {stage === 0 && (
        <div style={{ maxWidth: '640px', margin: '0 auto', width: '100%', display: 'flex', flexDirection: 'column', gap: '16px', padding: '12px 0' }}>
          <div className="op-card" style={{ boxShadow: '0 2px 8px rgba(0,0,0,0.04)', borderRadius: '8px' }}>
            <div className="card-header">
              <span>Wave information</span>
              <Tag color="green">Auto-generated</Tag>
            </div>
            <div style={{ marginBottom: '16px' }}>
              <label style={{ fontSize: '12px', fontWeight: 'bold', display: 'block', marginBottom: '6px', color: '#262626' }}>Wave code</label>
              <Input value={defaultWaveCode} readOnly style={{ background: '#f5f5f5', borderRadius: '6px' }} size="large" />
            </div>
            <div style={{ marginBottom: '20px' }}>
              <label style={{ fontSize: '12px', fontWeight: 'bold', display: 'block', marginBottom: '6px', color: '#262626' }}>Arrival trip (optional)</label>
              <Select
                size="large"
                style={{ width: '100%' }}
                placeholder="Can be linked later from inbound arrivals"
                value={tripId}
                allowClear
                onChange={(val) => { setTripSelectionTouched(true); setSelectedTripId(val); }}
                options={(tripsQuery.data ?? []).map(t => ({
                  value: t.id,
                  label: `🚚 ${t.external_trip_no} (${t.vehicle_plate || 'no plate'}) [${t.unit_count ?? 0} units]`
                }))}
              />
              {waveId && tripSelectionTouched && (
                <Button
                  size="small"
                  type="link"
                  style={{ paddingLeft: 0, marginTop: 6 }}
                  loading={command.isPending}
                  onClick={() => {
                    command.mutate({
                      path: `/ops/v1/planning/waves/${waveId}/arrival-trip`,
                      method: 'PATCH',
                      body: {
                        arrivalBatchNo: matchedTrip?.external_trip_no ?? null,
                        reason: 'Operator linked arrival trip after wave creation'
                      }
                    });
                    setTripSelectionTouched(false);
                  }}
                >
                  Save trip link
                </Button>
              )}
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
                const code = prompt("Enter a unique wave code for this station:", defaultCode);
                if (code && code.trim()) {
                  const selectedTrip = tripsQuery.data?.find(t => t.id === selectedTripId);
                  createWave({ waveCode: code.trim(), arrivalBatchNo: selectedTrip?.external_trip_no });
                }
              }}
            >
              ➕ Create wave
            </Button>
          </div>

          <div className="op-card" style={{ boxShadow: '0 2px 8px rgba(0,0,0,0.04)', borderRadius: '8px' }}>
            <div className="card-header" style={{ fontSize: '14px', fontWeight: 'bold' }}>Today’s parcel overview</div>
            <div style={{ fontSize: '13px', lineHeight: '2.0', color: '#434343' }}>
              <div>• Ready to plan: <b style={{ fontSize: '15px', color: '#1677ff' }}>{all.length}</b></div>
              <div>• ⚡ Priority parcels: <b style={{ color: '#c41d7f', fontSize: '15px' }}>{all.filter(p=>p.priority_flag).length}</b></div>
              <div>• 📮 Standard parcels: <b style={{ color: '#262626' }}>{all.filter(p=>!p.priority_flag).length}</b></div>
            </div>
            <Button className="btn-primary btn-block" style={{ marginTop: '16px', height: '42px', fontSize: '14px', fontWeight: 'bold' }} onClick={() => ensureWaveAndProceed(1)}>
              Start wave and continue <i className="fa-solid fa-arrow-right"></i>
            </Button>

          </div>
        </div>
      )}

      {/* STEP 2 panel: handling-unit planning matrix */}
      {stage === 1 && (
        <div style={{ maxWidth: '960px', margin: '0 auto', width: '100%', display: 'flex', flexDirection: 'column', gap: '16px', padding: '8px 0' }}>
          <div style={{ background: '#fff', padding: '16px', borderRadius: '8px', border: '1px solid #f0f0f0', boxShadow: '0 2px 8px rgba(0,0,0,0.04)' }}>

            <Table
              size="small"
              rowKey="id"
              dataSource={serviceAreasQuery.data ?? []}
              pagination={{ pageSize: 6, showSizeChanger: false, showTotal: (total) => `${total} delivery areas` }}
              columns={[
                {
                  title: 'Area code / name',
                  dataIndex: 'area_code',
                  key: 'area_code',
                  render: (text, record: any) => (
                    <div>
                      <div style={{ fontWeight: 'bold', color: '#1677ff' }}>{text}</div>
                      <div style={{ fontSize: '11px', color: '#8c8c8c' }}>{record.area_name || 'Default grid'}</div>
                    </div>
                  )
                },
                {
                  title: 'Linked parcels',
                  key: 'parcels',
                  width: 90,
                  render: (_, record: any) => {
                    const count = all.filter(p => (p.area_id ?? p.area_version_id) === record.id).length;
                    return <Tag color={count > 0 ? 'blue' : 'default'}>{count}</Tag>;
                  }
                },
                {
                  title: 'Target handling unit (HU)',
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
                      label: `📦 ${u.external_unit_no} (${u.linked_piece_count} linked)`
                    }));

                    return (
                      <Select
                        style={{ width: '100%', maxWidth: '280px' }}
                        placeholder="Select handling unit..."
                        allowClear
                        value={currentUnitId}
                        options={unitOptions}
                        onChange={(newUnitId) => {
                          // area-fill is a replace operation, not an append. Build
                          // the complete mapping first; otherwise selecting a
                          // second area for the same HU silently removes the first
                          // area when the page is revisited.
                          const next: Record<number, number[]> = Object.fromEntries(
                            Object.entries(linkedAreasByUnit).map(([id, ids]) => [Number(id), [...ids]])
                          );
                          Object.keys(next).forEach(id => {
                            next[Number(id)] = next[Number(id)].filter(aId => aId !== areaVerId);
                          });
                          if (newUnitId) {
                            next[newUnitId] = [...(next[newUnitId] ?? []), areaVerId];
                          }
                          setUnitSelectedAreas(next);

                          const affected = new Set<number>();
                          if (currentUnitId) affected.add(currentUnitId);
                          if (newUnitId) affected.add(newUnitId);
                          Promise.all(Array.from(affected).map(unitId => api(
                            `/ops/v1/handling-units/${unitId}/area-fill`,
                            session,
                            {
                              method: 'POST',
                              body: JSON.stringify({
                                deliveryAreaIds: next[unitId] ?? [],
                                reason: 'Manual area-centric assignment'
                              })
                            },
                            station
                          ))).then(async () => {
                            message.success('Handling-unit area mapping saved');
                            await Promise.all([
                              cache.invalidateQueries({ queryKey: ['arrival-trip', station, tripId] }),
                              cache.invalidateQueries({ queryKey: ['arrival-trips', station, serviceDate] }),
                              refresh()
                            ]);
                          }).catch((error: Error) => message.error(`Failed to save handling-unit mapping: ${error.message}`));
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
              <i className="fa-solid fa-wand-magic-sparkles"></i> Fill using defaults
            </Button>
            <Button className="btn-primary" onClick={() => setStage(2)}>
              Confirm handling-unit plan <i className="fa-solid fa-arrow-right"></i>
            </Button>
          </div>
        </div>
      )}



      {/* STEP 3 panel: area assignment */}
      {stage === 2 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          {/* Primary action 1: assign by responsibility area */}
          <Card 
            size="small" 
            style={{ borderRadius: '10px', background: 'linear-gradient(135deg, #e6f4ff 0%, #f0f5ff 100%)', borderColor: '#91caef', boxShadow: '0 2px 8px rgba(22,119,255,0.08)' }}
          >
              <div style={{ fontWeight: 'bold', color: '#0958d9', fontSize: '14px', marginBottom: '4px' }}>
              <i className="fa-solid fa-wand-magic-sparkles" style={{ marginRight: 6 }}></i>
              Auto-assign all parcels by driver area
            </div>
            <div style={{ fontSize: '12px', color: '#595959', marginBottom: '10px', lineHeight: '1.5' }}>
              Match parcel areas to driver preferences and remaining capacity in one action.
            </div>
            <Button 
              type="primary"
              block
              size="middle"
              style={{ fontWeight: 'bold', height: '38px', borderRadius: '8px' }}
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
              ⚡ Run default assignment
            </Button>
          </Card>

          {/* Primary action 2: assign one driver and area */}
          <div className="op-card" style={{ borderRadius: '10px' }}>
            <div style={{ fontWeight: 'bold', color: '#1f1f1f', fontSize: '13px', marginBottom: '10px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span><i className="fa-solid fa-location-dot" style={{ color: '#1677ff', marginRight: 6 }}></i> Assign by area</span>
              <Button 
                type="link" 
                size="small" 
                style={{ padding: 0, fontSize: '12px' }}
                onClick={() => setCapacityOpen(true)}
              >
                ⚙️ Driver capacity
              </Button>
            </div>

            <div style={{ marginBottom: '10px' }}>
              <label style={{ fontSize: '12px', color: '#595959', fontWeight: 600, display: 'block', marginBottom: '4px' }}>1. Select driver</label>
              <Select 
                value={driver} 
                onChange={(val) => {
                  setDriver(val);
                  setAreaVersion(undefined);
                  setCurrentArea(undefined);
                }} 
                style={{ width: '100%' }} 
                allowClear
                placeholder="Select driver"
                options={available.map(s => ({ value: s.driver_id, label: `${s.driver_name} (${s.assigned_count}/${s.parcel_capacity ?? 200} assigned)` }))}
              />
            </div>

            <div style={{ marginBottom: '12px' }}>
              <label style={{ fontSize: '12px', color: '#595959', fontWeight: 600, display: 'block', marginBottom: '4px' }}>2. Select area</label>
              <Select 
                value={areaVersion} 
                onChange={(val) => {
                  setAreaVersion(val);
                  if (val) setCurrentArea(val);
                }} 
                style={{ width: '100%' }} 
                allowClear
                placeholder="Select area"
                options={areas}
              />
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              <Button 
                type="primary" 
                block
                disabled={!driver || !areaVersion}
                style={{ borderRadius: '6px' }}
                onClick={() => {
                  if (waveId && driver && areaVersion) {
                    command.mutate({
                      path: `/ops/v1/planning/waves/${waveId}/assign-area`,
                      body: { driverId: driver, areaVersionId: areaVersion }
                    });
                  }
                }}
              >
                🎯 Assign area parcels
              </Button>

              <Button
                type="dashed"
                block
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
                🧭 Calculate driver route
              </Button>
            </div>
          </div>

          <Button className="btn-primary btn-block" style={{ marginTop: '12px' }} onClick={() => setStage(3)}>
            Assignment complete, continue to preflight <i className="fa-solid fa-arrow-right"></i>
          </Button>
        </div>
      )}

      {/* STEP 4 panel */}
      {stage === 3 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
          <div className="op-card">
            <div className="card-header">
              <span>Wave gate checks</span>
              <i className="fa-solid fa-shield-halved" style={{ color: '#52c41a' }}></i>
            </div>
            <div style={{ fontSize: '13px', lineHeight: '2' }}>
              <div><i className="fa-solid fa-circle-check" style={{ color: '#52c41a' }}></i> {all.length} parcels have a route</div>
              <div><i className="fa-solid fa-circle-check" style={{ color: '#52c41a' }}></i> No driver is over capacity</div>
              <div><i className="fa-solid fa-circle-check" style={{ color: '#52c41a' }}></i> {all.filter(p=>p.priority_flag).length} ⚡ priority parcels assigned</div>
              <div><i className="fa-solid fa-circle-check" style={{ color: '#52c41a' }}></i> Driver shifts are available</div>
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
            <i className="fa-solid fa-rocket"></i> {waveStatus === 'PUBLISHED' ? 'Wave published' : 'Publish wave'}
          </Button>
        </div>
      )}
    </div>

    {/* Right Map Panel with Collapsible Parcel Drawer */}
    {stage >= 2 && (
      <div className="dispatch-map-panel" style={{ border: '1px solid #e8e8e8', borderRadius: '8px', overflow: 'hidden', position: 'relative', background: '#e5e9ec', display: 'flex', flexDirection: 'column' }}>

        <div className="mobile-dispatch-controls">
          <Button className="mobile-driver-button" type="primary" onClick={() => setDriverDrawerOpen(true)}>
            <i className="fa-solid fa-user" style={{ marginRight: 6 }}></i>
            {driver ? `Driver: ${available.find(s => s.driver_id === driver)?.driver_name ?? driver}` : 'Select driver'}
            <i className="fa-solid fa-chevron-down" style={{ marginLeft: 8, fontSize: 11 }}></i>
          </Button>
          <Tag color={currentArea ? 'blue' : 'default'}>
            {currentArea ? `Area ${areas.find(a => a.value === currentArea)?.label ?? currentArea}` : 'All station parcels'}
          </Tag>
        </div>

        {/* Floating Map Controls & Collapsible Parcel Drawer Toggle */}
        <div style={{ position: 'absolute', top: 12, right: 12, zIndex: 1000, display: 'flex', gap: '8px' }}>
          <Button 
            type={listOpen ? 'primary' : 'default'}
            icon={<i className={`fa-solid ${listOpen ? 'fa-xmark' : 'fa-list-check'}`}></i>}
            onClick={() => setListOpen(!listOpen)}
            style={{ boxShadow: '0 2px 6px rgba(0,0,0,0.15)' }}
          >
            {listOpen ? 'Hide parcel details' : `Show parcel details (${filteredVisibleParcels.length})`}
            {selected.size > 0 && <Tag color="purple" style={{ marginLeft: 6 }}>{selected.size} selected</Tag>}
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
          <div className="dispatch-parcel-panel" style={{
            position: 'absolute', top: 0, right: 0, bottom: 0, width: '420px',
            background: '#fff', borderLeft: '1px solid #d9d9d9', boxShadow: '-4px 0 16px rgba(0,0,0,0.12)',
            zIndex: 1001, display: 'flex', flexDirection: 'column', padding: '12px'
          }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px', paddingBottom: '8px', borderBottom: '1px solid #f0f0f0' }}>
              <span style={{ fontWeight: 'bold', fontSize: '14px' }}>
                <i className="fa-solid fa-boxes-packing" style={{ color: '#1677ff', marginRight: 6 }}></i>
                Parcel details ({filteredVisibleParcels.length})
              </span>
              <Button size="small" type="text" onClick={() => setListOpen(false)}>
                <i className="fa-solid fa-xmark"></i>
              </Button>
            </div>

            {/* Batch Reassign Bar when items are selected */}
            {selected.size > 0 && (
              <div style={{ padding: '8px', background: '#f9f0ff', border: '1px solid #d3ade8', borderRadius: '6px', marginBottom: '8px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ fontSize: '12px', color: '#531dab', fontWeight: 'bold' }}>
                  {selected.size} parcels selected
                </span>
                <Space>
                  <Button size="small" onClick={() => setSelected(new Set())}>Clear selection</Button>
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
                    Transfer to selected driver
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
                    title: 'Tracking number',
                    dataIndex: 'tracking_no',
                    render: (text: string, r) => (
                      <div>
                        <div style={{ fontWeight: 'bold', fontSize: '12px', color: '#1677ff' }}>{text}</div>
                    <div style={{ fontSize: '11px', color: '#8c8c8c' }}>{r.area_code ?? 'Unmapped'}</div>
                      </div>
                    )
                  },
                  {
                    title: 'Assigned driver',
                    dataIndex: 'driver_name',
                    render: (name: string) => name ? <Tag color="blue">{name}</Tag> : <Tag color="default">Unassigned</Tag>
                  },
                  {
                    title: 'Status',
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


  {stage >= 2 && <MobileActionBar label="Open dispatch parcel list" count={filteredVisibleParcels.length} onClick={() => setListOpen(true)} />}
  <Drawer
    title="Select dispatch driver"
    placement="bottom"
    height="72vh"
    open={driverDrawerOpen}
    onClose={() => setDriverDrawerOpen(false)}
    className="mobile-driver-drawer"
  >
    <Button
      block
      type={!driver ? 'primary' : 'default'}
      style={{ marginBottom: 8, textAlign: 'left' }}
      onClick={() => { setDriver(undefined); setAreaVersion(undefined); setCurrentArea(undefined); setDriverDrawerOpen(false); }}
    >
      All station parcels (clear driver filter)
    </Button>
    <List
      dataSource={available}
      locale={{ emptyText: 'No available drivers' }}
      renderItem={(shift) => (
        <List.Item
          className={driver === shift.driver_id ? 'mobile-driver-option active' : 'mobile-driver-option'}
          onClick={() => { setDriver(shift.driver_id); setAreaVersion(undefined); setCurrentArea(undefined); setDriverDrawerOpen(false); }}
        >
          <List.Item.Meta
            avatar={<i className="fa-solid fa-id-badge" style={{ color: driver === shift.driver_id ? '#1677ff' : '#8c8c8c', fontSize: 20 }}></i>}
            title={shift.driver_name}
            description={`${shift.driver_code} · ${shift.assigned_count}/${shift.parcel_capacity ?? 200} assigned`}
          />
          {driver === shift.driver_id && <Tag color="blue">Current</Tag>}
        </List.Item>
      )}
    />
  </Drawer>
  <Drawer 
    title={<div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
      <span style={{ fontSize: '18px', fontWeight: 600 }}>{t('dispatch.manageCapacity')}</span>
      <Input.Search 
        placeholder="Search name, code, or driver ID..."
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
        showTotal: (total) => `${total} drivers`,
        size: 'small'
      }}
      style={{ marginTop: '4px' }}
    />
  </Drawer>
  <Drawer 
    open={!!focus} 
    onClose={()=>setFocus(undefined)} 
    title={`📦 Parcel detail: ${focus?.tracking_no ?? ''}`}
    width={480}
    zIndex={2000}
  >
    {focus&&<><List dataSource={Object.entries(focus)} renderItem={([key,value])=><List.Item><Typography.Text type="secondary">{key}</Typography.Text><Typography.Text>{String(value??'—')}</Typography.Text></List.Item>}/>{focus.driver_id&&waveId&&<Space.Compact block style={{ marginTop: '16px' }}><Select value={driver} onChange={setDriver} style={{width:'70%'}} options={available.map(s=>({value:s.driver_id,label:s.driver_name}))}/><Button disabled={!driver||driver===focus.driver_id} onClick={()=>command.mutate({path:`/ops/v1/planning/waves/${waveId}/parcels/${focus.parcel_id}/reassign`,body:{driverId:driver,reason:'Operator map reassignment'}})}>{t('dispatch.reassign')}</Button></Space.Compact>}</>}
  </Drawer>
 </div>;
}
