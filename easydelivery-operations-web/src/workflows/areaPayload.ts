export type AreaForm = {
    areaCode: string;
    areaName: string;
    areaLevel?: number;
    driverIds: number[];
    geoJson: string;
    changeReason: string;
};

export function areaPayload(values: AreaForm) {
    const parsed = normalizeAreaGeoJson(JSON.parse(values.geoJson));
    return {
        areaCode: values.areaCode.trim().toUpperCase(),
        areaName: values.areaName.trim(),
        areaLevel: values.areaLevel ?? 1,
        driverIds: values.driverIds ?? [],
        geoJson: parsed,
        changeReason: values.changeReason.trim(),
    };
}
import { normalizeAreaGeoJson } from './areaGeometry';
