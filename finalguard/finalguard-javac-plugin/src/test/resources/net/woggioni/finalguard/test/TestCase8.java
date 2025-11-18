import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class TestCase8 {

    public void testMethod() throws IOException {
        try (InputStream is = Files.newInputStream(Path.of("some-path"))) { // Error: is could be final

        }
    }
}