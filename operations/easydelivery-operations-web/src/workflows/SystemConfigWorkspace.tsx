import { useState } from 'react';
import { Table, Tag, Button, Modal, Form, Input, Select, Card, Badge, App, Space, Drawer, InputNumber, DatePicker } from 'antd';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, type Session } from '../api/client';
import { useTranslation } from 'react-i18next';

const PROVINCE_CITIES_MAP: Record<string, Array<{ value: string; label: string }>> = {
  NS: [
    { value: 'HALIFAX', label: 'HALIFAX' },
    { value: 'DARTMOUTH', label: 'DARTMOUTH' },
    { value: 'BEDFORD', label: 'BEDFORD' },
    { value: 'SYDNEY', label: 'SYDNEY' },
  ],
  ON: [
    { value: 'TORONTO', label: 'TORONTO' },
    { value: 'MISSISSAUGA', label: 'MISSISSAUGA' },
    { value: 'BRAMPTON', label: 'BRAMPTON' },
    { value: 'MARKHAM', label: 'MARKHAM' },
    { value: 'OTTAWA', label: 'OTTAWA' },
    { value: 'HAMILTON', label: 'HAMILTON' },
  ],
  BC: [
    { value: 'VANCOUVER', label: 'VANCOUVER' },
    { value: 'RICHMOND', label: 'RICHMOND' },
    { value: 'BURNABY', label: 'BURNABY' },
    { value: 'SURREY', label: 'SURREY' },
    { value: 'VICTORIA', label: 'VICTORIA' },
  ],
  QC: [
    { value: 'MONTREAL', label: 'MONTREAL' },
    { value: 'QUEBEC CITY', label: 'QUEBEC CITY' },
    { value: 'LAVAL', label: 'LAVAL' },
  ],
  AB: [
    { value: 'CALGARY', label: 'CALGARY' },
    { value: 'EDMONTON', label: 'EDMONTON' },
  ],
  NY: [
    { value: 'NEW YORK', label: 'NEW YORK' },
    { value: 'BUFFALO', label: 'BUFFALO' },
  ],
  CA: [
    { value: 'LOS ANGELES', label: 'LOS ANGELES' },
    { value: 'SAN FRANCISCO', label: 'SAN FRANCISCO' },
    { value: 'SAN JOSE', label: 'SAN JOSE' },
  ],
};

const DEFAULT_CITIES = [
  { value: 'HALIFAX', label: 'HALIFAX' },
  { value: 'TORONTO', label: 'TORONTO' },
  { value: 'VANCOUVER', label: 'VANCOUVER' },
];

export function SystemConfigWorkspace({ session, station, mode }: { session: Session; station: number | string; mode: 'drivers' | 'stations' }) {

  const { message } = App.useApp();
  const { i18n } = useTranslation();
  const english = i18n.language === 'en-CA';
  const label = (en: string, zh: string) => english ? en : zh;
  const queryClient = useQueryClient();
  const [driverModalOpen, setDriverModalOpen] = useState(false);
  const [areaModalOpen, setAreaModalOpen] = useState(false);
  const [stationModalOpen, setStationModalOpen] = useState(false);
  const [shiftDrawerOpen, setShiftDrawerOpen] = useState(false);
  const [shiftDate] = useState(new Date().toISOString().slice(0, 10));

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
    enabled: mode === 'stations',
  });

  const currentStationObj = stationsQuery.data?.find((s) => s.station_code === station);

  const driversQuery = useQuery({
    queryKey: ['system-drivers', station],
    queryFn: () => api<any[]>('/ops/v1/system/drivers', session, {}, station),
    enabled: mode === 'drivers',
  });

  const shiftsQuery = useQuery({
    queryKey: ['system-config-shifts', station, shiftDate],
    queryFn: () => api<any[]>(`/ops/v1/planning/shifts?serviceDate=${shiftDate}`, session, {}, station),
    enabled: mode === 'drivers'
  });

  const updateShiftMutation = useMutation({
    mutationFn: async ({ driverId, availabilityStatus, parcelCapacity }: { driverId: number; availabilityStatus: string; parcelCapacity: number }) => {
      await api('/ops/v1/planning/shifts', session, {
        method: 'POST',
        body: JSON.stringify({
          driverId,
          serviceDate: shiftDate,
          availabilityStatus,
          parcelCapacity
        })
      }, station);
    },
    onSuccess: () => {
      message.success(label('Driver shift updated successfully', '司机出勤班次及容量更新成功！'));
      void queryClient.invalidateQueries({ queryKey: ['system-config-shifts', station, shiftDate] });
      void queryClient.invalidateQueries({ queryKey: ['control-tower-driver-capacity', station] });
    },
    onError: (err: Error) => {
      message.error(label(`Failed to update shift: ${err.message}`, `更新班次失败: ${err.message}`));
    }
  });

  const serviceAreasQuery = useQuery({
    queryKey: ['station-service-areas', station],
    queryFn: () => api<any[]>('/ops/v1/system/service-areas', session, {}, station),
    enabled: mode === 'stations',
  });

  const handleCreateStation = async (values: any) => {
    try {
      await api('/ops/v1/system/stations', session, {
        method: 'POST',
        body: JSON.stringify(values),
      }, station);
      message.success(english ? 'Station created successfully' : '末端站点新增成功');
      setStationModalOpen(false);
      stationForm.resetFields();
      void queryClient.invalidateQueries({ queryKey: ['stations'] });
    } catch (e: any) {
      message.error(e.message || (english ? 'Failed to create station' : '新建末端站点失败'));
    }
  };

  const handleCreateDriver = async (values: any) => {
    try {
      await api('/ops/v1/system/drivers', session, {
        method: 'POST',
        body: JSON.stringify(values),
      }, station);
      message.success(english ? 'Driver created successfully' : '司机新增成功');
      setDriverModalOpen(false);
      form.resetFields();
      void queryClient.invalidateQueries({ queryKey: ['system-drivers', station] });
    } catch (e: any) {
      message.error(e.message || (english ? 'Failed to create driver' : '新建司机失败'));
    }
  };

  const handleToggleDriverStatus = async (driverId: number, currentStatus: string) => {
    try {
      const nextStatus = currentStatus === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
      await api(`/ops/v1/system/drivers/${driverId}/status`, session, {
        method: 'PUT',
        body: JSON.stringify({ status: nextStatus }),
      }, station);
      message.success(english ? 'Driver status updated' : '司机状态已更新');
      void queryClient.invalidateQueries({ queryKey: ['system-drivers', station] });
    } catch (e: any) {
      message.error(e.message || (english ? 'Failed to update driver status' : '更新司机状态失败'));
    }
  };

  const handleCreateServiceArea = async (values: any) => {
    try {
      await api('/ops/v1/system/service-areas', session, {
        method: 'POST',
        body: JSON.stringify(values),
      }, station);
      message.success(english ? 'Service area created successfully' : '服务范围扩展成功');
      setAreaModalOpen(false);
      areaForm.resetFields();
      void queryClient.invalidateQueries({ queryKey: ['station-service-areas', station] });
    } catch (e: any) {
      message.error(e.message || (english ? 'Failed to create service area' : '新建服务范围失败'));
    }
  };

  const driverColumns = [
    { title: label('ID', 'ID'), dataIndex: 'id', key: 'id', width: 80 },
    { title: label('Credential ID', '工号/账号'), dataIndex: 'credential_id', key: 'credential_id' },
    { title: label('Driver name', '姓名'), dataIndex: 'driver_name', key: 'driver_name' },
    { title: label('Phone number', '手机号'), dataIndex: 'phone', key: 'phone' },
    {
      title: label('Account Status', '账号状态'),
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: (status: string) => (
        <Badge status={status === 'ACTIVE' ? 'success' : 'error'} text={status === 'ACTIVE' ? label('Active', '正常在职') : label('Inactive', '已禁用')} />
      ),
    },
    {
      title: label('Today Shift', '今日出勤'),
      key: 'shift_status',
      width: 120,
      render: (_: any, record: any) => (
        <Tag color={record.status === 'ACTIVE' ? 'green' : 'default'}>
          {record.status === 'ACTIVE' ? `🟢 ${label('On Duty', '出勤中')}` : `⚪ ${label('Off Duty', '未出勤')}`}
        </Tag>
      ),
    },
    {
      title: label('Account Control', '账号维护'),
      key: 'action',
      width: 140,
      render: (_: any, record: any) => (
        <Button
          size="small"
          danger={record.status === 'ACTIVE'}
          onClick={() => handleToggleDriverStatus(record.id, record.status)}
        >
          {record.status === 'ACTIVE' ? label('Deactivate', '停用账号') : label('Activate', '启用账号')}
        </Button>
      ),
    },
  ];

  const serviceAreaColumns = [
    { title: label('ID', 'ID'), dataIndex: 'id', key: 'id', width: 80 },
    { title: label('Country', '国家'), dataIndex: 'country_code', key: 'country_code' },
    { title: label('Province/state', '省/州'), dataIndex: 'province_code', key: 'province_code' },
    { title: label('City', '城市'), dataIndex: 'city_name', key: 'city_name' },
    {
      title: label('Postal prefix', '邮编前缀匹配'),
      dataIndex: 'postal_prefix',
      key: 'postal_prefix',
      render: (prefix: string) => prefix ? <Tag color="blue">{prefix}</Tag> : <Tag>{label('Citywide', '全城通用')}</Tag>,
    },
    { title: label('Priority', '优先级'), dataIndex: 'priority', key: 'priority' },
    {
      title: label('Status', '状态'),
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => <Tag color={status === 'ACTIVE' ? 'green' : 'red'}>{status}</Tag>,
    },
  ];

  return (
    <div style={{ padding: '16px' }}>
      <Card title={mode === 'drivers' ? (english ? '👨‍✈️ Driver configuration' : '👨‍✈️ 司机配置') : (english ? '🏢 Station configuration and coverage' : '🏢 站点配置与服务覆盖')}>
        {mode === 'stations' ? (
          <div>
                  <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between' }}>
                    <span>{english ? 'Delivery stations and their routing coverage' : '全网末端配送站点及其服务范围'}</span>
                    <Button type="primary" onClick={() => setStationModalOpen(true)}>{english ? '+ Add station' : '+ 新增末端站点'}</Button>
                  </div>
                  <Table
                    loading={stationsQuery.isLoading}
                    dataSource={stationsQuery.data ?? []}
                    columns={[
                      { title: label('Station code', '站点代码'), dataIndex: 'station_code', key: 'station_code', width: 110 },
                      { title: label('Station name', '站点全称'), dataIndex: 'station_name', key: 'station_name' },
                      { title: label('City', '城市'), dataIndex: 'city', key: 'city' },
                      { title: label('Province/state', '省/州'), dataIndex: 'province_code', key: 'province_code', width: 80 },
                      { title: label('Country', '国家'), dataIndex: 'country_code', key: 'country_code', width: 80 },
                      { title: label('Timezone', '时区'), dataIndex: 'timezone', key: 'timezone' },
                      { title: label('Status', '状态'), dataIndex: 'status', key: 'status', width: 90, render: (s: string) => <Tag color={s === 'ACTIVE' ? 'green' : 'red'}>{s}</Tag> },
                      {
                        title: label('Coverage management', '服务覆盖管理'),
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
                            {label('+ Add coverage rule', '+ 扩展该站点覆盖范围')}
                          </Button>
                        ),
                      },
                    ]}
                    rowKey="station_code"
                  />

                  <div style={{ marginTop: 24 }}>
                    <h4>📍 {label('Station service area auto-routing rules', '站点服务覆盖自动路由规则表 (Service Area Rules)')}</h4>
                    <Table
                      loading={serviceAreasQuery.isLoading}
                      dataSource={serviceAreasQuery.data ?? []}
                      columns={serviceAreaColumns}
                      rowKey="id"
                      size="small"
                    />
                  </div>
          </div>
        ) : (
          <div>
                  <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <span>{label('Registered drivers list for current station', '已录入司机列表（属于当前站点）')}</span>
                    <Space>
                      <Button
                        type="primary"
                        ghost
                        onClick={() => setShiftDrawerOpen(true)}
                      >
                        {label('Manage Driver Attendance & Capacity', '⚙️ 设置出勤与班次容量')}
                      </Button>
                      <Button type="primary" onClick={() => setDriverModalOpen(true)}>{label('+ Add driver account', '+ 新建司机账号')}</Button>
                    </Space>
                  </div>
                  <Table
                    loading={driversQuery.isLoading}
                    dataSource={driversQuery.data ?? []}
                    columns={driverColumns}
                    rowKey="id"
                  />
          </div>
        )}
      </Card>

      {/* 新增司机 Modal */}
      {mode === 'drivers' && <Modal
        title={label('Add Driver Account', '新增司机账号')}
        open={driverModalOpen}
        onCancel={() => setDriverModalOpen(false)}
        onOk={() => form.submit()}
      >
        <Form form={form} layout="vertical" onFinish={handleCreateDriver} autoComplete="off">
          <Form.Item label={label('Credential ID', '司机工号/账号')} name="credentialId" rules={[{ required: true, message: label('Please enter credential ID', '请输入工号') }]}>
            <Input placeholder="driver.yhz.05" autoComplete="new-password" />
          </Form.Item>
          <Form.Item label={label('Driver Name', '司机姓名')} name="driverName" rules={[{ required: true, message: label('Please enter driver name', '请输入姓名') }]}>
            <Input placeholder="Driver Name" autoComplete="off" />
          </Form.Item>
          <Form.Item label={label('Phone Number', '手机号')} name="phone" rules={[{ pattern: /^\+?[0-9\s-]{7,15}$/, message: label('Please enter valid phone number', '请输入有效的手机格式') }]}>
            <Input placeholder="+1 902 123 4567" type="tel" autoComplete="off" />
          </Form.Item>
          <Form.Item label={label('Initial Password', '初始密码')} name="password">
            <Input.Password placeholder={label('Default: password123', '默认: password123')} autoComplete="new-password" />
          </Form.Item>
        </Form>
      </Modal>}

      {/* 新增服务范围 Modal */}
      {mode === 'stations' && <Modal
        title={label('Add Service Area', '扩展站点覆盖范围')}
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
          <Form.Item label={label('Country Code', '国家代码')} name="countryCode" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'CA', label: 'CA - Canada' },
                { value: 'US', label: 'US - United States' },
              ]}
            />
          </Form.Item>
          <Form.Item label={label('Province/State Code', '省/州代码')} name="provinceCode" rules={[{ required: true }]}>
            <Select
              onChange={handleProvinceChange}
              options={[
                { value: 'NS', label: 'NS - Nova Scotia' },
                { value: 'ON', label: 'ON - Ontario' },
                { value: 'BC', label: 'BC - British Columbia' },
                { value: 'QC', label: 'QC - Quebec' },
                { value: 'AB', label: 'AB - Alberta' },
                { value: 'NY', label: 'NY - New York' },
                { value: 'CA', label: 'CA - California' },
              ]}
            />
          </Form.Item>
          <Form.Item label={label('City Name', '城市名称')} name="cityName" rules={[{ required: true, message: label('Please enter or select city', '请输入或选择城市名称') }]}>
            <Select
              showSearch
              allowClear
              placeholder={label('Select or search city', '选择常用城市，或搜索/手写输入')}
              options={availableCities}
              filterOption={false}
              onSearch={(text) => {
                if (text && !availableCities.some((c) => c.value === text.toUpperCase())) {
                  areaForm.setFieldsValue({ cityName: text.toUpperCase() });
                }
              }}
            />
          </Form.Item>
          <Form.Item label={label('Postal Prefix (Optional)', '邮编前缀限制 (可选)')} name="postalPrefix">
            <Input placeholder={label('e.g. B3K (leave blank for citywide)', '例如 B3K (留空代表覆盖全城)')} style={{ textTransform: 'uppercase' }} />
          </Form.Item>
        </Form>
      </Modal>}

      {/* 新增末端站点 Modal */}
      {mode === 'stations' && <Modal
        title={label('Add Last-Mile Station', '新增末端配送站点 (Station)')}
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
          <Form.Item label={label('Country', '国家')} name="countryCode" rules={[{ required: true }]}>
            <Select options={[{ value: 'CA', label: 'CA - Canada' }, { value: 'US', label: 'US - United States' }]} />
          </Form.Item>
          <Form.Item label={label('Province/State', '省/州')} name="provinceCode" rules={[{ required: true }]}>
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
                { value: 'ON', label: 'ON - Ontario' },
                { value: 'NS', label: 'NS - Nova Scotia' },
                { value: 'BC', label: 'BC - British Columbia' },
                { value: 'QC', label: 'QC - Quebec' },
                { value: 'AB', label: 'AB - Alberta' },
                { value: 'NY', label: 'NY - New York' },
                { value: 'CA', label: 'CA - California' },
              ]}
            />
          </Form.Item>
          <Form.Item label={label('City Name', '城市名称 (随省自动级联)')} name="city" rules={[{ required: true, message: label('Please select city', '请选择城市') }]}>
            <Select
              showSearch
              placeholder={label('Select or search city', '选择常用城市，或搜索/手写输入')}
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
          <Form.Item label={label('Station Code', '站点代码')} name="stationCode" rules={[{ required: true }]}>
            <Input placeholder="HAM-01" style={{ textTransform: 'uppercase' }} />
          </Form.Item>
          <Form.Item label={label('Station Full Name', '站点全称')} name="stationName" rules={[{ required: true }]}>
            <Input placeholder="Hamilton Last Mile Station" />
          </Form.Item>
          <Form.Item label={label('Timezone', '时区')} name="timezone" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'America/Toronto', label: 'America/Toronto (EST)' },
                { value: 'America/Vancouver', label: 'America/Vancouver (PST)' },
                { value: 'America/Halifax', label: 'America/Halifax (AST)' },
              ]}
            />
          </Form.Item>
          <Form.Item label={label('Address Line (Optional)', '详细地址 (可选)')} name="addressLine">
            <Input placeholder="100 King St W, Hamilton, ON" />
          </Form.Item>
        </Form>
      </Modal>}

      {/* 司机出勤与班次容量管理 Drawer */}
      {mode === 'drivers' && (
        <Drawer
          title={label('Driver Attendance & Capacity Management', '⚙️ 司机出勤状态与班次容量管理')}
          width={640}
          open={shiftDrawerOpen}
          onClose={() => setShiftDrawerOpen(false)}
        >
          <div style={{ marginBottom: 16, display: 'flex', alignItems: 'center', justifyContent: 'space-between', background: '#fafafa', padding: '12px', borderRadius: '8px' }}>
            <span>{label('Service Date: ', '当前班次营业日：')} <strong>{shiftDate}</strong></span>
            <Button size="small" type="primary" onClick={() => void shiftsQuery.refetch()}>
              {label('Refresh Shifts', '刷新出勤数据')}
            </Button>
          </div>

          <Table
            loading={shiftsQuery.isLoading}
            dataSource={shiftsQuery.data ?? []}
            rowKey="driver_id"
            pagination={false}
            columns={[
              {
                title: label('Driver Name', '司机姓名'),
                dataIndex: 'driver_name',
                key: 'driver_name',
                render: (name: string, r: any) => (
                  <div>
                    <strong>{name}</strong>
                    <div style={{ fontSize: '12px', color: '#8c8c8c' }}>{r.driver_code}</div>
                  </div>
                ),
              },
              {
                title: label('Today Attendance', '今日出勤状态'),
                dataIndex: 'availability_status',
                key: 'availability_status',
                render: (status: string, record: any) => (
                  <Select
                    size="small"
                    value={status ?? 'AVAILABLE'}
                    onChange={(val) =>
                      updateShiftMutation.mutate({
                        driverId: record.driver_id,
                        availabilityStatus: val,
                        parcelCapacity: record.parcel_capacity ?? 200,
                      })
                    }
                    style={{ width: 120 }}
                    options={[
                      { value: 'AVAILABLE', label: <Tag color="green" style={{ margin: 0 }}>🟢 {label('Available', '出勤中')}</Tag> },
                      { value: 'UNAVAILABLE', label: <Tag color="default" style={{ margin: 0 }}>⚪ {label('Off Duty', '未出勤')}</Tag> },
                    ]}
                  />
                ),
              },
              {
                title: label('Max Capacity', '班次容量上限'),
                dataIndex: 'parcel_capacity',
                key: 'parcel_capacity',
                render: (cap: number, record: any) => (
                  <InputNumber
                    size="small"
                    min={10}
                    max={1000}
                    value={cap ?? 200}
                    onBlur={(e) => {
                      const val = Number(e.target.value);
                      if (val > 0 && val !== cap) {
                        updateShiftMutation.mutate({
                          driverId: record.driver_id,
                          availabilityStatus: record.availability_status ?? 'AVAILABLE',
                          parcelCapacity: val,
                        });
                      }
                    }}
                    style={{ width: 100 }}
                  />
                ),
              },
            ]}
          />
        </Drawer>
      )}
    </div>
  );
}
