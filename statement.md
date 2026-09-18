# Problem Statement

## Problem Statement

Small and mid-sized libraries (college department libraries, community reading rooms)
often rely on manual registers or spreadsheets to track which books are out, who has
them, and when they are due back. This makes it easy to lose track of overdue books,
miscalculate fines, and leave members unaware that a return is due — leading to lost
books, unpaid fines, and frustrated staff who have to cross-check records by hand.

This project builds a **Java-based Library Management System** that automates book
cataloguing, member registration, book issuing/returning, overdue tracking, and
report generation, backed by a persistent relational database (via JDBC) rather than
plain files or spreadsheets.

## Scope of the Project

In scope:
- Single-library catalog and membership management (not a multi-branch network)
- Console-based interaction (no GUI/web front end)
- Local, embedded database (SQLite) rather than a networked DB server
- Core lending workflow: issue, return, overdue fine calculation, notifications
- File-based reporting (CSV/TXT export) and logging

Out of scope:
- Multi-branch / inter-library transfers
- Payment gateway integration for fine collection
- Graphical or web-based user interface
- Concurrent multi-user network access (the system is single-process; multithreading
  here is used for the internal background notification scanner, not multi-user access)

## Target Users

- **Members** — students/patrons who search the catalog, borrow books, and return them.
- **Librarians** — staff who maintain the catalog, register members, monitor overdue
  loans, and generate operational reports.

## High-Level Features

1. **Catalog Management** — add, list, and search books by title, author, or category,
   backed by an in-memory cache (`HashMap`) synchronized with the database.
2. **Membership Management** — register members and librarians, modeled through an
   OOP inheritance hierarchy (`User` → `Member` / `Librarian`) with polymorphic role
   and privilege reporting.
3. **Loan Management** — issue and return books with rule enforcement (availability
   check, per-member borrow limit) implemented via custom checked/unchecked exceptions
   (`BookNotAvailableException`, `MemberNotFoundException`, `InvalidOperationException`).
4. **Overdue Fine Calculation** — automatic fine computation based on days overdue,
   applied at return time and tracked per member.
5. **Background Notification Service** — a daemon thread (`NotificationService`)
   that periodically scans all active loans and logs overdue/due-soon alerts without
   blocking the main console thread.
6. **Reporting & Logging** — catalog, member, and overdue reports exported to disk
   using character streams (`BufferedWriter`) and a byte-stream backup
   (`FileInputStream`/`FileOutputStream`); every operation is timestamped in
   `logs/system.log` via a thread-safe singleton logger.
7. **Persistent Storage via JDBC** — all entities (books, members, librarians, loans)
   are stored in a SQLite database accessed exclusively through `PreparedStatement`
   queries (SQL-injection safe).
