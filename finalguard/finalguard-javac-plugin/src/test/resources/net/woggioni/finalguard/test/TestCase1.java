import java.util.Arrays;

public class TestCase1 {

    public void testMethod(String param1, String param2) {  // Warning: param1 could be final
        String localVar = "hello";  // Warning: localVar could be final
        String reassignedVar = "initial";  // No warning (reassigned below)
        reassignedVar = "changed";

        param2 = "modified";  // No warning for param2 (reassigned)

        for (int i = 0; i < 10; i++) {  // Warning: i could be final
            String loopVar = "constant";  // Warning: loopVar could be final
        }

        // Enhanced for loop - no warning for loop variable
        for (String item : Arrays.asList("a", "b")) {
            // item is effectively final in each iteration
        }
    }

    public void finalMethod(final String param1, String param2) {  // Warning only for param2
        final String localVar = "hello";  // No warning (already final)
        String anotherVar = "world";  // Warning: anotherVar could be final
    }
}