export type Session={accessToken:string;refreshToken:string;user:{username:string;displayName:string;stationCode:string|null;roles:string[];preferredLocale?:string}};
export type ApiEnvelope<T>={biz_code:string;biz_message:string;biz_data:T};
export class ApiError extends Error{constructor(public status:number,public code:string,message:string){super(message)}}
const base=import.meta.env.VITE_API_BASE_URL??'/api';
export async function api<T>(path:string,session?:Session,init:RequestInit={},stationCode?:string):Promise<T>{
 const headers=new Headers(init.headers);headers.set('Content-Type','application/json');headers.set('X-Request-Id',crypto.randomUUID());headers.set('Accept-Language',localStorage.getItem('opendelivery.locale')??'en-CA');
 if(session)headers.set('Authorization',`Bearer ${session.accessToken}`);if(stationCode)headers.set('X-Station-Code',stationCode);
 const response=await fetch(base+path,{...init,headers});const body=await response.json() as ApiEnvelope<T>;
 if(!response.ok||body.biz_code!=='COMMON.QUERY.SUCCESS')throw new ApiError(response.status,body.biz_code,body.biz_message);return body.biz_data;
}
