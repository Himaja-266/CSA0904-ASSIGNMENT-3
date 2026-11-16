import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class ConcurrencyDemo {
    static final class Account {
        final int id;
        int balance = 100;
        Account(int id) { this.id = id; }
    }

    static final class LockAccount {
        final int id;
        final ReentrantLock lock = new ReentrantLock();
        int balance = 100;
        LockAccount(int id) { this.id = id; }
    }

    static void unsafeTransfer(Account from, Account to) {
        synchronized (from) {
            try { Thread.sleep(40); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            synchronized (to) { from.balance--; to.balance++; }
        }
    }

    static void safeTransfer(Account from, Account to) {
        Account first = from.id < to.id ? from : to;
        Account second = from.id < to.id ? to : from;
        synchronized (first) {
            synchronized (second) { from.balance--; to.balance++; }
        }
    }

    static boolean tryTransfer(LockAccount from, LockAccount to) throws InterruptedException {
        LockAccount first = from.id < to.id ? from : to;
        LockAccount second = from.id < to.id ? to : from;
        if (!first.lock.tryLock(10, TimeUnit.MILLISECONDS)) return false;
        try {
            if (!second.lock.tryLock(10, TimeUnit.MILLISECONDS)) return false;
            try { from.balance--; to.balance++; return true; }
            finally { second.lock.unlock(); }
        } finally { first.lock.unlock(); }
    }

    static final class TokenCounter {
        final int[] buffer = new int[5];
        int in, out, count;
        synchronized void put(int token) throws InterruptedException {
            while (count == buffer.length) wait();
            buffer[in] = token; in = (in + 1) % buffer.length; count++; notifyAll();
        }
        synchronized int take() throws InterruptedException {
            while (count == 0) wait();
            int token = buffer[out]; out = (out + 1) % buffer.length; count--; notifyAll(); return token;
        }
    }

    static final class UnsafeIfCounter {
        final int[] buffer = new int[1];
        int count, waiting;
        synchronized int take() throws InterruptedException {
            if (count == 0) {
                waiting++;
                notifyAll();
                wait();
            }
            int token = buffer[0];
            count--;
            return token;
        }
        synchronized void putOne() throws InterruptedException {
            while (waiting < 2) wait();
            buffer[0] = 7;
            count++;
            notifyAll();
        }
    }

    static void deadlockDemo() throws InterruptedException {
        Account a = new Account(1), b = new Account(2);
        Thread first = new Thread(() -> unsafeTransfer(a, b), "A-to-B");
        Thread second = new Thread(() -> unsafeTransfer(b, a), "B-to-A");
        first.setDaemon(true); second.setDaemon(true); first.start(); second.start();
        Thread.sleep(90);
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        long[] ids = bean.findDeadlockedThreads();
        System.out.println("Deadlock detected by ThreadMXBean: " + (ids != null));
        if (ids != null) for (long id : ids) System.out.println("Deadlocked thread: " + bean.getThreadInfo(id).getThreadName());
        Account c = new Account(3), d = new Account(4);
        Thread t1 = new Thread(() -> { for (int i = 0; i < 10_000; i++) safeTransfer(c, d); });
        Thread t2 = new Thread(() -> { for (int i = 0; i < 10_000; i++) safeTransfer(d, c); });
        t1.start(); t2.start(); t1.join(); t2.join();
        System.out.println("Ordered transfer balances total=" + (c.balance + d.balance));
    }

    static void tokenDemo() throws InterruptedException {
        TokenCounter counter = new TokenCounter();
        int[] sum = {0};
        Thread producer = new Thread(() -> {
            try { for (int i = 1; i <= 20; i++) counter.put(i); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        Thread consumer = new Thread(() -> {
            try {
                for (int i = 1; i <= 20; i++) {
                    int token = counter.take();
                    synchronized (sum) { sum[0] += token; }
                    synchronized (counter) { System.out.println("buffer size=" + counter.count); }
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        producer.start(); consumer.start(); producer.join(); consumer.join();
        System.out.println("Sum of tokens collected: " + sum[0]);
    }

    static void unsafeIfDemo(int run) throws InterruptedException {
        UnsafeIfCounter counter = new UnsafeIfCounter();
        Thread c1 = new Thread(() -> { try { counter.take(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
        Thread c2 = new Thread(() -> { try { counter.take(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
        c1.start(); c2.start();
        synchronized (counter) {
            while (counter.waiting < 2) counter.wait(100);
        }
        counter.putOne();
        c1.join(1_000); c2.join(1_000);
        System.out.println("Unsafe if run " + run + " after one token wakes two consumers: count=" + counter.count + " (invalid)");
    }

    static void queueDemo() throws InterruptedException {
        ArrayBlockingQueue<Integer> queue = new ArrayBlockingQueue<>(5);
        int[] sum = {0};
        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= 20; i++) queue.put(i);
                queue.put(-1);
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        Thread consumer = new Thread(() -> {
            try {
                int token;
                while ((token = queue.take()) != -1) sum[0] += token;
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        producer.start(); consumer.start(); producer.join(); consumer.join();
        System.out.println("ArrayBlockingQueue sum: " + sum[0]);
    }

    static void tryLockDemo() throws InterruptedException {
        LockAccount a = new LockAccount(1), b = new LockAccount(2);
        Thread t1 = new Thread(() -> { for (int i = 0; i < 1_000; i++) try { tryTransfer(a, b); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; } });
        Thread t2 = new Thread(() -> { for (int i = 0; i < 1_000; i++) try { tryTransfer(b, a); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; } });
        t1.start(); t2.start(); t1.join(); t2.join();
        System.out.println("Timed tryLock transfer balances total=" + (a.balance + b.balance));
    }

    public static void main(String[] args) throws Exception {
        deadlockDemo();
        tokenDemo();
        for (int i = 1; i <= 5; i++) unsafeIfDemo(i);
        queueDemo();
        tryLockDemo();
        System.out.println("ArrayBlockingQueue capacity=5; timed tryLock is used for transfers.");
        System.out.println("wait releases the monitor; sleep does not. A wait condition must be checked in a loop.");
    }
}
