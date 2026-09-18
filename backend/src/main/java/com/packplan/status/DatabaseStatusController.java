package com.packplan.status;

import org.neo4j.driver.Driver;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.TransactionConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
public class DatabaseStatusController {
    private static final Logger log = LoggerFactory.getLogger(DatabaseStatusController.class);
    private final Driver driver;

    public DatabaseStatusController(Driver driver) {
        this.driver = driver;
    }

    @GetMapping("/api/database/status")
    public ResponseEntity<DatabaseStatus> status() {
        // A real query checks authentication and database access, not just an open port.
        try (var session = driver.session(SessionConfig.forDatabase("neo4j"))) {
            session.run("RETURN 1 AS connected", TransactionConfig.builder()
                    .withTimeout(Duration.ofSeconds(2)).build()).consume();
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .body(new DatabaseStatus("UP"));
        } catch (RuntimeException exception) {
            // Do not expose connection strings, credentials, or driver errors to clients.
            log.warn("Database readiness check failed ({})", exception.getClass().getSimpleName());
            return ResponseEntity.status(503).cacheControl(CacheControl.noStore())
                    .body(new DatabaseStatus("DOWN"));
        }
    }

    public record DatabaseStatus(String status) {}
}
