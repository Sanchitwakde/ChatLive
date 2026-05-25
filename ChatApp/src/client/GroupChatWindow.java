package client;

import javax.swing.*;
import java.awt.*;

public class GroupChatWindow extends JPanel {

    public interface SendHandler {
        void send(String msg);
    }

    private final JTextArea area = new JTextArea();
    private final JTextField input = new JTextField();
    private final SendHandler handler;

    public GroupChatWindow(String title, SendHandler handler) {
        this.handler = handler;
        setLayout(new BorderLayout());

        area.setEditable(false);
        JScrollPane scroll = new JScrollPane(area);

        add(scroll, BorderLayout.CENTER);
        add(input, BorderLayout.SOUTH);

        input.addActionListener(e -> {
            String text = input.getText().trim();
            if (!text.isEmpty()) {
                handler.send(text);
                input.setText("");
            }
        });
    }

    public JTextField getInputField() {
        return input;
    }

    public void addMessage(String msg) {
        area.append(msg + "\n");
    }
}
