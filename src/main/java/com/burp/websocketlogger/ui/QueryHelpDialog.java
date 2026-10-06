package com.burp.websocketlogger.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class QueryHelpDialog extends JDialog {

    public QueryHelpDialog(Window parent) {
        super(parent, "WebSocket Logger++ - Query Syntax Guide", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(750, 560);
        setLocationRelativeTo(parent);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        JEditorPane editorPane = new JEditorPane();
        editorPane.setEditable(false);
        editorPane.setContentType("text/html");
        editorPane.setText(getHelpTextHtml());
        editorPane.setCaretPosition(0);

        JScrollPane scrollPane = new JScrollPane(editorPane);
        scrollPane.setBorder(BorderFactory.createEtchedBorder());
        panel.add(scrollPane, BorderLayout.CENTER);

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.add(closeBtn);
        panel.add(btnPanel, BorderLayout.SOUTH);

        setContentPane(panel);
    }

    private String getHelpTextHtml() {
        return "<html><body style='font-family: sans-serif; font-size: 11pt; padding: 10px;'>"
                + "<h2 style='color: #FF6633;'>WebSocket Logger++ Query Syntax</h2>"
                + "<p>You can query WebSocket frames using field operators, logic operators, or plain text searches.</p>"
                + "<h3>Supported Fields</h3>"
                + "<ul>"
                + "<li><b>payload</b> (or <code>body</code>, <code>data</code>, <code>p</code>): Message payload string</li>"
                + "<li><b>dir</b> (or <code>direction</code>, <code>d</code>): Direction (<code>client</code>, <code>server</code>, <code>outgoing</code>, <code>incoming</code>)</li>"
                + "<li><b>host</b> (or <code>h</code>): Target host name</li>"
                + "<li><b>path</b>: WebSocket path (e.g. <code>/socket.io/</code>)</li>"
                + "<li><b>url</b>: Complete WebSocket URL</li>"
                + "<li><b>length</b> (or <code>len</code>, <code>size</code>): Payload size in bytes</li>"
                + "<li><b>type</b>: <code>Text</code> or <code>Binary</code></li>"
                + "<li><b>tool</b>: Originating Burp tool (<code>Proxy</code>, <code>Repeater</code>, etc.)</li>"
                + "<li><b>id</b>: Frame sequence number</li>"
                + "<li><b>comment</b>: User note / comment</li>"
                + "</ul>"
                + "<h3>Supported Operators</h3>"
                + "<ul>"
                + "<li><code>==</code> or <code>=</code>: Exact match (case-insensitive for text)</li>"
                + "<li><code>!=</code>: Not equal</li>"
                + "<li><code>contains</code> / <code>!contains</code>: Substring check</li>"
                + "<li><code>matches</code> or <code>regex</code>: Regular expression match</li>"
                + "<li><code>startswith</code> / <code>endswith</code>: Prefix or suffix match</li>"
                + "<li><code>&gt;</code>, <code>&lt;</code>, <code>&gt;=</code>, <code>&lt;=</code>: Numeric comparisons (for <code>length</code>, <code>id</code>, <code>port</code>)</li>"
                + "<li><code>AND</code> (or <code>&amp;&amp;</code>), <code>OR</code> (or <code>||</code>), <code>NOT</code> (or <code>!</code>): Boolean logic</li>"
                + "<li><code>( ... )</code>: Parentheses grouping</li>"
                + "</ul>"
                + "<h3>Example Queries</h3>"
                + "<table border='1' cellpadding='6' cellspacing='0' style='border-collapse: collapse; width: 100%;'>"
                + "<tr style='background-color: #EEE;'><th>Query</th><th>Description</th></tr>"
                + "<tr><td><code>payload contains \"token\"</code></td><td>Messages containing \"token\"</td></tr>"
                + "<tr><td><code>dir == client and length &gt; 50</code></td><td>Outgoing client messages longer than 50 bytes</td></tr>"
                + "<tr><td><code>host contains \"api\" and path == \"/ws\"</code></td><td>Specific endpoint and host</td></tr>"
                + "<tr><td><code>payload matches \".*\"action\":\"login\".*\"</code></td><td>Regex pattern on JSON action</td></tr>"
                + "<tr><td><code>(dir == server or len &gt; 200) and not payload contains \"ping\"</code></td><td>Complex grouping with OR and NOT</td></tr>"
                + "<tr><td><code>admin</code></td><td>Free-text search across payload, URL, host, and comments</td></tr>"
                + "</table>"
                + "</body></html>";
    }
}
