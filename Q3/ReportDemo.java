import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ReportDemo {
    private static final Logger LOG = Logger.getLogger(ReportDemo.class.getName());
    static int opened, closed;

    static final class FailingClose implements AutoCloseable {
        public void close() throws IOException { throw new IOException("close also failed"); }
    }

    static final class TrackedReader implements AutoCloseable {
        final BufferedReader reader;
        TrackedReader(Path path) throws IOException {
            reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
            opened++;
        }
        String readLine() throws IOException { return reader.readLine(); }
        public void close() throws IOException { try { reader.close(); } finally { closed++; } }
    }

    static void makeInputs(Path dir) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("north.csv"), "store,sales\nNorth,120\n", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("south.csv"), "store,sales\nSouth,80\n", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("bad.csv"), "store,sales\nBad,not-a-number\n", StandardCharsets.UTF_8);
    }

    static int parse(TrackedReader reader, Path file) throws IOException {
        String header = reader.readLine();
        String row = reader.readLine();
        if (header == null || row == null) throw new IOException("Missing report row");
        String[] parts = row.split(",");
        try { return Integer.parseInt(parts[1]); }
        catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Invalid sales in file " + file.getFileName(), e);
            throw new IOException("Invalid sales in " + file.getFileName(), e);
        }
    }

    static int tryWithResources(List<Path> files, Path output) throws IOException {
        int total = 0;
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            writer.write("store,total\n");
            for (Path file : files) {
                try (TrackedReader reader = new TrackedReader(file)) {
                    total += parse(reader, file);
                    writer.write(file.getFileName().toString() + "," + total + "\n");
                }
            }
        }
        return total;
    }

    static int manualClose(List<Path> files, Path output, boolean finallyClose) throws IOException {
        int total = 0;
        BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8);
        try {
            writer.write("store,total\n");
            for (Path file : files) {
                TrackedReader reader = new TrackedReader(file);
                try {
                    total += parse(reader, file);
                    writer.write(file.getFileName().toString() + "," + total + "\n");
                } finally {
                    if (finallyClose) reader.close();
                }
            }
        } finally {
            writer.close();
        }
        return total;
    }

    public static void main(String[] args) throws Exception {
        Path input = Path.of("input");
        makeInputs(input);
        List<Path> good = List.of(input.resolve("north.csv"), input.resolve("south.csv"));
        Path manual = Path.of("manual-report.csv"), managed = Path.of("twr-report.csv");
        int a = manualClose(good, manual, true);
        int b = tryWithResources(good, managed);
        System.out.println("Manual total=" + a + "; try-with-resources total=" + b);
        System.out.println("Reports identical: " + Files.readString(manual).equals(Files.readString(managed)));

        List<Path> failing = List.of(input.resolve("north.csv"), input.resolve("bad.csv"), input.resolve("south.csv"));
        opened = closed = 0;
        try { manualClose(failing, Path.of("manual-failure.csv"), false); }
        catch (IOException e) { System.out.println("Manual failure: " + e.getMessage()); }
        System.out.println("Manual failing run opened=" + opened + ", closed=" + closed);
        opened = closed = 0;
        try { tryWithResources(failing, Path.of("twr-failure.csv")); }
        catch (IOException e) { System.out.println("TWR failure: " + e.getMessage()); }
        System.out.println("TWR failing run opened=" + opened + ", closed=" + closed);
        try (FailingClose ignored = new FailingClose()) {
            throw new IOException("processing failed");
        } catch (IOException e) {
            System.out.println("Primary failure: " + e.getMessage());
            System.out.println("Suppressed failure: " + e.getSuppressed()[0].getMessage());
        }
        System.out.println("The no-finally leak is an intentional controlled demonstration; normal manual mode uses finallyClose=true.");
    }
}
