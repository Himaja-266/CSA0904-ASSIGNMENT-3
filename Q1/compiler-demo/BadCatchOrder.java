import java.io.IOException;

class BadCatchOrder {
    void invalid() throws IOException {
        try {
            throw new IOException("demo");
        } catch (Exception broad) {
            System.out.println(broad.getMessage());
        } catch (IOException narrow) {
            System.out.println(narrow.getMessage());
        }
    }
}
