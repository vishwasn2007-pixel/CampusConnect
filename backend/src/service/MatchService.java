package service;

import database.DatabaseConnection;
import exception.AppException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import model.Room;
import util.Json;

/**
 * The "Omegle" part: pairs two users who are both looking for a stranger.
 *
 * Every waiting browser calls findMatch() about every 1.5 seconds. That call
 * both refreshes the user's place in waiting_queue and tries to grab a partner.
 */
public class MatchService implements Service {

    /** A queue row not refreshed for this long belongs to a closed tab. */
    public static final int STALE_SECONDS = 10;

    // Only one thread may match people at a time, otherwise two users
    // could both be paired with the same third person.
    private static final Object MATCH_LOCK = new Object();

    private final RoomService roomService = new RoomService();

    @Override
    public String serviceName() {
        return "MatchService";
    }

    /** Returns the new/existing Room if matched, or null if still waiting. */
    public Room findMatch(int userId) throws AppException {
        synchronized (MATCH_LOCK) {
            Room existing = roomService.getActiveRoomFor(userId);
            if (existing != null) {
                leaveQueue(userId);
                return existing;
            }

            try (Connection con = DatabaseConnection.getConnection()) {
                removeStaleEntries(con);
                refreshQueueEntry(con, userId);

                int partnerId = findWaitingPartner(con, userId);
                if (partnerId == -1) {
                    return null;
                }

                removeFromQueue(con, userId);
                removeFromQueue(con, partnerId);
                Room created = roomService.createRoom(userId, partnerId);
                return roomService.getRoom(created.getId(), userId);
            } catch (SQLException e) {
                throw new AppException("Matching failed: " + e.getMessage(), 500, e);
            }
        }
    }

    public void cancelSearch(int userId) throws AppException {
        leaveQueue(userId);
    }

    public boolean isWaiting(int userId) throws AppException {
        String sql = "SELECT 1 FROM waiting_queue WHERE user_id = ? "
                + "AND last_seen > (NOW() - INTERVAL " + STALE_SECONDS + " SECOND)";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new AppException("Could not check queue: " + e.getMessage(), 500, e);
        }
    }

    /** Numbers for the home screen, read from the chat_stats view. */
    public String getStatsJson() throws AppException {
        try (Connection con = DatabaseConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM chat_stats")) {
            rs.next();
            return Json.obj(
                    "totalUsers", rs.getInt("total_users"),
                    "activeChats", rs.getInt("active_chats"),
                    "totalChats", rs.getInt("total_chats"),
                    "totalMessages", rs.getInt("total_messages"),
                    "usersWaiting", rs.getInt("users_waiting"));
        } catch (SQLException e) {
            throw new AppException("Could not load stats: " + e.getMessage(), 500, e);
        }
    }

    // ---------- private helpers ----------

    private void refreshQueueEntry(Connection con, int userId) throws SQLException {
        String sql = "INSERT INTO waiting_queue (user_id) VALUES (?) "
                + "ON DUPLICATE KEY UPDATE last_seen = NOW()";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        }
    }

    /** Oldest fresh waiting user who is not me and not already chatting (subqueries). */
    private int findWaitingPartner(Connection con, int userId) throws SQLException {
        String sql = "SELECT user_id FROM waiting_queue "
                + "WHERE user_id <> ? "
                + "AND last_seen > (NOW() - INTERVAL " + STALE_SECONDS + " SECOND) "
                + "AND user_id NOT IN (SELECT user1_id FROM rooms WHERE status = 'ACTIVE') "
                + "AND user_id NOT IN (SELECT user2_id FROM rooms WHERE status = 'ACTIVE') "
                + "ORDER BY joined_at LIMIT 1";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("user_id") : -1;
            }
        }
    }

    private void removeStaleEntries(Connection con) throws SQLException {
        try (Statement st = con.createStatement()) {
            st.executeUpdate("DELETE FROM waiting_queue WHERE last_seen < (NOW() - INTERVAL 60 SECOND)");
        }
    }

    private void removeFromQueue(Connection con, int userId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM waiting_queue WHERE user_id = ?")) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        }
    }

    private void leaveQueue(int userId) throws AppException {
        try (Connection con = DatabaseConnection.getConnection()) {
            removeFromQueue(con, userId);
        } catch (SQLException e) {
            throw new AppException("Could not leave queue: " + e.getMessage(), 500, e);
        }
    }
}
