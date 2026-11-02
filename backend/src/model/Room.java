package model;

import java.sql.Timestamp;
import util.Json;

/**
 * One private chat session between two matched users.
 * partnerId / partnerUsername describe "the other person" from the point of
 * view of whoever asked for the room.
 */
public class Room extends BaseEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_ENDED = "ENDED";

    private int user1Id;
    private int user2Id;
    private String status;
    private Integer endedBy;          // null while the chat is still active
    private int partnerId;
    private String partnerUsername;

    public Room(int id, int user1Id, int user2Id, String status, Integer endedBy, Timestamp createdAt) {
        super(id, createdAt);
        this.user1Id = user1Id;
        this.user2Id = user2Id;
        this.status = status;
        this.endedBy = endedBy;
    }

    // Overloaded constructor for a room that has just been created
    public Room(int id, int user1Id, int user2Id) {
        this(id, user1Id, user2Id, STATUS_ACTIVE, null, null);
    }

    public int getUser1Id() {
        return user1Id;
    }

    public int getUser2Id() {
        return user2Id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getEndedBy() {
        return endedBy;
    }

    public void setEndedBy(Integer endedBy) {
        this.endedBy = endedBy;
    }

    public int getPartnerId() {
        return partnerId;
    }

    public String getPartnerUsername() {
        return partnerUsername;
    }

    public void setPartner(int partnerId, String partnerUsername) {
        this.partnerId = partnerId;
        this.partnerUsername = partnerUsername;
    }

    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }

    public boolean hasParticipant(int userId) {
        return user1Id == userId || user2Id == userId;
    }

    @Override
    public String describe() {
        return "Room#" + getId() + " [" + user1Id + " <-> " + user2Id + ", " + status + "]";
    }

    @Override
    public String toJson() {
        return Json.obj(
                "id", getId(),
                "status", status,
                "partnerId", partnerId,
                "partner", partnerUsername,
                "endedBy", endedBy);
    }
}
