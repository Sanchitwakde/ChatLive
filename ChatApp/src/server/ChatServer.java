package server;

import java.net.ServerSocket;
import java.net.Socket;

public class ChatServer {

    public static void main(String[] args) {
        int port = 5000;
        System.out.println("ChatServer starting on port " + port);
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket s = serverSocket.accept();
                System.out.println("Client connected: " + s.getRemoteSocketAddress());
                new ClientHandler(s).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
