package model;

import java.sql.Timestamp;
import util.Json;

/** A registered account. The password hash is never sent to the browser. */
public class User extends BaseEntity {

    public static final int MIN_PASSWORD_LENGTH = 6;

    private String username;
    private String email;
    private String passwordHash;

    // Constructor used when reading a row from the database
    public User(int id, String username, String email, String passwordHash, Timestamp createdAt) {
        super(id, createdAt);
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    // Overloaded constructor used for a brand-new user (no id yet)
    public User(String username, String email, String passwordHash) {
        this(0, username, email, passwordHash, null);
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    @Override
    public String describe() {
        return "User#" + getId() + " (" + username + ")";
    }

    @Override
    public String toJson() {
        return Json.obj("id", getId(), "username", username, "email", email);
    }
}
