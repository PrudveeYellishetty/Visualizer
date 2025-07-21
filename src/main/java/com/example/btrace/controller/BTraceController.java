// src/main/java/com/example/btrace/controller/BTraceController.java
package com.example.btrace.controller;

import com.example.btrace.ast.ASTTracer;
import com.example.btrace.ast.ASTTracer.TraceResult;
import com.example.btrace.dto.TraceRequest;
import com.example.btrace.dto.TraceResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;

@RestController
@RequestMapping("/api/trace")
@CrossOrigin(origins = "*") // Allow CORS for frontend integration
public class BTraceController {

    private final ASTTracer astTracer = new ASTTracer();

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
            // Execute trace using AST-based approach
            TraceResult result = astTracer.executeAndTrace(
                request.getClassName(), 
                request.getMethodName(), 
                request.getSourceCode()
            );
            
            if (result.isSuccess()) {
                return ResponseEntity.ok(new TraceResponse(
                    true, 
                    "AST-based trace completed with " + result.getTrace().size() + " events",
                    result.getTrace(),
                    result.getRawOutput()
                ));
            } else {
                return ResponseEntity.ok(new TraceResponse(
                    false, 
                    result.getMessage(),
                    new ArrayList<>(),
                    null
                ));
            }
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(new TraceResponse(
                false, 
                "AST trace failed: " + e.getMessage(),
                new ArrayList<>(),
                null
            ));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("BTrace AST service is running!");
    }
}
