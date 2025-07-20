// src/main/java/com/example/btrace/dto/TraceResponse.java
package com.example.btrace.dto;

import java.util.List;
import java.util.Map;

public class TraceResponse {
    private boolean success;
    private String message;
    private List<Map<String, Object>> trace;
    private String rawOutput;
    
    // Constructors
    public TraceResponse() {}
    
    public TraceResponse(boolean success, String message, List<Map<String, Object>> trace, String rawOutput) {
        this.success = success;
        this.message = message;
        this.trace = trace;
        this.rawOutput = rawOutput;
    }
    
    // Getters and Setters
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public List<Map<String, Object>> getTrace() {
        return trace;
    }
    
    public void setTrace(List<Map<String, Object>> trace) {
        this.trace = trace;
    }
    
    public String getRawOutput() {
        return rawOutput;
    }
    
    public void setRawOutput(String rawOutput) {
        this.rawOutput = rawOutput;
    }
}
