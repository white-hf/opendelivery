import { useEffect, useRef, useState } from 'react';
import { Alert, Button, Empty, Space, Tag } from 'antd';
import { MarkerClusterer } from '@googlemaps/markerclusterer';
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
    const node=useRef<HTMLDivElement>(null);const map=useRef<google.maps.Map|undefined>(undefined);const markers=useRef<google.maps.Marker[]>([]);const cluster=useRef<MarkerClusterer|undefined>(undefined);const [ready,setReady]=useState(false);const [error,setError]=useState('');
    const key=import.meta.env.VITE_GOOGLE_MAPS_API_KEY?.trim();
    useEffect(()=>{if(!key||!node.current)return;let active=true;setError('');void loadGoogleMaps(key).then(({Map})=>{if(active&&node.current){map.current=new Map(node.current,{center:STATION_CENTERS[station]??STATION_CENTERS['YHZ-01'],zoom:11,mapTypeControl:false,streetViewControl:false,gestureHandling:'greedy'});setReady(true);}}).catch(()=>setError('Google Maps could not load'));return()=>{active=false;cluster.current?.clearMarkers();markers.current.forEach(m=>m.setMap(null));};},[key,station]);
    function fitAll(){const target=map.current;if(!target)return;const bounds=new google.maps.LatLngBounds();markers.current.forEach(marker=>{const position=marker.getPosition();if(position)bounds.extend(position);});if(!bounds.isEmpty())target.fitBounds(bounds,48);}
    useEffect(()=>{if(!map.current||!ready)return;cluster.current?.clearMarkers();markers.current.forEach(m=>m.setMap(null));markers.current=parcels.filter(p=>p.latitude!=null&&p.longitude!=null).map(p=>{const marker=new google.maps.Marker({position:{lat:Number(p.latitude),lng:Number(p.longitude)},title:`${p.tracking_no}${p.driver_name?' · '+p.driver_name:''}`,icon:{path:google.maps.SymbolPath.CIRCLE,scale:selected.has(p.parcel_id)?8:6,fillColor:color(p,selected.has(p.parcel_id)),fillOpacity:0.9,strokeColor:'#fff',strokeWeight:2}});marker.addListener('click',()=>onToggle(p.parcel_id));return marker;});cluster.current=new MarkerClusterer({map:map.current,markers:markers.current});fitAll();},[onToggle,parcels,ready,selected]);
    if(!key)return <Alert type="warning" showIcon message="Google Maps API key is not configured"/>;
    const locatable=parcels.filter(p=>p.latitude!=null&&p.longitude!=null).length;
    return <div className="planning-map-wrap">{error&&<Alert type="error" showIcon message={error}/>}<div ref={node} className="planning-map" aria-label="Parcel planning map"/>{ready&&locatable===0&&<div className="map-empty"><Empty description={parcels.length?'All matching parcels are missing coordinates':'No parcels match the current filter'}/></div>}<Space className="map-status" wrap><Tag>Queried {parcels.length}</Tag><Tag color="green">Locatable {locatable}</Tag><Tag color={parcels.length-locatable?'orange':'default'}>Missing {parcels.length-locatable}</Tag><Tag color="blue">Displayed {locatable}</Tag><Button size="small" onClick={fitAll} disabled={!locatable}>Fit all</Button></Space></div>;
}
