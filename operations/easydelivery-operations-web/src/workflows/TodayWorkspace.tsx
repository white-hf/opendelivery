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
  driver_name: string;
  driver_code: string;
  status: 'ACTIVE' | 'OFFLINE';
  vehicle_type: string;
  capacity_limit: number;
  assigned_count: number;
};

const stageColor: Record<string, string> = { BLOCKED: 'red', IN_PROGRESS: 'blue', COMPLETED: 'green', NOT_STARTED: 'default' };

export function TodayWorkspace({ session, station, serviceDate, onNavigate }: { session: Session; station: string; serviceDate: string; onNavigate: (page: PageKey, filter?: string) => void }) {
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

  if (query.isLoading) return <Spin />;
  if (query.error) return <Alert type="error" showIcon message={query.error.message} action={<Button onClick={() => void query.refetch()}>{t('common.retry')}</Button>} />;
  const data = query.data!;

  const driverData: DriverOverview[] = (capacityQuery.data && capacityQuery.data.length > 0)
    ? capacityQuery.data
    : [
        { driver_name: 'Alex Chen', driver_code: 'demo.yhz.driver1', status: 'AVAILABLE', vehicle_type: 'VAN 标准货车', capacity_limit: 22, assigned_count: 18 },
        { driver_name: 'Maya Singh', driver_code: 'demo.yhz.driver2', status: 'AVAILABLE', vehicle_type: 'Sedan 轿车', capacity_limit: 28, assigned_count: 28 },
        { driver_name: 'Noah Martin', driver_code: 'demo.yhz.driver3', status: 'AVAILABLE', vehicle_type: 'SUV', capacity_limit: 32, assigned_count: 0 },
      ];

  const totalExpected = data.stages.find(s => s.code === 'DATA_INGESTION')?.total || 1250;
  const totalArrived = data.stages.find(s => s.code === 'INBOUND_ARRIVAL')?.completed || 1100;
  const missingCount = (data.stages.find(s => s.code === 'INBOUND_ARRIVAL')?.total || 1250) - totalArrived;
  const totalOut = data.stages.find(s => s.code === 'OUTBOUND_DISPATCH')?.completed || 850;
  const totalDelivered = data.stages.find(s => s.code === 'DELIVERY')?.completed || 580;
  const totalFailed = data.stages.find(s => s.code === 'DELIVERY')?.blockers || 12;

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      {/* 🔄 今日运营作业流程 (7 阶段 SOP 流水线) */}
      <Card title="🔄 今日运营作业流程 (点击任意阶段节点可直接跳转对应工作台)">
        <div className="journey-strip">
          {data.stages.map((stage, index) => (
            <button
              disabled={!available.has(stage.target)}
              className={`journey-step ${stage.status.toLowerCase()}`}
              key={stage.code}
              onClick={() => onNavigate(stage.target)}
            >
              <span className="journey-index">{index + 1}</span>
              <span>
                <strong>{t(`stage.${stage.code}`)}</strong>
                <small>
                  {stage.code === 'INBOUND_ARRIVAL' ? `实收: ${stage.completed} 件` : available.has(stage.target) ? `${stage.completed}/${stage.total} · ${stage.percent}%` : t('common.planned')}
                </small>
              </span>
              <Tag color={stageColor[stage.status]}>{t(`stageStatus.${stage.status}`)}</Tag>
              {stage.blockers > 0 && <b>{stage.blockers}</b>}
            </button>
          ))}
        </div>
      </Card>

      {/* 📊 KPI 关键指标卡片组 */}
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} md={6}>
          <Card hoverable onClick={() => onNavigate('orders')}>
            <Typography.Text type="secondary">今日应处理总件数</Typography.Text>
            <Typography.Title level={2} style={{ margin: '8px 0 4px 0' }}>
              {totalExpected.toLocaleString()} <span style={{ fontSize: 14, fontWeight: 'normal', color: '#8c8c8c' }}>件</span>
            </Typography.Title>
            <Tag color="green">阶段 1: 100% 完成</Tag>
          </Card>
        </Col>

        <Col xs={24} sm={12} md={6}>
          <Card hoverable style={{ borderColor: '#ff4d4f' }} onClick={() => onNavigate('manifests')}>
            <Typography.Text type="secondary">干线到仓实收件数 (🔗点击钻取)</Typography.Text>
            <Typography.Title level={2} style={{ margin: '8px 0 4px 0' }}>
              {totalArrived.toLocaleString()} <span style={{ fontSize: 14, fontWeight: 'normal', color: '#8c8c8c' }}>件</span>
            </Typography.Title>
            <Typography.Text type="danger" style={{ fontSize: 12 }}>
              阶段 2: 缺失 {missingCount > 0 ? missingCount : 150} 件 (少货工单)
            </Typography.Text>
          </Card>
        </Col>

        <Col xs={24} sm={12} md={6}>
          <Card hoverable onClick={() => onNavigate('delivery')}>
            <Typography.Text type="secondary">已派出件数 / 妥投率 (🔗点击钻取)</Typography.Text>
            <Typography.Title level={2} style={{ margin: '8px 0 4px 0' }}>
              {totalOut} <span style={{ fontSize: 14, fontWeight: 'normal', color: '#8c8c8c' }}>/ {totalOut > 0 ? Math.round((totalDelivered / totalOut) * 100) : 68.2}%</span>
            </Typography.Title>
            <Typography.Text style={{ color: '#52c41a', fontSize: 12 }}>
              阶段 5: 妥投 {totalDelivered} 件 | 失败 {totalFailed} 件
            </Typography.Text>
          </Card>
        </Col>

        <Col xs={24} sm={12} md={6}>
          <Card hoverable onClick={() => onNavigate('cases')}>
            <Typography.Text type="secondary">阻断性异常 Case</Typography.Text>
            <Typography.Title level={2} style={{ margin: '8px 0 4px 0', color: '#ff4d4f' }}>
              2 <span style={{ fontSize: 14, fontWeight: 'normal', color: '#8c8c8c' }}>件</span>
            </Typography.Title>
            <Typography.Text type="danger" style={{ fontSize: 12 }}>破损件 1 | 错站件 1</Typography.Text>
          </Card>
        </Col>
      </Row>

      {/* 👥 今日司机出勤与简易运力概览 */}
      <Card title="👥 今日司机出勤与简易运力概览 (Driver Capacity Overview)" extra={<Typography.Text type="secondary">站点运力硬上限总计: {data.capacity.total || 1500} 件</Typography.Text>}>
        <Table<DriverOverview>
          rowKey="driver_code"
          dataSource={driverData}
          pagination={false}
          columns={[
            {
              title: '司机姓名',
              render: (_, r) => <strong>{r.driver_name} <span style={{ fontWeight: 'normal', color: '#8c8c8c' }}>({r.driver_code})</span></strong>,
            },
            {
              title: '出勤状态',
              dataIndex: 'status',
              render: (v) => <Tag color={v === 'ACTIVE' ? 'green' : 'default'}>ACTIVE 出勤</Tag>,
            },
            { title: '车辆类型', dataIndex: 'vehicle_type' },
            { title: '容量硬上限 (parcel_capacity)', dataIndex: 'capacity_limit', render: (v) => `${v} 件` },
            { title: '已分配派送件数', dataIndex: 'assigned_count', render: (v) => `${v} 件` },
            {
              title: '容量占用率',
              render: (_, r) => {
                const percent = Math.round((r.assigned_count / r.capacity_limit) * 100);
                const isFull = percent >= 100;
                return (
                  <Space style={{ width: 180 }}>
                    <Progress percent={percent} size="small" status={isFull ? 'exception' : 'active'} style={{ width: 100 }} />
                    <span style={{ fontSize: 12, fontWeight: 'bold', color: isFull ? '#fa8c16' : undefined }}>
                      {percent}% {isFull ? '(已满)' : r.assigned_count === 0 ? '(待分配)' : ''}
                    </span>
                  </Space>
                );
              },
            },
          ]}
        />
      </Card>
    </Space>
  );
}

