import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public record TestCase17(String s) {
    TestCase17(double a) {
        this("");
    }
}