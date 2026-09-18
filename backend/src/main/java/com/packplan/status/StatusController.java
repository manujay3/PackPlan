package com.packplan.status;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatusController {
    @GetMapping("/api/status")
    public ResponseEntity<AppStatus> status() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new AppStatus("PackPlan", "UP", 1, false));
    }

    public record AppStatus(String application, String status, int milestone, boolean catalogReady) {}
}
