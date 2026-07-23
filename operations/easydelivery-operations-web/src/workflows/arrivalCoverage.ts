export type UnitLinkedCount={id:number;linked_piece_count:number};
export type UnitParcelRef={unit_id:number};

/** Returns the parcel rows of one handling unit, or every row when no unit is selected. */
export function parcelsOfUnit<T extends UnitParcelRef>(parcels:T[]|undefined,unitId:number|undefined):T[]{
 if(!parcels)return[];
 return unitId===undefined?parcels:parcels.filter(p=>p.unit_id===unitId);
}

/** DoD gate surfaced in the UI: per-unit linked counters must equal the parcel-detail rollup. */
export function aggregateEqualsDetail(units:UnitLinkedCount[],parcels:UnitParcelRef[]):boolean{
 const counts=new Map<number,number>();
 for(const parcel of parcels)counts.set(parcel.unit_id,(counts.get(parcel.unit_id)??0)+1);
 return units.every(unit=>(counts.get(unit.id)??0)===unit.linked_piece_count);
}
