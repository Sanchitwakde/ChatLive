package server;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class MessageStore {
    public static List<String> getPrivateMessages(String user1, String user2) {
    List<String> list = new ArrayList<>();
    try {
        Connection conn = DatabaseConnection.getConnection();
        String sql = """
        SELECT sender, message, timestamp 
        FROM messages
        WHERE 
            (sender = ? AND receiver = ?)
         OR (sender = ? AND receiver = ?)
        ORDER BY timestamp ASC
        """;
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setString(1, user1);
        ps.setString(2, user2);
        ps.setString(3, user2);
        ps.setString(4, user1);

        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            Timestamp ts = rs.getTimestamp("timestamp");
            String sender = rs.getString("sender");
            String msg = rs.getString("message");
            list.add("[" + ts + "] " + sender + ": " + msg);
        }
        rs.close();
        ps.close();
    } catch (Exception e) {
        e.printStackTrace();
    }
    return list;
}


    public static void saveMessage(String sender, String receiver, String group, String message) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = "INSERT INTO messages(sender, receiver, groupname, message) VALUES (?,?,?,?)";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, sender);
            ps.setString(2, receiver);
            ps.setString(3, group);
            ps.setString(4, message);
            ps.executeUpdate();
            ps.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static List<String> getGroupMessages(String group) {
        List<String> list = new ArrayList<>();
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = "SELECT sender, message, timestamp FROM messages WHERE groupname = ? ORDER BY timestamp ASC";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, group);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Timestamp ts = rs.getTimestamp("timestamp");
                String sender = rs.getString("sender");
                String msg = rs.getString("message");
                list.add("[" + ts + "] " + sender + ": " + msg);
            }
            rs.close();
            ps.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }
}
