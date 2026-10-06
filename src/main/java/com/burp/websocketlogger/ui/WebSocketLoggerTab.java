package com.burp.websocketlogger.ui;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.editor.EditorOptions;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.WebSocketMessageEditor;
import com.burp.websocketlogger.export.LogExporter;
import com.burp.websocketlogger.model.WebSocketLogEntry;
import com.burp.websocketlogger.query.FilterEngine;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class WebSocketLoggerTab extends JPanel {
    private final MontoyaApi api;
    private final FilterEngine filterEngine;
    private final WebSocketTableModel tableModel;

    private JTable table;
    private JScrollPane tableScrollPane;
    private WebSocketMessageEditor wsMessageEditor;
    private HttpRequestEditor httpRequestEditor;
    private JTextArea detailsArea;

    private JTextField queryField;
    private JButton historyBtn;
    private JLabel queryValidationLabel;
    private JCheckBox clientCheckBox;
    private JCheckBox serverCheckBox;
    private JCheckBox hideHeartbeatCheckBox;
    private JCheckBox inScopeOnlyCheckBox;
    private JCheckBox autoScrollCheckBox;
    private JButton pauseResumeBtn;
    private JLabel statsLabel;

    private volatile boolean isLoggingPaused = false;
    private com.burp.websocketlogger.WebSocketHistorySync historySync;

    private static final int MAX_QUERY_HISTORY = 10;
    private final List<String> recentQueries = new ArrayList<>();

    public void setHistorySync(com.burp.websocketlogger.WebSocketHistorySync historySync) {
        this.historySync = historySync;
    }

    public WebSocketLoggerTab(MontoyaApi api) {
        this.api = api;
        this.filterEngine = new FilterEngine(url -> {
            try {
                return api.scope().isInScope(url);
            } catch (Exception e) {
                return false;
            }
        });
        this.tableModel = new WebSocketTableModel(filterEngine);

        setLayout(new BorderLayout(5, 5));
        initComponents();
    }

    private void initComponents() {
        // --- TOP PANEL: Controls, Query Bar, Quick Filters ---
        JPanel topContainer = new JPanel();
        topContainer.setLayout(new BoxLayout(topContainer, BoxLayout.Y_AXIS));
        topContainer.setBorder(new EmptyBorder(6, 8, 6, 8));

        // Row 1: Query bar
        JPanel queryRow = new JPanel(new BorderLayout(8, 0));

        JLabel queryTitle = new JLabel("Query Filter:");
        queryTitle.setFont(queryTitle.getFont().deriveFont(Font.BOLD));
        queryRow.add(queryTitle, BorderLayout.WEST);

        // History Popup & Button
        queryField = new JTextField();
        queryField.setToolTipText("Enter query or click ▾ for recent queries. e.g. payload contains \"admin\"");

        historyBtn = new JButton("▾");
        historyBtn.setToolTipText("Recent query history (last 10 queries)");
        historyBtn.setMargin(new Insets(2, 6, 2, 6));
        historyBtn.setFocusable(false);

        // Show history when historyBtn clicked or when double-clicking / right-clicking / clicking dropdown in query field
        historyBtn.addActionListener(e -> showQueryHistoryPopup(historyBtn, 0, historyBtn.getHeight()));
        queryField.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // If user double-clicks the query field or right clicks, show history popup
                if (e.getClickCount() == 2 || SwingUtilities.isRightMouseButton(e)) {
                    showQueryHistoryPopup(queryField, e.getX(), e.getY());
                }
            }
        });

        // Center sub-panel to hold queryField and historyBtn seamlessly
        JPanel queryInputWrapper = new JPanel(new BorderLayout());
        queryInputWrapper.add(queryField, BorderLayout.CENTER);
        queryInputWrapper.add(historyBtn, BorderLayout.EAST);
        queryRow.add(queryInputWrapper, BorderLayout.CENTER);

        JPanel queryActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton applyFilterBtn = new JButton("Apply");
        applyFilterBtn.addActionListener(e -> applyQuery());

        JButton clearFilterBtn = new JButton("Clear");
        clearFilterBtn.addActionListener(e -> {
            queryField.setText("");
            applyQuery();
        });

        JButton helpBtn = new JButton("Syntax Help (?)");
        helpBtn.addActionListener(e -> {
            Window win = SwingUtilities.getWindowAncestor(this);
            new QueryHelpDialog(win).setVisible(true);
        });

        queryValidationLabel = new JLabel("✓ Ready");
        queryValidationLabel.setForeground(new Color(34, 139, 34));

        queryActions.add(applyFilterBtn);
        queryActions.add(clearFilterBtn);
        queryActions.add(helpBtn);
        queryActions.add(queryValidationLabel);

        queryRow.add(queryActions, BorderLayout.EAST);
        topContainer.add(queryRow);
        topContainer.add(Box.createVerticalStrut(6));

        // Live typing validation on query field
        queryField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { validateTyping(); }
            @Override
            public void removeUpdate(DocumentEvent e) { validateTyping(); }
            @Override
            public void changedUpdate(DocumentEvent e) { validateTyping(); }

            private void validateTyping() {
                String q = queryField.getText().trim();
                if (q.isEmpty()) {
                    queryValidationLabel.setText("✓ Ready");
                    queryValidationLabel.setForeground(new Color(34, 139, 34));
                    return;
                }
                boolean valid = filterEngine.setQuery(q);
                if (valid) {
                    queryValidationLabel.setText("✓ Valid syntax");
                    queryValidationLabel.setForeground(new Color(34, 139, 34));
                } else {
                    String err = filterEngine.getLastError();
                    queryValidationLabel.setText("✗ " + (err != null ? err : "Syntax error"));
                    queryValidationLabel.setForeground(Color.RED);
                }
            }
        });
        queryField.addActionListener(e -> applyQuery());

        // Row 2: Quick Toggles & Action Buttons
        JPanel filterRow = new JPanel(new BorderLayout(5, 0));
        JPanel leftToggles = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));

        clientCheckBox = new JCheckBox("Outgoing (Client)", true);
        serverCheckBox = new JCheckBox("Incoming (Server)", true);
        hideHeartbeatCheckBox = new JCheckBox("Hide Heartbeats", false);
        inScopeOnlyCheckBox = new JCheckBox("In Scope Only", false);

        clientCheckBox.addActionListener(e -> {
            filterEngine.setShowClientToServer(clientCheckBox.isSelected());
            tableModel.reapplyFilter();
            updateCounters();
        });

        serverCheckBox.addActionListener(e -> {
            filterEngine.setShowServerToClient(serverCheckBox.isSelected());
            tableModel.reapplyFilter();
            updateCounters();
        });

        hideHeartbeatCheckBox.addActionListener(e -> {
            filterEngine.setHideHeartbeats(hideHeartbeatCheckBox.isSelected());
            tableModel.reapplyFilter();
            updateCounters();
        });

        inScopeOnlyCheckBox.addActionListener(e -> {
            filterEngine.setInScopeOnly(inScopeOnlyCheckBox.isSelected());
            tableModel.reapplyFilter();
            updateCounters();
        });

        leftToggles.add(clientCheckBox);
        leftToggles.add(serverCheckBox);
        leftToggles.add(hideHeartbeatCheckBox);
        leftToggles.add(inScopeOnlyCheckBox);
        filterRow.add(leftToggles, BorderLayout.WEST);

        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));

        autoScrollCheckBox = new JCheckBox("Auto Scroll", true);

        pauseResumeBtn = new JButton("⏸ Pause");
        pauseResumeBtn.addActionListener(e -> {
            isLoggingPaused = !isLoggingPaused;
            pauseResumeBtn.setText(isLoggingPaused ? "▶ Resume" : "⏸ Pause");
            pauseResumeBtn.setForeground(isLoggingPaused ? Color.RED : UIManager.getColor("Button.foreground"));
        });

        JButton syncBtn = new JButton("🔄 Sync");
        syncBtn.setToolTipText("Immediately synchronize with Burp Proxy WebSockets history");
        syncBtn.addActionListener(e -> {
            if (historySync != null) {
                int added = historySync.syncNow();
                updateCounters();
                JOptionPane.showMessageDialog(this, "Synced with Proxy WebSockets history (" + added + " new messages added).", "Sync Complete", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        JButton clearBtn = new JButton("🗑 Clear All");
        clearBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(
                    this, "Are you sure you want to clear all WebSocket logs?",
                    "Clear Logs", JOptionPane.YES_NO_OPTION
            );
            if (confirm == JOptionPane.YES_OPTION) {
                tableModel.clear();
                clearEditors();
                updateCounters();
            }
        });

        JButton exportBtn = new JButton("💾 Export...");
        exportBtn.addActionListener(e -> showExportDialog());

        statsLabel = new JLabel("Total: 0 | Filtered: 0 | Out: 0 | In: 0");
        statsLabel.setFont(statsLabel.getFont().deriveFont(Font.BOLD));

        rightActions.add(autoScrollCheckBox);
        rightActions.add(pauseResumeBtn);
        rightActions.add(syncBtn);
        rightActions.add(clearBtn);
        rightActions.add(exportBtn);
        rightActions.add(new JSeparator(SwingConstants.VERTICAL));
        rightActions.add(statsLabel);

        filterRow.add(rightActions, BorderLayout.EAST);
        topContainer.add(filterRow);

        add(topContainer, BorderLayout.NORTH);

        // --- CENTER PANEL: Table & Split View ---
        table = new JTable(tableModel);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.setRowHeight(22);
        table.setFillsViewportHeight(true);

        TableRowSorter<WebSocketTableModel> sorter = new TableRowSorter<>(tableModel);
        sorter.setSortsOnUpdates(true);
        table.setRowSorter(sorter);

        WebSocketTableRenderer renderer = new WebSocketTableRenderer(tableModel);
        for (int i = 0; i < tableModel.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }

        // Set column preferred widths
        table.getColumnModel().getColumn(0).setPreferredWidth(50);   // #
        table.getColumnModel().getColumn(1).setPreferredWidth(90);   // Time
        table.getColumnModel().getColumn(2).setPreferredWidth(100);  // Direction
        table.getColumnModel().getColumn(3).setPreferredWidth(75);   // Tool
        table.getColumnModel().getColumn(4).setPreferredWidth(140);  // Host
        table.getColumnModel().getColumn(5).setPreferredWidth(120);  // Path
        table.getColumnModel().getColumn(6).setPreferredWidth(60);   // Type
        table.getColumnModel().getColumn(7).setPreferredWidth(65);   // Length
        table.getColumnModel().getColumn(8).setPreferredWidth(350);  // Preview
        table.getColumnModel().getColumn(9).setPreferredWidth(100);  // Comment

        tableScrollPane = new JScrollPane(table);

        // Table selection listener
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = table.getSelectedRow();
                if (selectedRow != -1) {
                    int modelRow = table.convertRowIndexToModel(selectedRow);
                    WebSocketLogEntry entry = tableModel.getEntryAt(modelRow);
                    displayEntry(entry);
                }
            }
        });

        // Context menu on Table
        setupContextMenu();

        // Bottom Pane: Burp Editors & Details
        JTabbedPane bottomTabbedPane = new JTabbedPane();

        // Native WebSocket message editor
        wsMessageEditor = api.userInterface().createWebSocketMessageEditor(EditorOptions.READ_ONLY);
        bottomTabbedPane.addTab("WebSocket Message", wsMessageEditor.uiComponent());

        // Native HTTP Handshake editor
        httpRequestEditor = api.userInterface().createHttpRequestEditor(EditorOptions.READ_ONLY);
        bottomTabbedPane.addTab("Handshake Upgrade Request", httpRequestEditor.uiComponent());

        // Metadata panel
        detailsArea = new JTextArea();
        detailsArea.setEditable(false);
        detailsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        detailsArea.setBorder(new EmptyBorder(8, 8, 8, 8));
        JScrollPane detailsScrollPane = new JScrollPane(detailsArea);
        bottomTabbedPane.addTab("Message Details", detailsScrollPane);

        // Split pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScrollPane, bottomTabbedPane);
        splitPane.setResizeWeight(0.60);
        splitPane.setContinuousLayout(true);

        add(splitPane, BorderLayout.CENTER);

        // Apply Burp theme recursively
        api.userInterface().applyThemeToComponent(this);
    }

    private void applyQuery() {
        String q = queryField.getText().trim();
        boolean valid = filterEngine.setQuery(q);
        if (valid) {
            queryValidationLabel.setText("✓ Filter applied");
            queryValidationLabel.setForeground(new Color(34, 139, 34));
            if (!q.isEmpty()) {
                saveQueryToHistory(q);
            }
            tableModel.reapplyFilter();
            updateCounters();
        } else {
            String err = filterEngine.getLastError();
            queryValidationLabel.setText("✗ " + (err != null ? err : "Syntax error"));
            queryValidationLabel.setForeground(Color.RED);
        }
    }

    private void saveQueryToHistory(String query) {
        if (query == null || query.trim().isEmpty()) return;
        String q = query.trim();
        // Remove if already present so it moves to top (index 0)
        recentQueries.remove(q);
        recentQueries.add(0, q);
        while (recentQueries.size() > MAX_QUERY_HISTORY) {
            recentQueries.remove(recentQueries.size() - 1);
        }
    }

    private void showQueryHistoryPopup(Component invoker, int x, int y) {
        JPopupMenu popup = new JPopupMenu();

        if (recentQueries.isEmpty()) {
            JMenuItem emptyItem = new JMenuItem("No recent queries");
            emptyItem.setEnabled(false);
            popup.add(emptyItem);
        } else {
            JLabel header = new JLabel("  Recent Queries (Max " + MAX_QUERY_HISTORY + "):");
            header.setFont(header.getFont().deriveFont(Font.BOLD, 11f));
            header.setForeground(Color.GRAY);
            popup.add(header);
            popup.addSeparator();

            for (int i = 0; i < recentQueries.size(); i++) {
                String historyQuery = recentQueries.get(i);
                JMenuItem item = new JMenuItem((i + 1) + ". " + historyQuery);
                item.setToolTipText(historyQuery);
                item.addActionListener(e -> {
                    queryField.setText(historyQuery);
                    applyQuery();
                });
                popup.add(item);
            }

            popup.addSeparator();
            JMenuItem clearHistoryItem = new JMenuItem("Clear History");
            clearHistoryItem.addActionListener(e -> recentQueries.clear());
            popup.add(clearHistoryItem);
        }

        popup.show(invoker, x, y);
    }

    public List<String> getRecentQueries() {
        return new ArrayList<>(recentQueries);
    }

    public void addEntry(WebSocketLogEntry entry) {
        if (isLoggingPaused || entry == null) return;

        SwingUtilities.invokeLater(() -> {
            tableModel.addEntry(entry);
            updateCounters();
            handleAutoScroll();
        });
    }

    public void addEntries(List<WebSocketLogEntry> entries) {
        if (isLoggingPaused || entries == null || entries.isEmpty()) return;

        SwingUtilities.invokeLater(() -> {
            tableModel.addEntries(entries);
            updateCounters();
            handleAutoScroll();
        });
    }

    public void handleAutoScroll() {
        if (!autoScrollCheckBox.isSelected() || table.getRowCount() == 0) {
            return;
        }

        // Check if table is sorted descending (e.g. by ID descending, newest entry is at top)
        boolean isSortedDescending = false;
        RowSorter<?> sorter = table.getRowSorter();
        if (sorter != null && sorter.getSortKeys() != null && !sorter.getSortKeys().isEmpty()) {
            RowSorter.SortKey sortKey = sorter.getSortKeys().get(0);
            if (sortKey.getSortOrder() == SortOrder.DESCENDING) {
                isSortedDescending = true;
            }
        }

        // Check view position of the newest entry (last entry in the model)
        int lastModelRow = tableModel.getRowCount() - 1;
        int viewRow = -1;
        if (lastModelRow >= 0) {
            try {
                viewRow = table.convertRowIndexToView(lastModelRow);
            } catch (Exception ignored) {
            }
        }

        if (isSortedDescending || viewRow == 0) {
            // Newest request is at the top of the table -> keep scroll strictly at the top!
            if (tableScrollPane != null) {
                tableScrollPane.getVerticalScrollBar().setValue(0);
            }
            table.scrollRectToVisible(table.getCellRect(0, 0, true));
        } else {
            // Default / ascending sort -> scroll to bottom / newly added entry
            int targetRow = (viewRow >= 0) ? viewRow : table.getRowCount() - 1;
            if (tableScrollPane != null && targetRow == table.getRowCount() - 1) {
                JScrollBar vBar = tableScrollPane.getVerticalScrollBar();
                vBar.setValue(vBar.getMaximum());
            }
            table.scrollRectToVisible(table.getCellRect(targetRow, 0, true));
        }
    }

    private void displayEntry(WebSocketLogEntry entry) {
        if (entry == null) {
            clearEditors();
            return;
        }

        if (entry.getPayload() != null) {
            wsMessageEditor.setContents(entry.getPayload());
        }

        if (entry.getUpgradeRequest() != null) {
            httpRequestEditor.setRequest(entry.getUpgradeRequest());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Message ID:        ").append(entry.getId()).append("\n");
        sb.append("Timestamp:         ").append(entry.getFormattedTime()).append("\n");
        sb.append("Connection ID:     ").append(entry.getConnectionId()).append("\n");
        sb.append("Tool:              ").append(entry.getTool()).append("\n");
        sb.append("Direction:         ").append(entry.getDirection().getDisplayName()).append("\n");
        sb.append("Frame Type:        ").append(entry.getType()).append("\n");
        sb.append("Payload Length:    ").append(entry.getLength()).append(" bytes\n");
        sb.append("Target Host:       ").append(entry.getHost()).append(":").append(entry.getPort()).append("\n");
        sb.append("Path:              ").append(entry.getPath()).append("\n");
        sb.append("WebSocket URL:     ").append(entry.getUrl()).append("\n");
        sb.append("Comment:           ").append(entry.getComment()).append("\n");
        detailsArea.setText(sb.toString());
        detailsArea.setCaretPosition(0);
    }

    private void clearEditors() {
        detailsArea.setText("");
    }

    private void updateCounters() {
        statsLabel.setText(String.format(
                "Total: %d | Shown: %d | Out: %d | In: %d",
                tableModel.getTotalCount(),
                tableModel.getFilteredCount(),
                tableModel.getOutgoingCount(),
                tableModel.getIncomingCount()
        ));
    }

    private void setupContextMenu() {
        JPopupMenu popupMenu = new JPopupMenu();

        JMenuItem copyPayloadItem = new JMenuItem("Copy Payload");
        copyPayloadItem.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row != -1) {
                WebSocketLogEntry entry = tableModel.getEntryAt(table.convertRowIndexToModel(row));
                if (entry != null) {
                    copyToClipboard(entry.getPayloadText());
                }
            }
        });
        popupMenu.add(copyPayloadItem);

        JMenuItem copyUrlItem = new JMenuItem("Copy URL");
        copyUrlItem.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row != -1) {
                WebSocketLogEntry entry = tableModel.getEntryAt(table.convertRowIndexToModel(row));
                if (entry != null) {
                    copyToClipboard(entry.getUrl());
                }
            }
        });
        popupMenu.add(copyUrlItem);

        JMenuItem copyHandshakeItem = new JMenuItem("Copy Handshake Request");
        copyHandshakeItem.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row != -1) {
                WebSocketLogEntry entry = tableModel.getEntryAt(table.convertRowIndexToModel(row));
                if (entry != null && entry.getUpgradeRequest() != null) {
                    copyToClipboard(entry.getUpgradeRequest().toString());
                }
            }
        });
        popupMenu.add(copyHandshakeItem);

        popupMenu.addSeparator();

        // Highlight Submenu
        JMenu highlightMenu = new JMenu("Highlight");
        Color[] colors = {
                null,
                new Color(255, 102, 102), // Red
                new Color(255, 178, 102), // Orange
                new Color(255, 255, 102), // Yellow
                new Color(153, 255, 153), // Green
                new Color(153, 255, 255), // Cyan
                new Color(153, 204, 255), // Blue
                new Color(255, 153, 255), // Pink
                new Color(204, 153, 255), // Magenta
                new Color(220, 220, 220)  // Gray
        };
        String[] colorNames = {"None", "Red", "Orange", "Yellow", "Green", "Cyan", "Blue", "Pink", "Magenta", "Gray"};

        for (int i = 0; i < colors.length; i++) {
            Color color = colors[i];
            JMenuItem item = new JMenuItem(colorNames[i]);
            item.addActionListener(e -> {
                int[] selectedRows = table.getSelectedRows();
                for (int r : selectedRows) {
                    WebSocketLogEntry entry = tableModel.getEntryAt(table.convertRowIndexToModel(r));
                    if (entry != null) {
                        entry.setHighlightColor(color);
                    }
                }
                table.repaint();
            });
            highlightMenu.add(item);
        }
        popupMenu.add(highlightMenu);

        // Comment item
        JMenuItem commentItem = new JMenuItem("Add / Edit Comment");
        commentItem.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row != -1) {
                WebSocketLogEntry entry = tableModel.getEntryAt(table.convertRowIndexToModel(row));
                if (entry != null) {
                    String current = entry.getComment();
                    String updated = JOptionPane.showInputDialog(this, "Enter comment:", current);
                    if (updated != null) {
                        entry.setComment(updated);
                        tableModel.fireTableRowsUpdated(row, row);
                    }
                }
            }
        });
        popupMenu.add(commentItem);

        popupMenu.addSeparator();

        // Delete item
        JMenuItem deleteItem = new JMenuItem("Delete Selected Rows");
        deleteItem.addActionListener(e -> {
            int[] rows = table.getSelectedRows();
            if (rows.length > 0) {
                List<WebSocketLogEntry> toRemove = new ArrayList<>();
                for (int r : rows) {
                    WebSocketLogEntry entry = tableModel.getEntryAt(table.convertRowIndexToModel(r));
                    if (entry != null) toRemove.add(entry);
                }
                tableModel.removeEntries(toRemove);
                clearEditors();
                updateCounters();
            }
        });
        popupMenu.add(deleteItem);

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                showPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                showPopup(e);
            }

            private void showPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int row = table.rowAtPoint(e.getPoint());
                    if (row != -1 && !table.isRowSelected(row)) {
                        table.setRowSelectionInterval(row, row);
                    }
                    popupMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });
    }

    private void showExportDialog() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export WebSocket Logs");
        int res = chooser.showSaveDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            List<WebSocketLogEntry> entries = tableModel.getFilteredEntries();
            try {
                if (file.getName().toLowerCase().endsWith(".json")) {
                    LogExporter.exportToJson(file, entries);
                } else {
                    if (!file.getName().toLowerCase().endsWith(".csv")) {
                        file = new File(file.getAbsolutePath() + ".csv");
                    }
                    LogExporter.exportToCsv(file, entries);
                }
                JOptionPane.showMessageDialog(this, "Successfully exported " + entries.size() + " entries!", "Export Complete", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error exporting file: " + ex.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void copyToClipboard(String text) {
        if (text == null) text = "";
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }

    public boolean isLoggingPaused() {
        return isLoggingPaused;
    }

    public JTable getTable() {
        return table;
    }

    public JScrollPane getTableScrollPane() {
        return tableScrollPane;
    }

    public JCheckBox getAutoScrollCheckBox() {
        return autoScrollCheckBox;
    }

    public WebSocketTableModel getTableModel() {
        return tableModel;
    }

    public JTextField getQueryField() {
        return queryField;
    }

    public JButton getHistoryBtn() {
        return historyBtn;
    }
}
