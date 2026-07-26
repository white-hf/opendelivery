import { useState, useMemo } from 'react';
import { Card, Table, Tag, Input, Select, Button, Space, Typography, Progress, Drawer, List, App, Pagination } from 'antd';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, type Session } from '../api/client';
import { PlanningMap, type PlanningParcel } from './PlanningMap';

export function DispatchReassignWorkspace({ session, station, serviceDate }: { session: Session; station: number | string; serviceDate: string }) {

    const { message } = App.useApp();
    const cache = useQueryClient();
    const [selectedDriverId, setSelectedDriverId] = useState<number | undefined>(undefined);
    const [searchQuery, setSearchSearchQuery] = useState('');
    const [targetDriverId, setTargetDriverId] = useState<number | undefined>(undefined);
    const [selectedParcels, setSelectedParcels] = useState<Set<number>>(new Set());
    const [lassoActive, setLassoActive] = useState<boolean>(false);
    const [listOpen, setListOpen] = useState<boolean>(false);
    const [focusParcel, setFocusParcel] = useState<PlanningParcel | undefined>(undefined);

    // Fetch delivery service areas for polygon rendering
    const serviceAreasQuery = useQuery({
        queryKey: ['delivery-areas-map', station],
        queryFn: () => api<any[]>(`/ops/v1/delivery-areas?status=ACTIVE`, session, {}, station)
    });

    // Fetch shifts/drivers
    const shiftsQuery = useQuery({
        queryKey: ['planning-shifts', station, serviceDate],
        queryFn: () => api<any[]>(`/ops/v1/planning/shifts?serviceDate=${serviceDate}`, session, {}, station),
    });

    // Fetch all parcels for map and table
    const parcelsQuery = useQuery({
        queryKey: ['planning-parcels', station, serviceDate, 'reassign'],
        queryFn: () => api<PlanningParcel[]>(`/ops/v1/planning/parcels?serviceDate=${serviceDate}&limit=2000`, session, {}, station),
    });

    // Waves list to get current wave ID
    const wavesList = useQuery({
        queryKey: ['dispatch-waves-list', station, serviceDate],
        queryFn: () => api<any[]>(`/ops/v1/dispatch/waves?limit=100`, session, {}, station).then(res => (res ?? []).filter(w => w.service_date === serviceDate))
    });

    const currentWaveId = useMemo(() => {
        if (!wavesList.data || wavesList.data.length === 0) return undefined;
        return wavesList.data[0].wave_id ?? wavesList.data[0].id;
    }, [wavesList.data]);

    const drivers = shiftsQuery.data ?? [];
    const allParcels = parcelsQuery.data ?? [];

    const [driverPage, setDriverPage] = useState<number>(1);
    const DRIVER_PAGE_SIZE = 5;

    const pagedDrivers = useMemo(() => {
        const start = (driverPage - 1) * DRIVER_PAGE_SIZE;
        return drivers.slice(start, start + DRIVER_PAGE_SIZE);
    }, [drivers, driverPage]);

    const [currentArea, setCurrentArea] = useState<number | undefined>(undefined);

    // Filter parcels based on selected driver, area, or search
    const filteredParcels = useMemo(() => {
        let list = allParcels;
        if (selectedDriverId !== undefined) {
            list = list.filter(p => p.driver_id === selectedDriverId);
        }
        if (currentArea !== undefined) {
            const matchedArea = (serviceAreasQuery.data ?? []).find(a => Number(a.id) === Number(currentArea));
            list = list.filter(p => {
                if (p.area_id != null && Number(p.area_id) === Number(currentArea)) return true;
                if (matchedArea && p.area_code === matchedArea.area_code) return true;
                return false;
            });
        }
        if (searchQuery) {
            const q = searchQuery.toLowerCase();
            list = list.filter(p =>
                (typeof p.tracking_no === 'string' && p.tracking_no.toLowerCase().includes(q)) ||
                (typeof p.recipient_name === 'string' && p.recipient_name.toLowerCase().includes(q)) ||
                (typeof p.address_line1 === 'string' && p.address_line1.toLowerCase().includes(q)) ||
                (typeof p.area_code === 'string' && p.area_code.toLowerCase().includes(q))
            );
        }

        return list;
    }, [allParcels, selectedDriverId, currentArea, searchQuery, serviceAreasQuery.data]);

    // Reassign mutation
    const reassignMutation = useMutation({
        mutationFn: async () => {
            if (!currentWaveId || !targetDriverId || selectedParcels.size === 0) return;
            const parcelIds = Array.from(selectedParcels);
            for (const pid of parcelIds) {
                await api(`/ops/v1/planning/waves/${currentWaveId}/parcels/${pid}/reassign`, session, {
                    method: 'POST',
                    body: JSON.stringify({ driverId: targetDriverId, reason: 'High-frequency driver parcel dynamic reassignment' })
                }, station);
            }
        },
        onSuccess: () => {
            message.success(`成功将 ${selectedParcels.size} 件包裹划转给新司机！`);
            setSelectedParcels(new Set());
            cache.invalidateQueries({ queryKey: ['planning-parcels', station, serviceDate] });
            cache.invalidateQueries({ queryKey: ['planning-shifts', station, serviceDate] });
        },
        onError: (e: Error) => {
            message.error(`划转改派失败: ${e.message}`);
        }
    });

    return (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
            {/* 顶栏提示与统计 */}
            <Card size="small" style={{ borderRadius: '10px', background: '#fafafa' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <Space size="middle">
                        <span style={{ fontSize: '16px', fontWeight: 'bold' }}>
                            <i className="fa-solid fa-boxes-stacked" style={{ color: '#1677ff', marginRight: 8 }}></i>
                            3.2 司机包裹动态调配工作台 (Driver Re-assignment)
                        </span>
                        <Tag color="orange">高频调优</Tag>
                    </Space>
                    <Space>
                        <Select
                            placeholder="🔍 快速筛选司机查看其包裹"
                            allowClear
                            style={{ width: 240 }}
                            value={selectedDriverId}
                            onChange={setSelectedDriverId}
                            options={drivers.map(d => ({
                                value: d.driver_id,
                                label: `${d.driver_name} (${d.assigned_count}/${d.parcel_capacity ?? 200}件)`
                            }))}
                        />
                        <Input
                            placeholder="🔍 搜索单号 / 姓名 / 地址"
                            allowClear
                            value={searchQuery}
                            onChange={e => setSearchSearchQuery(e.target.value)}
                            style={{ width: 220 }}
                        />
                    </Space>
                </div>
            </Card>

            {/* 核心两列布局：左侧司机运力监控 + 右侧全屏地图区 (包含可收起包裹明细与改派工具条) */}
            <div style={{ display: 'grid', gridTemplateColumns: '320px 1fr', gap: '16px', height: '680px' }}>
                {/* 左侧司机列表与运力状态 (固定 680px 高度与地图对齐，增加分页) */}
                <Card 
                    title={<span><i className="fa-solid fa-id-card"></i> 当班司机容量监控 ({drivers.length} 人)</span>} 
                    size="small" 
                    style={{ height: '680px', display: 'flex', flexDirection: 'column' }}
                    styles={{ body: { flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden', padding: '12px' } }}
                >
                    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: '10px', overflowY: 'auto' }}>
                        {pagedDrivers.map(d => {
                            const cap = d.parcel_capacity ?? 200;
                            const isFull = d.assigned_count >= cap;
                            const isSelected = selectedDriverId === d.driver_id;
                            const percent = Math.min(100, Math.round((d.assigned_count / cap) * 100));

                            return (
                                <div
                                    key={d.driver_id}
                                    onClick={() => setSelectedDriverId(isSelected ? undefined : d.driver_id)}
                                    style={{
                                        padding: '10px 12px',
                                        borderRadius: '8px',
                                        border: isSelected ? '2px solid #1677ff' : '1px solid #e8e8e8',
                                        background: isSelected ? '#e6f4ff' : '#fff',
                                        cursor: 'pointer',
                                        transition: 'all 0.2s',
                                    }}
                                >
                                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px' }}>
                                        <span style={{ fontWeight: 'bold', fontSize: '13px' }}>
                                            <i className="fa-solid fa-steering-wheel" style={{ marginRight: 6, color: isSelected ? '#1677ff' : '#8c8c8c' }}></i>
                                            {d.driver_name}
                                        </span>
                                        <Tag color={isFull ? 'red' : 'green'} style={{ margin: 0 }}>
                                            {d.assigned_count} / {cap} 件
                                        </Tag>
                                    </div>
                                    <Progress percent={percent} status={isFull ? 'exception' : 'active'} size="small" showInfo={false} />
                                    <div style={{ fontSize: '11px', color: '#8c8c8c', marginTop: '4px', display: 'flex', justifyContent: 'space-between' }}>
                                        <span>偏好区域: {d.area_code ?? '未配置'}</span>
                                        <span>{isFull ? '🔴 容量已满' : `🟢 剩余 ${cap - d.assigned_count}`}</span>
                                    </div>
                                </div>
                            );
                        })}
                    </div>

                    <div style={{ paddingTop: '10px', borderTop: '1px solid #f0f0f0', display: 'flex', justifyContent: 'center' }}>
                        <Pagination
                            size="small"
                            current={driverPage}
                            pageSize={DRIVER_PAGE_SIZE}
                            total={drivers.length}
                            onChange={(page) => setDriverPage(page)}
                            showSizeChanger={false}
                        />
                    </div>
                </Card>

                {/* 右侧以地图为主的沉浸式工作区 (包含右上角套索、批量转移、与可折叠包裹抽屉) */}
                <div style={{ border: '1px solid #e8e8e8', borderRadius: '8px', overflow: 'hidden', position: 'relative', background: '#e5e9ec', display: 'flex', flexDirection: 'column' }}>

                    {/* 地图顶部控制条 */}
                    <div style={{ position: 'absolute', top: 12, left: 12, right: 12, zIndex: 1000, display: 'flex', justifyContent: 'space-between', pointerEvents: 'none' }}>
                        <div style={{ display: 'flex', gap: '8px', pointerEvents: 'auto', alignItems: 'center' }}>
                            <Button
                                type={lassoActive ? 'primary' : 'default'}
                                danger={lassoActive}
                                onClick={() => setLassoActive(!lassoActive)}
                                style={{ boxShadow: '0 2px 6px rgba(0,0,0,0.15)' }}
                            >
                                <i className="fa-solid fa-draw-polygon" style={{ marginRight: 6 }}></i>
                                {lassoActive ? '关闭地图套索' : '开启地图套索圈选'}
                            </Button>

                            {selectedParcels.size > 0 && (
                                <div style={{ display: 'flex', gap: '8px', alignItems: 'center', background: '#ffffff', padding: '4px 12px', borderRadius: '6px', boxShadow: '0 2px 8px rgba(0,0,0,0.15)', border: '1px solid #d3ade8' }}>
                                    <Tag color="purple" style={{ margin: 0, fontSize: '13px', fontWeight: 'bold' }}>
                                        已选 {selectedParcels.size} 件
                                    </Tag>
                                    <span style={{ fontSize: '12px', color: '#595959' }}>转给:</span>
                                    <Select
                                        size="small"
                                        placeholder="选择目标司机"
                                        style={{ width: 150 }}
                                        value={targetDriverId}
                                        onChange={setTargetDriverId}
                                        options={drivers.map(d => ({ value: d.driver_id, label: `${d.driver_name}` }))}
                                    />
                                    <Button
                                        size="small"
                                        type="primary"
                                        disabled={!targetDriverId}
                                        loading={reassignMutation.isPending}
                                        style={{ background: '#722ed1', borderColor: '#722ed1' }}
                                        onClick={() => reassignMutation.mutate()}
                                    >
                                        确认划转
                                    </Button>
                                    <Button size="small" type="text" onClick={() => setSelectedParcels(new Set())}>
                                        <i className="fa-solid fa-xmark"></i>
                                    </Button>
                                </div>
                            )}
                        </div>

                        <div style={{ display: 'flex', gap: '8px', pointerEvents: 'auto' }}>
                            <Button
                                type="primary"
                                ghost
                                disabled={!selectedDriverId || !currentWaveId}
                                onClick={() => {
                                    if (currentWaveId && selectedDriverId) {
                                        api(`/ops/v1/planning/waves/${currentWaveId}/drivers/${selectedDriverId}/optimize-route`, session, { method: 'POST', body: JSON.stringify({}) }, station)
                                            .then(() => {
                                                message.success('已通过 OSRM 算法重新生成该司机的最优派送路线与序号！');
                                                cache.invalidateQueries({ queryKey: ['planning-parcels', station, serviceDate] });
                                            })
                                            .catch(e => message.error(`路线优化失败: ${e.message}`));
                                    }
                                }}
                                style={{ boxShadow: '0 2px 6px rgba(0,0,0,0.15)', background: '#fff' }}
                            >
                                <i className="fa-solid fa-route" style={{ marginRight: 6 }}></i>
                                🧭 OSRM 路线规划
                            </Button>

                            <Button
                                type={listOpen ? 'primary' : 'default'}
                                icon={<i className={`fa-solid ${listOpen ? 'fa-xmark' : 'fa-list-check'}`}></i>}
                                onClick={() => setListOpen(!listOpen)}
                                style={{ boxShadow: '0 2px 6px rgba(0,0,0,0.15)' }}
                            >
                                {listOpen ? '收起包裹明细' : `展开包裹明细 (${filteredParcels.length} 件)`}
                            </Button>
                        </div>
                    </div>

                    {/* 地图组件主体 */}
                    <div style={{ flex: 1, position: 'relative' }}>
                        <PlanningMap
                            station={station}
                            parcels={filteredParcels}
                            serviceAreas={serviceAreasQuery.data ?? []}
                            selected={selectedParcels}
                            selectedDriverName={drivers.find(d => Number(d.driver_id) === Number(selectedDriverId))?.driver_name}
                            activeAreaId={currentArea}
                            onSelectArea={setCurrentArea}
                            lassoActive={lassoActive}
                            onToggle={(id) => {
                                setSelectedParcels(prev => {
                                    const next = new Set(prev);
                                    if (next.has(id)) next.delete(id);
                                    else next.add(id);
                                    return next;
                                });
                            }}
                            onSelect={(p) => setFocusParcel(p)}
                            onLassoSelect={(ids) => setSelectedParcels(new Set(ids))}
                        />
                    </div>

                    {/* 右侧可折叠的浮动包裹动态明细抽屉 */}
                    {listOpen && (
                        <div style={{
                            position: 'absolute', top: 0, right: 0, bottom: 0, width: '440px',
                            background: '#fff', borderLeft: '1px solid #d9d9d9', boxShadow: '-4px 0 16px rgba(0,0,0,0.12)',
                            zIndex: 1001, display: 'flex', flexDirection: 'column', padding: '12px'
                        }}>
                            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px', paddingBottom: '8px', borderBottom: '1px solid #f0f0f0' }}>
                                <span style={{ fontWeight: 'bold', fontSize: '14px' }}>
                                    <i className="fa-solid fa-boxes-packing" style={{ color: '#1677ff', marginRight: 6 }}></i>
                                    包裹动态明细 ({filteredParcels.length} 件)
                                </span>
                                <Button size="small" type="text" onClick={() => setListOpen(false)}>
                                    <i className="fa-solid fa-xmark"></i>
                                </Button>
                            </div>

                            {/* 批量划转操作栏 */}
                            <div style={{ padding: '8px', background: '#f9f0ff', border: '1px solid #d3ade8', borderRadius: '6px', marginBottom: '8px', display: 'flex', flexDirection: 'column', gap: '8px' }}>
                                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                    <span style={{ fontSize: '12px', color: '#531dab', fontWeight: 'bold' }}>
                                        转移已选 {selectedParcels.size} 件包裹给:
                                    </span>
                                    {selectedParcels.size > 0 && (
                                        <Button size="small" type="text" danger onClick={() => setSelectedParcels(new Set())}>取消选择</Button>
                                    )}
                                </div>
                                <div style={{ display: 'flex', gap: '8px' }}>
                                    <Select
                                        size="small"
                                        placeholder="选择目标接手司机"
                                        style={{ flex: 1 }}
                                        value={targetDriverId}
                                        onChange={setTargetDriverId}
                                        options={drivers.map(d => ({ value: d.driver_id, label: `${d.driver_name} (余 ${d.parcel_capacity ? d.parcel_capacity - d.assigned_count : 200}件)` }))}
                                    />
                                    <Button
                                        size="small"
                                        type="primary"
                                        icon={<i className="fa-solid fa-arrows-rotate"></i>}
                                        disabled={!targetDriverId || selectedParcels.size === 0}
                                        loading={reassignMutation.isPending}
                                        style={{ background: '#722ed1', borderColor: '#722ed1' }}
                                        onClick={() => reassignMutation.mutate()}
                                    >
                                        确认划转
                                    </Button>
                                </div>
                            </div>

                            {/* 包裹列表 (支持勾选多选与点击数据行查看详情) */}
                            <div style={{ flex: 1, overflowY: 'auto' }}>
                                <Table<PlanningParcel>
                                    size="small"
                                    rowKey="parcel_id"
                                    dataSource={filteredParcels}
                                    pagination={{ pageSize: 15, size: 'small', showSizeChanger: false }}
                                    onRow={(r) => ({
                                        onClick: () => setFocusParcel(r),
                                        style: { cursor: 'pointer' }
                                    })}
                                    rowSelection={{
                                        selectedRowKeys: Array.from(selectedParcels),
                                        onChange: (keys) => setSelectedParcels(new Set(keys.map(Number))),
                                    }}
                                    columns={[
                                        {
                                            title: '追踪号 (Tracking No)',
                                            dataIndex: 'tracking_no',
                                            render: (val: string, r) => (
                                                <div>
                                                    <div style={{ fontWeight: 'bold', fontSize: '12px', color: '#1677ff' }}>{val}</div>
                                                    <div style={{ fontSize: '11px', color: '#8c8c8c' }}>{r.area_code ?? '未划区'}</div>
                                                </div>
                                            )
                                        },
                                        {
                                            title: '当前司机',
                                            dataIndex: 'driver_name',
                                            render: (val: string) => val ? <Tag color="blue">{val}</Tag> : <Tag color="default">未指派</Tag>
                                        },
                                        {
                                            title: '状态',
                                            dataIndex: 'status',
                                            render: (val: string) => <Tag color={val === 'ASSIGNED' ? 'green' : 'orange'}>{val}</Tag>
                                        }
                                    ]}
                                />
                            </div>
                        </div>
                    )}
                </div>
            </div>

            {/* 单个包裹详情抽屉 (zIndex 2000 保证最顶层无遮挡) */}
            <Drawer
                open={!!focusParcel}
                onClose={() => setFocusParcel(undefined)}
                title={`📦 包裹详情: ${focusParcel?.tracking_no ?? ''}`}
                width={480}
                zIndex={2000}
            >
                {focusParcel && (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
                        <List
                            dataSource={Object.entries(focusParcel)}
                            renderItem={([key, value]) => (
                                <List.Item>
                                    <Typography.Text type="secondary">{key}</Typography.Text>
                                    <Typography.Text>{String(value ?? '—')}</Typography.Text>
                                </List.Item>
                            )}
                        />
                    </div>
                )}
            </Drawer>
        </div>
    );
}
