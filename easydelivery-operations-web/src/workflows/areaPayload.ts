export type AreaForm = {
    areaCode: string;
    areaName: string;
    areaLevel?: number;
    geoJson: string;
    changeReason: string;
};

export function areaPayload(values: AreaForm) {
    const parsed = JSON.parse(values.geoJson) as unknown;
    return {
        areaCode: values.areaCode.trim().toUpperCase(),
        areaName: values.areaName.trim(),
        areaLevel: values.areaLevel ?? 1,
        geoJson: parsed,
        changeReason: values.changeReason.trim(),
    };
}
