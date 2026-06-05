import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public record TestCase16(double a, double b) {
    TestCase16(double c) {
        this(c, 2.0);

        int d = 42;
        System.out.println(d);
    }

    double foo(double e) {
        int f = 42;
        System.out.println(f);
        return a;
    }
}