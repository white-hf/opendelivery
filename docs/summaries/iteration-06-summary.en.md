# I06 Failure, Return, and Redispatch Summary

I06 completes failure rules, Driver Attempt V2, task closeout, RETURN Session, station approval, and same-station redispatch. Reasons define photo/note evidence, next action, and maximum attempts. Attempts deduplicate by driver and idempotency key. Address failures open a Case without changing station.

Only the task driver can create, scan, and submit a RETURN Session. The driver retains custody until a supervisor at that station approves `REDISPATCH` or `RETURN_UPSTREAM`. Redispatch returns the Parcel to `READY_FOR_DISPATCH/STATION`, while an open address Case still blocks candidate inventory.

The real MySQL schema upgraded to V7. E2E verified address evidence, Attempt replay, address Case, owner return scan/submit, supervisor approval, custody return, and redispatch blocking. The Maven reactor passed.
