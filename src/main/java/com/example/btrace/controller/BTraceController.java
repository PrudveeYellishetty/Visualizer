// src/main/java/com/example/btrace/controller/BTraceController.java
package com.example.btrace.controller;

import com.example.btrace.dto.TraceRequest;
import com.example.btrace.dto.TraceResponse;
import com.example.btrace.service.BTraceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trace")
@CrossOrigin(origins = "*") // Allow CORS for frontend integration
public class BTraceController {

    @Autowired
    private BTraceService btraceService;

    @PostMapping("/execute")
    public ResponseEntity<TraceResponse> executeTrace(@RequestBody TraceRequest request) {
        // Validate request
        if (request.getClassName() == null || request.getClassName().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(
                new TraceResponse(false, "Class name is required", null, null)
            );
        }
        
        if (request.getMethodName() == null || request.getMethodName().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(
                new TraceResponse(false, "Method name is required", null, null)
            );
        }
        
        if (request.getSourceCode() == null || request.getSourceCode().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(
                new TraceResponse(false, "Source code is required", null, null)
            );
        }

        try {
            TraceResponse response = btraceService.executeTrace(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(
                new TraceResponse(false, "Internal server error: " + e.getMessage(), null, null)
            );
        }
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("BTrace Visualizer API is running");
    }
}
