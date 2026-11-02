package model;

import util.Json;

/** One chat message sent inside a room. */
public class Message extends BaseEntity {

    public static final int MAX_LENGTH = 500;

    private final int roomId;
    private final int senderId;
    private final String senderUsername;
    private final String content;
    private final long sentEpoch;      // seconds since 1970

    public Message(int id, int roomId, int senderId, String senderUsername, String content, long sentEpoch) {
        super(id, null);
        this.roomId = roomId;
        this.senderId = senderId;
        this.senderUsername = senderUsername;
        this.content = content;
        this.sentEpoch = sentEpoch;
    }

    public int getRoomId() {
        return roomId;
    }

    public int getSenderId() {
        return senderId;
    }

    public String getSenderUsername() {
        return senderUsername;
    }

    public String getContent() {
        return content;
    }

    public long getSentEpoch() {
        return sentEpoch;
    }

    @Override
    public String describe() {
        return "Message#" + getId() + " from " + senderUsername + ": " + content;
    }

    @Override
    public String toJson() {
        return Json.obj(
                "id", getId(),
                "roomId", roomId,
                "senderId", senderId,
                "sender", senderUsername,
                "content", content,
                "sentAt", sentEpoch);
    }
}
