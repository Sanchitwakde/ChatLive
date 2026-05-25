package server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class ClientHandler extends Thread {

    private final Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String username;

    private static final List<ClientHandler> clients = new ArrayList<>();

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    public String getUsername() {
        return username;
    }

    public void sendMessage(String msg) {
        out.println(msg);
    }

    private String ts() {
        return "[" + LocalTime.now().truncatedTo(ChronoUnit.SECONDS) + "]";
    }

    // ----- user + group list broadcasting -----

    private static void broadcastUserList() {
        StringBuilder sb = new StringBuilder("USERS:");
        synchronized (clients) {
            for (ClientHandler c : clients) {
                if (c.username != null) {
                    if (sb.length() > 6) sb.append(',');
                    sb.append(c.username);
                }
            }
            String msg = sb.toString();
            for (ClientHandler c : clients) {
                c.sendMessage(msg);
            }
        }
    }

    private static void broadcastGroupList() {
        StringBuilder sb = new StringBuilder("GROUPS:");
        for (String g : GroupManager.getGroupNames()) {
            if (sb.length() > 7) sb.append(',');
            sb.append(g);
        }
        String msg = sb.toString();
        synchronized (clients) {
            for (ClientHandler c : clients) {
                c.sendMessage(msg);
            }
        }
    }

    private static void broadcastPublic(String msg) {
        synchronized (clients) {
            for (ClientHandler c : clients) {
                c.sendMessage(msg);
            }
        }
    }

    @Override
    public void run() {
        try {
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            // ----- LOGIN LOOP -----
            while (true) {
                out.println("USERNAME:");
                String u = in.readLine();
                if (u == null) return;
                out.println("PASSWORD:");
                String p = in.readLine();
                if (p == null) return;

                if (UserDatabase.authenticate(u, p)) {
                    username = u;
                    out.println("LOGIN_SUCCESS");
                    break;
                } else {
                    String reason = UserDatabase.getLastAuthError();
                    out.println(reason == null ? "LOGIN_FAIL" : "LOGIN_ERROR:" + reason);
                }
            }

            synchronized (clients) {
                clients.add(this);
            }

            broadcastUserList();
            broadcastGroupList();

            broadcastPublic(ts() + " >> " + username + " joined the chat");

            // ----- MAIN LOOP -----
            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.startsWith("/pm ")) {
                    handlePrivateMessage(line);
                } else if (line.startsWith("/create ")) {
                    String[] sp = line.split(" ", 2);
                    if (sp.length == 2) {
                        GroupManager.createGroup(sp[1]);
                        GroupManager.joinGroup(sp[1], this);
                        broadcastGroupList();
                        out.println("INFO:Group " + sp[1] + " created");
                    }
                } else if (line.startsWith("/join ")) {
                    String[] sp = line.split(" ", 2);
                    if (sp.length == 2) {
                        GroupManager.joinGroup(sp[1], this);
                        out.println("INFO:JOINED " + sp[1]);
                        // (optional) send history
                        for (String h : MessageStore.getGroupMessages(sp[1])) {
                            out.println("GROUP " + sp[1] + " " + h);
                        }
                    }
                } else if (line.startsWith("/gmsg ")) {
                    String[] sp = line.split(" ", 3);
                    if (sp.length == 3) {
                        String group = sp[1];
                        String msg = sp[2];
                        String formatted = ts() + " " + username + ": " + msg;
                        MessageStore.saveMessage(username, null, group, msg);
                        GroupManager.sendToGroup(group, formatted);
                    }
                } else if (line.startsWith("/typing")) {
                    // we don't really parse start/stop, just forward
                    String state = "start";
                    if (line.contains("stop")) state = "stop";
                    synchronized (clients) {
                        for (ClientHandler c : clients) {
                            if (c != this) {
                                c.sendMessage("TYPING:" + username + ":" + state);
                            }
                        }
                    }
                } else {
                    String formatted = ts() + " " + username + ": " + line;
                    MessageStore.saveMessage(username, "ALL", null, line);
                    broadcastPublic(formatted);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try { socket.close(); } catch (Exception ignored) {}
            synchronized (clients) {
                clients.remove(this);
            }
            GroupManager.leaveAllGroups(this);
            broadcastUserList();
            broadcastPublic(ts() + " << " + username + " left the chat");
        }
    }

    private void handlePrivateMessage(String line) {
    String[] sp = line.split(" ", 3);
    if (sp.length < 3) return;

    String target = sp[1];
    String msg    = sp[2];

    // ⚡ Send history first to sender
    for (String h : MessageStore.getPrivateMessages(username, target)) {
        this.sendMessage("PMHIST " + target + " " + h);
    }

    // ⚡ Send history to receiver too (if needed)
    synchronized (clients) {
        for (ClientHandler c : clients) {
            if (c.username != null && c.username.equals(target)) {
                for (String h : MessageStore.getPrivateMessages(username, target)) {
                    c.sendMessage("PMHIST " + username + " " + h);
                }
            }
        }
    }

    // Normal PM send
    String formatted = ts() + " " + username + ": " + msg;

    synchronized (clients) {
        for (ClientHandler c : clients) {
            if (c.username != null && c.username.equals(target)) {
                c.sendMessage("PM " + username + " " + formatted);
            }
        }
    }

    // Sender echo
    this.sendMessage("PM " + target + " " + formatted);

    MessageStore.saveMessage(username, target, null, msg);
}


  

}
