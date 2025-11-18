public class TestCase7 {

    public void testMethod() {
        try {

        } catch (RuntimeException re) { // Error: re should be final

        }
    }
}