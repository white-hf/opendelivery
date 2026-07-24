import {createContext,useContext,useEffect,useMemo,useState,type ReactNode} from 'react';import {api,setUnauthorizedHandler,type Session} from '../api/client';
import { App } from 'antd';

type Auth={session:Session|null;login:(u:string,p:string)=>Promise<void>;logout:()=>Promise<void>};const Context=createContext<Auth|null>(null);const key='opendelivery.operator.session';

export function AuthProvider({children}:{children:ReactNode}){
  const { message } = App.useApp();
  const [session,setSession]=useState<Session|null>(()=>{try{const stored=sessionStorage.getItem(key);return stored?JSON.parse(stored) as Session:null;}catch{return null;}});
  
  useEffect(() => {
    setUnauthorizedHandler(() => {
      sessionStorage.removeItem(key);
      setSession(null);
      message.error('登录会话已过期，请重新登录');
    });
    return () => setUnauthorizedHandler(null);
  }, [message]);

  const value=useMemo<Auth>(()=>({session,async login(username,password){const tokens=await api<{accessToken:string;refreshToken:string}>('/ops/auth/login',undefined,{method:'POST',body:JSON.stringify({username,password})});const partial={accessToken:tokens.accessToken,refreshToken:tokens.refreshToken,user:{username:'',displayName:'',stationCode:null,roles:[]}};const user=await api<Session['user']>('/ops/auth/me',partial);const next={...partial,user};sessionStorage.setItem(key,JSON.stringify(next));setSession(next);},async logout(){if(session)await api('/ops/auth/logout',session,{method:'POST'});sessionStorage.removeItem(key);setSession(null);}}),[session]);
  return <Context.Provider value={value}>{children}</Context.Provider>
}

export function useAuth(){const value=useContext(Context);if(!value)throw new Error('AuthProvider missing');return value}
