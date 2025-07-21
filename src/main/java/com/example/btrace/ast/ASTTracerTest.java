package com.example.btrace.ast;

public class ASTTracerTest {
    public static void main(String[] args) {
        ASTTracer tracer = new ASTTracer();
        
        String sourceCode = "public class SimpleTest { public static void main(String[] args) { int x = 5; int y = 10; int sum = x + y; System.out.println(\"Sum: \" + sum); } }";
        
        System.out.println("Testing AST Tracer...");
        
        ASTTracer.TraceResult result = tracer.executeAndTrace("SimpleTest", "main", sourceCode);
        
        System.out.println("Success: " + result.isSuccess());
        System.out.println("Message: " + result.getMessage());
        System.out.println("Trace events: " + result.getTrace().size());
        
        if (result.isSuccess()) {
            System.out.println("\n=== INSTRUMENTED CODE ===");
            System.out.println(result.getInstrumentedCode());
            
            System.out.println("\n=== RAW OUTPUT ===");
            System.out.println(result.getRawOutput());
            
            System.out.println("\n=== TRACE EVENTS ===");
            for (int i = 0; i < result.getTrace().size(); i++) {
                System.out.println("Event " + i + ": " + result.getTrace().get(i));
            }
        } else {
            System.out.println("Error: " + result.getMessage());
        }
    }
}
