package com.burp.websocketlogger.ui;

import com.burp.websocketlogger.model.HeartbeatRule;
import com.burp.websocketlogger.query.FilterEngine;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class HeartbeatRulesDialog extends JDialog {
    private final FilterEngine filterEngine;
    private final Runnable onRulesChanged;

    private final List<HeartbeatRule> rulesList = new ArrayList<>();
    private RulesTableModel tableModel;
    private JTable table;

    public HeartbeatRulesDialog(Window parent, FilterEngine filterEngine, Runnable onRulesChanged) {
        super(parent, "WebSocket++ - Heartbeat Filter Rules", ModalityType.APPLICATION_MODAL);
        this.filterEngine = filterEngine;
        this.onRulesChanged = onRulesChanged;

        // Copy existing rules
        for (HeartbeatRule r : filterEngine.getHeartbeatRules()) {
            rulesList.add(new HeartbeatRule(r.getId(), r.getName(), r.getQuery(), r.isEnabled()));
        }

        initComponents();
        setSize(780, 480);
        setLocationRelativeTo(parent);
    }

    private void initComponents() {
        JPanel contentPanel = new JPanel(new BorderLayout(8, 8));
        contentPanel.setBorder(new EmptyBorder(12, 12, 12, 12));

        // Description header
        JLabel infoLabel = new JLabel("<html><b>Custom Heartbeat / Ping-Pong Rules</b><br>"
                + "When <i>Hide Heartbeats</i> is checked, messages matching these rules are automatically hidden from the log table.</html>");
        contentPanel.add(infoLabel, BorderLayout.NORTH);

        // Center Table
        tableModel = new RulesTableModel();
        table = new JTable(tableModel);
        table.setRowHeight(24);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        table.getColumnModel().getColumn(0).setPreferredWidth(55);  // Enabled
        table.getColumnModel().getColumn(1).setPreferredWidth(140); // Name
        table.getColumnModel().getColumn(2).setPreferredWidth(420); // Query
        table.getColumnModel().getColumn(3).setPreferredWidth(85);  // Valid

        JScrollPane scrollPane = new JScrollPane(table);
        contentPanel.add(scrollPane, BorderLayout.CENTER);

        // Right side buttons
        JPanel actionsPanel = new JPanel(new GridLayout(6, 1, 0, 6));
        JButton addBtn = new JButton("➕ Add Rule");
        addBtn.addActionListener(e -> addRule());

        JButton editBtn = new JButton("✏ Edit Rule");
        editBtn.addActionListener(e -> editSelectedRule());

        JButton deleteBtn = new JButton("🗑 Delete Rule");
        deleteBtn.addActionListener(e -> deleteSelectedRule());

        JButton clearAllBtn = new JButton("Clear All");
        clearAllBtn.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(this, "Remove all custom heartbeat rules?", "Confirm", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                rulesList.clear();
                tableModel.fireTableDataChanged();
            }
        });

        actionsPanel.add(addBtn);
        actionsPanel.add(editBtn);
        actionsPanel.add(deleteBtn);
        actionsPanel.add(clearAllBtn);

        JPanel rightWrapper = new JPanel(new BorderLayout());
        rightWrapper.add(actionsPanel, BorderLayout.NORTH);
        contentPanel.add(rightWrapper, BorderLayout.EAST);

        // Bottom buttons
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton saveBtn = new JButton("Save & Apply");
        saveBtn.setFont(saveBtn.getFont().deriveFont(Font.BOLD));
        saveBtn.addActionListener(e -> saveAndClose());

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());

        bottomPanel.add(saveBtn);
        bottomPanel.add(cancelBtn);
        contentPanel.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(contentPanel);
    }

    private void addRule() {
        HeartbeatRuleEditDialog editDialog = new HeartbeatRuleEditDialog(
                this,
                new HeartbeatRule(UUID.randomUUID().toString(), "New Rule", "payload == \"{}\"", true),
                true
        );
        editDialog.setVisible(true);
        if (editDialog.isSaved()) {
            rulesList.add(editDialog.getRule());
            tableModel.fireTableDataChanged();
            int newIdx = rulesList.size() - 1;
            table.setRowSelectionInterval(newIdx, newIdx);
        }
    }

    private void editSelectedRule() {
        int row = table.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Please select a rule to edit.", "Selection Required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        HeartbeatRule current = rulesList.get(row);
        HeartbeatRuleEditDialog editDialog = new HeartbeatRuleEditDialog(this, current, false);
        editDialog.setVisible(true);
        if (editDialog.isSaved()) {
            rulesList.set(row, editDialog.getRule());
            tableModel.fireTableRowsUpdated(row, row);
        }
    }

    private void deleteSelectedRule() {
        int row = table.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Please select a rule to delete.", "Selection Required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        rulesList.remove(row);
        tableModel.fireTableDataChanged();
    }

    private void saveAndClose() {
        filterEngine.setHeartbeatRules(rulesList);
        if (onRulesChanged != null) {
            onRulesChanged.run();
        }
        dispose();
    }

    private class RulesTableModel extends AbstractTableModel {
        private final String[] cols = {"Active", "Rule Name", "Query Filter", "Syntax Status"};

        @Override
        public int getRowCount() {
            return rulesList.size();
        }

        @Override
        public int getColumnCount() {
            return cols.length;
        }

        @Override
        public String getColumnName(int column) {
            return cols[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 0) return Boolean.class;
            return String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 0; // Checkbox is directly toggleable
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            HeartbeatRule r = rulesList.get(rowIndex);
            switch (columnIndex) {
                case 0: return r.isEnabled();
                case 1: return r.getName();
                case 2: return r.getQuery();
                case 3: return r.isValid() ? "✓ Valid" : "✗ Error";
                default: return "";
            }
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (columnIndex == 0 && aValue instanceof Boolean) {
                rulesList.get(rowIndex).setEnabled((Boolean) aValue);
                fireTableCellUpdated(rowIndex, columnIndex);
            }
        }
    }
}
