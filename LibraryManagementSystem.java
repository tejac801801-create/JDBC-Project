import java.sql.*;
import java.util.Scanner;

public class LibraryManagementSystem {
    private static final String URL = "jdbc:mysql://localhost:3306/jdbc_projects";
    private static final String DB_USER = "hello";
    private static final String DB_PASSWORD = "hello";

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(URL, DB_USER, DB_PASSWORD);
    }

    public static void main(String[] args) {
        try (Scanner sc = new Scanner(System.in)) {
            while (true) {
                System.out.println("\nLIBRARY MANAGEMENT SYSTEM");
                System.out.println("1. Add book\n2. View books\n3. Search book\n4. Register member");
                System.out.println("5. Issue book\n6. Return book\n7. View issue records\n0. Exit");
                System.out.print("Choice: ");
                int choice;
                try { choice = Integer.parseInt(sc.nextLine().trim()); }
                catch (NumberFormatException e) { System.out.println("Enter a valid number."); continue; }
                try {
                    switch (choice) {
                        case 1 -> addBook(sc);
                        case 2 -> viewBooks();
                        case 3 -> searchBook(sc);
                        case 4 -> addMember(sc);
                        case 5 -> issueBook(sc);
                        case 6 -> returnBook(sc);
                        case 7 -> viewIssues();
                        case 0 -> { System.out.println("Goodbye!"); return; }
                        default -> System.out.println("Invalid choice.");
                    }
                } catch (SQLException e) { System.out.println("Database error: " + e.getMessage()); }
                  catch (IllegalArgumentException e) { System.out.println("Input error: " + e.getMessage()); }
            }
        }
    }

    private static void addBook(Scanner sc) throws SQLException {
        System.out.print("Book ID: "); int id = Integer.parseInt(sc.nextLine().trim());
        System.out.print("Title: "); String title = sc.nextLine().trim();
        System.out.print("Author: "); String author = sc.nextLine().trim();
        System.out.print("Category: "); String category = sc.nextLine().trim();
        System.out.print("Available copies: "); int copies = Integer.parseInt(sc.nextLine().trim());
        if (copies < 0) throw new IllegalArgumentException("Copies cannot be negative.");
        String sql = "insert into books(book_id,title,author,category,available_copies) values(?,?,?,?,?)";
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1,id); ps.setString(2,title); ps.setString(3,author);
            ps.setString(4,category); ps.setInt(5,copies); ps.executeUpdate();
            System.out.println("Book added successfully.");
        }
    }

    private static void viewBooks() throws SQLException {
        try (Connection con = connect(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("select * from books order by book_id")) {
            System.out.println("ID | Title | Author | Category | Copies"); boolean found = false;
            while (rs.next()) { found = true; System.out.printf("%d | %s | %s | %s | %d%n",
                rs.getInt("book_id"), rs.getString("title"), rs.getString("author"),
                rs.getString("category"), rs.getInt("available_copies")); }
            if (!found) System.out.println("No books found.");
        }
    }

    private static void searchBook(Scanner sc) throws SQLException {
        System.out.print("Title keyword: "); String keyword = sc.nextLine().trim();
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(
                "select * from books where title like ?")) {
            ps.setString(1, "%" + keyword + "%");
            try (ResultSet rs = ps.executeQuery()) {
                boolean found = false;
                while (rs.next()) { found = true; System.out.printf("%d | %s | %s | %s | Copies: %d%n",
                    rs.getInt("book_id"), rs.getString("title"), rs.getString("author"),
                    rs.getString("category"), rs.getInt("available_copies")); }
                if (!found) System.out.println("No matching books found.");
            }
        }
    }

    private static void addMember(Scanner sc) throws SQLException {
        System.out.print("Member ID: "); int id = Integer.parseInt(sc.nextLine().trim());
        System.out.print("Member name: "); String name = sc.nextLine().trim();
        System.out.print("Email: "); String email = sc.nextLine().trim();
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(
                "insert into members(member_id,member_name,email) values(?,?,?)")) {
            ps.setInt(1,id); ps.setString(2,name); ps.setString(3,email); ps.executeUpdate();
            System.out.println("Member registered.");
        }
    }

    private static void issueBook(Scanner sc) throws SQLException {
        System.out.print("Book ID: "); int bookId = Integer.parseInt(sc.nextLine().trim());
        System.out.print("Member ID: "); int memberId = Integer.parseInt(sc.nextLine().trim());
        try (Connection con = connect()) {
            con.setAutoCommit(false);
            try {
                int copies;
                try (PreparedStatement ps = con.prepareStatement(
                    "select available_copies from books where book_id=? for update")) {
                    ps.setInt(1,bookId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Book ID not found.");
                        copies = rs.getInt(1);
                    }
                }
                if (copies < 1) throw new SQLException("No available copies.");
                try (PreparedStatement ps = con.prepareStatement(
                    "insert into book_issues(book_id,member_id,issue_date,status) values(?,?,current_date(),'ISSUED')");
                     PreparedStatement up = con.prepareStatement(
                    "update books set available_copies=available_copies-1 where book_id=?")) {
                    ps.setInt(1,bookId); ps.setInt(2,memberId); ps.executeUpdate();
                    up.setInt(1,bookId); up.executeUpdate();
                }
                con.commit(); System.out.println("Book issued successfully.");
            } catch (SQLException e) { con.rollback(); throw e; }
            finally { con.setAutoCommit(true); }
        }
    }

    private static void returnBook(Scanner sc) throws SQLException {
        System.out.print("Issue ID to return: "); int issueId = Integer.parseInt(sc.nextLine().trim());
        try (Connection con = connect()) {
            con.setAutoCommit(false);
            try {
                int bookId;
                try (PreparedStatement ps = con.prepareStatement(
                    "select book_id from book_issues where issue_id=? and status='ISSUED' for update")) {
                    ps.setInt(1,issueId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Active issue record not found.");
                        bookId = rs.getInt(1);
                    }
                }
                try (PreparedStatement ps = con.prepareStatement(
                    "update book_issues set status='RETURNED', return_date=current_date() where issue_id=?");
                     PreparedStatement up = con.prepareStatement(
                    "update books set available_copies=available_copies+1 where book_id=?")) {
                    ps.setInt(1,issueId); ps.executeUpdate(); up.setInt(1,bookId); up.executeUpdate();
                }
                con.commit(); System.out.println("Book returned successfully.");
            } catch (SQLException e) { con.rollback(); throw e; }
            finally { con.setAutoCommit(true); }
        }
    }

    private static void viewIssues() throws SQLException {
        String sql = "select bi.issue_id,b.title,m.member_name,bi.issue_date,bi.return_date,bi.status " +
                     "from book_issues bi join books b on bi.book_id=b.book_id " +
                     "join members m on bi.member_id=m.member_id order by bi.issue_id";
        try (Connection con = connect(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            System.out.println("Issue ID | Book | Member | Issue Date | Return Date | Status");
            boolean found = false;
            while (rs.next()) { found = true; System.out.printf("%d | %s | %s | %s | %s | %s%n",
                rs.getInt("issue_id"), rs.getString("title"), rs.getString("member_name"),
                rs.getDate("issue_date"), rs.getDate("return_date"), rs.getString("status")); }
            if (!found) System.out.println("No issue records found.");
        }
    }
}
