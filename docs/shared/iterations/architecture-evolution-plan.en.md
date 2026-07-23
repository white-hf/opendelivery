# Architecture Evolution Plan: Decoupling Dual-Backend Services

> Status: `REVIEWED` (2026-07-23)  
> Goal: Decouple Driver API, Operations API, and Operations Web into clear, independent services while maintaining 100% contract and test compatibility.

---

## 1. Objectives

1. Decouple Driver API (Port 9000) and Operations API (Port 9001) into separate Spring Boot runnable modules.
2. Preserve `easydelivery-common` for shared entities, Flyway migrations, and DTOs.
3. Keep `easydelivery-app` as a legacy entry point for backward compatibility.
4. Update `run.sh` and Docker setup to seamlessly support both monolithic and multi-service deployment modes.

---

## 2. DoD (Definition of Done)

* [x] Architecture evolution plan document reviewed (`REVIEWED`)
* [ ] Create independent `easydelivery-driver-api` and `easydelivery-ops-api` modules
* [ ] Update `run.sh` build and execution options
* [ ] Ensure all 49+ JUnit tests pass (`./run.sh test`)
* [ ] Complete execution summary document
