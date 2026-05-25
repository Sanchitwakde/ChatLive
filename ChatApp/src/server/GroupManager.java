package server;

import java.util.*;

public class GroupManager {

    private static final Map<String, List<ClientHandler>> groups = new HashMap<>();

    public static synchronized void createGroup(String name) {
        groups.putIfAbsent(name, new ArrayList<>());
        System.out.println("Group created: " + name);
    }

    public static synchronized void joinGroup(String name, ClientHandler client) {
        groups.putIfAbsent(name, new ArrayList<>());
        List<ClientHandler> members = groups.get(name);
        if (!members.contains(client)) {
            members.add(client);
        }
        System.out.println(client.getUsername() + " joined group " + name);
    }

    public static synchronized void leaveAllGroups(ClientHandler client) {
        for (List<ClientHandler> members : groups.values()) {
            members.remove(client);
        }
    }

    public static synchronized void sendToGroup(String name, String message) {
        List<ClientHandler> members = groups.get(name);
        if (members == null) return;
        for (ClientHandler c : members) {
            c.sendMessage("GROUP " + name + " " + message);
        }
    }

    public static synchronized Set<String> getGroupNames() {
        return new HashSet<>(groups.keySet());
    }
}
