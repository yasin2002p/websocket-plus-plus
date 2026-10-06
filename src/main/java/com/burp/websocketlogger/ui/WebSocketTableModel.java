package com.burp.websocketlogger.ui;

import com.burp.websocketlogger.model.DirectionType;
import com.burp.websocketlogger.model.WebSocketLogEntry;
import com.burp.websocketlogger.query.FilterEngine;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WebSocketTableModel extends AbstractTableModel {
    public static final String[] COLUMN_NAMES = {
            "#", "Time", "Direction", "Tool", "Host", "Path", "Type", "Length", "Security / Tag", "Preview", "Comment"
    };

    public static final Class<?>[] COLUMN_CLASSES = {
            Integer.class, String.class, String.class, String.class, String.class, String.class, String.class, Integer.class, String.class, String.class, String.class
    };

    private final List<WebSocketLogEntry> allEntries = Collections.synchronizedList(new ArrayList<>());
    private final List<WebSocketLogEntry> filteredEntries = Collections.synchronizedList(new ArrayList<>());
    private final FilterEngine filterEngine;

    private int incomingCount = 0;
    private int outgoingCount = 0;

    public WebSocketTableModel(FilterEngine filterEngine) {
        this.filterEngine = filterEngine;
    }

    public synchronized void addEntry(WebSocketLogEntry entry) {
        allEntries.add(entry);
        if (entry.getDirection() == DirectionType.SERVER_TO_CLIENT) {
            incomingCount++;
        } else {
            outgoingCount++;
        }

        if (filterEngine.matches(entry)) {
            int newRow = filteredEntries.size();
            filteredEntries.add(entry);
            fireTableRowsInserted(newRow, newRow);
        }
    }

    public synchronized void addEntries(List<WebSocketLogEntry> entries) {
        if (entries == null || entries.isEmpty()) return;

        int firstRow = filteredEntries.size();
        int addedFiltered = 0;

        for (WebSocketLogEntry entry : entries) {
            allEntries.add(entry);
            if (entry.getDirection() == DirectionType.SERVER_TO_CLIENT) {
                incomingCount++;
            } else {
                outgoingCount++;
            }

            if (filterEngine.matches(entry)) {
                filteredEntries.add(entry);
                addedFiltered++;
            }
        }

        if (addedFiltered > 0) {
            fireTableRowsInserted(firstRow, firstRow + addedFiltered - 1);
        }
    }

    public synchronized void reapplyFilter() {
        filteredEntries.clear();
        for (WebSocketLogEntry entry : allEntries) {
            if (filterEngine.matches(entry)) {
                filteredEntries.add(entry);
            }
        }
        fireTableDataChanged();
    }

    public synchronized void clear() {
        allEntries.clear();
        filteredEntries.clear();
        incomingCount = 0;
        outgoingCount = 0;
        fireTableDataChanged();
    }

    public synchronized void removeEntries(List<WebSocketLogEntry> entriesToRemove) {
        allEntries.removeAll(entriesToRemove);
        filteredEntries.removeAll(entriesToRemove);

        // Recalculate stats
        incomingCount = 0;
        outgoingCount = 0;
        for (WebSocketLogEntry e : allEntries) {
            if (e.getDirection() == DirectionType.SERVER_TO_CLIENT) incomingCount++;
            else outgoingCount++;
        }

        fireTableDataChanged();
    }

    public synchronized WebSocketLogEntry getEntryAt(int rowIndex) {
        if (rowIndex >= 0 && rowIndex < filteredEntries.size()) {
            return filteredEntries.get(rowIndex);
        }
        return null;
    }

    public synchronized List<WebSocketLogEntry> getAllEntries() {
        return new ArrayList<>(allEntries);
    }

    public synchronized List<WebSocketLogEntry> getFilteredEntries() {
        return new ArrayList<>(filteredEntries);
    }

    public synchronized int getTotalCount() {
        return allEntries.size();
    }

    public synchronized int getFilteredCount() {
        return filteredEntries.size();
    }

    public synchronized int getIncomingCount() {
        return incomingCount;
    }

    public synchronized int getOutgoingCount() {
        return outgoingCount;
    }

    @Override
    public int getRowCount() {
        return filteredEntries.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMN_NAMES.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMN_NAMES[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return COLUMN_CLASSES[columnIndex];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        WebSocketLogEntry entry = getEntryAt(rowIndex);
        if (entry == null) return "";

        switch (columnIndex) {
            case 0: return entry.getId();
            case 1: return entry.getFormattedTime();
            case 2: return entry.getDirection().getDisplayName();
            case 3: return entry.getTool();
            case 4: return entry.getHost();
            case 5: return entry.getPath();
            case 6: return entry.getType();
            case 7: return entry.getLength();
            case 8: return entry.getSecurityTags();
            case 9: return entry.getPreview(150);
            case 10: return entry.getComment();
            default: return "";
        }
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return columnIndex == 10; // Allow in-place editing of Comment column
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        if (columnIndex == 10) {
            WebSocketLogEntry entry = getEntryAt(rowIndex);
            if (entry != null) {
                entry.setComment(aValue != null ? aValue.toString() : "");
                fireTableCellUpdated(rowIndex, columnIndex);
            }
        }
    }
}
