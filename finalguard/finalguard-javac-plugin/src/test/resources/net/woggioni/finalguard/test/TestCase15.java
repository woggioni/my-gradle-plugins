import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class TestCase15 {
    public static void foo() {
        final InputStream source = null;
        final InputStream is = new FilterInputStream(source) {

            @Override
            public int read() throws IOException {
                int a = 5;
                return a;
            }
        };
    }

    static class Bar extends FilterInputStream {

        public Bar(InputStream source) {
            super(source);
        }
    };
}