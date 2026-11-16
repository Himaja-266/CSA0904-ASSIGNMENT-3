# Q6 — Deadlock and inter-thread communication

## Shared / Joint

Both problems coordinate threads around shared state. Deadlock needs a lock-order policy that removes a circular wait; producer-consumer needs a condition protocol that releases the monitor and wakes threads when the predicate can change. Consistent account-id ordering prevents cycles; the token counter's synchronized `while` predicates and `notifyAll()` allow progress while keeping count in [0,5].

## Student A

`wait()` is called only while holding the monitor and inside `while`: wakeups can be spurious, and another consumer may consume the item before a woken consumer reacquires the lock. `notify()` selects an arbitrary waiter, which can be the wrong condition group; `notifyAll()` is safe here because all waiters recheck the appropriate predicate. The output logs count after takes and shows values in 0..5. `sleep()` pauses without releasing a monitor; `wait()` releases the monitor until notification/timeout.

The intentionally incorrect `if` version waits for two consumers, publishes one token, and wakes both; both pass the stale check, causing count `-1`. It is run five times. The correct `while` implementation remains in `TokenCounter` and gives `Sum of tokens collected: 210`.

## Student B

The opposing transfer threads acquire A then B and B then A, with a pause between locks. The run's `ThreadMXBean.findDeadlockedThreads()` reports both names. Coffman conditions: mutual exclusion (intrinsic monitors), hold-and-wait (each keeps its first account), no preemption (monitors cannot be forcibly taken), and circular wait (opposite order). Locking lower account id first imposes one global order, eliminating circular wait; opposing transfers then complete and balances reconcile.

## Shared / Joint alternative

`ArrayBlockingQueue<Integer>(5)` is exercised with producer/consumer tasks and yields the same sum. `tryLock(timeout)` is exercised for transfers with a consistent account lock order; timeout returns false and allows a retry/failure policy. Both are less error-prone than hand-built monitor protocols, at the cost of explicit timeout handling. Put/take are O(1), with O(5) queue storage; a transfer acquires two locks and is O(1) aside from contention.
