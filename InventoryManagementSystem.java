import java.sql.*;
import java.util.Scanner;

public class InventoryManagementSystem {
    private static final String URL = "jdbc:mysql://localhost:3306/jdbc_projects";
    private static final String DB_USER = "hello";
    private static final String DB_PASSWORD = "hello"; 

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(URL, DB_USER, DB_PASSWORD);
    }

    public static void main(String[] args) {
        try (Scanner sc = new Scanner(System.in)) {
            while (true) {
                System.out.println("\nINVENTORY MANAGEMENT SYSTEM");
                System.out.println("1. Add product\n2. View products\n3. Update stock");
                System.out.println("4. Record sale\n5. View sales report\n0. Exit");
                System.out.print("Choice: ");
                int choice;
                try { choice = Integer.parseInt(sc.nextLine().trim()); }
                catch (NumberFormatException e) { System.out.println("Enter a valid number."); continue; }
                try {
                    switch (choice) {
                        case 1 -> addProduct(sc);
                        case 2 -> viewProducts();
                        case 3 -> updateStock(sc);
                        case 4 -> recordSale(sc);
                        case 5 -> salesReport();
                        case 0 -> { System.out.println("Goodbye!"); return; }
                        default -> System.out.println("Invalid choice.");
                    }
                } catch (SQLException e) { System.out.println("Database error: " + e.getMessage()); }
                  catch (IllegalArgumentException e) { System.out.println("Input error: " + e.getMessage()); }
            }
        }
    }

    private static void addProduct(Scanner sc) throws SQLException {
        System.out.print("Product ID: "); int id = Integer.parseInt(sc.nextLine().trim());
        System.out.print("Product name: "); String name = sc.nextLine().trim();
        System.out.print("Category: "); String category = sc.nextLine().trim();
        System.out.print("Initial quantity: "); int qty = Integer.parseInt(sc.nextLine().trim());
        System.out.print("Unit price: "); double price = Double.parseDouble(sc.nextLine().trim());
        if (qty < 0 || price < 0) throw new IllegalArgumentException("Quantity and price must not be negative.");
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(
            "insert into products(product_id,product_name,category,quantity,unit_price) values(?,?,?,?,?)")) {
            ps.setInt(1,id); ps.setString(2,name); ps.setString(3,category);
            ps.setInt(4,qty); ps.setDouble(5,price); ps.executeUpdate();
            System.out.println("Product added successfully.");
        }
    }

    private static void viewProducts() throws SQLException {
        try (Connection con = connect(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("select * from products order by product_id")) {
            System.out.println("ID | Product | Category | Quantity | Unit Price"); boolean found = false;
            while (rs.next()) { found = true; System.out.printf("%d | %s | %s | %d | %.2f%n",
                rs.getInt("product_id"), rs.getString("product_name"), rs.getString("category"),
                rs.getInt("quantity"), rs.getDouble("unit_price")); }
            if (!found) System.out.println("No products found.");
        }
    }

    private static void updateStock(Scanner sc) throws SQLException {
        System.out.print("Product ID: "); int id = Integer.parseInt(sc.nextLine().trim());
        System.out.print("Quantity change (negative reduces stock): "); int delta = Integer.parseInt(sc.nextLine().trim());
        String sql = "update products set quantity=quantity+? where product_id=? and quantity+?>=0";
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1,delta); ps.setInt(2,id); ps.setInt(3,delta);
            System.out.println(ps.executeUpdate() > 0 ? "Stock updated." : "Product not found or stock cannot be negative.");
        }
    }

    private static void recordSale(Scanner sc) throws SQLException {
        System.out.print("Product ID: "); int id = Integer.parseInt(sc.nextLine().trim());
        System.out.print("Quantity sold: "); int sold = Integer.parseInt(sc.nextLine().trim());
        if (sold <= 0) throw new IllegalArgumentException("Quantity sold must be positive.");
        try (Connection con = connect()) {
            con.setAutoCommit(false);
            try {
                double price;
                try (PreparedStatement ps = con.prepareStatement(
                    "select unit_price,quantity from products where product_id=? for update")) {
                    ps.setInt(1,id);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Product not found.");
                        price = rs.getDouble("unit_price");
                        if (rs.getInt("quantity") < sold) throw new SQLException("Insufficient stock.");
                    }
                }
                try (PreparedStatement up = con.prepareStatement(
                    "update products set quantity=quantity-? where product_id=?");
                     PreparedStatement ins = con.prepareStatement(
                    "insert into sales(product_id,quantity_sold,total_amount) values(?,?,?)")) {
                    up.setInt(1,sold); up.setInt(2,id); up.executeUpdate();
                    ins.setInt(1,id); ins.setInt(2,sold);
                    ins.setBigDecimal(3, java.math.BigDecimal.valueOf(price).multiply(java.math.BigDecimal.valueOf(sold)));
                    ins.executeUpdate();
                }
                con.commit(); System.out.printf("Sale recorded. Total: %.2f%n", price * sold);
            } catch (SQLException e) { con.rollback(); throw e; }
            finally { con.setAutoCommit(true); }
        }
    }

    private static void salesReport() throws SQLException {
        String sql = "select s.sale_id,p.product_name,s.quantity_sold,s.sale_date,s.total_amount " +
                     "from sales s join products p on s.product_id=p.product_id order by s.sale_id";
        try (Connection con = connect(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            System.out.println("Sale ID | Product | Quantity | Date | Total");
            boolean found = false; double grandTotal = 0;
            while (rs.next()) { found = true; double total = rs.getDouble("total_amount"); grandTotal += total;
                System.out.printf("%d | %s | %d | %s | %.2f%n", rs.getInt("sale_id"),
                    rs.getString("product_name"), rs.getInt("quantity_sold"), rs.getTimestamp("sale_date"), total); }
            if (!found) System.out.println("No sales found.");
            System.out.printf("Grand total sales: %.2f%n", grandTotal);
        }
    }
}
