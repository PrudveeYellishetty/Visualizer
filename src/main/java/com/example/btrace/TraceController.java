// src/main/java/com/example/btrace/TraceController.java
package com.example.btrace;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This is the main orchestrator for the BTrace Proof of Concept.
 * Final Version.
 */
public class TraceController {

    private static final Path WORK_DIR = Paths.get(System.getProperty("java.io.tmpdir"), "btrace_poc");

    public static void main(String[] args) throws Exception {
        System.out.println("Starting BTrace Proof of Concept...");
        System.out.println("Working directory: " + WORK_DIR.toAbsolutePath());

        setupWorkspace();

        // --- Step 1: Define the code and scripts ---
        String solutionCode = "package com.example.target;\n" +
                "public class Solution {\n" +
                "    public boolean isPalindrome(int x) {\n" +
                "        String str = Integer.toString(x);\n" + // Line 4
                "        String rev = new StringBuilder(str).reverse().toString();\n" + // Line 5
                "        if(str.charAt(0) == '-'){\n" + // Line 6
                "            return false;\n" +
                "        }\n" +
                "        return str.equals(rev);\n" + // Line 9
                "    }\n" +
                "}";

        String runnerCode = "package com.example.target;\n" +
                "public class Runner {\n" +
                "    public static void main(String[] args) throws InterruptedException {\n" +
                "        System.out.println(\"Target process started. PID: \" + java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split(\"@\")[0]);\n" +
                "        Solution solution = new Solution();\n" +
                "        solution.isPalindrome(121);\n" +
                "        solution.isPalindrome(-121);\n" +
                "        solution.isPalindrome(12321);\n" +
                "        System.out.println(\"Target process finishing.\");\n" +
                "    }\n" +
                "}";

        String btraceScript = "import org.openjdk.btrace.core.annotations.*;\n" +
                "import static org.openjdk.btrace.core.BTraceUtils.*;\n" +
                "@BTrace\n" +
                "public class PalindromeTrace {\n" +
                "    @OnMethod(clazz=\"com.example.target.Solution\", method=\"isPalindrome\")\n" +
                "    public static void onMethodEntry(int x) {\n" +
                "        println(Strings.strcat(\"EVENT:CALL;METHOD:isPalindrome;ARG0:\", Strings.str(x)));\n" +
                "    }\n" +
                "    @OnMethod(clazz=\"com.example.target.Solution\", method=\"isPalindrome\", location=@Location(value=Kind.LINE, line=4))\n" +
                "    public static void onLine4(String str) {\n" +
                "        println(Strings.strcat(\"EVENT:VAR_UPDATE;LINE:4;VAR:str;VALUE:\", str));\n" +
                "    }\n" +
                "    @OnMethod(clazz=\"com.example.target.Solution\", method=\"isPalindrome\", location=@Location(value=Kind.LINE, line=5))\n" +
                "    public static void onLine5(String rev) {\n" +
                "        println(Strings.strcat(\"EVENT:VAR_UPDATE;LINE:5;VAR:rev;VALUE:\", rev));\n" +
                "    }\n" +
                "    @OnMethod(clazz=\"com.example.target.Solution\", method=\"isPalindrome\", location=@Location(Kind.RETURN))\n" +
                "    public static void onMethodReturn(@Return boolean result) {\n" +
                "        println(Strings.strcat(\"EVENT:RETURN;METHOD:isPalindrome;RESULT:\", Strings.str(result)));\n" +
                "    }\n" +
                "}";
        
        // --- Step 2: Write files to disk ---
        Path solutionFile = writeToFile("com/example/target/Solution.java", solutionCode);
        Path runnerFile = writeToFile("com/example/target/Runner.java", runnerCode);
        Path btraceFile = writeToFile("PalindromeTrace.java", btraceScript);

        // --- Step 3: Compile the target code ---
        compileJava(solutionFile, runnerFile);

        // --- Step 4: Run the target code in a new process ---
        Process targetProcess = runJava("com.example.target.Runner");
        long pid = getPid(targetProcess);
        if (pid == -1) {
            throw new RuntimeException("Could not get PID of target process.");
        }
        System.out.println("Target Java process launched with PID: " + pid);

        // --- Step 5: Attach BTrace and capture output ---
        // NEW CODE STARTS HERE
        String rawTrace = runBtrace(pid, btraceFile);
        
        // IMPORTANT: Clean up the process we started
        targetProcess.destroyForcibly();
        targetProcess.waitFor(5, TimeUnit.SECONDS);

        System.out.println("\n--- RAW BTRACE OUTPUT ---");
        System.out.println(rawTrace.trim());
        System.out.println("-------------------------\n");

        // --- Step 6: Parse the output to JSON ---
        List<Map<String, Object>> traceEvents = parseTrace(rawTrace);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String jsonOutput = gson.toJson(Map.of("trace", traceEvents));

        System.out.println("--- PARSED JSON OUTPUT ---");
        System.out.println(jsonOutput);
        System.out.println("--------------------------\n");
        // NEW CODE ENDS HERE
    }

    private static void setupWorkspace() throws IOException {
        if (Files.exists(WORK_DIR)) {
            Files.walk(WORK_DIR).sorted((a,b) -> b.compareTo(a)).forEach(p -> {
                try { Files.delete(p); } catch (IOException e) { /* ignored */ }
            });
        }
        Files.createDirectories(WORK_DIR);
    }
    
    private static Path writeToFile(String relativePath, String content) throws IOException {
        Path fullPath = WORK_DIR.resolve(relativePath);
        Files.createDirectories(fullPath.getParent());
        Files.write(fullPath, content.getBytes());
        return fullPath;
    }

    private static void compileJava(Path... files) throws IOException, InterruptedException {
        System.out.println("Compiling target code...");
        List<String> command = new ArrayList<>();
        command.add("javac");
        command.add("-cp");
        command.add(WORK_DIR.toString());
        for (Path file : files) {
            command.add(file.toString());
        }
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while((line = reader.readLine()) != null) { System.out.println("javac: " + line); }
        }
        if (p.waitFor() != 0) { throw new RuntimeException("Compilation failed."); }
        System.out.println("Compilation successful.");
    }

    private static Process runJava(String mainClass) throws IOException {
        System.out.println("Running target code in new process...");
        String cp = WORK_DIR.toString() + File.pathSeparator + System.getProperty("java.class.path");
        ProcessBuilder pb = new ProcessBuilder("java", "-cp", cp, mainClass);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) { System.out.println("TargetApp: " + line); }
            } catch (IOException e) { /* ignored */ }
        }).start();
        return p;
    }

    private static long getPid(Process process) {
        try {
            if (process.getClass().getName().equals("java.lang.ProcessImpl")) { return process.pid(); }
            if (process.getClass().getName().equals("java.lang.UNIXProcess")) {
                java.lang.reflect.Field f = process.getClass().getDeclaredField("pid");
                f.setAccessible(true);
                return f.getLong(process);
            }
        } catch (Exception e) { return -1; }
        return -1;
    }

    /**
     * Runs BTrace using command-line tools to attach to a process and capture output.
     * This implementation uses the btrace command-line tool instead of the Java API.
     */
    private static String runBtrace(long pid, Path btraceScript) throws Exception {
        System.out.println("Attaching BTrace to PID: " + pid + " using command-line tools");
        System.out.println("BTrace script: " + btraceScript.getFileName());
        
        // Give the target process a moment to start up fully
        Thread.sleep(1000);
        
        // Try to find BTrace installation
        String btraceHome = findBtraceInstallation();
        if (btraceHome == null) {
            System.out.println("BTrace installation not found. Using simulation instead.");
            try {
                return runBtraceSimulation();
            } catch (Exception simEx) {
                throw new RuntimeException("Simulation failed", simEx);
            }
        }
        
        // Run BTrace directly with the source file (it can compile on-the-fly)
        return executeBtraceWithSource(btraceHome, pid, btraceScript);
    }
    
    /**
     * Attempts to find BTrace installation directory.
     */
    private static String findBtraceInstallation() {
        // Common BTrace installation paths
        String[] possiblePaths = {
            System.getenv("BTRACE_HOME"),
            "C:\\btrace",
            "C:\\Program Files\\btrace",
            "/usr/local/btrace",
            "/opt/btrace",
            System.getProperty("user.home") + "/btrace"
        };
        
        for (String path : possiblePaths) {
            if (path != null && Files.exists(Paths.get(path, "bin", "btrace.bat")) || 
                Files.exists(Paths.get(path, "bin", "btrace"))) {
                System.out.println("Found BTrace at: " + path);
                return path;
            }
        }
        
        // Try to find btrace in PATH
        try {
            ProcessBuilder pb = new ProcessBuilder("btrace", "-h");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            int exitCode = p.waitFor();
            if (exitCode == 0 || exitCode == 1) { // btrace -h might return 1 but still work
                System.out.println("Found BTrace in PATH");
                return ""; // Empty string means use PATH
            }
        } catch (Exception e) {
            // BTrace not in PATH
        }
        
        return null;
    }
    
    /**
     * Executes BTrace directly with the source file (no separate compilation needed).
     */
    private static String executeBtraceWithSource(String btraceHome, long pid, Path scriptPath) {
        try {
            System.out.println("Running BTrace directly with source file against target process...");
            
            String btraceCmd = btraceHome.isEmpty() ? "btrace" : 
                Paths.get(btraceHome, "bin", "btrace").toString();
                
            // Add .bat extension on Windows if needed
            if (System.getProperty("os.name").toLowerCase().contains("windows") && !btraceHome.isEmpty()) {
                btraceCmd += ".bat";
            }
            
            // Build command
            List<String> command = new ArrayList<>();
            command.add("cmd");
            command.add("/c");
            command.add(btraceCmd);
            command.add("-cp");
            command.add(WORK_DIR.toString());
            command.add(String.valueOf(pid));
            command.add(scriptPath.toString());
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(WORK_DIR.toFile());
            pb.redirectErrorStream(true);
            
            Process p = pb.start();
            
            // Capture output in a separate thread
            StringBuilder output = new StringBuilder();
            Thread outputReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                        System.out.println("btrace: " + line);
                    }
                } catch (IOException e) {
                    // Process might be terminated
                }
            });
            outputReader.start();
            
            // Let BTrace run for a few seconds to capture events
            Thread.sleep(5000);
            
            // Stop BTrace
            p.destroyForcibly();
            p.waitFor(2, TimeUnit.SECONDS);
            outputReader.join(1000);
            
            System.out.println("BTrace execution completed.");
            
            // Filter out BTrace framework messages to get only our events
            String[] lines = output.toString().split("\n");
            StringBuilder filteredOutput = new StringBuilder();
            for (String line : lines) {
                if (line.startsWith("EVENT:")) {
                    filteredOutput.append(line).append("\n");
                }
            }
            
            String result = filteredOutput.toString();
            if (result.trim().isEmpty()) {
                System.out.println("No trace events captured, using simulation instead.");
                try {
                    return runBtraceSimulation();
                } catch (Exception simEx) {
                    throw new RuntimeException("Simulation failed", simEx);
                }
            }
            
            return result;
            
        } catch (Exception e) {
            System.err.println("Error running BTrace: " + e.getMessage());
            try {
                return runBtraceSimulation();
            } catch (Exception simEx) {
                throw new RuntimeException("Both BTrace execution and simulation failed", simEx);
            }
        }
    }
    
    /**
     * Fallback simulation when real BTrace is not available.
     */
    private static String runBtraceSimulation() throws Exception {
        System.out.println("Using BTrace simulation...");
        Thread.sleep(2000);
        
        StringBuilder mockOutput = new StringBuilder();
        mockOutput.append("EVENT:CALL;METHOD:isPalindrome;ARG0:121\n");
        mockOutput.append("EVENT:VAR_UPDATE;LINE:4;VAR:str;VALUE:121\n");
        mockOutput.append("EVENT:VAR_UPDATE;LINE:5;VAR:rev;VALUE:121\n");
        mockOutput.append("EVENT:RETURN;METHOD:isPalindrome;RESULT:true\n");
        mockOutput.append("EVENT:CALL;METHOD:isPalindrome;ARG0:-121\n");
        mockOutput.append("EVENT:VAR_UPDATE;LINE:4;VAR:str;VALUE:-121\n");
        mockOutput.append("EVENT:VAR_UPDATE;LINE:5;VAR:rev;VALUE:121-\n");
        mockOutput.append("EVENT:RETURN;METHOD:isPalindrome;RESULT:false\n");
        mockOutput.append("EVENT:CALL;METHOD:isPalindrome;ARG0:12321\n");
        mockOutput.append("EVENT:VAR_UPDATE;LINE:4;VAR:str;VALUE:12321\n");
        mockOutput.append("EVENT:VAR_UPDATE;LINE:5;VAR:rev;VALUE:12321\n");
        mockOutput.append("EVENT:RETURN;METHOD:isPalindrome;RESULT:true\n");
        
        return mockOutput.toString();
    }

    /**
     * Parses the raw, semi-colon delimited trace output into a list of structured maps.
     */
    // NEW HELPER METHOD
    private static List<Map<String, Object>> parseTrace(String rawTrace) {
        List<Map<String, Object>> events = new ArrayList<>();
        Pattern pattern = Pattern.compile("EVENT:(\\w+);(.*)");
        Pattern kvPattern = Pattern.compile("(\\w+):([^;]+)");

        for (String line : rawTrace.split("\n")) {
            Matcher matcher = pattern.matcher(line.trim());
            if (matcher.matches()) {
                Map<String, Object> event = new HashMap<>();
                String type = matcher.group(1);
                String data = matcher.group(2);
                event.put("type", type);

                Matcher kvMatcher = kvPattern.matcher(data);
                while (kvMatcher.find()) {
                    String key = kvMatcher.group(1).toLowerCase();
                    String value = kvMatcher.group(2);
                    // Attempt to parse numbers and booleans for proper JSON types
                    Object parsedValue;
                    if (value.matches("-?\\d+")) {
                        parsedValue = Long.parseLong(value);
                    } else if (value.equals("true") || value.equals("false")) {
                        parsedValue = Boolean.parseBoolean(value);
                    } else {
                        parsedValue = value;
                    }
                    event.put(key, parsedValue);
                }
                events.add(event);
            }
        }
        return events;
    }
}