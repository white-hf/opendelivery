import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';

export const SUPPORTED_LOCALES = ['en-CA', 'fr-CA', 'zh-CN'] as const;
export type SupportedLocale = typeof SUPPORTED_LOCALES[number];
const resources = {
    'en-CA': { translation: { 'app.title':'OpenDelivery Operations','auth.username':'Username','auth.password':'Password','auth.signIn':'Sign in','auth.signOut':'Sign out','locale.label':'Language','nav.dashboard':'Dashboard','nav.areas':'Areas','nav.manifests':'Manifests','nav.dispatch':'Dispatch','nav.cases':'Cases','nav.callbacks':'Callbacks','nav.closeout':'Closeout','areas.title':'Delivery areas','areas.import':'Import GeoJSON','areas.help':'Published polygons define reusable planning areas. Import GeoJSON, validate it, then publish it.','areas.validate':'Validate','areas.publish':'Publish','areas.dialog':'Import delivery area','areas.reason':'Change reason','areas.create':'Create draft','areas.success':'Area operation completed','areas.invalidJson':'GeoJSON is not valid JSON' } },
    'fr-CA': { translation: { 'app.title':'Opérations OpenDelivery','auth.username':'Nom d’utilisateur','auth.password':'Mot de passe','auth.signIn':'Ouvrir une session','auth.signOut':'Fermer la session','locale.label':'Langue','nav.dashboard':'Tableau de bord','nav.areas':'Zones','nav.manifests':'Manifestes','nav.dispatch':'Répartition','nav.cases':'Dossiers','nav.callbacks':'Rappels','nav.closeout':'Clôture','areas.title':'Zones de livraison','areas.import':'Importer GeoJSON','areas.help':'Les polygones publiés définissent les zones de planification. Importez, validez, puis publiez le GeoJSON.','areas.validate':'Valider','areas.publish':'Publier','areas.dialog':'Importer une zone','areas.reason':'Motif du changement','areas.create':'Créer le brouillon','areas.success':'Opération de zone terminée','areas.invalidJson':'Le GeoJSON n’est pas valide' } },
    'zh-CN': { translation: { 'app.title':'OpenDelivery 运营系统','auth.username':'用户名','auth.password':'密码','auth.signIn':'登录','auth.signOut':'退出登录','locale.label':'语言','nav.dashboard':'运营看板','nav.areas':'派送区域','nav.manifests':'到货清单','nav.dispatch':'派送计划','nav.cases':'异常工单','nav.callbacks':'上游回调','nav.closeout':'日终关站','areas.title':'派送区域','areas.import':'导入 GeoJSON','areas.help':'已发布多边形用于本站派送规划。导入 GeoJSON，校验通过后再发布。','areas.validate':'校验','areas.publish':'发布','areas.dialog':'导入派送区域','areas.reason':'变更原因','areas.create':'创建草稿','areas.success':'区域操作完成','areas.invalidJson':'GeoJSON 不是有效 JSON' } },
};
const stored=localStorage.getItem('opendelivery.locale');
const initial=SUPPORTED_LOCALES.includes(stored as SupportedLocale)?stored!:'en-CA';
void i18n.use(initReactI18next).init({resources,lng:initial,fallbackLng:'en-CA',interpolation:{escapeValue:false}});
export async function changeLocale(locale:SupportedLocale){localStorage.setItem('opendelivery.locale',locale);await i18n.changeLanguage(locale);}
export default i18n;
