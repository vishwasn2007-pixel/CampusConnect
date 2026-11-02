package service;

import database.DatabaseConnection;
import exception.AppException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.regex.Pattern;
import model.User;

/** Registration, login and user lookup. */
public class UserService implements Service {

    private static final String PEPPER = "campusconnect-2026";
    private static final Pattern USERNAME_RULE = Pattern.compile("^[A-Za-z0-9_]{3,30}$");
    private static final Pattern EMAIL_RULE = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    @Override
    public String serviceName() {
        return "UserService";
    }

    public User register(String username, String email, String password) throws AppException {
        String cleanName = username == null ? "" : username.trim();
        String cleanEmail = email == null ? "" : email.trim().toLowerCase();

        if (!USERNAME_RULE.matcher(cleanName).matches()) {
            throw new AppException("Username must be 3-30 characters: letters, numbers or underscore.", 400);
        }
        if (!EMAIL_RULE.matcher(cleanEmail).matches() || cleanEmail.length() > 100) {
            throw new AppException("Please enter a valid email address.", 400);
        }
        if (password == null || password.length() < User.MIN_PASSWORD_LENGTH) {
            throw new AppException("Password must be at least " + User.MIN_PASSWORD_LENGTH + " characters.", 400);
        }

        String hash = hashPassword(cleanName, password);
        String sql = "INSERT INTO users (username, email, password_hash) VALUES (?, ?, ?)";

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, cleanName);
            ps.setString(2, cleanEmail);
            ps.setString(3, hash);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return new User(keys.getInt(1), cleanName, cleanEmail, hash, null);
            }
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new AppException("That username or email is already registered.", 409, e);
        } catch (SQLException e) {
            throw new AppException("Could not register: " + e.getMessage(), 500, e);
        }
    }

    /** identifier can be either the username or the email. */
    public User login(String identifier, String password) throws AppException {
        if (identifier == null || identifier.trim().isEmpty() || password == null || password.isEmpty()) {
            throw new AppException("Enter your username/email and password.", 400);
        }
        String sql = "SELECT id, username, email, password_hash, created_at FROM users "
                + "WHERE username = ? OR email = ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, identifier.trim());
            ps.setString(2, identifier.trim().toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    User user = mapUser(rs);
                    String attempt = hashPassword(user.getUsername(), password);
                    boolean same = MessageDigest.isEqual(
                            attempt.getBytes(StandardCharsets.UTF_8),
                            user.getPasswordHash().getBytes(StandardCharsets.UTF_8));
                    if (same) {
                        return user;
                    }
                }
            }
        } catch (SQLException e) {
            throw new AppException("Could not log in: " + e.getMessage(), 500, e);
        }
        throw new AppException("Wrong username/email or password.", 401);
    }

    public User getById(int id) throws AppException {
        String sql = "SELECT id, username, email, password_hash, created_at FROM users WHERE id = ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapUser(rs);
                }
            }
        } catch (SQLException e) {
            throw new AppException("Could not load user: " + e.getMessage(), 500, e);
        }
        throw new AppException("User not found.", 404);
    }

    private User mapUser(ResultSet rs) throws SQLException {
        return new User(
                rs.getInt("id"),
                rs.getString("username"),
                rs.getString("email"),
                rs.getString("password_hash"),
                rs.getTimestamp("created_at"));
    }

    /** SHA-256 of (username + pepper + password), as 64 hex characters. */
    public static String hashPassword(String username, String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(
                    (username.toLowerCase() + ":" + PEPPER + ":" + password).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
