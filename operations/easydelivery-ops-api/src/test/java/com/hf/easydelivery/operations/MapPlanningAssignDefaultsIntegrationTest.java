package com.hf.easydelivery.operations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hf.easydelivery.common.exception.GlobalExceptionHandler;
import com.hf.easydelivery.config.OperationsAccess;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.sql.DataSource;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MapPlanningAssignDefaultsIntegrationTest {

    private MockMvc mvc;
    private JdbcTemplate jdbc;
    private MapPlanningService planningService;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
        dataSource.setDriverClassName("org.h2.Driver");
        jdbc = new JdbcTemplate(dataSource);


        // 1. Prepare minimal schema for H2 integration test
        jdbc.execute("DROP TABLE IF EXISTS operation_audit_log, driver_task_item, driver_task_area, driver_task, dispatch_wave, driver_area_preference, delivery_area, driver, driver_shift, station, parcel_area_assignment, parcel, waybill;");

        jdbc.execute("""
                CREATE TABLE operation_audit_log (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    operator_user_id BIGINT NULL,
                    actor_type VARCHAR(32) DEFAULT 'OPERATOR',
                    actor_id BIGINT NULL,
                    station_id BIGINT NULL,
                    action_code VARCHAR(64) NOT NULL,
                    resource_type VARCHAR(64) NULL,
                    resource_id BIGINT NULL,
                    outcome VARCHAR(32) DEFAULT 'SUCCESS',
                    reason_text VARCHAR(255) NULL,
                    after_json VARCHAR(1000) NULL,
                    request_id VARCHAR(64) NULL,
                    occurred_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
                """);


        jdbc.execute("""
                CREATE TABLE station (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    station_code VARCHAR(64) NOT NULL UNIQUE,
                    default_capacity INT DEFAULT 200
                );
                """);

        jdbc.execute("""
                CREATE TABLE driver (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    home_station_id BIGINT NOT NULL,
                    credential_id VARCHAR(64) NOT NULL,
                    driver_name VARCHAR(64) NOT NULL,
                    status VARCHAR(32) DEFAULT 'ACTIVE',
                    is_test_driver TINYINT DEFAULT 0
                );
                """);

        jdbc.execute("""
                CREATE TABLE driver_shift (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    driver_id BIGINT NOT NULL,
                    service_date DATE NOT NULL,
                    parcel_capacity INT DEFAULT 200,
                    availability_status VARCHAR(32) DEFAULT 'AVAILABLE'
                );
                """);

        jdbc.execute("""
                CREATE TABLE delivery_area (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    station_id BIGINT NOT NULL,
                    area_code VARCHAR(64) NOT NULL,
                    status VARCHAR(32) DEFAULT 'ACTIVE'
                );
                """);

        jdbc.execute("""
                CREATE TABLE driver_area_preference (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    driver_id BIGINT NOT NULL,
                    delivery_area_id BIGINT NOT NULL,
                    priority INT DEFAULT 1,
                    status VARCHAR(32) DEFAULT 'ACTIVE',
                    effective_from DATE NULL,
                    effective_to DATE NULL
                );
                """);

        jdbc.execute("""
                CREATE TABLE dispatch_wave (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    station_id BIGINT NOT NULL,
                    wave_code VARCHAR(64) NOT NULL,
                    service_date DATE NOT NULL,
                    route_code VARCHAR(64) DEFAULT 'DEFAULT',
                    status VARCHAR(32) DEFAULT 'DRAFT',
                    version INT DEFAULT 1,
                    is_test TINYINT DEFAULT 0
                );
                """);

        jdbc.execute("""
                CREATE TABLE driver_task (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    wave_id BIGINT NOT NULL,
                    driver_id BIGINT NOT NULL,
                    station_id BIGINT NOT NULL,
                    task_code VARCHAR(64) NOT NULL,
                    service_date DATE NOT NULL,
                    status VARCHAR(32) DEFAULT 'DRAFT',
                    version INT DEFAULT 1,
                    is_test TINYINT DEFAULT 0,
                    CONSTRAINT uk_station_task_code UNIQUE (station_id, task_code)
                );
                """);

        jdbc.execute("""
                CREATE TABLE driver_task_item (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    task_id BIGINT NOT NULL,
                    parcel_id BIGINT NOT NULL,
                    stop_sequence INT DEFAULT 1,
                    item_status VARCHAR(32) DEFAULT 'ASSIGNED',
                    active_slot TINYINT DEFAULT 1,
                    is_test TINYINT DEFAULT 0,
                    CONSTRAINT uk_parcel_active_task UNIQUE (parcel_id, active_slot)
                );
                """);

        jdbc.execute("""
                CREATE TABLE driver_task_area (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    task_id BIGINT NOT NULL,
                    delivery_area_id BIGINT NOT NULL,
                    assignment_mode VARCHAR(32) DEFAULT 'WHOLE_AREA',
                    assigned_by VARCHAR(64) NULL
                );
                """);

        jdbc.execute("""
                CREATE TABLE waybill (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    external_waybill_no VARCHAR(64) NOT NULL,
                    is_test TINYINT DEFAULT 0
                );
                """);

        jdbc.execute("""
                CREATE TABLE operational_case (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    station_id BIGINT NULL,
                    parcel_id BIGINT NULL,
                    status VARCHAR(32) DEFAULT 'OPEN'
                );
                """);

        jdbc.execute("""
                CREATE TABLE parcel (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    waybill_id BIGINT NOT NULL,
                    tracking_no VARCHAR(64) NOT NULL UNIQUE,
                    current_station_id BIGINT NOT NULL,
                    current_area_id BIGINT NULL,
                    status VARCHAR(32) DEFAULT 'READY_FOR_DISPATCH',
                    is_test TINYINT DEFAULT 0
                );
                """);


        jdbc.execute("""
                CREATE TABLE parcel_area_assignment (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    parcel_id BIGINT NOT NULL,
                    delivery_area_id BIGINT NOT NULL,
                    ended_at TIMESTAMP NULL
                );
                """);

        // 2. Insert test seed data
        jdbc.execute("INSERT INTO station(id, station_code, default_capacity) VALUES (1, 'ST001', 100);");
        jdbc.execute("INSERT INTO driver(id, home_station_id, credential_id, driver_name, status) VALUES (101, 1, 'driver101', '张三', 'ACTIVE');");
        jdbc.execute("INSERT INTO delivery_area(id, station_id, area_code, status) VALUES (201, 1, 'AREA-01', 'ACTIVE');");
        jdbc.execute("INSERT INTO driver_area_preference(driver_id, delivery_area_id, priority, status) VALUES (101, 201, 1, 'ACTIVE');");
        jdbc.execute("INSERT INTO dispatch_wave(id, station_id, wave_code, service_date, status) VALUES (1001, 1, '20260725-WAVE-01', '2026-07-25', 'DRAFT');");

        // Insert 3 test parcels
        jdbc.execute("INSERT INTO waybill(id, external_waybill_no) VALUES (1, 'WB001'), (2, 'WB002'), (3, 'WB003');");
        jdbc.execute("INSERT INTO parcel(id, waybill_id, tracking_no, current_station_id, status) VALUES (301, 1, 'TRK001', 1, 'READY_FOR_DISPATCH'), (302, 2, 'TRK002', 1, 'READY_FOR_DISPATCH'), (303, 3, 'TRK003', 1, 'READY_FOR_DISPATCH');");
        jdbc.execute("INSERT INTO parcel_area_assignment(parcel_id, delivery_area_id) VALUES (301, 201), (302, 201), (303, 201);");

        // 3. Setup mock station context access
        OperationsAccess access = new OperationsAccess() {
            @Override public void requireStation(long stationId) {}
        };


        planningService = new MapPlanningService(jdbc, access, new ObjectMapper());

        OperationsController controller = new OperationsController(
                null, null, null, null, null, planningService, null, null, null, null, null
        );

        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }



    @Test
    @DisplayName("API级别系统测试: 连续3次触发一键指派，必须保持幂等成功且不抛出 Duplicate Entry 数据库报错")
    void testAssignDefaultsIdempotencyRepeatedCalls() throws Exception {
        // First Call: 成功指派 3 件包裹
        mvc.perform(post("/ops/v1/planning/waves/1001/assign-defaults")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.biz_code").value("COMMON.QUERY.SUCCESS"))
                .andExpect(jsonPath("$.biz_data.assignedCount").value(3));

        // 第二次重复点击：必须幂等响应，assignedCount 为 0，绝对不能抛出 500 / Duplicate entry
        mvc.perform(post("/ops/v1/planning/waves/1001/assign-defaults")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.biz_code").value("COMMON.QUERY.SUCCESS"))
                .andExpect(jsonPath("$.biz_data.assignedCount").value(0));

        // 第三次重复点击：依然幂等响应 200 OK
        mvc.perform(post("/ops/v1/planning/waves/1001/assign-defaults")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.biz_code").value("COMMON.QUERY.SUCCESS"))
                .andExpect(jsonPath("$.biz_data.assignedCount").value(0));


        // 物理数据库断言：driver_task_item 记录数严格为 3，Active Task 关联严格唯一
        Integer taskItemCount = jdbc.queryForObject("SELECT COUNT(*) FROM driver_task_item", Integer.class);
        assertEquals(3, taskItemCount);

        Integer taskCount = jdbc.queryForObject("SELECT COUNT(*) FROM driver_task", Integer.class);
        assertEquals(1, taskCount);
    }
}
