# Q5 — Cinema seat booking and race conditions

## Shared / Joint

`SeatBooking` runs unsynchronized, synchronized-method, synchronized-block, atomic, per-seat `ReentrantLock`, and `ConcurrentHashMap.putIfAbsent` variants. The synchronized version's expected exact output is `Seats sold: 10`; both locking variants produce it. A 20-screen cinema should use one lock per screen (or per seat if contention warrants it), so bookings on unrelated screens do not block each other.

## Student A

| Run | Unsynchronized seats sold |
|---:|---:|
| 1–10 | See raw `results.csv` |

A short `Thread.sleep(0, 100_000)` widens the interval between checking and setting a seat; it does not guarantee a particular schedule. Two workers can both observe false before either writes true, then both increment `bookingsMade`. The check-and-set plus counter increment is the critical section. Overselling in the recorded runs demonstrates the race.

## Student B

The full-method lock uses the booking object's intrinsic monitor. The block variant uses the same monitor only around the check/update; because this tiny method has almost no unrelated work, differences are noise. `static synchronized` locks the `Class` object and is appropriate only when the invariant is shared across all instances.

The benchmark runs 10,000 concurrent attempts per round after warmup; raw elapsed nanoseconds and per-variant average/min/max are in `results.csv`. `AtomicBoolean.compareAndSet` gives one atomic reservation flag per seat. `ReentrantLock` enables `tryLock`/timeout/fairness choices but must be unlocked in `finally`. `putIfAbsent` makes a seat-key insertion atomic. All variants use O(s) state for s seats; the map/lock/atomic alternatives have additional per-seat object overhead. Under contention, each serializes or retries around the same seat; benchmark results are machine/JVM dependent.

## Shared / Joint alternative

Prefer per-screen locking for 20 independently managed screens; a single global lock needlessly serializes different screens. Use per-seat CAS or map insertion when that data model and lock granularity suit the rest of the system.
