# I05 Dispatch and Load Handover Summary

I05 completes candidate inventory, Wave draft, publication, unscanned revoke, and driver load handover. Candidates are station-scoped and exclude unrouted pieces, non-station custody, invalid states, and open Cases. Draft creation locks and validates each piece; active-slot constraints prevent two active tasks. Publish revalidates inventory to catch changes after drafting.

Only the owning driver may create, scan, report, and submit a LOAD Session. Drivers may submit only `SUBMITTED`; direct `APPROVED` returns 401. A supervisor may approve only a submitted Session at the selected station. Before approval, Parcels remain `ASSIGNED/STATION`. Approval atomically changes Parcel to `OUT_FOR_DELIVERY/DRIVER`, updates Task Item and Task, and writes custody, status event, and outbox per piece. An unscanned published Wave can be revoked to `READY_FOR_DISPATCH`.

The real MySQL schema upgraded to V6. E2E verified two-piece publication, owner scanning, driver self-approval denial, supervisor approval, two custody/status records, and inventory restoration after revoking another Wave. The Maven reactor passed.

I06 next adds failure reasons, failed attempts, driver return Sessions, return approval, and redispatch.
