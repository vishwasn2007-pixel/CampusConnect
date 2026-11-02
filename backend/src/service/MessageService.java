package service;

import database.DatabaseConnection;
import exception.AppException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import model.Message;
import model.Room;

/** Sending and reading chat messages. */
public class MessageService implements Service {

    private final RoomService roomService = new RoomService();

    @Override
    public String serviceName() {
        return "MessageService";
    }

    public Message send(int roomId, int senderId, String content) throws AppException {
        if (content == null || content.trim().isEmpty()) {
            throw new AppException("Message cannot be empty.", 400);
        }
        String text = content.trim();
        if (text.length() > Message.MAX_LENGTH) {
            throw new AppException("Message is too long (max " + Message.MAX_LENGTH + " characters).", 400);
        }

        Room room = roomService.getRoom(roomId, senderId);   // also checks the sender belongs here
        if (!room.isActive()) {
            throw new AppException("This chat has ended.", 409);
        }

        String sql = "INSERT INTO messages (room_id, sender_id, content) VALUES (?, ?, ?)";
        int newId;
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, roomId);
            ps.setInt(2, senderId);
            ps.setString(3, text);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                newId = keys.getInt(1);
            }
        } catch (SQLException e) {
            throw new AppException("Could not send message: " + e.getMessage(), 500, e);
        }
        return getById(newId);
    }

    // Method overloading: all messages ...
    public List<Message> getMessages(int roomId, int viewerId) throws AppException {
        return getMessages(roomId, viewerId, 0);
    }

    // ... or only the ones newer than afterId
    public List<Message> getMessages(int roomId, int viewerId, int afterId) throws AppException {
        roomService.getRoom(roomId, viewerId);   // permission check
        String sql = "SELECT id, room_id, sender_id, sender_username, content, sent_epoch "
                + "FROM message_details WHERE room_id = ? AND id > ? ORDER BY id";
        List<Message> result = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, roomId);
            ps.setInt(2, afterId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapMessage(rs));
                }
            }
        } catch (SQLException e) {
            throw new AppException("Could not load messages: " + e.getMessage(), 500, e);
        }
        return result;
    }

    private Message getById(int id) throws AppException {
        String sql = "SELECT id, room_id, sender_id, sender_username, content, sent_epoch "
                + "FROM message_details WHERE id = ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapMessage(rs);
                }
            }
        } catch (SQLException e) {
            throw new AppException("Could not read message: " + e.getMessage(), 500, e);
        }
        throw new AppException("Message not found.", 404);
    }

    private Message mapMessage(ResultSet rs) throws SQLException {
        return new Message(
                rs.getInt("id"),
                rs.getInt("room_id"),
                rs.getInt("sender_id"),
                rs.getString("sender_username"),
                rs.getString("content"),
                rs.getLong("sent_epoch"));
    }
}
