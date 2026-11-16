import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ThreadDemo {
    static final List<String> finishOrder = Collections.synchronizedList(new ArrayList<>());

    static class ExtendingStation extends Thread {
        private final int delay;
        ExtendingStation(String name, int delay) { super(name); this.delay = delay; }
        public void run() {
            System.out.println(getName() + " initial state=" + getState());
            try { Thread.sleep(delay); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            finishOrder.add(getName());
            System.out.println(getName() + " finished on " + Thread.currentThread().getName());
        }
    }

    static class RunnableStation implements Runnable {
        private final String name;
        private final int delay;
        RunnableStation(String name, int delay) { this.name = name; this.delay = delay; }
        public void run() {
            try { Thread.sleep(delay); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            finishOrder.add(name);
        }
    }

    static void round(int run) throws InterruptedException {
        finishOrder.clear();
        ExtendingStation[] stations = new ExtendingStation[4];
        for (int i = 0; i < stations.length; i++) {
            stations[i] = new ExtendingStation("Station-" + (i + 1), 20 + (int)(Math.random() * 20));
            System.out.println("Run " + run + " " + stations[i].getName() + " created state=" + stations[i].getState());
            stations[i].setPriority(i == 0 ? Thread.MAX_PRIORITY : Thread.NORM_PRIORITY);
            stations[i].start();
        }
        System.out.println("Run " + run + " station-1 state after start=" + stations[0].getState());
        while (stations[0].isAlive() && stations[0].getState() != Thread.State.TIMED_WAITING) Thread.yield();
        if (stations[0].isAlive()) System.out.println("Run " + run + " station-1 during sleep=" + stations[0].getState());
        for (ExtendingStation station : stations) station.join();
        System.out.println("Run " + run + " finish order=" + String.join(" > ", finishOrder));
        System.out.println("Run " + run + " station-1 after join state=" + stations[0].getState()
                + ", isAlive=" + stations[0].isAlive());
    }

    static double executorOnce() throws Exception {
        long start = System.nanoTime();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> tasks = new ArrayList<>();
            for (int i = 1; i <= 4; i++) tasks.add(pool.submit(new RunnableStation("Pool-" + i, 10)));
            for (Future<?> task : tasks) task.get();
        } finally { pool.shutdown(); }
        return (System.nanoTime() - start) / 1_000_000.0;
    }

    static void executorDemo() throws Exception {
        executorOnce();
        executorOnce();
        double[] times = new double[10];
        double sum = 0, min = Double.MAX_VALUE, max = 0;
        System.out.println("Executor benchmark after 2 warmups (two workers, four tasks)");
        System.out.println("run,elapsed_ms,workers,tasks");
        for (int i = 0; i < times.length; i++) {
            times[i] = executorOnce();
            sum += times[i];
            min = Math.min(min, times[i]);
            max = Math.max(max, times[i]);
            System.out.printf("%d,%.3f,2,4%n", i + 1, times[i]);
        }
        System.out.printf("AVERAGE,%.3f,2,4%nMIN,%.3f,2,4%nMAX,%.3f,2,4%n", sum / times.length, min, max);
    }

    public static void main(String[] args) throws Exception {
        for (int i = 1; i <= 10; i++) round(i);
        Thread direct = new Thread(() -> System.out.println("Calling run() directly executes on " + Thread.currentThread().getName()));
        direct.run();
        Thread daemon = new Thread(() -> {
            try { Thread.sleep(60); System.out.println("Daemon finished before main"); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "daemon-demo");
        daemon.setDaemon(true);
        daemon.start();
        System.out.println("Daemon flag=" + daemon.isDaemon() + "; main does not join daemon");
        executorDemo();
    }
}
