public class TestCase4 {

    public void testMethod() {
        for (int i = 0; i < 10; ) {  // Error: i could be final
        }
    }
}