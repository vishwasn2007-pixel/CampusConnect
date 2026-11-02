package database;

import exception.AppException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import util.Config;

/** The one place that knows how to open a JDBC connection to MySQL. */
public class DatabaseConnection {

    private static final String URL = Config.get("db.url",
            "jdbc:mysql://localhost:3306/campusconnect"
                    + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8");
    private static final String USER = Config.get("db.user", "root");
    private static final String PASSWORD = Config.get("db.password", "vishwas@280107rekha_anusha");

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("WARNING: MySQL JDBC driver not found. "
                    + "Put mysql-connector-j-*.jar inside backend/lib/");
        }
    }

    private DatabaseConnection() {
    }

    public static Connection getConnection() throws AppException {
        try {
            return DriverManager.getConnection(URL, USER, PASSWORD);
        } catch (SQLException e) {
            throw new AppException("Database connection failed: " + e.getMessage(), 500, e);
        }
    }

    /** Called once at startup so problems show up immediately. */
    public static void testConnection() throws AppException {
        try (Connection con = getConnection()) {
            System.out.println("Connected to MySQL as '" + USER + "'.");
        } catch (SQLException e) {
            throw new AppException("Database test failed: " + e.getMessage(), 500, e);
        }
    }
}
