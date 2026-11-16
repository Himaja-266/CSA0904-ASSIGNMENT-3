import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

public class ExamDemo {
    static class ExamException extends Exception {
        ExamException(String message) { super(message); }
        ExamException(String message, Throwable cause) { super(message, cause); }
    }
    static final class ExamWindowClosedException extends ExamException {
        ExamWindowClosedException(String message) { super(message); }
    }
    static final class NotRegisteredException extends ExamException {
        NotRegisteredException(String message) { super(message); }
    }
    static final class FileTooLargeException extends ExamException {
        FileTooLargeException(String message) { super(message); }
    }
    static final class SubmissionStorageException extends ExamException {
        SubmissionStorageException(String message, Throwable cause) { super(message, cause); }
    }

    static final class Storage {
        void save(String fileName) throws SubmissionStorageException {
            try {
                if (fileName.equals("answer02.pdf")) throw new IOException("Disk quota exceeded");
                System.out.println("Saved " + fileName);
            } catch (IOException e) {
                throw new SubmissionStorageException("Could not store " + fileName, e);
            }
        }
    }
    static final class Service {
        private final Storage storage = new Storage();
        void submit(String student, String file, long bytes, boolean windowOpen, boolean registered)
                throws ExamException {
            if (!windowOpen) throw new ExamWindowClosedException("Exam window is closed");
            if (!registered) throw new NotRegisteredException("Student is not registered");
            if (bytes > 10_000_000) throw new FileTooLargeException("Submission exceeds 10 MB");
            storage.save(file);
        }
    }
    static final class Controller {
        private final Service service = new Service();
        void handle(String student, String file, long bytes, boolean open, boolean registered) {
            try {
                service.submit(student, file, bytes, open, registered);
            } catch (ExamException e) {
                System.out.println("Student sees: " + e.getMessage());
                if (e.getCause() != null) System.out.println("Cause: " + e.getCause().getMessage());
            }
        }
    }

    static long captureAtDepth(int depth) {
        if (depth == 0) {
            Exception e = new Exception("benchmark");
            e.fillInStackTrace();
            return e.getStackTrace().length;
        }
        return captureAtDepth(depth - 1);
    }

    static void benchmark() {
        final int warmup = 1_000, rounds = 10, count = 1_000;
        for (int i = 0; i < warmup; i++) captureAtDepth(1);
        System.out.println("Stack capture benchmark (ns/op; " + rounds + " measured rounds)");
        System.out.println("round,depth_1,depth_8,depth_32");
        for (int r = 1; r <= rounds; r++) {
            long[] times = new long[3];
            int[] depths = {1, 8, 32};
            for (int j = 0; j < depths.length; j++) {
                int depth = depths[j];
                long start = System.nanoTime();
                for (int n = 0; n < count; n++) captureAtDepth(depth);
                times[j] = System.nanoTime() - start;
            }
            System.out.printf("%d,%.1f,%.1f,%.1f%n", r, times[0] / (double) count, times[1] / (double) count, times[2] / (double) count);
        }
    }

    public static void main(String[] args) throws Exception {
        Controller controller = new Controller();
        controller.handle("s1", "answer01.pdf", 1024, true, true);
        controller.handle("s2", "answer02.pdf", 1024, true, true);
        controller.handle("s3", "answer03.pdf", 1024, false, true);
        controller.handle("s4", "answer04.pdf", 1024, true, false);
        controller.handle("s5", "answer05.pdf", 20_000_000, true, true);
        System.out.println("--- StorageDemo exact-output section ---");
        Storage storage = new Storage();
        storage.save("answer01.pdf");
        try {
            storage.save("answer02.pdf");
        } catch (SubmissionStorageException e) {
            System.out.println("Error: " + e.getMessage());
            System.out.println("Cause: " + e.getCause().getMessage());
            StringWriter trace = new StringWriter();
            e.printStackTrace(new PrintWriter(trace));
            System.out.println("--- Chained stack trace ---");
            System.out.print(trace);
        }
        try {
            SubmissionStorageException lost = new SubmissionStorageException("Could not store answer02.pdf", null);
            System.out.println(lost.getCause().getMessage());
        } catch (NullPointerException e) {
            System.out.println("Without cause: getCause() is null; dereferencing it throws NullPointerException");
        }
        System.out.println("Unchecked alternative: extend RuntimeException; remove throws/catch declarations where propagation is intentional.");
        benchmark();
    }
}
