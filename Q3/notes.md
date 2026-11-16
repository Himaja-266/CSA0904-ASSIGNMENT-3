# Q3 — Daily sales report resource safety

## Shared / Joint

The same two good store inputs yield byte-identical manual-finally and try-with-resources reports. The failing input list includes `bad.csv`, which fails after earlier resources have opened. Tracked counts: manual close omitted on the failure path opened 2 / closed 0; try-with-resources opened 2 / closed 2. This controlled no-finally branch intentionally leaks until the process exits.

TWR is not sufficient when a resource is not `AutoCloseable`, when a partially written report must be atomically replaced, when retry/rollback is needed, or when external resources escape across threads. Write to a temporary file and move atomically where supported; define how close failures affect retries.

## Student A

Logging reports the failing `bad.csv` and the `NumberFormatException` cause. Debugger walkthrough: set a breakpoint at `parse`'s `Integer.parseInt`, run `ReportDemo`, inspect `file`, `row`, and `parts`; step into the invalid parse, step out to the catch/log, then inspect `opened`/`closed` at both cleanup paths. Screenshot placeholder: **[Insert VS Code debugger screenshot here]**. No screenshot was available from the headless run.

## Student B

Resources: each `TrackedReader` must close after its file; the report `BufferedWriter` must close after the full report. With multiple resources declared in one TWR header, Java closes in reverse declaration order. Manual close in a `finally` prevents ordinary body failures from bypassing close; nested `try` is needed so one close exception does not prevent attempts to close other resources. A close exception can mask an earlier processing exception in naive manual code.

Manual-close pattern: put a `try { reader.close(); } catch (IOException closeFailure) { ... }` around each resource's `close()` in the outer `finally`, and do the same for the writer even if reader close fails. Preserve the processing exception as primary and attach cleanup failures as suppressed rather than silently discarding them.

`FailingClose` demonstrates TWR suppression: the primary `processing failed` exception remains primary, and `close also failed` is available via `getSuppressed()`. Complexity is O(f) report processing for f files. In the deliberately broken no-close sequential loop, at most f readers can accumulate on failure; in this example the failure at file 2 leaves 2 readers open. Sequential finally/TWR closes each reader before moving on, so at most one reader plus the output writer is open.
