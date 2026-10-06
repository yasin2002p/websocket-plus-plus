package com.burp.websocketlogger.ui;

import com.burp.websocketlogger.model.DirectionType;
import com.burp.websocketlogger.model.WebSocketLogEntry;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

public class WebSocketTableRenderer extends DefaultTableCellRenderer {
    private final WebSocketTableModel tableModel;

    private static final Color OUTGOING_COLOR = new Color(30, 144, 255);
    private static final Color INCOMING_COLOR = new Color(46, 139, 87);

    public WebSocketTableRenderer(WebSocketTableModel tableModel) {
        this.tableModel = tableModel;
    }

    @Override
    public Component getTableCellRendererComponent(
            JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column
    ) {
        Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        int modelRow = table.convertRowIndexToModel(row);
        WebSocketLogEntry entry = tableModel.getEntryAt(modelRow);

        if (entry != null) {
            // Check row highlight color
            Color highlight = entry.getHighlightColor();
            if (!isSelected) {
                if (highlight != null) {
                    c.setBackground(highlight);
                } else {
                    c.setBackground(row % 2 == 0 ? table.getBackground() : new Color(table.getBackground().getRGB() ^ 0x050505));
                }
                c.setForeground(table.getForeground());
            }

            // Direction column styling
            if (column == 2) {
                if (entry.getDirection() == DirectionType.CLIENT_TO_SERVER) {
                    setText("⬆ Outgoing");
                    if (!isSelected) setForeground(OUTGOING_COLOR);
                } else {
                    setText("⬇ Incoming");
                    if (!isSelected) setForeground(INCOMING_COLOR);
                }
                setFont(getFont().deriveFont(Font.BOLD));
            }

            // Security / Tag column styling (Column 8)
            if (column == 8) {
                String tags = entry.getSecurityTags();
                setText(tags);
                if (!tags.isEmpty() && !isSelected) {
                    if (tags.contains("Error")) {
                        setForeground(Color.RED.darker());
                    } else if (tags.contains("Token") || tags.contains("Secret") || tags.contains("Key")) {
                        setForeground(new Color(180, 100, 0)); // Dark Amber
                    } else if (tags.contains("PII")) {
                        setForeground(new Color(0, 102, 204)); // Dark Blue
                    }
                    setFont(getFont().deriveFont(Font.BOLD));
                }
            }
        }

        return c;
    }
}
