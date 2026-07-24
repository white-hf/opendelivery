import { useState } from 'react';
import { Table, Tag, Button, Modal, Form, Input, Select, Space, Card, Tabs, Badge, App } from 'antd';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api, type Session } from '../api/client';

const PROVINCE_CITIES_MAP: Record<string, Array<{ value: string; label: string }>> = {
  NS: [
    { value: 'HALIFAX', label: 'HALIFAX (哈利法克斯)' },
    { value: 'DARTMOUTH', label: 'DARTMOUTH (达特茅斯)' },
    { value: 'BEDFORD', label: 'BEDFORD (贝德福德)' },
    { value: 'SYDNEY', label: 'SYDNEY (悉尼)' },
  ],
  ON: [
    { value: 'TORONTO', label: 'TORONTO (多伦多)' },
    { value: 'MISSISSAUGA', label: 'MISSISSAUGA (密西沙加)' },
    { value: 'BRAMPTON', label: 'BRAMPTON (布兰普顿)' },
    { value: 'MARKHAM', label: 'MARKHAM (万锦)' },
    { value: 'OTTAWA', label: 'OTTAWA (渥太华)' },
    { value: 'HAMILTON', label: 'HAMILTON (汉密尔顿)' },
  ],
  BC: [
    { value: 'VANCOUVER', label: 'VANCOUVER (温哥华)' },
    { value: 'RICHMOND', label: 'RICHMOND (列治文)' },
    { value: 'BURNABY', label: 'BURNABY (本拿比)' },
    { value: 'SURREY', label: 'SURREY (萨里)' },
    { value: 'VICTORIA', label: 'VICTORIA (维多利亚)' },
  ],
  QC: [
    { value: 'MONTREAL', label: 'MONTREAL (蒙特利尔)' },
    { value: 'QUEBEC CITY', label: 'QUEBEC CITY (魁北克市)' },
    { value: 'LAVAL', label: 'LAVAL (拉瓦勒)' },
  ],
  AB: [
    { value: 'CALGARY', label: 'CALGARY (卡尔加里)' },
    { value: 'EDMONTON', label: 'EDMONTON (埃德蒙顿)' },
  ],
  NY: [
    { value: 'NEW YORK', label: 'NEW YORK (纽约)' },
    { value: 'BUFFALO', label: 'BUFFALO (水牛城)' },
  ],
  CA: [
    { value: 'LOS ANGELES', label: 'LOS ANGELES (洛杉矶)' },
    { value: 'SAN FRANCISCO', label: 'SAN FRANCISCO (旧金山)' },
    { value: 'SAN JOSE', label: 'SAN JOSE (圣何塞)' },
  ],
};

const DEFAULT_CITIES = [
  { value: 'HALIFAX', label: 'HALIFAX (哈利法克斯)' },
  { value: 'TORONTO', label: 'TORONTO (多伦多)' },
  { value: 'VANCOUVER', label: 'VANCOUVER (温哥华)' },
];

export function SystemConfigWorkspace({ session, station }: { session: Session; station: string }) {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [driverModalOpen, setDriverModalOpen] = useState(false);
  const [areaModalOpen, setAreaModalOpen] = useState(false);
  const [stationModalOpen, setStationModalOpen] = useState(false);
  const [form] = Form.useForm();
  const [areaForm] = Form.useForm();
  const [stationForm] = Form.useForm();

  const selectedProvince = Form.useWatch('provinceCode', areaForm);
  const availableCities = selectedProvince ? (PROVINCE_CITIES_MAP[selectedProvince] ?? DEFAULT_CITIES) : DEFAULT_CITIES;

  const stationSelectedProvince = Form.useWatch('provinceCode', stationForm);
  const stationAvailableCities = stationSelectedProvince ? (PROVINCE_CITIES_MAP[stationSelectedProvince] ?? DEFAULT_CITIES) : DEFAULT_CITIES;

  const handleProvinceChange = (newProvince: string) => {
    const defaultCityForProv = PROVINCE_CITIES_MAP[newProvince]?.[0]?.value ?? 'OTHER';
    areaForm.setFieldsValue({ cityName: defaultCityForProv });
  };

  const stationsQuery = useQuery({
    queryKey: ['stations'],
    queryFn: () => api<Array<{ station_code: string; station_name: string; city: string; province_code: string; country_code: string }>>('/ops/v1/stations', session),
  });

  const currentStationObj = stationsQuery.data?.find((s) => s.station_code === station);

  const driversQuery = useQuery({
    queryKey: ['system-drivers', station],
    queryFn: () => api<any[]>('/ops/v1/system/drivers', session, {}, station),
  });

  const serviceAreasQuery = useQuery({
    queryKey: ['station-service-areas', station],
    queryFn: () => api<any[]>('/ops/v1/system/service-areas', session, {}, station),
  });

  const handleCreateStation = async (values: any) => {
    try {
      await api('/ops/v1/system/stations', session, {
        method: 'POST',
        body: JSON.stringify(values),
      }, station);
      message.success('末端站点新增成功');
      setStationModalOpen(false);
      stationForm.resetFields();
      void queryClient.invalidateQueries({ queryKey: ['stations'] });
    } catch (e: any) {
      message.error(e.message || '新建末端站点失败');
    }
  };

  const handleCreateDriver = async (values: any) => {
    try {
      await api('/ops/v1/system/drivers', session, {
        method: 'POST',
        body: JSON.stringify(values),
      }, station);
      message.success('司机新增成功');
      setDriverModalOpen(false);
      form.resetFields();
      void queryClient.invalidateQueries({ queryKey: ['system-drivers', station] });
    } catch (e: any) {
      message.error(e.message || '新建司机失败');
    }
  };

  const handleToggleDriverStatus = async (driverId: number, currentStatus: string) => {
    try {
      const nextStatus = currentStatus === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
      await api(`/ops/v1/system/drivers/${driverId}/status`, session, {
        method: 'PUT',
        body: JSON.stringify({ status: nextStatus }),
      }, station);
      message.success('司机状态已更新');
      void queryClient.invalidateQueries({ queryKey: ['system-drivers', station] });
    } catch (e: any) {
      message.error(e.message || '更新司机状态失败');
    }
  };

  const handleCreateServiceArea = async (values: any) => {
    try {
      await api('/ops/v1/system/service-areas', session, {
        method: 'POST',
        body: JSON.stringify(values),
      }, station);
      message.success('服务范围扩展成功');
      setAreaModalOpen(false);
      areaForm.resetFields();
      void queryClient.invalidateQueries({ queryKey: ['station-service-areas', station] });
    } catch (e: any) {
      message.error(e.message || '新建服务范围失败');
    }
  };

  const driverColumns = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    { title: '工号/账号', dataIndex: 'credential_id', key: 'credential_id' },
    { title: '姓名', dataIndex: 'driver_name', key: 'driver_name' },
    { title: '手机号', dataIndex: 'phone', key: 'phone' },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => (
        <Badge status={status === 'ACTIVE' ? 'success' : 'default'} text={status === 'ACTIVE' ? '正常在职' : '已停用'} />
      ),
    },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: any) => (
        <Button
          size="small"
          danger={record.status === 'ACTIVE'}
          onClick={() => handleToggleDriverStatus(record.id, record.status)}
        >
          {record.status === 'ACTIVE' ? '停用' : '启用'}
        </Button>
      ),
    },
  ];

  const serviceAreaColumns = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
    { title: '国家', dataIndex: 'country_code', key: 'country_code' },
    { title: '省/州', dataIndex: 'province_code', key: 'province_code' },
    { title: '城市', dataIndex: 'city_name', key: 'city_name' },
    {
      title: '邮编前缀匹配',
      dataIndex: 'postal_prefix',
      key: 'postal_prefix',
      render: (prefix: string) => prefix ? <Tag color="blue">{prefix}</Tag> : <Tag>全城通用</Tag>,
    },
    { title: '优先级', dataIndex: 'priority', key: 'priority' },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => <Tag color={status === 'ACTIVE' ? 'green' : 'red'}>{status}</Tag>,
    },
  ];

  return (
    <div style={{ padding: '16px' }}>
      <Card title="⚙️ 站点与司机系统配置中心">
        <Tabs
          items={[
            {
              key: 'stations',
              label: '🏢 站点物理网络与服务覆盖范围 (Stations & Service Areas)',
              children: (
                <div>
                  <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between' }}>
                    <span>全网末端配送站点及其包揽的路由服务范围</span>
                    <Button type="primary" onClick={() => setStationModalOpen(true)}>+ 新增末端站点</Button>
                  </div>
                  <Table
                    loading={stationsQuery.isLoading}
                    dataSource={stationsQuery.data ?? []}
                    columns={[
                      { title: '站点代码', dataIndex: 'station_code', key: 'station_code', width: 110 },
                      { title: '站点全称', dataIndex: 'station_name', key: 'station_name' },
                      { title: '城市', dataIndex: 'city', key: 'city' },
                      { title: '省/州', dataIndex: 'province_code', key: 'province_code', width: 80 },
                      { title: '国家', dataIndex: 'country_code', key: 'country_code', width: 80 },
                      { title: '时区', dataIndex: 'timezone', key: 'timezone' },
                      { title: '状态', dataIndex: 'status', key: 'status', width: 90, render: (s: string) => <Tag color={s === 'ACTIVE' ? 'green' : 'red'}>{s}</Tag> },
                      {
                        title: '服务覆盖管理',
                        key: 'area_action',
                        render: (_: any, record: any) => (
                          <Button
                            size="small"
                            type="dashed"
                            onClick={() => {
                              areaForm.setFieldsValue({
                                countryCode: record.country_code || 'CA',
                                provinceCode: record.province_code || 'ON',
                                cityName: record.city || 'TORONTO',
                                priority: 100,
                              });
                              setAreaModalOpen(true);
                            }}
                          >
                            + 扩展该站点覆盖范围
                          </Button>
                        ),
                      },
                    ]}
                    rowKey="station_code"
                  />

                  <div style={{ marginTop: 24 }}>
                    <h4>📍 站点服务覆盖自动路由规则表 (Service Area Rules)</h4>
                    <Table
                      loading={serviceAreasQuery.isLoading}
                      dataSource={serviceAreasQuery.data ?? []}
                      columns={serviceAreaColumns}
                      rowKey="id"
                      size="small"
                    />
                  </div>
                </div>
              ),
            },
            {
              key: 'drivers',
              label: '👨‍✈️ 司机管理 (Drivers)',
              children: (
                <div>
                  <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between' }}>
                    <span>已录入司机列表（属于当前站点）</span>
                    <Button type="primary" onClick={() => setDriverModalOpen(true)}>+ 新建司机账号</Button>
                  </div>
                  <Table
                    loading={driversQuery.isLoading}
                    dataSource={driversQuery.data ?? []}
                    columns={driverColumns}
                    rowKey="id"
                  />
                </div>
              ),
            },
          ]}
        />
      </Card>

      {/* 新增司机 Modal */}
      <Modal
        title="新增司机账号"
        open={driverModalOpen}
        onCancel={() => setDriverModalOpen(false)}
        onOk={() => form.submit()}
      >
        <Form form={form} layout="vertical" onFinish={handleCreateDriver} autoComplete="off">
          <Form.Item label="司机工号/账号" name="credentialId" rules={[{ required: true, message: '请输入工号' }]}>
            <Input placeholder="例如: driver.yhz.05" autoComplete="new-password" />
          </Form.Item>
          <Form.Item label="司机姓名" name="driverName" rules={[{ required: true, message: '请输入姓名' }]}>
            <Input placeholder="例如: 张师傅" autoComplete="off" />
          </Form.Item>
          <Form.Item label="手机号" name="phone" rules={[{ pattern: /^\+?[0-9\s-]{7,15}$/, message: '请输入有效的手机格式（纯数字或含国家码）' }]}>
            <Input placeholder="例如: 19021234567" type="tel" autoComplete="off" />
          </Form.Item>
          <Form.Item label="初始密码" name="password">
            <Input.Password placeholder="默认: password123" autoComplete="new-password" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 新增服务范围 Modal */}
      <Modal
        title="扩展站点覆盖范围"
        open={areaModalOpen}
        onCancel={() => setAreaModalOpen(false)}
        onOk={() => areaForm.submit()}
      >
        <Form
          form={areaForm}
          layout="vertical"
          onFinish={handleCreateServiceArea}
          initialValues={{
            countryCode: currentStationObj?.country_code ?? 'CA',
            provinceCode: currentStationObj?.province_code ?? 'NS',
            cityName: currentStationObj?.city ?? 'HALIFAX',
            priority: 100,
          }}
        >
          <Form.Item label="国家代码" name="countryCode" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'CA', label: 'CA - Canada (加拿大)' },
                { value: 'US', label: 'US - United States (美国)' },
              ]}
            />
          </Form.Item>
          <Form.Item label="省/州代码" name="provinceCode" rules={[{ required: true }]}>
            <Select
              onChange={handleProvinceChange}
              options={[
                { value: 'NS', label: 'NS - Nova Scotia (新斯佳省)' },
                { value: 'ON', label: 'ON - Ontario (安大略省)' },
                { value: 'BC', label: 'BC - British Columbia (不列颠哥伦比亚省)' },
                { value: 'QC', label: 'QC - Quebec (魁北克省)' },
                { value: 'AB', label: 'AB - Alberta (阿尔伯塔省)' },
                { value: 'NY', label: 'NY - New York (纽约州)' },
                { value: 'CA', label: 'CA - California (加利福尼亚州)' },
              ]}
            />
          </Form.Item>
          <Form.Item label="城市名称" name="cityName" rules={[{ required: true, message: '请输入或选择城市名称' }]}>
            <Select
              showSearch
              allowClear
              placeholder="选择常用城市，或搜索/手写输入"
              options={availableCities}
              filterOption={false}
              onSearch={(text) => {
                if (text && !availableCities.some((c) => c.value === text.toUpperCase())) {
                  areaForm.setFieldsValue({ cityName: text.toUpperCase() });
                }
              }}
            />
          </Form.Item>
          <Form.Item label="邮编前缀限制 (可选)" name="postalPrefix">
            <Input placeholder="例如 B3K (留空代表覆盖全城)" style={{ textTransform: 'uppercase' }} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 新增末端站点 Modal */}
      <Modal
        title="新增末端配送站点 (Station)"
        open={stationModalOpen}
        onCancel={() => setStationModalOpen(false)}
        onOk={() => stationForm.submit()}
      >
        <Form
          form={stationForm}
          layout="vertical"
          onFinish={handleCreateStation}
          initialValues={{
            countryCode: 'CA',
            provinceCode: 'ON',
            city: 'HAMILTON',
            stationCode: 'HAM-01',
            stationName: 'Hamilton Last Mile Station',
            timezone: 'America/Toronto',
          }}
        >
          <Form.Item label="国家" name="countryCode" rules={[{ required: true }]}>
            <Select options={[{ value: 'CA', label: 'CA - Canada (加拿大)' }, { value: 'US', label: 'US - United States (美国)' }]} />
          </Form.Item>
          <Form.Item label="省/州" name="provinceCode" rules={[{ required: true }]}>
            <Select
              onChange={(newProv) => {
                const cityList = PROVINCE_CITIES_MAP[newProv] ?? DEFAULT_CITIES;
                const defaultCity = cityList[0]?.value ?? 'HAMILTON';
                const prefix = defaultCity.slice(0, 3);
                const code = `${prefix}-01`;
                const capitalCity = defaultCity.charAt(0) + defaultCity.slice(1).toLowerCase();
                const name = `${capitalCity} Last Mile Station`;
                const tz = newProv === 'BC' ? 'America/Vancouver' : (newProv === 'NS' ? 'America/Halifax' : 'America/Toronto');
                stationForm.setFieldsValue({
                  city: defaultCity,
                  stationCode: code,
                  stationName: name,
                  timezone: tz,
                });
              }}
              options={[
                { value: 'ON', label: 'ON - Ontario (安大略省)' },
                { value: 'NS', label: 'NS - Nova Scotia (新斯佳省)' },
                { value: 'BC', label: 'BC - British Columbia (不列颠哥伦比亚省)' },
                { value: 'QC', label: 'QC - Quebec (魁北克省)' },
                { value: 'AB', label: 'AB - Alberta (阿尔伯塔省)' },
                { value: 'NY', label: 'NY - New York (纽约州)' },
                { value: 'CA', label: 'CA - California (加利福尼亚州)' },
              ]}
            />
          </Form.Item>
          <Form.Item label="城市名称 (随省自动级联)" name="city" rules={[{ required: true, message: '请选择城市' }]}>
            <Select
              showSearch
              placeholder="选择常用城市，或搜索/手写输入"
              options={stationAvailableCities}
              onChange={(val) => {
                const rawCity = String(val).trim().toUpperCase();
                if (rawCity) {
                  const prefix = rawCity.slice(0, 3);
                  const code = `${prefix}-01`;
                  const capitalCity = rawCity.charAt(0) + rawCity.slice(1).toLowerCase();
                  const name = `${capitalCity} Last Mile Station`;
                  stationForm.setFieldsValue({
                    stationCode: code,
                    stationName: name,
                  });
                }
              }}
              onSearch={(text) => {
                if (text && !stationAvailableCities.some((c) => c.value === text.toUpperCase())) {
                  const rawCity = text.toUpperCase();
                  const prefix = rawCity.slice(0, 3);
                  const code = `${prefix}-01`;
                  const capitalCity = rawCity.charAt(0) + rawCity.slice(1).toLowerCase();
                  const name = `${capitalCity} Last Mile Station`;
                  stationForm.setFieldsValue({
                    city: rawCity,
                    stationCode: code,
                    stationName: name,
                  });
                }
              }}
            />
          </Form.Item>
          <Form.Item label="站点代码 (根据城市极简自动推导)" name="stationCode" rules={[{ required: true }]}>
            <Input placeholder="例如 HAM-01" style={{ textTransform: 'uppercase' }} />
          </Form.Item>
          <Form.Item label="站点全称 (自动生成)" name="stationName" rules={[{ required: true }]}>
            <Input placeholder="例如 Hamilton Last Mile Station" />
          </Form.Item>
          <Form.Item label="时区 (根据省自动匹配)" name="timezone" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'America/Toronto', label: 'America/Toronto (加东/EST)' },
                { value: 'America/Vancouver', label: 'America/Vancouver (加西/PST)' },
                { value: 'America/Halifax', label: 'America/Halifax (大西洋/AST)' },
              ]}
            />
          </Form.Item>
          <Form.Item label="详细地址 (可选)" name="addressLine">
            <Input placeholder="例如 100 King St W, Hamilton, ON" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
