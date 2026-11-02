package model;

/**
 * Contract (interface): anything that can describe itself as JSON.
 * User, Room and Message all implement it.
 */
@FunctionalInterface
public interface Jsonable {
    String toJson();
}
