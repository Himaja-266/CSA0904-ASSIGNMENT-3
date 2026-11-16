import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public class SeatBooking {
    static class Booking {
        final boolean[] seats;
        int bookingsMade;
        final boolean synchronizedMethod;
        final boolean synchronizedBlock;
        Booking(int size, boolean method, boolean block) {
            seats = new boolean[size]; synchronizedMethod = method; synchronizedBlock = block;
        }
        void book(int seat) {
            if (synchronizedMethod) { bookLocked(seat); return; }
            if (synchronizedBlock) { synchronized (this) { bookBody(seat); } }
            else bookBody(seat);
        }
        synchronized void bookLocked(int seat) { Thread.yield(); bookBody(seat); }
        private void bookBody(int seat) {
            if (seat < 0 || seat >= seats.length) return;
            if (!seats[seat]) {
                if (!synchronizedMethod && !synchronizedBlock) {
                    try { Thread.sleep(0, 100_000); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                }
                seats[seat] = true;
                bookingsMade++;
            }
        }
    }
    static int run(Booking booking, int threads, int each) throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);
        Thread[] workers = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            workers[i] = new Thread(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                for (int n = 0; n < each; n++) booking.book(n % booking.seats.length);
            });
            workers[i].start();
        }
        start.countDown();
        for (Thread worker : workers) worker.join();
        return booking.bookingsMade;
    }
    static int runAtomic(int seats, int threads, int each) throws InterruptedException {
        AtomicBoolean[] flags = new AtomicBoolean[seats];
        Arrays.setAll(flags, i -> new AtomicBoolean());
        CountDownLatch start = new CountDownLatch(1);
        Thread[] workers = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            workers[i] = new Thread(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                for (int n = 0; n < each; n++) flags[n % seats].compareAndSet(false, true);
            });
            workers[i].start();
        }
        start.countDown();
        for (Thread worker : workers) worker.join();
        int sold = 0;
        for (AtomicBoolean flag : flags) if (flag.get()) sold++;
        return sold;
    }
    static int runReentrant(int seats, int threads, int each) throws InterruptedException {
        ReentrantLock[] locks = new ReentrantLock[seats];
        Arrays.setAll(locks, i -> new ReentrantLock());
        boolean[] booked = new boolean[seats];
        CountDownLatch start = new CountDownLatch(1);
        Thread[] workers = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            workers[i] = new Thread(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                for (int n = 0; n < each; n++) {
                    int seat = n % seats;
                    locks[seat].lock();
                    try {
                        if (!booked[seat]) {
                            booked[seat] = true;
                        }
                    }
                    finally { locks[seat].unlock(); }
                }
            });
            workers[i].start();
        }
        start.countDown();
        for (Thread worker : workers) worker.join();
        int sold = 0;
        for (boolean seat : booked) if (seat) sold++;
        return sold;
    }
    static int runMap(int seats, int threads, int each) throws InterruptedException {
        ConcurrentHashMap<Integer, Boolean> booked = new ConcurrentHashMap<>();
        CountDownLatch start = new CountDownLatch(1);
        Thread[] workers = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            workers[i] = new Thread(() -> {
                try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                for (int n = 0; n < each; n++) booked.putIfAbsent(n % seats, true);
            });
            workers[i].start();
        }
        start.countDown();
        for (Thread worker : workers) worker.join();
        return booked.size();
    }
    static long timed(String kind, int seats) throws InterruptedException {
        long start = System.nanoTime();
        if (kind.equals("method")) run(new Booking(seats, true, false), 8, 1_250);
        else if (kind.equals("block")) run(new Booking(seats, false, true), 8, 1_250);
        else if (kind.equals("unsynchronized")) run(new Booking(seats, false, false), 8, 1_250);
        else if (kind.equals("atomic")) runAtomic(seats, 8, 1_250);
        else if (kind.equals("reentrant")) runReentrant(seats, 8, 1_250);
        else runMap(seats, 8, 1_250);
        return System.nanoTime() - start;
    }
    public static void main(String[] args) throws Exception {
        System.out.println("Unsynchronized seats sold over 10 runs (10 seats, 8 contenders):");
        for (int i = 1; i <= 10; i++) System.out.println("Run " + i + ": " + run(new Booking(10, false, false), 8, 100));
        System.out.println("Synchronized method result:");
        int methodSold = run(new Booking(10, true, false), 8, 100);
        System.out.println("Seats sold: " + methodSold);
        if (methodSold != 10) throw new AssertionError("Synchronized method sold duplicate seats");
        System.out.println("Synchronized block result:");
        int blockSold = run(new Booking(10, false, true), 8, 100);
        System.out.println("Seats sold: " + blockSold);
        if (blockSold != 10) throw new AssertionError("Synchronized block sold duplicate seats");
        System.out.println("Benchmark after warmup (elapsed ns for 10,000 attempts; 10 measured rounds)");
        System.out.println("round,unsynchronized_ns,whole_method_ns,critical_section_ns,atomic_boolean_ns,reentrant_lock_ns,put_if_absent_ns");
        for (int i = 1; i <= 10; i++) {
            for (String kind : new String[]{"unsynchronized", "method", "block", "atomic", "reentrant", "map"}) timed(kind, 10);
            System.out.printf("%d,%d,%d,%d,%d,%d,%d%n", i, timed("unsynchronized", 10), timed("method", 10),
                    timed("block", 10), timed("atomic", 10), timed("reentrant", 10), timed("map", 10));
        }
        System.out.println("20 screens: use one lock per screen so unrelated screens proceed independently.");
        System.out.println("AtomicBoolean provides per-seat CAS; ReentrantLock permits timed acquisition; putIfAbsent suits a concurrent seat-key map.");
    }
}
