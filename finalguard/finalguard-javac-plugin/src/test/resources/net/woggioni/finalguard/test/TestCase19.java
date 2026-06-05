public class TestCase19 {
    private long id;

    @java.lang.SuppressWarnings("all")
    public TestCase19() {
    }

    @java.lang.SuppressWarnings("all")
    public long getId() {
        return this.id;
    }

    @java.lang.SuppressWarnings("all")
    public void setId(final long id) {
        this.id = id;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public boolean equals(final java.lang.Object o) {
        if (o == this) return true;
        if (!(o instanceof TestCase19)) return false;
        final TestCase19 other = (TestCase19) o;
        if (!other.canEqual((java.lang.Object) this)) return false;
        if (this.getId() != other.getId()) return false;
        return true;
    }

    @java.lang.SuppressWarnings("all")
    protected boolean canEqual(final java.lang.Object other) {
        return other instanceof TestCase19;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final long $id = this.getId();
        result = result * PRIME + (int) ($id >>> 32 ^ $id);
        return result;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public java.lang.String toString() {
        return "TestCase19(id=" + this.getId() + ")";
    }
}
