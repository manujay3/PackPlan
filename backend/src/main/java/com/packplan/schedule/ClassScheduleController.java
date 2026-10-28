package com.packplan.schedule;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.regex.Pattern;

@RestController
public class ClassScheduleController {
    private static final Pattern COURSE_CODE = Pattern.compile("(?i)^[A-Z]{1,4}\\s*\\d{3}[A-Z]?$" );
    private final ClassScheduleService service;

    public ClassScheduleController(ClassScheduleService service) { this.service = service; }

    @GetMapping("/api/schedule/sections")
    public ResponseEntity<?> sections(@RequestParam String courseCode, @RequestParam(defaultValue = "2271") String term) {
        if (!COURSE_CODE.matcher(courseCode.trim()).matches()) return ResponseEntity.badRequest().body(new ApiError("Enter a course code such as CSC 116."));
        if (!term.equals("2271")) return ResponseEntity.badRequest().body(new ApiError("Only the published Spring 2027 schedule is supported."));
        try {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.findSections(courseCode, term));
        } catch (RuntimeException exception) {
            return ResponseEntity.status(502).cacheControl(CacheControl.noStore())
                    .body(new ApiError("NC State's public class search is temporarily unavailable. Retry or use Class Search directly."));
        }
    }

    public record ApiError(String message) {}
}
