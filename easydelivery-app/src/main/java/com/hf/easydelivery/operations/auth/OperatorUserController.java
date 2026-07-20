package com.hf.easydelivery.operations.auth;

import com.hf.easydelivery.common.exception.BizException;
import com.hf.easydelivery.common.response.AppResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@Profile("!memory")
@RequestMapping("/ops/v1/users")
public class OperatorUserController {
    private final JdbcTemplate jdbc;

    public OperatorUserController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping
    public AppResponse<?> users() {
        return AppResponse.success(jdbc.queryForList("""
                SELECT u.id,u.username,u.display_name,s.station_code,u.status,
                       GROUP_CONCAT(r.role_code ORDER BY r.role_code) roles
                FROM operator_user u LEFT JOIN station s ON s.id=u.default_station_id
                LEFT JOIN operator_user_role ur ON ur.user_id=u.id LEFT JOIN operator_role r ON r.id=ur.role_id
                GROUP BY u.id,u.username,u.display_name,s.station_code,u.status ORDER BY u.username
                """));
    }

    @PostMapping
    @Transactional
    public AppResponse<?> create(@RequestBody CreateOperatorRequest request) {
        if (request.password() == null || request.password().length() < 8) {
            throw new BizException("OPERATOR.PASSWORD.WEAK", "Operator password must contain at least 8 characters");
        }
        List<Long> stations = jdbc.query("SELECT id FROM station WHERE station_code=? AND status='ACTIVE'",
                (rs,n)->rs.getLong(1), request.stationCode().toUpperCase());
        List<Long> roles = jdbc.query("SELECT id FROM operator_role WHERE role_code=?",
                (rs,n)->rs.getLong(1), request.roleCode().toUpperCase());
        if (stations.isEmpty()) throw new BizException("STATION.NOT.FOUND", "Active station not found");
        if (roles.isEmpty()) throw new BizException("OPERATOR.ROLE.NOT.FOUND", "Operator role not found");
        jdbc.update("""
                INSERT INTO operator_user(username,password_hash,display_name,default_station_id,status)
                VALUES (?, ?, ?, ?, 'ACTIVE')
                """, request.username(), BCrypt.hashpw(request.password(), BCrypt.gensalt()), request.displayName(), stations.get(0));
        Long userId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbc.update("INSERT INTO operator_user_role(user_id,role_id) VALUES (?,?)", userId, roles.get(0));
        return AppResponse.success("Operator created", Map.of("userId", userId));
    }

    public record CreateOperatorRequest(String username,String password,String displayName,String stationCode,String roleCode) {}
}
