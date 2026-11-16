# Programming in Java — Assignment 3 (CO3)

**Student A name:** ____________________  **Register number:** ____________________  
**Student B name:** ____________________  **Register number:** ____________________

## Environment

The programs target Java 17 or newer (`javac --release 17`). At preparation time, `java -version` reported Java 27.0. The output and benchmark files contain measurements from this machine/run; timing values are illustrative, not portable performance guarantees.

## Compile and run

From this directory, compile and run one question in PowerShell:

```powershell
Push-Location Q1; javac --release 17 AtmDemo.java; java AtmDemo; Pop-Location
Push-Location Q2; javac --release 17 ExamDemo.java; java ExamDemo; Pop-Location
Push-Location Q3; javac --release 17 ReportDemo.java; java ReportDemo; Pop-Location
Push-Location Q4; javac --release 17 ThreadDemo.java; java ThreadDemo; Pop-Location
Push-Location Q5; javac --release 17 SeatBooking.java; java SeatBooking; Pop-Location
Push-Location Q6; javac --release 17 ConcurrencyDemo.java; java ConcurrencyDemo; Pop-Location
Push-Location Q7; javac --release 17 ScoreProcessor.java; java ScoreProcessor; Pop-Location
```

Q3 creates its `input` folder and report CSVs. Q7 creates 10,000 score-file paths in `scores` (100 are intentionally missing) and runs the longer pool-size benchmark. `output.txt` and `results.csv` are captured run evidence; rerunning a program replaces or regenerates its evidence. Q1's intentional uncaught trace can be reproduced with `java AtmDemo uncaught`. The invalid catch-order snippet in `Q1/compiler-demo/` is deliberately excluded from normal compilation.

## Result notes

- **Q1:** Valid withdrawal succeeds; invalid values are handled and cleanup runs. Throwing-based validation costs more than returning an error code in this sample.
- **Q2:** Storage errors retain the `IOException` cause; stack-capture cost rises with call depth.
- **Q3:** Manual and try-with-resources reports match; tracked resources close on failure only with finally/TWR.
- **Q4:** Ten finish-order runs vary. `join()` keeps the final result after station completion.
- **Q5:** Synchronized and atomic booking sell at most one ticket per seat; unsynchronized runs can overcount.
- **Q6:** The opposing transfer demo is detected as deadlocked; ordered transfers complete and the correct bounded-buffer sum is 210.
- **Q7:** All 10,000 task results reconcile; the best measured pool size is summarized in `results.csv` and `report.md`.

See [report.md](./report.md) for the labeled assignment responses, tables, design notes, measured data, and limitations.
