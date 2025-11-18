public class TestCase5 {

    public void testMethod() {
        for (int i = 0; i < 10; i++) {
            String loopVar = "constant";  // Error: loopVar should be final
        }
    }
}