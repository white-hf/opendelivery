import { useEffect, useRef } from 'react';
import { Alert } from 'antd';
import { loadGoogleMaps, STATION_CENTERS } from './AreaMapEditor';

export type PlanningParcel={parcel_id:number;tracking_no:string;status?:string;longitude?:number;latitude?:number;exception_code?:string;driver_id?:number;driver_name?:string;area_code?:string;area_version_id?:number;[key:string]:unknown};

function color(parcel:PlanningParcel,selected:boolean){
    if(selected)return '#722ed1';
    if(parcel.exception_code==='OPEN_CASE')return '#cf1322';
    if(parcel.exception_code)return '#d46b08';
    if(parcel.driver_id)return '#389e0d';
    return '#1677ff';
}

export function PlanningMap({station,parcels,selected,onToggle}:{station:string;parcels:PlanningParcel[];selected:Set<number>;onToggle:(id:number)=>void}){
    const node=useRef<HTMLDivElement>(null);const map=useRef<google.maps.Map|undefined>(undefined);const markers=useRef<google.maps.Marker[]>([]);
    const key=import.meta.env.VITE_GOOGLE_MAPS_API_KEY?.trim();
    useEffect(()=>{if(!key||!node.current)return;let active=true;void loadGoogleMaps(key).then(({Map})=>{if(active&&node.current)map.current=new Map(node.current,{center:STATION_CENTERS[station]??STATION_CENTERS['YHZ-01'],zoom:11,mapTypeControl:false,streetViewControl:false,gestureHandling:'greedy'});});return()=>{active=false;markers.current.forEach(m=>m.setMap(null));};},[key,station]);
    useEffect(()=>{if(!map.current)return;markers.current.forEach(m=>m.setMap(null));markers.current=parcels.filter(p=>p.latitude!=null&&p.longitude!=null).map(p=>{const marker=new google.maps.Marker({map:map.current,position:{lat:Number(p.latitude),lng:Number(p.longitude)},title:`${p.tracking_no}${p.driver_name?' · '+p.driver_name:''}`,icon:{path:google.maps.SymbolPath.CIRCLE,scale:selected.has(p.parcel_id)?8:6,fillColor:color(p,selected.has(p.parcel_id)),fillOpacity:0.9,strokeColor:'#fff',strokeWeight:2}});marker.addListener('click',()=>onToggle(p.parcel_id));return marker;});},[onToggle,parcels,selected]);
    if(!key)return <Alert type="warning" showIcon message="Google Maps API key is not configured"/>;
    return <div ref={node} className="planning-map" aria-label="Parcel planning map"/>;
}
