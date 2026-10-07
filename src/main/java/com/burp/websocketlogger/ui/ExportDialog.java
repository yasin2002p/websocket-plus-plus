package com.burp.websocketlogger.ui;

import burp.api.montoya.MontoyaApi;
import com.burp.websocketlogger.export.LogExporter;
import com.burp.websocketlogger.model.WebSocketLogEntry;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ExportDialog extends JDialog {

    private final MontoyaApi api;
    private final WebSocketTableModel tableModel;
    private final List<WebSocketLogEntry> selectedEntries;

    private JRadioButton selectedRadio;
    private JRadioButton filteredRadio;
    private JRadioButton allRadio;

    private JRadioButton csvRadio;
    private JRadioButton jsonRadio;

    private JTextField filePathField;
    private JButton browseBtn;
    private JButton exportBtn;
    private JButton cancelBtn;

    public ExportDialog(MontoyaApi api, WebSocketTableModel tableModel, List<WebSocketLogEntry> selectedEntries) {
        super(resolveParentFrame(api), "Export WebSocket Logs", true);
        this.api = api;
        this.tableModel = tableModel;
        this.selectedEntries = selectedEntries != null ? selectedEntries : new ArrayList<>();

        initComponents();
        if (api != null && api.userInterface() != null) {
            try {
                api.userInterface().applyThemeToComponent(this);
            } catch (Throwable ignored) {}
        }
    }

    private static Frame resolveParentFrame(MontoyaApi api) {
        try {
            if (api != null && api.userInterface() != null && api.userInterface().swingUtils() != null) {
                Frame suiteFrame = api.userInterface().swingUtils().suiteFrame();
                if (suiteFrame != null) return suiteFrame;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private void initComponents() {
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        setResizable(false);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(new EmptyBorder(12, 14, 12, 14));

        // 1. Header Banner
        JPanel headerPanel = new JPanel(new BorderLayout(8, 4));
        headerPanel.setBorder(new EmptyBorder(0, 0, 10, 0));
        JLabel titleLabel = new JLabel("Export WebSocket Logs");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        JLabel descLabel = new JLabel("Save captured WebSocket messages to CSV or JSON format for analysis and reporting.");
        descLabel.setForeground(Color.GRAY);
        headerPanel.add(titleLabel, BorderLayout.NORTH);
        headerPanel.add(descLabel, BorderLayout.SOUTH);
        mainPanel.add(headerPanel);

        // 2. Scope Panel
        JPanel scopePanel = new JPanel(new GridLayout(0, 1, 4, 4));
        scopePanel.setBorder(new CompoundBorder(
                BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Export Scope", TitledBorder.LEFT, TitledBorder.TOP),
                new EmptyBorder(4, 8, 8, 8)
        ));

        ButtonGroup scopeGroup = new ButtonGroup();

        int selCount = selectedEntries.size();
        selectedRadio = new JRadioButton("Selected entries only (" + selCount + " message" + (selCount == 1 ? "" : "s") + ")");
        selectedRadio.setEnabled(selCount > 0);
        scopeGroup.add(selectedRadio);
        scopePanel.add(selectedRadio);

        int filterCount = tableModel.getFilteredCount();
        filteredRadio = new JRadioButton("Filtered entries currently visible (" + filterCount + " message" + (filterCount == 1 ? "" : "s") + ")");
        filteredRadio.setEnabled(filterCount > 0);
        scopeGroup.add(filteredRadio);
        scopePanel.add(filteredRadio);

        int totalCount = tableModel.getTotalCount();
        allRadio = new JRadioButton("All logged entries (" + totalCount + " message" + (totalCount == 1 ? "" : "s") + ")");
        scopeGroup.add(allRadio);
        scopePanel.add(allRadio);

        // Set initial selection
        if (selCount > 0) {
            selectedRadio.setSelected(true);
        } else if (filterCount > 0) {
            filteredRadio.setSelected(true);
        } else {
            allRadio.setSelected(true);
        }

        mainPanel.add(scopePanel);
        mainPanel.add(Box.createVerticalStrut(10));

        // 3. Format Panel
        JPanel formatPanel = new JPanel(new GridLayout(0, 1, 4, 4));
        formatPanel.setBorder(new CompoundBorder(
                BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "File Format", TitledBorder.LEFT, TitledBorder.TOP),
                new EmptyBorder(4, 8, 8, 8)
        ));

        ButtonGroup formatGroup = new ButtonGroup();
        csvRadio = new JRadioButton("CSV (.csv) - Comma-Separated Values (Excel, Sheets, SIEM)", true);
        jsonRadio = new JRadioButton("JSON (.json) - Structured JSON array with full frame properties", false);

        formatGroup.add(csvRadio);
        formatGroup.add(jsonRadio);
        formatPanel.add(csvRadio);
        formatPanel.add(jsonRadio);

        csvRadio.addActionListener(e -> updateExtensionInPath(".csv", ".json"));
        jsonRadio.addActionListener(e -> updateExtensionInPath(".json", ".csv"));

        mainPanel.add(formatPanel);
        mainPanel.add(Box.createVerticalStrut(10));

        // 4. Destination File Panel
        JPanel destPanel = new JPanel(new BorderLayout(6, 4));
        destPanel.setBorder(new CompoundBorder(
                BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Destination File", TitledBorder.LEFT, TitledBorder.TOP),
                new EmptyBorder(6, 8, 8, 8)
        ));

        filePathField = new JTextField(generateDefaultFilePath(".csv"), 36);
        filePathField.setCaretPosition(filePathField.getText().length());
        browseBtn = new JButton("Browse...");
        browseBtn.addActionListener(e -> onBrowseClicked());

        destPanel.add(filePathField, BorderLayout.CENTER);
        destPanel.add(browseBtn, BorderLayout.EAST);

        mainPanel.add(destPanel);
        add(mainPanel, BorderLayout.CENTER);

        // 5. Button Actions
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());

        exportBtn = new JButton("Export");
        exportBtn.setFont(exportBtn.getFont().deriveFont(Font.BOLD));
        exportBtn.addActionListener(e -> performExport());

        buttonPanel.add(cancelBtn);
        buttonPanel.add(exportBtn);
        add(buttonPanel, BorderLayout.SOUTH);

        // Keyboard navigation
        getRootPane().setDefaultButton(exportBtn);
        getRootPane().registerKeyboardAction(
                e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        pack();
        setLocationRelativeTo(getParent());
    }

    private String generateDefaultFilePath(String extension) {
        String baseDir = System.getProperty("user.home");
        File desktop = new File(baseDir, "Desktop");
        if (desktop.exists() && desktop.isDirectory()) {
            baseDir = desktop.getAbsolutePath();
        }
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        return new File(baseDir, "websocket_logs_" + timestamp + extension).getAbsolutePath();
    }

    private void updateExtensionInPath(String newExt, String oldExt) {
        String current = filePathField.getText().trim();
        if (current.toLowerCase().endsWith(oldExt)) {
            filePathField.setText(current.substring(0, current.length() - oldExt.length()) + newExt);
        } else if (!current.toLowerCase().endsWith(newExt)) {
            filePathField.setText(current + newExt);
        }
    }

    private void onBrowseClicked() {
        String ext = jsonRadio.isSelected() ? ".json" : ".csv";
        File currentFile = new File(filePathField.getText().trim());

        // Attempt 1: Native AWT FileDialog (Fastest, zero hang, native look & feel)
        Frame parentFrame = resolveParentFrame(api);
        try {
            FileDialog fd = new FileDialog(parentFrame, "Save WebSocket Logs", FileDialog.SAVE);
            if (currentFile.getParent() != null) {
                fd.setDirectory(currentFile.getParent());
            }
            fd.setFile(currentFile.getName());
            fd.setVisible(true);

            String dir = fd.getDirectory();
            String name = fd.getFile();
            if (dir != null && name != null) {
                File chosen = new File(dir, name);
                if (!chosen.getName().toLowerCase().endsWith(ext)) {
                    chosen = new File(chosen.getAbsolutePath() + ext);
                }
                filePathField.setText(chosen.getAbsolutePath());
                return;
            } else {
                // User pressed cancel in FileDialog
                return;
            }
        } catch (Throwable t) {
            // Attempt 2: Fallback to JFileChooser if FileDialog is restricted
            try {
                JFileChooser chooser = new JFileChooser();
                chooser.setSelectedFile(currentFile);
                int res = chooser.showSaveDialog(this);
                if (res == JFileChooser.APPROVE_OPTION) {
                    File chosen = chooser.getSelectedFile();
                    if (!chosen.getName().toLowerCase().endsWith(ext)) {
                        chosen = new File(chosen.getAbsolutePath() + ext);
                    }
                    filePathField.setText(chosen.getAbsolutePath());
                }
            } catch (Throwable fallbackErr) {
                JOptionPane.showMessageDialog(this, "Could not open file browser: " + fallbackErr.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void performExport() {
        String path = filePathField.getText().trim();
        if (path.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please specify a destination file path.", "Invalid Path", JOptionPane.WARNING_MESSAGE);
            filePathField.requestFocusInWindow();
            return;
        }

        String ext = jsonRadio.isSelected() ? ".json" : ".csv";
        if (!path.toLowerCase().endsWith(ext)) {
            path = path + ext;
            filePathField.setText(path);
        }

        File targetFile = new File(path);
        if (targetFile.exists()) {
            int overwrite = JOptionPane.showConfirmDialog(
                    this,
                    "The file already exists:\n" + targetFile.getAbsolutePath() + "\n\nDo you want to overwrite it?",
                    "Confirm Overwrite",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (overwrite != JOptionPane.YES_OPTION) {
                return;
            }
        }

        // Determine entries to export
        List<WebSocketLogEntry> entriesToExport;
        if (selectedRadio.isSelected()) {
            entriesToExport = selectedEntries;
        } else if (filteredRadio.isSelected()) {
            entriesToExport = tableModel.getFilteredEntries();
        } else {
            entriesToExport = tableModel.getAllEntries();
        }

        if (entriesToExport.isEmpty()) {
            JOptionPane.showMessageDialog(this, "There are no entries to export for the selected scope.", "No Data", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            if (jsonRadio.isSelected()) {
                LogExporter.exportToJson(targetFile, entriesToExport);
            } else {
                LogExporter.exportToCsv(targetFile, entriesToExport);
            }

            dispose();

            // Success dialog with option to open folder
            showExportSuccess(targetFile, entriesToExport.size());

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to export logs: " + ex.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showExportSuccess(File file, int count) {
        Object[] options = {"Open Containing Folder", "Close"};
        int choice = JOptionPane.showOptionDialog(
                getParent(),
                "Successfully exported " + count + " entries to:\n" + file.getAbsolutePath(),
                "Export Complete",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.INFORMATION_MESSAGE,
                null,
                options,
                options[0]
        );

        if (choice == 0) {
            openFolder(file);
        }
    }

    private void openFolder(File file) {
        try {
            if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
                new ProcessBuilder("explorer.exe", "/select,", file.getAbsolutePath()).start();
            } else if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(file.getParentFile());
            }
        } catch (Throwable ignored) {}
    }
}
