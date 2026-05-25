package client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class ChatClientGUI extends JFrame {

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    private DefaultListModel<String> chatListModel;
    private JList<String> chatList;

    private JTextArea publicChatArea;
    private JTextField publicInputField;
    private JLabel typingLabel;
    private JTabbedPane tabPane;

    private final Map<String, GroupChatWindow> groupTabs = new HashMap<>();
    private final Map<String, GroupChatWindow> privateTabs = new HashMap<>();

    private final java.util.List<String> currentUsers  = new ArrayList<>();
    private final java.util.List<String> currentGroups = new ArrayList<>();

    public ChatClientGUI() {
        openLoginWindow();
    }

    private void openLoginWindow() {
        final LoginWindow[] loginRef = new LoginWindow[1];

        loginRef[0] = new LoginWindow((user, pass) -> {
            try {
                connectToServer(user, pass);
                loginRef[0].dispose();
                initChatWindow(user);
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(
                        loginRef[0],
                        "Login failed: " + e.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        });

        loginRef[0].setVisible(true);
    }

    private void connectToServer(String user, String pass) throws Exception {
        socket = new Socket("localhost", 5000);
        in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);

        String prompt = in.readLine();
        if (!"USERNAME:".equals(prompt)) throw new Exception("Protocol error");
        out.println(user);

        prompt = in.readLine();
        if (!"PASSWORD:".equals(prompt)) throw new Exception("Protocol error");
        out.println(pass);

        String res = in.readLine();
        if (res != null && res.startsWith("LOGIN_ERROR:")) {
            throw new Exception(res.substring("LOGIN_ERROR:".length()).trim());
        }
        if (!"LOGIN_SUCCESS".equals(res)) {
            throw new Exception("Invalid username/password");
        }
    }

    private void initChatWindow(String username) {
        setTitle("Chat - Logged in as " + username);
        setSize(900, 600);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        typingLabel = new JLabel(" ");
        add(typingLabel, BorderLayout.NORTH);

        tabPane = new JTabbedPane();
        add(tabPane, BorderLayout.CENTER);

        // LEFT SIDEBAR LIST
        chatListModel = new DefaultListModel<>();
        chatList = new JList<>(chatListModel);
        chatList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane listScroll = new JScrollPane(chatList);
        listScroll.setPreferredSize(new Dimension(200, 0));
        add(listScroll, BorderLayout.WEST);

        // initial content
        refreshChatList();

        chatList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String label = chatList.getSelectedValue();
                if (label != null) {
                    openChat(label);
                }
            }
        });

        Thread reader = new Thread(this::listenToServer);
        reader.setDaemon(true);
        reader.start();

        setVisible(true);
    }

    // ------------- CHAT LIST (USERS + GROUPS) -------------

    private void refreshChatList() {
        if (chatListModel == null) return;
        chatListModel.clear();

        chatListModel.addElement("Public");
        chatListModel.addElement("---- Users ----");
        for (String u : currentUsers) {
            chatListModel.addElement("[U] " + u);
        }

        chatListModel.addElement("---- Groups ----");
        for (String g : currentGroups) {
            chatListModel.addElement("[G] " + g);
        }
    }

    private void handleUsersMessage(String line) {
        String body = line.substring("USERS:".length());
        currentUsers.clear();
        if (!body.isBlank()) {
            String[] parts = body.split(",");
            for (String u : parts) {
                String t = u.trim();
                if (!t.isEmpty()) currentUsers.add(t);
            }
        }
        SwingUtilities.invokeLater(this::refreshChatList);
    }

    private void handleGroupsMessage(String line) {
        String body = line.substring("GROUPS:".length());
        currentGroups.clear();
        if (!body.isBlank()) {
            String[] parts = body.split(",");
            for (String g : parts) {
                String t = g.trim();
                if (!t.isEmpty()) currentGroups.add(t);
            }
        }
        SwingUtilities.invokeLater(this::refreshChatList);
    }

    private void openChat(String label) {
        if (label.equals("Public")) {
            openPublicChat();
            return;
        }
        if (label.startsWith("----")) {
            return; // separator rows
        }
        if (label.startsWith("[U] ")) {
            String user = label.substring(4);
            openPrivateChat(user);
            return;
        }
        if (label.startsWith("[G] ")) {
            String group = label.substring(4);
            openGroupTabIfAbsent(group);
        }
    }

    // ------------- PUBLIC CHAT -------------

    private void openPublicChat() {
        // already open?
        for (int i = 0; i < tabPane.getTabCount(); i++) {
            if ("Public".equals(tabPane.getTitleAt(i))) {
                tabPane.setSelectedIndex(i);
                return;
            }
        }

        JPanel panel = new JPanel(new BorderLayout());
        publicChatArea = new JTextArea();
        publicChatArea.setEditable(false);
        JScrollPane scroll = new JScrollPane(publicChatArea);

        publicInputField = new JTextField();
        attachTypingEvents(publicInputField);
        publicInputField.addActionListener(e -> {
            String msg = publicInputField.getText().trim();
            if (!msg.isEmpty()) {
                out.println(msg);
                publicInputField.setText("");
            }
        });

        panel.add(scroll, BorderLayout.CENTER);
        panel.add(publicInputField, BorderLayout.SOUTH);

        tabPane.addTab("Public", panel);
        tabPane.setSelectedComponent(panel);
    }

    // ------------- PRIVATE CHAT (PM) -------------

    private void openPrivateChat(String user) {
        GroupChatWindow win = privateTabs.get(user);
        if (win == null) {
            win = new GroupChatWindow("PM: " + user, text -> out.println("/pm " + user + " " + text));
            attachTypingEvents(win.getInputField());
            privateTabs.put(user, win);
            tabPane.addTab("PM " + user, win);
        }
        tabPane.setSelectedComponent(win);
    }

    // ------------- GROUP CHAT -------------

    private GroupChatWindow openGroupTabIfAbsent(String group) {
        GroupChatWindow win = groupTabs.get(group);
        if (win == null) {
            win = new GroupChatWindow(group, text -> out.println("/gmsg " + group + " " + text));
            attachTypingEvents(win.getInputField());
            groupTabs.put(group, win);
            tabPane.addTab(group, win);
        }
        tabPane.setSelectedComponent(win);
        return win;
    }

    // ------------- COMMON UTILITIES -------------

    private void attachTypingEvents(JTextField field) {
        field.addKeyListener(new KeyAdapter() {
            private boolean typingSent = false;
            private long lastTyped = 0L;

            @Override
            public void keyPressed(KeyEvent e) {
                lastTyped = System.currentTimeMillis();
                if (!typingSent) {
                    out.println("/typing start");
                    typingSent = true;
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                new Thread(() -> {
                    try { Thread.sleep(800); } catch (Exception ignored) {}
                    long now = System.currentTimeMillis();
                    if (now - lastTyped >= 700) {
                        out.println("/typing stop");
                        typingSent = false;
                    }
                }).start();
            }
        });
    }

    private void listenToServer() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                final String msg = line;

                if (msg.startsWith("TYPING:")) {
                    handleTypingMessage(msg);
                } else if (msg.startsWith("USERS:")) {
                    handleUsersMessage(msg);
                } else if (msg.startsWith("GROUPS:")) {
                    handleGroupsMessage(msg);
                } else if (msg.startsWith("GROUP ")) {
                    handleGroupMessage(msg);
                }else if (msg.startsWith("PM ")) {
                    handlePrivateIncoming(msg);
                } else if (msg.startsWith("PMHIST ")) {
                    handlePrivateHistory(msg);
                }
                else {
                    if (publicChatArea != null) {
                        SwingUtilities.invokeLater(() -> publicChatArea.append(msg + "\n"));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void handlePrivateIncoming(String msg) {
    // format: PM sender [timestamp] sender: message
    String[] sp = msg.split(" ", 3);
    if (sp.length < 3) return;

    String partner = sp[1];   // who sent it
    String content = sp[2];

    SwingUtilities.invokeLater(() -> {
        GroupChatWindow win = privateTabs.get(partner);
        if (win == null) {
            win = new GroupChatWindow("PM: " + partner, text -> out.println("/pm " + partner + " " + text));
            attachTypingEvents(win.getInputField());
            privateTabs.put(partner, win);
            tabPane.addTab("PM " + partner, win);
        }
        win.addMessage(content);
        tabPane.setSelectedComponent(win);
    });
}
private void handlePrivateHistory(String msg) {
    // format: PMHIST username message...
    String[] sp = msg.split(" ", 3);
    if (sp.length < 3) return;

    String partner = sp[1];
    String content = sp[2];

    SwingUtilities.invokeLater(() -> {
        GroupChatWindow win = privateTabs.get(partner);
        if (win == null) {
            win = new GroupChatWindow("PM: " + partner, text -> out.println("/pm " + partner + " " + text));
            attachTypingEvents(win.getInputField());
            privateTabs.put(partner, win);
            tabPane.addTab("PM " + partner, win);
        }
        win.addMessage(content);
    });
}


    private void handleTypingMessage(String msg) {
        String[] sp = msg.split(":");
        if (sp.length < 3) return;
        String user = sp[1];
        String state = sp[2];
        SwingUtilities.invokeLater(() ->
                typingLabel.setText("start".equals(state) ? user + " is typing..." : " ")
        );
    }

    private void handleGroupMessage(String msg) {
        String[] sp = msg.split(" ", 3);
        if (sp.length < 3) return;
        String group = sp[1];
        String content = sp[2];
        SwingUtilities.invokeLater(() -> {
            GroupChatWindow win = openGroupTabIfAbsent(group);
            if (win != null) win.addMessage(content);
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ChatClientGUI::new);
    }
}
