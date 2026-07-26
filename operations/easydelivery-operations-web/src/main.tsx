import React from 'react';import ReactDOM from 'react-dom/client';import {QueryClient,QueryClientProvider} from '@tanstack/react-query';import {ConfigProvider} from 'antd';import {AuthProvider} from './auth/session';import {App} from './App';import './styles.css';import './i18n';
ReactDOM.createRoot(document.getElementById('root')!).render(<React.StrictMode><QueryClientProvider client={new QueryClient()}><AuthProvider><ConfigProvider wave={{ disabled: true }}><App/></ConfigProvider></AuthProvider></QueryClientProvider></React.StrictMode>);

