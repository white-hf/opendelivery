import { useState } from 'react';
import { Alert, Button, Card, Drawer, Empty, Form, Input, Space, Spin, Table, Tag, Typography, message } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, type Session } from '../api/client';
import { useTranslation } from 'react-i18next';

type FailedReturn = {
    parcel_id:number; tracking_no:string; driver_name:string; driver_id:number; task_code:string;
    failure_reason_code?:string; failure_note?:string; attempted_at?:string;
};

export function FailedReturnWorkspace({session,station,serviceDate}:{session:Session;station:string;serviceDate:string}) {
    const {t}=useTranslation();
    const cache=useQueryClient();
    const [selected,setSelected]=useState<FailedReturn>();
    const [form]=Form.useForm();
    const query=useQuery({queryKey:['failed-returns',station,serviceDate],queryFn:()=>api<FailedReturn[]>(`/ops/v1/failed-returns?serviceDate=${serviceDate}`,session,{},station)});
    const receive=useMutation({
        mutationFn:(values:{reasonCode:string;note?:string})=>api(`/ops/v1/failed-returns/${selected!.parcel_id}/receive`,session,{method:'POST',body:JSON.stringify(values)},station),
        onSuccess:async()=>{message.success(t('returns.received'));setSelected(undefined);form.resetFields();await Promise.all([cache.invalidateQueries({queryKey:['failed-returns',station,serviceDate]}),cache.invalidateQueries({queryKey:['control-tower',station,serviceDate]})]);},
        onError:(error:Error)=>message.error(error.message),
    });
    if(query.isLoading)return <Spin/>;
    if(query.error)return <Alert type="error" showIcon message={query.error.message} action={<Button onClick={()=>void query.refetch()}>{t('common.retry')}</Button>}/>;
    const rows=query.data??[];
    return <Space direction="vertical" size="middle" style={{width:'100%'}}>
        <Card title={t('returns.title')} extra={<Tag color={rows.length?'orange':'green'}>{t('returns.pending',{count:rows.length})}</Tag>}>
            <Typography.Paragraph type="secondary">{t('returns.help')}</Typography.Paragraph>
            {rows.length?<Table<FailedReturn> rowKey="parcel_id" dataSource={rows} pagination={{pageSize:20}} columns={[
                {title:t('field.tracking_no'),dataIndex:'tracking_no'},
                {title:t('field.driver_name'),dataIndex:'driver_name'},
                {title:t('field.task_code'),dataIndex:'task_code'},
                {title:t('returns.failureReason'),dataIndex:'failure_reason_code',render:value=><Tag color="red">{value||'FAILED'}</Tag>},
                {title:t('returns.failedAt'),dataIndex:'attempted_at'},
                {title:t('common.action'),render:(_,row)=><Button type="primary" onClick={()=>setSelected(row)}>{t('returns.receive')}</Button>},
            ]}/>:<Empty description={t('returns.empty')}/>} 
        </Card>
        <Drawer open={!!selected} onClose={()=>setSelected(undefined)} width={480} title={`${t('returns.confirmTitle')} · ${selected?.tracking_no}`}>
            {selected&&<Space direction="vertical" style={{width:'100%'}}><Alert type="warning" showIcon message={t('returns.physicalCheck')} description={`${selected.driver_name} · ${selected.task_code}`}/><Form form={form} layout="vertical" onFinish={values=>receive.mutate(values)} initialValues={{reasonCode:'DRIVER_RETURN'}}><Form.Item name="reasonCode" label={t('returns.reasonCode')} rules={[{required:true}]}><Input/></Form.Item><Form.Item name="note" label={t('returns.note')}><Input.TextArea rows={4}/></Form.Item><Button block type="primary" htmlType="submit" loading={receive.isPending}>{t('returns.confirm')}</Button></Form></Space>}
        </Drawer>
    </Space>;
}
