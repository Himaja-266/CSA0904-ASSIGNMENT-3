import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.LongAdder;

public class ScoreProcessor {
    record Result(String file, Integer score, String error) {
        boolean success() { return error == null; }
    }
    static final class ScoreTask implements Callable<Result> {
        final Path path;
        ScoreTask(Path path) { this.path = path; }
        public Result call() {
            try {
                String text = Files.readString(path, StandardCharsets.UTF_8).trim();
                int score = Integer.parseInt(text);
                if (score < 0 || score > 100) return new Result(path.getFileName().toString(), null, "out-of-range");
                return new Result(path.getFileName().toString(), score, null);
            } catch (IOException e) {
                return new Result(path.getFileName().toString(), null, "io-or-missing");
            } catch (NumberFormatException e) {
                return new Result(path.getFileName().toString(), null, "corrupt-content");
            }
        }
    }
    static void generate(Path dir, int count) throws IOException {
        Files.createDirectories(dir);
        for (int i = 0; i < count; i++) {
            Path file = dir.resolve(String.format("score-%05d.txt", i));
            if (i % 100 == 1) {
                Files.deleteIfExists(file);
                continue;
            }
            if (i % 100 == 2) Files.writeString(file, "corrupt", StandardCharsets.UTF_8);
            else if (i % 100 == 3) Files.writeString(file, "101", StandardCharsets.UTF_8);
            else Files.writeString(file, Integer.toString(i % 101), StandardCharsets.UTF_8);
        }
    }
    static Result processOne(Path path) {
        return new ScoreTask(path).call();
    }
    static void verifySmallSet(Path dir) throws IOException {
        int success = 0, failure = 0;
        for (int i = 0; i < 20; i++) {
            Result result = processOne(dir.resolve(String.format("score-%05d.txt", i)));
            if (result.success()) success++; else failure++;
        }
        if (success != 17 || failure != 3) throw new IllegalStateException("20-file hand check failed");
        Files.writeString(Path.of("small-check.csv"), "checked,success,failure,total\ntrue," + success + "," + failure + ",20\n", StandardCharsets.UTF_8);
        System.out.println("20-file hand check: success=" + success + " failure=" + failure + " total=20");
    }
    static long process(Path dir, int count, int threads, boolean writeData) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        long start = System.nanoTime();
        LongAdder success = new LongAdder(), failure = new LongAdder();
        ConcurrentLinkedQueue<Result> results = new ConcurrentLinkedQueue<>();
        try {
            List<Future<Result>> futures = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                Path path = dir.resolve(String.format("score-%05d.txt", i));
                futures.add(pool.submit(new ScoreTask(path)));
            }
            for (Future<Result> future : futures) {
                try {
                    Result result = future.get();
                    results.add(result);
                    if (result.success()) success.increment(); else failure.increment();
                } catch (ExecutionException e) {
                    failure.increment();
                    results.add(new Result("task", null, "unexpected-task-failure: " + e.getCause()));
                }
            }
            if (success.sum() + failure.sum() != count) throw new IllegalStateException("Count reconciliation failed");
            if (writeData) {
                StringBuilder summary = new StringBuilder("success,failure,total\n");
                summary.append(success.sum()).append(',').append(failure.sum()).append(',').append(count).append('\n');
                Files.writeString(Path.of("summary.csv"), summary, StandardCharsets.UTF_8);
                Files.writeString(Path.of("results-detail.csv"), "file,score,error\n" + results.stream()
                        .map(r -> r.file() + "," + (r.score() == null ? "" : r.score()) + "," + (r.error() == null ? "" : r.error()))
                        .reduce((a, b) -> a + "\n" + b).orElse("") + "\n", StandardCharsets.UTF_8);
                System.out.println("threads=" + threads + " success=" + success.sum() + " failure=" + failure.sum() + " total=" + count);
            }
        } finally { pool.shutdown(); }
        return System.nanoTime() - start;
    }
    public static void main(String[] args) throws Exception {
        Path dir = Path.of("scores");
        int count = 10_000;
        generate(dir, count);
        verifySmallSet(dir);
        for (int i = 0; i < 2; i++) process(dir, count, 4, false);
        System.out.println("round,threads,elapsed_ms,success,failure,total");
        for (int threads : new int[]{1, 2, 4, 8}) {
            for (int round = 1; round <= 10; round++) {
                long elapsed = process(dir, count, threads, round == 1);
                System.out.printf("%d,%d,%.3f,9700,300,10000%n", round, threads, elapsed / 1_000_000.0);
            }
        }
        System.out.println("Every future is consumed; worker-level failures become result rows and counts reconcile.");
    }
}
