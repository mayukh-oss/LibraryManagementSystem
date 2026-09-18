import java.io.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LibraryManagementSystem {
    static Connection conn;
    static final Map<String, Book> books = new HashMap<>();
    static final Map<String, Member> members = new ConcurrentHashMap<>();
    static PrintWriter log;
    public static void main(String[] args) throws Exception {
        System.out.println("Initializing Library Management System...");
        for (String d : new String[]{"data", "logs", "reports"}) new File(d).mkdirs();
        log = new PrintWriter(new FileWriter("logs/system.log", true), true);
        System.out.println("Connecting to SQLite database (data/library.db)...");
        Class.forName("org.sqlite.JDBC");
        conn = DriverManager.getConnection("jdbc:sqlite:data/library.db");
        System.out.println("Database connected.");
        initSchema();
        System.out.println("Schema verified.");
        loadBooks();
        System.out.println("Loaded " + books.size() + " book(s) into memory cache.");
        loadMembers();
        System.out.println("Loaded " + members.size() + " member(s) into memory cache.");
        seedDemoData();

        System.out.println("Starting background notification thread...");
        Thread notifier = new Thread(new NotificationService(), "NotificationThread");
        notifier.setDaemon(true);
        notifier.start();
        System.out.println("System ready.\n");

        Scanner sc = new Scanner(System.in);
        boolean exit = false;
        while (!exit) {
            printMenu();
            String choice = sc.nextLine().trim();
            try {
                switch (choice) {
                    case "1" -> listBooks();
                    case "2" -> searchBooks(sc);
                    case "3" -> addBook(sc);
                    case "4" -> registerMember(sc);
                    case "5" -> issueBook(sc);
                    case "6" -> returnBook(sc);
                    case "7" -> viewActiveLoans();
                    case "8" -> viewMembers();
                    case "9" -> generateReport();
                    case "0" -> exit = true;
                    default -> System.out.println("Invalid choice.");
                }
            } catch (BookNotAvailableException | MemberNotFoundException e) {
                logMsg("WARN", e.getMessage());
                System.out.println("ERROR: " + e.getMessage());
            } catch (InvalidOperationException e) {
                logMsg("WARN", e.getMessage());
                System.out.println("REJECTED: " + e.getMessage());
            } catch (Exception e) {
                logMsg("ERROR", e.toString());
                System.out.println("Unexpected error - see logs/system.log");
            }
        }
        System.out.println("Stopping notification thread...");
        notifier.interrupt();
        System.out.println("Closing database connection...");
        conn.close();
        sc.close();
        System.out.println("Goodbye!");
    }
    static void printMenu() {
        System.out.println("\n===== LIBRARY MANAGEMENT SYSTEM =====");
        System.out.println("1.List Books 2.Search 3.Add Book 4.Register Member 5.Issue Book");
        System.out.println("6.Return Book 7.Active Loans 8.Members 9.Reports 0.Exit");
        System.out.print("Choice: ");
    }
    static void initSchema() throws SQLException {
        Statement st = conn.createStatement();
        st.execute("CREATE TABLE IF NOT EXISTS books(isbn TEXT PRIMARY KEY,title TEXT,author TEXT," +
                "category TEXT,total INTEGER,available INTEGER)");
        st.execute("CREATE TABLE IF NOT EXISTS members(id TEXT PRIMARY KEY,name TEXT,email TEXT,fine REAL DEFAULT 0)");
        st.execute("CREATE TABLE IF NOT EXISTS loans(loan_id INTEGER PRIMARY KEY AUTOINCREMENT,isbn TEXT," +
                "member_id TEXT,issue_date TEXT,due_date TEXT,return_date TEXT,returned INTEGER DEFAULT 0)");
    }
    static void loadBooks() throws SQLException {
        ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM books");
        while (rs.next())
            books.put(rs.getString("isbn"), new Book(rs.getString("isbn"), rs.getString("title"),
                    rs.getString("author"), rs.getString("category"), rs.getInt("total"), rs.getInt("available")));
    }
    static void loadMembers() throws SQLException {
        ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM members");
        while (rs.next()) {
            Member m = new Member(rs.getString("id"), rs.getString("name"), rs.getString("email"));
            m.fine = rs.getDouble("fine");
            members.put(m.userId, m);
        }
    }
    static void seedDemoData() throws SQLException {
        if (books.isEmpty()) {
            System.out.println("No books found - seeding demo catalog...");
            addBookRecord(new Book("ISBN001", "Java: The Complete Reference", "Herbert Schildt", "Programming", 3, 3));
            addBookRecord(new Book("ISBN002", "Effective Java", "Joshua Bloch", "Programming", 2, 2));
            addBookRecord(new Book("ISBN003", "Clean Code", "Robert C. Martin", "Software Engineering", 2, 2));
            System.out.println("Demo catalog seeded (3 books).");
        }
        if (members.isEmpty()) {
            System.out.println("No members found - seeding demo members...");
            registerMemberRecord(new Member("M001", "Aditi Sharma", "aditi@example.com"));
            registerMemberRecord(new Member("M002", "Rohan Verma", "rohan@example.com"));
            System.out.println("Demo members seeded (2 members).");
        }
    }
    static void listBooks() {
        System.out.println("Fetching catalog from memory cache...");
        if (books.isEmpty()) { System.out.println("No books."); return; }
        System.out.println("Found " + books.size() + " book(s):");
        books.values().stream().sorted(Comparator.comparing(b -> b.title)).forEach(System.out::println);
    }
    static void searchBooks(Scanner sc) {
        System.out.print("Keyword: ");
        String kw = sc.nextLine().trim().toLowerCase();
        System.out.println("Searching title/author/category for \"" + kw + "\"...");
        List<Book> found = new ArrayList<>();
        for (Book b : books.values())
            if (b.title.toLowerCase().contains(kw) || b.author.toLowerCase().contains(kw) || b.category.toLowerCase().contains(kw))
                found.add(b);
        if (found.isEmpty()) System.out.println("No matches.");
        else { System.out.println("Found " + found.size() + " match(es):"); found.forEach(System.out::println); }
    }
    static void addBook(Scanner sc) throws SQLException {
        System.out.print("ISBN: "); String isbn = sc.nextLine().trim();
        System.out.print("Title: "); String title = sc.nextLine().trim();
        System.out.print("Author: "); String author = sc.nextLine().trim();
        System.out.print("Category: "); String cat = sc.nextLine().trim();
        System.out.print("Copies: "); int copies = Integer.parseInt(sc.nextLine().trim());
        System.out.println("Saving book to database...");
        addBookRecord(new Book(isbn, title, author, cat, copies, copies));
        System.out.println("Book added: " + title);
    }
    static void addBookRecord(Book b) throws SQLException {
        PreparedStatement ps = conn.prepareStatement("INSERT INTO books VALUES(?,?,?,?,?,?)");
        ps.setString(1, b.isbn); ps.setString(2, b.title); ps.setString(3, b.author);
        ps.setString(4, b.category); ps.setInt(5, b.total); ps.setInt(6, b.available);
        ps.executeUpdate();
        books.put(b.isbn, b);
    }
    static void registerMember(Scanner sc) throws SQLException {
        System.out.print("Member ID: "); String id = sc.nextLine().trim();
        System.out.print("Name: "); String name = sc.nextLine().trim();
        System.out.print("Email: "); String email = sc.nextLine().trim();
        System.out.println("Saving member to database...");
        registerMemberRecord(new Member(id, name, email));
        System.out.println("Member registered: " + name);
    }
    static void registerMemberRecord(Member m) throws SQLException {
        PreparedStatement ps = conn.prepareStatement("INSERT INTO members(id,name,email,fine) VALUES(?,?,?,0)");
        ps.setString(1, m.userId); ps.setString(2, m.name); ps.setString(3, m.email);
        ps.executeUpdate();
        members.put(m.userId, m);
    }
    static synchronized void issueBook(Scanner sc) throws Exception {
        System.out.print("ISBN: "); String isbn = sc.nextLine().trim();
        System.out.print("Member ID: "); String memberId = sc.nextLine().trim();
        System.out.println("Validating member " + memberId + "...");
        Member m = members.get(memberId);
        if (m == null) throw new MemberNotFoundException("No member: " + memberId);
        System.out.println("Member OK. Checking availability of " + isbn + "...");
        Book b = books.get(isbn);
        if (b == null || b.available <= 0) throw new BookNotAvailableException("Not available: " + isbn);
        System.out.println("Book available. Checking borrow limit for " + memberId + "...");
        if (activeLoanCount(memberId) >= 3) throw new InvalidOperationException("Borrow limit (3) reached for " + memberId);
        System.out.println("Within borrow limit. Reserving copy and recording loan...");

        b.available--;
        PreparedStatement upd = conn.prepareStatement("UPDATE books SET available=? WHERE isbn=?");
        upd.setInt(1, b.available); upd.setString(2, isbn); upd.executeUpdate();

        LocalDate issue = LocalDate.now(), due = issue.plusDays(14);
        PreparedStatement ps = conn.prepareStatement("INSERT INTO loans(isbn,member_id,issue_date,due_date) VALUES(?,?,?,?)");
        ps.setString(1, isbn); ps.setString(2, memberId); ps.setString(3, issue.toString()); ps.setString(4, due.toString());
        ps.executeUpdate();
        m.borrowed++;
        logMsg("INFO", "Issued " + isbn + " to " + memberId);
        System.out.println("Issued. Due date: " + due);
    }
    static int activeLoanCount(String memberId) throws SQLException {
        PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM loans WHERE member_id=? AND returned=0");
        ps.setString(1, memberId);
        ResultSet rs = ps.executeQuery();
        return rs.next() ? rs.getInt(1) : 0;
    }
    static synchronized void returnBook(Scanner sc) throws Exception {
        System.out.print("Loan ID: "); int loanId = Integer.parseInt(sc.nextLine().trim());
        System.out.print("ISBN: "); String isbn = sc.nextLine().trim();
        System.out.print("Member ID: "); String memberId = sc.nextLine().trim();
        System.out.println("Validating member " + memberId + "...");
        Member m = members.get(memberId);
        if (m == null) throw new MemberNotFoundException("No member: " + memberId);
        System.out.println("Looking up active loan #" + loanId + "...");
        PreparedStatement find = conn.prepareStatement("SELECT due_date FROM loans WHERE loan_id=? AND member_id=? AND returned=0");
        find.setInt(1, loanId); find.setString(2, memberId);
        ResultSet rs = find.executeQuery();
        if (!rs.next()) throw new InvalidOperationException("No active loan #" + loanId + " for " + memberId);
        LocalDate due = LocalDate.parse(rs.getString(1)), today = LocalDate.now();
        System.out.println("Loan found. Calculating overdue fine (if any)...");
        long overdueDays = Math.max(0, ChronoUnit.DAYS.between(due, today));
        double fine = overdueDays * 5.0;

        System.out.println("Updating loan and book records in database...");
        PreparedStatement upd = conn.prepareStatement("UPDATE loans SET returned=1,return_date=? WHERE loan_id=?");
        upd.setString(1, today.toString()); upd.setInt(2, loanId); upd.executeUpdate();
        Book b = books.get(isbn);
        if (b != null) {
            b.available++;
            PreparedStatement bu = conn.prepareStatement("UPDATE books SET available=? WHERE isbn=?");
            bu.setInt(1, b.available); bu.setString(2, isbn); bu.executeUpdate();
        }
        m.borrowed = Math.max(0, m.borrowed - 1);
        if (fine > 0) {
            System.out.println("Book is " + overdueDays + " day(s) overdue. Applying fine...");
            m.fine += fine;
            PreparedStatement fu = conn.prepareStatement("UPDATE members SET fine=? WHERE id=?");
            fu.setDouble(1, m.fine); fu.setString(2, memberId); fu.executeUpdate();
            System.out.printf("Returned. Overdue fine: %.2f%n", fine);
        } else System.out.println("Returned on time. No fine.");
        logMsg("INFO", "Returned loan #" + loanId);
    }
    static void viewActiveLoans() throws SQLException {
        System.out.println("Fetching active loans from database...");
        ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM loans WHERE returned=0");
        boolean any = false;
        while (rs.next()) {
            any = true;
            System.out.printf("Loan#%d ISBN:%s Member:%s Due:%s%n", rs.getInt("loan_id"), rs.getString("isbn"),
                    rs.getString("member_id"), rs.getString("due_date"));
        }
        if (!any) System.out.println("No active loans.");
    }
    static void viewMembers() {
        System.out.println("Fetching members from memory cache...");
        if (members.isEmpty()) { System.out.println("No members."); return; }
        members.values().forEach(System.out::println);
    }
    /** Writes a combined text report (character stream) then a byte-stream backup copy. */
    static void generateReport() throws IOException {
        String path = "reports/report.txt";
        System.out.println("Writing character-stream report to " + path + "...");
        try (BufferedWriter w = new BufferedWriter(new FileWriter(path))) {
            w.write("LIBRARY REPORT - " + LocalDate.now()); w.newLine();
            w.write("--- Books ---"); w.newLine();
            for (Book b : books.values()) { w.write(b.toString()); w.newLine(); }
            w.write("--- Members ---"); w.newLine();
            for (Member m : members.values()) { w.write(m.toString()); w.newLine(); }
        }
        System.out.println("Creating byte-stream backup copy (report.bak)...");
        try (InputStream in = new FileInputStream(path); OutputStream out = new FileOutputStream("reports/report.bak")) {
            byte[] buf = new byte[4096]; int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
        System.out.println("Report written to " + path + " (+ byte-stream backup .bak)");
        logMsg("INFO", "Report generated");
    }
    static void logMsg(String level, String msg) {
        String line = "[" + LocalDate.now() + "] [" + level + "] " + msg;
        System.out.println(line);
        log.println(line);
    }
}

abstract class User {
    String userId, name, email;
    User(String id, String n, String e) { userId = id; name = n; email = e; }
    abstract String role();
    public String toString() { return "[" + role() + "] " + name + " (" + userId + ") - " + email; }
}
class Member extends User {
    int borrowed = 0;
    double fine = 0;
    Member(String id, String n, String e) { super(id, n, e); }
    String role() { return "MEMBER"; }
    public String toString() { return super.toString() + String.format(" | Borrowed:%d Fine:%.2f", borrowed, fine); }
}
class Librarian extends User {
    String staffCode;
    Librarian(String id, String n, String e, String s) { super(id, n, e); staffCode = s; }
    String role() { return "LIBRARIAN"; }
}
class Book {
    String isbn, title, author, category;
    int total, available;
    Book(String isbn, String t, String a, String c, int total, int avail) {
        this.isbn = isbn; this.title = t; this.author = a; this.category = c; this.total = total; this.available = avail;
    }
    public String toString() {
        return String.format("%-8s | %-28s | %-18s | %-14s | %d/%d", isbn, title, author, category, available, total);
    }
}
class BookNotAvailableException extends Exception { BookNotAvailableException(String m) { super(m); } }
class MemberNotFoundException extends Exception { MemberNotFoundException(String m) { super(m); } }
class InvalidOperationException extends RuntimeException { InvalidOperationException(String m) { super(m); } }

class NotificationService implements Runnable {
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                System.out.println("[NotificationThread] Scanning active loans for overdue items...");
                ResultSet rs = LibraryManagementSystem.conn.createStatement()
                        .executeQuery("SELECT loan_id,member_id,due_date FROM loans WHERE returned=0");
                LocalDate today = LocalDate.now();
                int overdueCount = 0;
                while (rs.next()) {
                    LocalDate due = LocalDate.parse(rs.getString("due_date"));
                    long days = ChronoUnit.DAYS.between(due, today);
                    if (days > 0) {
                        overdueCount++;
                        LibraryManagementSystem.logMsg("WARN",
                                "Loan #" + rs.getInt("loan_id") + " for " + rs.getString("member_id") + " is " + days + " day(s) overdue");
                    }
                }
                System.out.println("[NotificationThread] Scan complete. " + overdueCount + " overdue loan(s) found. Sleeping 15s...");
                Thread.sleep(15000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                LibraryManagementSystem.logMsg("ERROR", "NotificationService: " + e.getMessage());
            }
        }
    }
}
