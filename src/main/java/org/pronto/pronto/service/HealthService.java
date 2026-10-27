package org.pronto.pronto.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pronto.pronto.dto.HealthResponse;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Slf4j
@Service
@RequiredArgsConstructor
public class HealthService {

    private static final String UP = "UP";
    private static final String DOWN = "DOWN";

    private final DataSource dataSource;

    public HealthResponse check() {
        boolean dbUp = isDatabaseUp();
        return HealthResponse.builder()
                .status(dbUp ? UP : DOWN)
                .database(dbUp ? UP : DOWN)
                .build();
    }

    private boolean isDatabaseUp() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2);
        } catch (SQLException e) {
            log.warn("Database health check failed", e);
            return false;
        }
    }
}
