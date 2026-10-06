package com.burp.websocketlogger.ui;

import com.burp.websocketlogger.model.HeartbeatRule;
import com.burp.websocketlogger.query.QueryParser;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

public class HeartbeatRuleEditDialog extends JDialog {
    private final HeartbeatRule rule;
    private boolean saved = false;

    private JTextField nameField;
    private JTextArea queryArea;
    private JCheckBox enabledCheckBox;
    private JLabel statusLabel;

    public HeartbeatRuleEditDialog(Window parent, HeartbeatRule rule, boolean isNew) {
        super(parent, isNew ? "Add Heartbeat Filter Rule" : "Edit Heartbeat Filter Rule", ModalityType.APPLICATION_MODAL);
        this.rule = rule;

        initComponents();
        setSize(580, 360);
        setLocationRelativeTo(parent);
    }

    private void initComponents() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));

        // Form fields
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 4, 4, 4);

        // Row 1: Rule Name
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Rule Name:"), gbc);

        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 1.0;
        nameField = new JTextField(rule.getName());
        formPanel.add(nameField, gbc);

        // Row 2: Active toggle
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Active:"), gbc);

        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 1.0;
        enabledCheckBox = new JCheckBox("Enable this rule", rule.isEnabled());
        formPanel.add(enabledCheckBox, gbc);

        // Row 3: Query Area
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        formPanel.add(new JLabel("Filter Query:"), gbc);

        gbc.gridx = 1; gbc.gridy = 2; gbc.weightx = 1.0; gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        queryArea = new JTextArea(rule.getQuery(), 4, 30);
        queryArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        queryArea.setLineWrap(true);
        queryArea.setWrapStyleWord(true);
        JScrollPane queryScroll = new JScrollPane(queryArea);
        formPanel.add(queryScroll, gbc);

        // Row 4: Status validation
        gbc.gridx = 1; gbc.gridy = 3; gbc.weighty = 0.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        statusLabel = new JLabel("✓ Valid syntax");
        statusLabel.setForeground(new Color(34, 139, 34));
        formPanel.add(statusLabel, gbc);

        panel.add(formPanel, BorderLayout.CENTER);

        // Live validator
        queryArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { validateQuery(); }
            @Override
            public void removeUpdate(DocumentEvent e) { validateQuery(); }
            @Override
            public void changedUpdate(DocumentEvent e) { validateQuery(); }
        });
        validateQuery();

        // Bottom buttons
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton saveBtn = new JButton("Save Rule");
        saveBtn.setFont(saveBtn.getFont().deriveFont(Font.BOLD));
        saveBtn.addActionListener(e -> {
            String q = queryArea.getText().trim();
            if (q.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Query cannot be empty.", "Validation Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            try {
                QueryParser.parse(q);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Query has syntax error:\n" + ex.getMessage(), "Syntax Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            rule.setName(nameField.getText().trim());
            rule.setQuery(q);
            rule.setEnabled(enabledCheckBox.isSelected());
            saved = true;
            dispose();
        });

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());

        bottomPanel.add(saveBtn);
        bottomPanel.add(cancelBtn);
        panel.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(panel);
    }

    private void validateQuery() {
        String q = queryArea.getText().trim();
        if (q.isEmpty()) {
            statusLabel.setText("⚠ Query cannot be empty");
            statusLabel.setForeground(Color.ORANGE.darker());
            return;
        }
        try {
            QueryParser.parse(q);
            statusLabel.setText("✓ Valid syntax");
            statusLabel.setForeground(new Color(34, 139, 34));
        } catch (Exception ex) {
            statusLabel.setText("✗ " + ex.getMessage());
            statusLabel.setForeground(Color.RED);
        }
    }

    public boolean isSaved() {
        return saved;
    }

    public HeartbeatRule getRule() {
        return rule;
    }
}
