export type DispatchDraftValues = {
    waveCode: string;
    serviceDate: string;
    routeCode?: string;
    driverId: number;
};

export function scanPayload(trackingNo: string, damaged: boolean, eventId: string = crypto.randomUUID()) {
    return {
        trackingNo: trackingNo.trim(),
        deviceEventId: eventId,
        conditionCode: damaged ? 'DAMAGED' : 'NORMAL',
    };
}

export function dispatchDraftPayload(values: DispatchDraftValues, trackingNumbers: string[]) {
    return {
        waveCode: values.waveCode.trim().toUpperCase(),
        serviceDate: values.serviceDate,
        routeCode: values.routeCode?.trim().toUpperCase() || null,
        driverId: values.driverId,
        trackingNumbers: [...new Set(trackingNumbers.map((value) => value.trim()).filter(Boolean))],
    };
}
