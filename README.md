# EasyDelivery App Demo Backend Service

This is a Spring Boot 3.3 monolithic backend demo service for the **EasyDelivery Android** application. It implements all the APIs defined in the Android SPI courier service interface (`ICourierService.java`).

## 1. Module Partition (Microservice Readiness)

To allow future microservice separation, the project is divided into five Maven modules:

*   **`easydelivery-common`**: Houses shared DTOs, response schemas, and the `MemoryDataStore` which acts as our in-memory data store.
*   **`easydelivery-auth`**: Handles driver credentials validation and session token generation.
*   **`easydelivery-delivery`**: Deals with package lists retrieval and proof-of-delivery (POD) photo uploads.
*   **`easydelivery-scan`**: Handles package barcode scanning, batch creation, scan reports, and batch reviews.
*   **`easydelivery-app`**: Bootstraps the Spring Boot application and contains configurations.

---

## 2. Technical Stack
*   **Language**: Java 17
*   **Framework**: Spring Boot 3.3.0
*   **Modularity**: Maven multi-module structure
*   **Database**: MySQL 8 with Flyway migrations. The `memory` profile remains available for isolated demo tests.

---

## 3. How to Run

### Option A: Import into IntelliJ IDEA (Recommended)
1. Open IntelliJ IDEA.
2. Select **Open** and choose the `/Users/whitetang/Desktop/Code/easydelivery_backend` directory.
3. IntelliJ will automatically detect the Maven modules.
4. Run `EasyDeliveryApplication` located under `easydelivery-app/src/main/java/com/hf/easydelivery/EasyDeliveryApplication.java`.

### Option B: Command Line (Requires Maven installed)
1. Navigate to the root directory:
   ```bash
   cd /Users/whitetang/Desktop/Code/easydelivery_backend
   ```
2. Export required secrets and build the project:
   ```bash
   export DB_PASSWORD='<local database password>'
   export JWT_SECRET='<at least 32 random characters>'
   export UPSTREAM_API_KEY='<upstream integration key>'
   export OPERATIONS_API_KEY='<operations key>'
   mvn clean package
   ```
3. Run the application:
   ```bash
   java -jar easydelivery-app/target/easydelivery-app-1.0.0.jar
   ```

The application starts on port **9000** (matching the default endpoint configuration of the Android application).

Flyway creates the production schema from `easydelivery-app/src/main/resources/db/migration`. Development-only reference data is in `scripts/db/002_development_seed.sql` and is never applied automatically.

---

## 4. API Endpoints Quick Reference

### Auth Module
*   `POST /auth/login` (Body: `{ "credential_id": "driver123", "password": "password123" }`)

### Delivery Module
*   `GET /delivery/parcels/tasks?criteria=UNSCANNED&driver_id={driverId}`
*   `GET /delivery/parcels/delivering?driver_id={driverId}`
*   `POST /delivery` (Multipart form parameters: `order_id`, `longitude`, `latitude`, `delivery_result`, and file `pod_images[]`)
*   `POST /delivery/retry` (Multipart form parameters: `order_id`, `longitude`, `latitude`, `driver_id`, and file `pod_img[]`)

### Scan Module
*   `POST /delivery/ext/scan` (Body: `{ "tracking_no": "...", "scan_batch_id": ... }`)
*   `POST /delivery/scan/batch` (Body: `{ "driver_id": ..., "operator_role": ..., "scan_as": ... }`)
*   `POST /delivery/scan/batch/report` (Body: `{ "scan_batch_id": ... }`)
*   `GET /delivery/ext/scan/batch/reports?warehouse={warehouse}&driver_id={driverId}&start_date={startDate}`
*   `PUT /delivery/ext/scan/batch/{scanBatchId}` (Body: `{ "status": "APPROVED" }`)
*   `GET /delivery/to-be-picked-up/brief/{driverId}`
