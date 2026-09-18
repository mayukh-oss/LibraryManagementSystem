# Library Management System (Java, JDBC, Multithreading)

A single-file **Library Management System** built for **CSE2006 – Programming in Java**.
It applies the full breadth of the syllabus in one runnable program: core Java & OOP,
exception handling, multithreading, collections, I/O streams, and JDBC-based database
persistence.

## Overview

Libraries need to track books, members, and loans while making sure overdue books are
flagged and fines are calculated automatically. This project implements that as a single,
compact Java file — `LibraryManagementSystem.java` (under 300 lines) — containing:

- A **catalog module** for managing books (add/search/list) with an in-memory cache backed
  by a relational database.
- A **loan module** that issues and returns books, enforcing borrow-limit and availability
  rules through custom exceptions.
- A **background notification thread** that continuously scans for overdue loans without
  blocking the console UI.
- A **reporting module** that exports a combined catalog + member report to disk using
  both character and byte I/O streams.

## Features

- View, search (by title/author/category), add books
- Register members and librarians (OOP inheritance: `User` → `Member` / `Librarian`)
- Issue and return books with automatic overdue fine calculation
- Enforce a maximum-books-per-member borrowing rule
- Background daemon thread that periodically checks for overdue loans
- Export a combined catalog + member report as a text file, plus a byte-level backup copy
- Persistent storage via JDBC (SQLite, file-based — no separate DB server required)
- Centralized file-based logging of every operation (`logs/system.log`)

## Technologies / Tools Used

| Concern | Technology |
|---|---|
| Language | Java 17+ (developed & tested on JDK 21) |
| Persistence | JDBC + SQLite (`org.sqlite.JDBC`) |
| Concurrency | `java.lang.Thread` / `Runnable` |
| Collections | `HashMap`, `ConcurrentHashMap`, `ArrayList`, `Collections` |
| I/O | `FileReader`/`FileWriter`, `BufferedReader`/`BufferedWriter`, `FileInputStream`/`FileOutputStream` |
| Build | `javac` (no external build tool required) |
| Version Control | Git |

## Project Structure

```
LibraryManagementSystem/
├── LibraryManagementSystem.java   (single source file, ~294 lines — all classes)
├── lib/
│   ├── sqlite-jdbc.jar             JDBC driver (required to compile & run)
│   ├── slf4j-api.jar                Logging facade required by the driver
│   └── slf4j-nop.jar                 No-op logging provider (silences driver warnings)
├── .gitignore
├── statement.md
└── README.md
```

`data/`, `logs/`, and `reports/` are created automatically the first time the program
runs — they are not checked into the repository.

## Steps to Install & Run

### Prerequisites
- JDK 17 or later (`javac -version` to check)
- No external database server needed — SQLite runs embedded from the bundled JAR.

### Compile
```bash
git clone <repository-url>
cd LibraryManagementSystem
javac -cp "lib/*" LibraryManagementSystem.java
```

### Run
```bash
java -cp ".:lib/*" LibraryManagementSystem      # Linux / macOS
java -cp ".;lib/*" LibraryManagementSystem       # Windows
```

> **Note (JDK 22+):** you may see warnings like `WARNING: A restricted method in
> java.lang.System has been called`. These come from the SQLite driver loading its
> native library and are harmless — the program still runs correctly. To silence
> them, add `--enable-native-access=ALL-UNNAMED` before the class name.

On first run the program creates `data/`, `logs/`, and `reports/`, sets up the SQLite
schema, and seeds a small demo catalog (3 books) and two demo members so the menu is
immediately explorable.

## Instructions for Testing

Manual/functional test flow using the console menu:

1. **Option 1** – View all books → confirms catalog loads and displays correctly.
2. **Option 5** – Issue book to member (e.g., ISBN `ISBN001`, member `M001`) → confirms
   JDBC insert + in-memory cache update.
3. **Option 5** (repeat 3× for the same member) → the 4th attempt should raise
   `InvalidOperationException` (borrow-limit rule).
4. **Option 5** with an unknown ISBN or member ID → confirms
   `BookNotAvailableException` / `MemberNotFoundException` are caught and reported
   cleanly without crashing the program.
5. **Option 6** – Return a book → confirms fine calculation logic (0 for on-time returns).
6. **Option 7 / 8** – View active loans / members → confirms state consistency.
7. **Option 9** – Generate reports → confirms `reports/report.txt` and
   `reports/report.bak` are created; inspect `logs/system.log` to see the full
   timestamped audit trail, including the background `NotificationThread` periodically
   scanning for overdue loans.

To reset to a clean state, delete the `data/`, `logs/`, and `reports/` folders before
re-running.

## Documentation

The problem statement and scope are in [`statement.md`](statement.md). The full project
report (with diagrams) is submitted separately on the course portal.
