import java.util.Arrays;

public class TestCase6 {

    public void testMethod() {
        for (String item : Arrays.asList("a", "b")) { // Error: item should be final
        }
    }
}