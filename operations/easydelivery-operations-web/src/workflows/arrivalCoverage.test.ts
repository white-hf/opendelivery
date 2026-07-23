import {describe,expect,it} from 'vitest';
import {aggregateEqualsDetail,parcelsOfUnit} from './arrivalCoverage';

describe('parcelsOfUnit',()=>{
 const parcels=[{unit_id:1,tracking_no:'A'},{unit_id:2,tracking_no:'B'},{unit_id:1,tracking_no:'C'}];
 it('returns every parcel when no unit is selected',()=>{
  expect(parcelsOfUnit(parcels,undefined)).toHaveLength(3);
 });
 it('filters parcels by unit',()=>{
  expect(parcelsOfUnit(parcels,1).map(p=>p.tracking_no)).toEqual(['A','C']);
 });
 it('handles missing data',()=>{
  expect(parcelsOfUnit(undefined,1)).toEqual([]);
 });
});

describe('aggregateEqualsDetail',()=>{
 it('accepts counters that match the parcel detail',()=>{
  expect(aggregateEqualsDetail(
   [{id:1,linked_piece_count:2},{id:2,linked_piece_count:1}],
   [{unit_id:1},{unit_id:1},{unit_id:2}])).toBe(true);
 });
 it('rejects counters that disagree with the detail',()=>{
  expect(aggregateEqualsDetail([{id:1,linked_piece_count:3}],[{unit_id:1}])).toBe(false);
 });
 it('treats a unit without parcel rows as zero',()=>{
  expect(aggregateEqualsDetail([{id:9,linked_piece_count:0}],[])).toBe(true);
 });
});
