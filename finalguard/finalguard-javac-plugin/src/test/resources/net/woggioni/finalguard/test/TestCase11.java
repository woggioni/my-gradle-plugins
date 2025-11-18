import java.util.Arrays;

public abstract class TestCase11<T1> {

    abstract T1 get(int n);

    public T1[] toArray(T1[] t1s) {
        int size = 42;
        T1[] result = Arrays.copyOf(t1s, size);
        for (int i = 0; i < size; i++) {
            result[i] = get(i);
        }
        return result;
    }
}