import { Alert, Button, Card, Col, Progress, Row, Space, Spin, Table, Tag, Typography } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { api, type Session } from '../api/client';
import type { PageKey } from '../auth/permissions';
import { useTranslation } from 'react-i18next';

type Tower = {
  station: Record<string, unknown>;
  serviceDate: string;
  generatedAt: string;
  metrics: Array<{ code: string; count: number; target: PageKey; filter?: string }>;
  stages: Array<{ code: string; status: string; total: number; completed: number; blockers: number; percent: number; target: PageKey }>;
  capacity: { availableDrivers: number; total: number; assigned: number; remaining: number; shortage: number };
  exceptions: Array<{ code: string; count: number; severity: string; target: PageKey; filter: string }>;
  actions: Array<{ code: string; count: number; severity: string; target: PageKey; filter?: string }>;
};

type DriverOverview = {
  driver_id: number;
  driver_code: string;
  driver_name: string;
  status: string;
  assigned_count?: number;
  assignedCount?: number;
};

const stageColor: Record<string, string> = { BLOCKED: 'red', IN_PROGRESS: 'blue', COMPLETED: 'green', NOT_STARTED: 'default' };

export function TodayWorkspace({ session, station, serviceDate, onNavigate }: { session: Session; station: number | string; serviceDate: string; onNavigate: (page: PageKey, filter?: string) => void }) {

  const { t } = useTranslation();

  const query = useQuery({
    queryKey: ['control-tower', station, serviceDate],
    queryFn: () => api<Tower>(`/ops/v1/control-tower?serviceDate=${serviceDate}`, session, {}, station),
    refetchInterval: 60000,
  });

  const capacityQuery = useQuery({
    queryKey: ['control-tower-driver-capacity', station, serviceDate],
    queryFn: () => api<DriverOverview[]>(`/ops/v1/control-tower/driver-capacity?serviceDate=${serviceDate}`, session, {}, station),
  });

  const available = new Set<PageKey>(['dashboard', 'orders', 'dispatch', 'manifests', 'cases', 'areas', 'delivery']);

  if (query.isLoading) return <Spin style={{ margin: '40px auto', display: 'block' }} />;
  if (query.error) return <Alert type="error" showIcon message={query.error.message} action={<Button onClick={() => void query.refetch()}>{t('common.retry')}</Button>} />;
  const data = query.data!;

  const driverData: DriverOverview[] = capacityQuery.data ?? [];

  const totalExpected = data.stages.find(s => s.code === 'DATA_INGESTION')?.total ?? 0;
  const totalArrived = data.stages.find(s => s.code === 'INBOUND_ARRIVAL')?.completed ?? 0;
  const missingCount = (data.stages.find(s => s.code === 'INBOUND_ARRIVAL')?.total ?? 0) - totalArrived;
  const totalOut = data.stages.find(s => s.code === 'OUTBOUND_DISPATCH')?.completed ?? 0;
  const totalDelivered = data.stages.find(s => s.code === 'DELIVERY')?.completed ?? 0;
  const totalFailed = data.stages.find(s => s.code === 'DELIVERY')?.blockers ?? 0;

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      {/* 🔄 今日运营 Slogan & Pipeline Flow */}
      <Card 
        size="small" 
        style={{ borderRadius: '12px', boxShadow: '0 2px 10px rgba(0,0,0,0.04)', border: '1px solid #e8edf3' }}
        title={
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '4px 0' }}>
            <span style={{ fontSize: '15px', fontWeight: 600, color: '#1e293b' }}>
              <i className="fa-solid fa-diagram-next" style={{ color: '#2563eb', marginRight: 8 }}></i>
              {t('tower.journey')} (select a stage to open its workspace)
            </span>
            <Tag color="blue" style={{ borderRadius: '12px', padding: '0 10px', margin: 0 }}>7-stage operating flow</Tag>
          </div>
        }
      >
        <div className="journey-strip" style={{ gap: '10px', padding: '4px 0' }}>
          {data.stages.map((stage, index) => {
            const isClickable = available.has(stage.target);
            const statusKey = stage.status.toLowerCase();
            return (
              <button
                disabled={!isClickable}
                className={`journey-step ${statusKey}`}
                key={stage.code}
                onClick={() => onNavigate(stage.target)}
                style={{
                  borderRadius: '10px',
                  padding: '12px 14px',
                  transition: 'all 0.25s cubic-bezier(0.4, 0, 0.2, 1)',
                  boxShadow: '0 1px 3px rgba(0,0,0,0.04)',
                  position: 'relative'
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', width: '100%', alignItems: 'center' }}>
                  <span className="journey-index" style={{ fontWeight: 'bold' }}>{index + 1}</span>
                  <Tag color={stageColor[stage.status]} style={{ borderRadius: '10px', margin: 0, fontSize: '11px' }}>
                    {t(`stageStatus.${stage.status}`)}
                  </Tag>
                </div>

                <div style={{ marginTop: '6px' }}>
                  <strong style={{ fontSize: '13px', color: '#0f172a', display: 'block' }}>{t(`stage.${stage.code}`)}</strong>
                  <small style={{ color: '#64748b', marginTop: '2px', fontSize: '11px', display: 'block' }}>
                    {stage.code === 'INBOUND_ARRIVAL' ? `Received: ${stage.completed}` : isClickable ? `${stage.completed}/${stage.total} · ${stage.percent}%` : t('common.planned')}
                  </small>
                </div>

                {stage.blockers > 0 && (
                  <b style={{
                    position: 'absolute',
                    top: '-6px',
                    right: '-6px',
                    background: '#ef4444',
                    color: '#fff',
                    borderRadius: '50%',
                    width: '20px',
                    height: '20px',
                    fontSize: '11px',
                    display: 'grid',
                    placeItems: 'center',
                    boxShadow: '0 2px 5px rgba(239,68,68,0.4)'
                  }}>
                    {stage.blockers}
                  </b>
                )}
              </button>
            );
          })}
        </div>
      </Card>

      {/* 📊 KPI 关键指标卡片组 (高质感渐变卡片 + 矢量图标 + 图标装饰) */}
      <Row gutter={[16, 16]}>
        {/* 卡片 1：今日应处理总件数 */}
        <Col xs={24} sm={12} md={6}>
          <Card 
            hoverable 
            onClick={() => onNavigate('orders')}
            style={{ 
              borderRadius: '12px', 
              background: 'linear-gradient(135deg, #eff6ff 0%, #ffffff 100%)',
              border: '1px solid #bfdbfe',
              boxShadow: '0 4px 12px rgba(37, 99, 235, 0.06)'
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <div>
                <Typography.Text type="secondary" style={{ fontSize: '12px', fontWeight: 500, color: '#475569' }}>{t('metric.EXPECTED')}</Typography.Text>
                <Typography.Title level={2} style={{ margin: '8px 0 4px 0', fontFamily: 'Outfit, Inter, sans-serif', color: '#1e3a8a', fontWeight: 700 }}>
                  {totalExpected.toLocaleString()} <span style={{ fontSize: 13, fontWeight: 500, color: '#64748b' }}>parcels</span>
                </Typography.Title>
              </div>
              <div style={{ width: '40px', height: '40px', borderRadius: '10px', background: '#dbeafe', display: 'grid', placeItems: 'center', color: '#2563eb', fontSize: '18px' }}>
                <i className="fa-solid fa-boxes-stacked"></i>
              </div>
            </div>
            <div style={{ marginTop: '8px' }}>
              <Tag color="blue" style={{ borderRadius: '12px', padding: '0 8px', fontSize: '11px', border: 'none' }}>Stage 1: inbound ready</Tag>
            </div>
          </Card>
        </Col>

        {/* 卡片 2：干线到仓实收件数 */}
        <Col xs={24} sm={12} md={6}>
          <Card 
            hoverable 
            onClick={() => onNavigate('manifests')}
            style={{ 
              borderRadius: '12px', 
              background: 'linear-gradient(135deg, #fff7ed 0%, #ffffff 100%)',
              border: missingCount > 0 ? '1px solid #fed7aa' : '1px solid #e2e8f0',
              boxShadow: '0 4px 12px rgba(234, 88, 12, 0.06)'
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <div>
                <Typography.Text type="secondary" style={{ fontSize: '12px', fontWeight: 500, color: '#475569' }}>{t('metric.ARRIVED')}</Typography.Text>
                <Typography.Title level={2} style={{ margin: '8px 0 4px 0', fontFamily: 'Outfit, Inter, sans-serif', color: '#9a3412', fontWeight: 700 }}>
                  {totalArrived.toLocaleString()} <span style={{ fontSize: 13, fontWeight: 500, color: '#64748b' }}>parcels</span>
                </Typography.Title>
              </div>
              <div style={{ width: '40px', height: '40px', borderRadius: '10px', background: '#ffedd5', display: 'grid', placeItems: 'center', color: '#ea580c', fontSize: '18px' }}>
                <i className="fa-solid fa-truck-ramp-box"></i>
              </div>
            </div>
            <Typography.Text type={missingCount > 0 ? "danger" : "secondary"} style={{ fontSize: 12, display: 'block', marginTop: '4px' }}>
              {missingCount > 0 ? `⚠️ Missing or short: ${missingCount}` : '🟢 Inbound balanced'}
            </Typography.Text>
          </Card>
        </Col>

        {/* 卡片 3：派妥件数 / 妥投率 */}
        <Col xs={24} sm={12} md={6}>
          <Card 
            hoverable 
            onClick={() => onNavigate('delivery')}
            style={{ 
              borderRadius: '12px', 
              background: 'linear-gradient(135deg, #f0fdf4 0%, #ffffff 100%)',
              border: '1px solid #bbf7d0',
              boxShadow: '0 4px 12px rgba(22, 163, 74, 0.06)'
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <div>
                <Typography.Text type="secondary" style={{ fontSize: '12px', fontWeight: 500, color: '#475569' }}>Dispatched / delivery rate</Typography.Text>
                <Typography.Title level={2} style={{ margin: '8px 0 4px 0', fontFamily: 'Outfit, Inter, sans-serif', color: '#166534', fontWeight: 700 }}>
                  {totalOut} <span style={{ fontSize: 13, fontWeight: 500, color: '#16a34a' }}>/ {totalOut > 0 ? Math.round((totalDelivered / totalOut) * 100) : 0}%</span>
                </Typography.Title>
              </div>
              <div style={{ width: '40px', height: '40px', borderRadius: '10px', background: '#dcfce7', display: 'grid', placeItems: 'center', color: '#16a34a', fontSize: '18px' }}>
                <i className="fa-solid fa-circle-check"></i>
              </div>
            </div>
            <Typography.Text style={{ color: '#16a34a', fontSize: 12, display: 'block', marginTop: '4px' }}>
              Delivered {totalDelivered} | Failed {totalFailed}
            </Typography.Text>
          </Card>
        </Col>

        {/* 卡片 4：阻断性异常 Case */}
        <Col xs={24} sm={12} md={6}>
          <Card 
            hoverable 
            onClick={() => onNavigate('cases')}
            style={{ 
              borderRadius: '12px', 
              background: 'linear-gradient(135deg, #fef2f2 0%, #ffffff 100%)',
              border: data.exceptions.length > 0 ? '1px solid #fca5a5' : '1px solid #e2e8f0',
              boxShadow: '0 4px 12px rgba(220, 38, 38, 0.06)'
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <div>
                <Typography.Text type="secondary" style={{ fontSize: '12px', fontWeight: 500, color: '#475569' }}>{t('tower.exceptions')}</Typography.Text>
                <Typography.Title level={2} style={{ margin: '8px 0 4px 0', fontFamily: 'Outfit, Inter, sans-serif', color: data.exceptions.length > 0 ? '#dc2626' : '#16a34a', fontWeight: 700 }}>
                  {data.exceptions.reduce((sum, e) => sum + e.count, 0)} <span style={{ fontSize: 13, fontWeight: 500, color: '#64748b' }}>items</span>
                </Typography.Title>
              </div>
              <div style={{ width: '40px', height: '40px', borderRadius: '10px', background: '#fee2e2', display: 'grid', placeItems: 'center', color: '#dc2626', fontSize: '18px' }}>
                <i className="fa-solid fa-triangle-exclamation"></i>
              </div>
            </div>
            <Typography.Text type={data.exceptions.length > 0 ? "danger" : "secondary"} style={{ fontSize: 12, display: 'block', marginTop: '4px' }}>
              {data.exceptions.length > 0 ? data.exceptions.map(e => `${e.code}: ${e.count}`).join(' | ') : '🟢 No blockers'}
            </Typography.Text>
          </Card>
        </Col>
      </Row>

      {/* 👥 今日司机出勤概览 */}
      <Card 
        size="small"
        style={{ borderRadius: '12px', boxShadow: '0 2px 10px rgba(0,0,0,0.04)', border: '1px solid #e8edf3' }}
        title={
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '4px 0' }}>
            <span style={{ fontSize: '15px', fontWeight: 600, color: '#1e293b' }}>
              <i className="fa-solid fa-id-card" style={{ color: '#2563eb', marginRight: 8 }}></i>
              Driver attendance and delivery workload
            </span>
            <Tag color="cyan" style={{ borderRadius: '12px', margin: 0 }}>{driverData.length} drivers on shift</Tag>
          </div>
        }
      >
        <Table<DriverOverview>
          rowKey={(r) => r.driver_code || String(r.driver_id)}
          dataSource={driverData}
          pagination={{ pageSize: 8, showSizeChanger: true, pageSizeOptions: ['8', '15', '30'] }}
          columns={[
            {
              title: '司机编号',
              dataIndex: 'driver_code',
              render: (v, r) => (
                <span style={{ fontFamily: 'monospace', fontWeight: 600, color: '#334155' }}>
                  {v || (r as any).driverCode || `DVR-${r.driver_id}`}
                </span>
              ),
            },
            {
              title: '司机姓名',
              render: (_, r) => (
                <Space>
                  <div style={{ width: 28, height: 28, borderRadius: '50%', background: '#e2e8f0', display: 'grid', placeItems: 'center', fontSize: 12, color: '#475569', fontWeight: 'bold' }}>
                    {(r.driver_name || 'D').slice(0, 1)}
                  </div>
                  <strong style={{ color: '#0f172a' }}>{r.driver_name || (r as any).driverName || '-'}</strong>
                </Space>
              ),
            },
            {
              title: '出勤状态',
              dataIndex: 'status',
              render: (v) => (
                <Tag color={v === 'AVAILABLE' ? 'success' : 'default'} style={{ borderRadius: '12px', padding: '0 10px' }}>
                  {v === 'AVAILABLE' ? '🟢 出勤在岗' : '⚪ 未出勤'}
                </Tag>
              ),
            },
            {
              title: '已分配派送件数',
              render: (_, r) => {
                const count = r.assigned_count ?? r.assignedCount ?? 0;
                return (
                  <Tag color={count > 0 ? 'blue' : 'default'} style={{ borderRadius: '10px', padding: '0 8px', fontWeight: 600 }}>
                    {count} 件
                  </Tag>
                );
              },
            },
          ]}
        />
      </Card>
    </Space>
  );
}
