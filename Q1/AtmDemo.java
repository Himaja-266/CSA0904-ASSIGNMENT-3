import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.InputMismatchException;

public class AtmDemo {
    static final class InsufficientFundsException extends Exception {
        InsufficientFundsException(String message) { super(message); }
    }

    static int withdraw(int balance, int amount) throws InsufficientFundsException {
        if (amount < 0) throw new IllegalArgumentException("Amount must not be negative");
        if (amount > balance) throw new InsufficientFundsException("Insufficient funds");
        return balance - amount;
    }

    static int parseAmount(String input) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            throw new InputMismatchException("Amount must be an integer");
        }
    }

    static int checkedWithdrawal(String input, int balance) throws InsufficientFundsException {
        return withdraw(balance, parseAmount(input));
    }

    static void readCard(Path card) throws IOException {
        try (InputStream ignored = new FileInputStream(card.toFile())) {
            ignored.read();
        }
    }

    static void uncaughtExample() {
        throw new IllegalStateException("simulated unrecoverable card-reader state");
    }

    static void report(String label, String input, int balance) {
        System.out.println("Case: " + label);
        try {
            int remaining = checkedWithdrawal(input, balance);
            System.out.println("Approved; balance=" + remaining);
        } catch (InputMismatchException | IllegalArgumentException e) {
            System.out.println("Input error: " + e.getMessage());
        } catch (InsufficientFundsException e) {
            System.out.println("Declined: " + e.getMessage());
        } catch (RuntimeException e) {
            System.out.println("Unexpected runtime error: " + e);
        } finally {
            System.out.println("Transaction cleanup complete");
        }
    }

    static long nanos(Runnable action, int repetitions) {
        long start = System.nanoTime();
        for (int i = 0; i < repetitions; i++) action.run();
        return System.nanoTime() - start;
    }

    static long throwPath() {
        try {
            parseAmount("x");
            return 0;
        } catch (InputMismatchException expected) {
            return expected.getStackTrace().length;
        }
    }

    static int codePath(String text) {
        if (text == null || text.isEmpty()) return -1;
        int index = text.charAt(0) == '-' ? 1 : 0;
        if (index == text.length()) return -1;
        long value = 0;
        for (; index < text.length(); index++) {
            char digit = text.charAt(index);
            if (digit < '0' || digit > '9') return -1;
            value = value * 10 + digit - '0';
            if (value > Integer.MAX_VALUE) return -1;
        }
        return text.charAt(0) == '-' ? -1 : (int) value;
    }

    static void benchmark() {
        final int warmup = 2_000, rounds = 10, operations = 2_000;
        for (int i = 0; i < warmup; i++) { throwPath(); codePath("x"); }
        System.out.println("Benchmark (ns/op; " + rounds + " measured rounds)");
        System.out.println("round,throw_exception_ns_op,error_code_ns_op");
        for (int r = 1; r <= rounds; r++) {
            long throwing = nanos(() -> throwPath(), operations);
            long codes = nanos(() -> codePath("x"), operations);
            System.out.printf("%d,%.1f,%.1f%n", r, throwing / (double) operations, codes / (double) operations);
        }
    }

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("uncaught")) uncaughtExample();
        System.out.println("ATM tests (balance=100)");
        report("valid", "30", 100);
        report("non-numeric", "abc", 100);
        report("negative", "-5", 100);
        report("insufficient funds", "101", 100);
        report("integer overflow", "999999999999999999", 100);
        System.out.println("Case: missing card");
        try {
            readCard(Path.of("missing-card.txt"));
        } catch (FileNotFoundException e) {
            System.out.println("Card error: FileNotFoundException (checked)");
        } catch (IOException e) {
            System.out.println("Card error: " + e.getMessage());
        } finally {
            System.out.println("Transaction cleanup complete");
        }
        System.out.println("Missing card file classification: FileNotFoundException (checked)");
        System.out.println("Error example classification: OutOfMemoryError (Error; not caught as a routine transaction failure)");
        benchmark();
    }
}
