package model;

import java.sql.Timestamp;

/**
 * Abstract parent of every database-backed object.
 * Holds what all entities share (id + creation time) and forces each
 * child class to override describe() and toJson().
 */
public abstract class BaseEntity implements Jsonable {

    private int id;
    private final Timestamp createdAt;

    protected BaseEntity(int id, Timestamp createdAt) {
        this.id = id;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    /** Short human-readable description, written differently by each child. */
    public abstract String describe();

    @Override
    public String toString() {
        return describe();   // dynamic method dispatch: calls the child's version
    }
}
