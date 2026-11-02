package service;

import database.DatabaseConnection;
import exception.AppException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import model.Room;
import model.User;

/** Creates, loads and ends the private chat rooms. */
public class RoomService implements Service {

    @Override
    public String serviceName() {
        return "RoomService";
    }

    // Method overloading: same name, different parameters
    public Room createRoom(User first, User second) throws AppException {
        return createRoom(first.getId(), second.getId());
    }

    public Room createRoom(int user1Id, int user2Id) throws AppException {
        if (user1Id == user2Id) {
            throw new AppException("You cannot chat with yourself.", 400);
        }
        String sql = "INSERT INTO rooms (user1_id, user2_id) VALUES (?, ?)";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, user1Id);
            ps.setInt(2, user2Id);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return new Room(keys.getInt(1), user1Id, user2Id);
            }
        } catch (SQLException e) {
            throw new AppException("Could not create chat room: " + e.getMessage(), 500, e);
        }
    }

    /** The user's current ACTIVE room, or null when they are not chatting. */
    public Room getActiveRoomFor(int userId) throws AppException {
        String sql = "SELECT id FROM rooms WHERE status = 'ACTIVE' AND (user1_id = ? OR user2_id = ?) "
                + "ORDER BY id DESC LIMIT 1";
        int roomId = -1;
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    roomId = rs.getInt("id");
                }
            }
        } catch (SQLException e) {
            throw new AppException("Could not look up your chat: " + e.getMessage(), 500, e);
        }
        return roomId == -1 ? null : getRoom(roomId, userId);
    }

    /** Loads a room as seen by viewerId (fills in the partner's name). */
    public Room getRoom(int roomId, int viewerId) throws AppException {
        String sql = "SELECT r.id, r.user1_id, r.user2_id, r.status, r.ended_by, r.created_at, "
                + "u.id AS partner_id, u.username AS partner_name "
                + "FROM rooms r JOIN users u ON u.id = IF(r.user1_id = ?, r.user2_id, r.user1_id) "
                + "WHERE r.id = ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, viewerId);
            ps.setInt(2, roomId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new AppException("Chat not found.", 404);
                }
                int endedByRaw = rs.getInt("ended_by");
                Integer endedBy = rs.wasNull() ? null : endedByRaw;
                Room room = new Room(
                        rs.getInt("id"),
                        rs.getInt("user1_id"),
                        rs.getInt("user2_id"),
                        rs.getString("status"),
                        endedBy,
                        rs.getTimestamp("created_at"));
                room.setPartner(rs.getInt("partner_id"), rs.getString("partner_name"));
                if (!room.hasParticipant(viewerId)) {
                    throw new AppException("You are not part of this chat.", 403);
                }
                return room;
            }
        } catch (SQLException e) {
            throw new AppException("Could not load chat: " + e.getMessage(), 500, e);
        }
    }

    /** Marks the room as ENDED. Calling it twice is harmless. */
    public void endRoom(int roomId, int userId) throws AppException {
        String sql = "UPDATE rooms SET status = 'ENDED', ended_by = ?, ended_at = NOW() "
                + "WHERE id = ? AND status = 'ACTIVE' AND (user1_id = ? OR user2_id = ?)";
        int changed;
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, roomId);
            ps.setInt(3, userId);
            ps.setInt(4, userId);
            changed = ps.executeUpdate();
        } catch (SQLException e) {
            throw new AppException("Could not end chat: " + e.getMessage(), 500, e);
        }
        if (changed == 0) {
            getRoom(roomId, userId);   // throws 404/403 if the room is invalid for this user
        }
    }
}
