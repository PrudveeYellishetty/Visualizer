import com.example.btrace.instrumentation.SourceCodeInstrumentor;
import java.io.IOException;

public class TestTraceDebug {
    public static void main(String[] args) throws IOException {
        String sourceCode = "public class SimpleTest { public static void main(String[] args) { int x = 5; int y = 10; int sum = x + y; System.out.println(\"Sum: \" + sum); } }";
        
        SourceCodeInstrumentor instrumentor = new SourceCodeInstrumentor();
        String output = instrumentor.executeInstrumentedCode("SimpleTest", "main", sourceCode);
        
        System.out.println("=== RAW OUTPUT ===");
        System.out.println(output);
        System.out.println("=== END RAW OUTPUT ===");
        
        // Split by lines to see each line
        String[] lines = output.split("\n");
        for (int i = 0; i < lines.length; i++) {
            System.out.println("Line " + i + ": [" + lines[i] + "]");
        }
    }
}
