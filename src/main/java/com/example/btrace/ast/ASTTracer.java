package com.example.btrace.ast;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.google.gson.Gson;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * AST-based Java Code Tracer using JavaParser
 * Provides clean variable tracing without regex complications
 */
public class ASTTracer {
    
    private final JavaParser parser = new JavaParser();
    private final Gson gson = new Gson();
    private final List<Map<String, Object>> traceEvents = new ArrayList<>();
    
    public TraceResult executeAndTrace(String className, String methodName, String sourceCode) {
        try {
            // Clear previous traces
            traceEvents.clear();
            
            // Parse the source code into AST
            CompilationUnit cu = parser.parse(sourceCode).getResult().orElse(null);
            if (cu == null) {
                return TraceResult.error("Failed to parse source code");
            }
            
            // Instrument the AST with tracing
            ASTInstrumenter instrumenter = new ASTInstrumenter();
            cu.accept(instrumenter, null);
            
            // Generate instrumented source code
            String instrumentedCode = cu.toString();
            
            // DEBUG: Print the instrumented code
            System.out.println("=== INSTRUMENTED CODE DEBUG ===");
            System.out.println(instrumentedCode);
            System.out.println("=== END DEBUG ===");
            
            // Compile and execute
            String output = compileAndExecute(className, instrumentedCode);
            
            // Parse trace output
            parseTraceOutput(output);
            
            return TraceResult.success(traceEvents, output, instrumentedCode);
            
        } catch (Exception e) {
            return TraceResult.error("Execution failed: " + e.getMessage());
        }
    }
    
    private String compileAndExecute(String className, String sourceCode) throws Exception {
        Path tempDir = Files.createTempDirectory("java_trace");
        Path sourceFile = tempDir.resolve(className + ".java");
        
        try {
            // Write source to file
            Files.write(sourceFile, sourceCode.getBytes());
            
            // Compile with timeout
            ProcessBuilder compileProcess = new ProcessBuilder(
                "javac", "-cp", ".", sourceFile.toString()
            );
            compileProcess.directory(tempDir.toFile());
            compileProcess.redirectErrorStream(true);
            
            Process compile = compileProcess.start();
            boolean compileFinished = compile.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            
            if (!compileFinished) {
                compile.destroyForcibly();
                throw new RuntimeException("Compilation timeout after 10 seconds");
            }
            
            if (compile.exitValue() != 0) {
                String error = new String(compile.getInputStream().readAllBytes());
                System.err.println("=== COMPILATION ERROR ===");
                System.err.println("Source written to: " + sourceFile);
                System.err.println("Error: " + error);
                System.err.println("=== END COMPILATION ERROR ===");
                throw new RuntimeException("Compilation failed: " + error);
            }
            
            // Execute with timeout
            ProcessBuilder runProcess = new ProcessBuilder(
                "java", "-cp", ".", className
            );
            runProcess.directory(tempDir.toFile());
            runProcess.redirectErrorStream(true);
            
            Process run = runProcess.start();
            boolean runFinished = run.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            
            if (!runFinished) {
                run.destroyForcibly();
                throw new RuntimeException("Execution timeout after 5 seconds");
            }
            
            // Get output
            String output = new String(run.getInputStream().readAllBytes());
            return output;
            
        } finally {
            // Cleanup
            try {
                Files.deleteIfExists(tempDir.resolve(className + ".class"));
                Files.deleteIfExists(sourceFile);
                Files.deleteIfExists(tempDir);
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }
    
    private void parseTraceOutput(String output) {
        String[] lines = output.split("\n");
        for (String line : lines) {
            if (line.startsWith("TRACE:")) {
                try {
                    String jsonStr = line.substring(6); // Remove "TRACE:" prefix
                    @SuppressWarnings("unchecked")
                    Map<String, Object> event = gson.fromJson(jsonStr, Map.class);
                    if (event != null) {
                        traceEvents.add(event);
                    }
                } catch (Exception e) {
                    // Create error event for malformed traces
                    Map<String, Object> errorEvent = new HashMap<>();
                    errorEvent.put("event_type", "parse_error");
                    errorEvent.put("message", "Failed to parse trace: " + e.getMessage());
                    errorEvent.put("raw_line", line);
                    traceEvents.add(errorEvent);
                }
            }
        }
    }
    
    /**
     * AST Visitor that instruments code with tracing calls
     */
    private static class ASTInstrumenter extends ModifierVisitor<Void> {
        
        private boolean instrumentationAdded = false;
        private Set<String> tracedVariables = new HashSet<>();
        
        @Override
        public Visitable visit(ClassOrInterfaceDeclaration cls, Void arg) {
            if (!instrumentationAdded) {
                addTracingInfrastructure(cls);
                instrumentationAdded = true;
            }
            return super.visit(cls, arg);
        }
        
        @Override
        public Visitable visit(MethodDeclaration method, Void arg) {
            if ("main".equals(method.getNameAsString())) {
                // Add method entry trace at the beginning
                BlockStmt body = method.getBody().orElse(new BlockStmt());
                
                // Create method entry trace
                ExpressionStmt entryTrace = new ExpressionStmt(
                    new MethodCallExpr("__traceMethodEntry")
                        .addArgument(new StringLiteralExpr("main"))
                );
                
                body.getStatements().add(0, entryTrace);
                method.setBody(body);
            }
            
            return super.visit(method, arg);
        }
        
        @Override
        public Visitable visit(VariableDeclarationExpr varDecl, Void arg) {
            // Track declared variables - but NOT in our tracing methods
            Node parent = varDecl.getParentNode().orElse(null);
            while (parent != null) {
                if (parent instanceof MethodDeclaration) {
                    MethodDeclaration method = (MethodDeclaration) parent;
                    String methodName = method.getNameAsString();
                    // Skip instrumenting variables in our own tracing methods
                    if (methodName.startsWith("__trace") || methodName.equals("varsToJson")) {
                        return super.visit(varDecl, arg);
                    }
                    break;
                }
                parent = parent.getParentNode().orElse(null);
            }
            
            for (VariableDeclarator var : varDecl.getVariables()) {
                String varName = var.getNameAsString();
                tracedVariables.add(varName);
            }
            return super.visit(varDecl, arg);
        }
        
        @Override
        public Visitable visit(ExpressionStmt stmt, Void arg) {
            Expression expr = stmt.getExpression();
            
            // Check if we're inside a tracing method - if so, skip instrumentation
            Node parent = stmt.getParentNode().orElse(null);
            while (parent != null) {
                if (parent instanceof MethodDeclaration) {
                    MethodDeclaration method = (MethodDeclaration) parent;
                    String methodName = method.getNameAsString();
                    // Skip instrumenting inside our own tracing methods
                    if (methodName.startsWith("__trace") || methodName.equals("varsToJson")) {
                        return super.visit(stmt, arg);
                    }
                    break;
                }
                parent = parent.getParentNode().orElse(null);
            }
            
            // Handle variable assignments
            if (expr instanceof VariableDeclarationExpr) {
                VariableDeclarationExpr varDecl = (VariableDeclarationExpr) expr;
                for (VariableDeclarator var : varDecl.getVariables()) {
                    String varName = var.getNameAsString();
                    if (var.getInitializer().isPresent()) {
                        // Add trace call after variable declaration
                        BlockStmt parentBlock = findParentBlock(stmt);
                        if (parentBlock != null) {
                            int index = parentBlock.getStatements().indexOf(stmt);
                            ExpressionStmt traceCall = new ExpressionStmt(
                                new MethodCallExpr("__traceVariable")
                                    .addArgument(new StringLiteralExpr(varName))
                                    .addArgument(new NameExpr(varName))
                            );
                            parentBlock.getStatements().add(index + 1, traceCall);
                        }
                    }
                }
            } else if (expr instanceof AssignExpr) {
                AssignExpr assignment = (AssignExpr) expr;
                if (assignment.getTarget() instanceof NameExpr) {
                    String varName = ((NameExpr) assignment.getTarget()).getNameAsString();
                    if (tracedVariables.contains(varName)) {
                        // Add trace call after assignment
                        BlockStmt parentBlock = findParentBlock(stmt);
                        if (parentBlock != null) {
                            int index = parentBlock.getStatements().indexOf(stmt);
                            ExpressionStmt traceCall = new ExpressionStmt(
                                new MethodCallExpr("__traceVariable")
                                    .addArgument(new StringLiteralExpr(varName))
                                    .addArgument(new NameExpr(varName))
                            );
                            parentBlock.getStatements().add(index + 1, traceCall);
                        }
                    }
                }
            }
            
            return super.visit(stmt, arg);
        }
        
        private BlockStmt findParentBlock(Node node) {
            Node parent = node.getParentNode().orElse(null);
            while (parent != null) {
                if (parent instanceof BlockStmt) {
                    return (BlockStmt) parent;
                }
                parent = parent.getParentNode().orElse(null);
            }
            return null;
        }
        
        private void addTracingInfrastructure(ClassOrInterfaceDeclaration cls) {
            // Add step counter field
            cls.addFieldWithInitializer(int.class, "__stepCounter", new IntegerLiteralExpr("1"))
               .setPrivate(true).setStatic(true);
            
            // Add simple trace method using string concatenation instead of complex parsing
            try {
                // Simple trace method
                MethodDeclaration traceMethod = cls.addMethod("__trace", com.github.javaparser.ast.Modifier.Keyword.PRIVATE, com.github.javaparser.ast.Modifier.Keyword.STATIC);
                traceMethod.addParameter("String", "eventType");
                traceMethod.addParameter("String", "action");
                traceMethod.addParameter("java.util.Map<String, Object>", "vars");
                traceMethod.setType("void");
                
                BlockStmt traceBody = new BlockStmt();
                traceBody.addStatement("System.out.println(\"TRACE:{\\\"step\\\":\" + (__stepCounter++) + \",\\\"event_type\\\":\\\"\" + eventType + \"\\\",\\\"action\\\":\\\"\" + action + \"\\\",\\\"vars\\\":{\" + varsToJson(vars) + \"},\\\"timestamp\\\":\" + System.currentTimeMillis() + \"}\");");
                traceMethod.setBody(traceBody);
                
                // varsToJson method
                MethodDeclaration varsToJsonMethod = cls.addMethod("varsToJson", com.github.javaparser.ast.Modifier.Keyword.PRIVATE, com.github.javaparser.ast.Modifier.Keyword.STATIC);
                varsToJsonMethod.addParameter("java.util.Map<String, Object>", "vars");
                varsToJsonMethod.setType("String");
                
                BlockStmt varsBody = new BlockStmt();
                varsBody.addStatement("StringBuilder sb = new StringBuilder();");
                varsBody.addStatement("boolean first = true;");
                varsBody.addStatement("for (java.util.Map.Entry<String, Object> entry : vars.entrySet()) { if (!first) sb.append(\",\"); sb.append(\"\\\"\").append(entry.getKey()).append(\"\\\":\\\"\").append(entry.getValue()).append(\"\\\"\"); first = false; }");
                varsBody.addStatement("return sb.toString();");
                varsToJsonMethod.setBody(varsBody);
                
                // traceVariable method - simplified to avoid recursion
                MethodDeclaration traceVarMethod = cls.addMethod("__traceVariable", com.github.javaparser.ast.Modifier.Keyword.PRIVATE, com.github.javaparser.ast.Modifier.Keyword.STATIC);
                traceVarMethod.addParameter("String", "varName");
                traceVarMethod.addParameter("Object", "value");
                traceVarMethod.setType("void");
                
                BlockStmt varBody = new BlockStmt();
                varBody.addStatement("System.out.println(\"TRACE:{\\\"step\\\":\" + (__stepCounter++) + \",\\\"event_type\\\":\\\"variable_update\\\",\\\"action\\\":\\\"Variable \" + varName + \" = \" + value + \"\\\",\\\"vars\\\":{\\\"\" + varName + \"\\\":\\\"\" + value + \"\\\"},\\\"timestamp\\\":\" + System.currentTimeMillis() + \"}\");");
                traceVarMethod.setBody(varBody);
                
                // traceMethodEntry method - simplified to avoid recursion
                MethodDeclaration traceEntryMethod = cls.addMethod("__traceMethodEntry", com.github.javaparser.ast.Modifier.Keyword.PRIVATE, com.github.javaparser.ast.Modifier.Keyword.STATIC);
                traceEntryMethod.addParameter("String", "methodName");
                traceEntryMethod.setType("void");
                
                BlockStmt entryBody = new BlockStmt();
                entryBody.addStatement("System.out.println(\"TRACE:{\\\"step\\\":\" + (__stepCounter++) + \",\\\"event_type\\\":\\\"method_entry\\\",\\\"action\\\":\\\"Entering method \" + methodName + \"\\\",\\\"vars\\\":{\\\"method\\\":\\\"\" + methodName + \"\\\"},\\\"timestamp\\\":\" + System.currentTimeMillis() + \"}\");");
                traceEntryMethod.setBody(entryBody);
                
            } catch (Exception e) {
                System.err.println("Failed to add tracing methods: " + e.getMessage());
                // Add minimal fallback
                cls.addFieldWithInitializer(String.class, "__TRACE_ERROR", new StringLiteralExpr("Tracing failed: " + e.getMessage()))
                   .setPrivate(true).setStatic(true);
            }
        }
    }
    
    /**
     * Result class for trace execution
     */
    public static class TraceResult {
        private final boolean success;
        private final String message;
        private final List<Map<String, Object>> trace;
        private final String rawOutput;
        private final String instrumentedCode;
        
        private TraceResult(boolean success, String message, List<Map<String, Object>> trace, 
                           String rawOutput, String instrumentedCode) {
            this.success = success;
            this.message = message;
            this.trace = trace != null ? trace : new ArrayList<>();
            this.rawOutput = rawOutput;
            this.instrumentedCode = instrumentedCode;
        }
        
        public static TraceResult success(List<Map<String, Object>> trace, String rawOutput, String instrumentedCode) {
            return new TraceResult(true, "Trace completed successfully", trace, rawOutput, instrumentedCode);
        }
        
        public static TraceResult error(String message) {
            return new TraceResult(false, message, null, null, null);
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public List<Map<String, Object>> getTrace() { return trace; }
        public String getRawOutput() { return rawOutput; }
        public String getInstrumentedCode() { return instrumentedCode; }
    }
}
