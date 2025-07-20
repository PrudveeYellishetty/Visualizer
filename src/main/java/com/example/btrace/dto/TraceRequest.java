// src/main/java/com/example/btrace/dto/TraceRequest.java
package com.example.btrace.dto;

import java.util.List;

public class TraceRequest {
    private String className;
    private String methodName;
    private String sourceCode;
    private List<String> testInputs;
    
    // Constructors
    public TraceRequest() {}
    
    public TraceRequest(String className, String methodName, String sourceCode, List<String> testInputs) {
        this.className = className;
        this.methodName = methodName;
        this.sourceCode = sourceCode;
        this.testInputs = testInputs;
    }
    
    // Getters and Setters
    public String getClassName() {
        return className;
    }
    
    public void setClassName(String className) {
        this.className = className;
    }
    
    public String getMethodName() {
        return methodName;
    }
    
    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }
    
    public String getSourceCode() {
        return sourceCode;
    }
    
    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }
    
    public List<String> getTestInputs() {
        return testInputs;
    }
    
    public void setTestInputs(List<String> testInputs) {
        this.testInputs = testInputs;
    }
}
