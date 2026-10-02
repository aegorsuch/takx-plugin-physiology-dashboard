package gov.takx.plugins.physiology;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionException;

final class PhysiologyPanel extends JPanel {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private final DeviceStore store;
    private final PhysioCsvLogger logger;
    private final DeviceTableModel devices = new DeviceTableModel();
    private final AlertTableModel alerts = new AlertTableModel();
    private final JTable deviceTable = new JTable(devices);
    private final JComboBox<DeviceStore.Sort> sort = new JComboBox<>(DeviceStore.Sort.values());
    private final JButton loggingButton = new JButton("Start logging");
    private final JTextField logPath = new JTextField();
    private final JLabel status = new JLabel("Waiting for biometric CoT data");
    private final Timer refreshTimer;

    PhysiologyPanel(DeviceStore store, PhysioCsvLogger logger) {
        super(new BorderLayout(6, 6));
        this.store = store;
        this.logger = logger;
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEADING));
        controls.add(new JLabel("Sort:"));
        controls.add(sort);
        controls.add(loggingButton);
        sort.addActionListener(event -> refresh());
        loggingButton.addActionListener(event -> toggleLogging());

        logPath.setEditable(false);
        logPath.setToolTipText("Completed CSV log path; select to copy");
        JPanel header = new JPanel(new BorderLayout());
        header.add(controls, BorderLayout.NORTH);
        header.add(logPath, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        deviceTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        deviceTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                updateAlerts();
            }
        });
        deviceTable.setDefaultRenderer(Object.class, new StatusRenderer());
        JTable alertTable = new JTable(alerts);
        JSplitPane content = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(deviceTable), new JScrollPane(alertTable));
        content.setResizeWeight(0.75);
        add(content, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
        logger.setFailureListener(error -> SwingUtilities.invokeLater(
                () -> status.setText("Log write failed: " + error.getMessage())));
        refreshTimer = new Timer(1000, event -> refresh());
        refreshTimer.start();
        refresh();
    }

    void refresh() {
        int selectedRow = deviceTable.getSelectedRow();
        String selectedUid = selectedRow < 0 ? null : devices.rows.get(selectedRow).uid;
        DeviceStore.Sort selectedSort = (DeviceStore.Sort) sort.getSelectedItem();
        devices.setRows(store.list(selectedSort, Instant.now()));
        if (selectedUid != null) {
            for (int row = 0; row < devices.getRowCount(); row++) {
                if (devices.rows.get(row).uid.equals(selectedUid)) {
                    deviceTable.setRowSelectionInterval(row, row);
                    break;
                }
            }
        }
        updateAlerts();
    }

    void dispose() {
        refreshTimer.stop();
        logger.setFailureListener(error -> {});
    }

    private void updateAlerts() {
        int row = deviceTable.getSelectedRow();
        alerts.setRows(row < 0 ? List.of() : devices.rows.get(row).history);
    }

    private void toggleLogging() {
        if (!logger.isRecording()) {
            try {
                Path file = logger.start();
                logPath.setText(file.toString());
                loggingButton.setText("Stop logging");
                status.setText("Recording biometric samples");
            } catch (IOException e) {
                status.setText("Unable to start logging: " + e.getMessage());
            }
            return;
        }
        loggingButton.setEnabled(false);
        loggingButton.setText("Finalizing...");
        logger.stop().whenComplete((file, error) -> SwingUtilities.invokeLater(() -> {
            loggingButton.setEnabled(true);
            loggingButton.setText("Start logging");
            if (error != null) {
                Throwable cause = error instanceof CompletionException ? error.getCause() : error;
                status.setText("Unable to finish log: " + cause.getMessage());
            } else {
                logPath.setText(file.toString());
                status.setText("Saved CSV: " + file);
            }
        }));
    }

    private static String measurement(Double value, String suffix) {
        return value == null ? "---" : String.format(Locale.ROOT, "%.1f%s", value, suffix);
    }

    private static final class DeviceTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {
                "Status", "Callsign", "Model", "Heart rate", "Skin temp", "Exertion",
                "Battery", "Calories", "HRLV", "VO2", "HRV", "HSSI", "Cadence",
                "Serial", "Reading", "Last seen"
        };
        private List<DeviceSnapshot> rows = List.of();

        void setRows(List<DeviceSnapshot> values) {
            rows = values;
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int column) { return COLUMNS[column]; }

        @Override
        public Object getValueAt(int row, int column) {
            DeviceSnapshot item = rows.get(row);
            return switch (column) {
                case 0 -> item.stale(Instant.now()) ? "Disconnected" : item.severity().name();
                case 1 -> item.callsign;
                case 2 -> item.model;
                case 3 -> measurement(item.heartRate, " bpm");
                case 4 -> measurement(item.skinTempC == null ? null : item.skinTempC * 9 / 5 + 32, " F");
                case 5 -> measurement(item.exertion, "%");
                case 6 -> measurement(item.battery, "%");
                case 7 -> measurement(item.calories, " kcal/hr");
                case 8 -> measurement(item.hrlv, "%");
                case 9 -> measurement(item.vo2, " ml/(kg min)");
                case 10 -> measurement(item.hrv, " ms");
                case 11 -> measurement(item.hssi, "%");
                case 12 -> measurement(item.cadence, " steps/min");
                case 13 -> item.serial;
                case 14 -> item.falsePositive ? "Check sensor fit" : "Normal";
                case 15 -> item.lastSeen == null ? "---" : TIME.format(item.lastSeen);
                default -> throw new IndexOutOfBoundsException(column);
            };
        }
    }

    private final class StatusRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            Component component = super.getTableCellRendererComponent(
                    table, value, selected, focused, row, column);
            if (!selected) {
                DeviceSnapshot item = devices.rows.get(row);
                component.setBackground(item.stale(Instant.now()) ? new Color(215, 215, 215)
                        : switch (item.severity()) {
                            case HIGH -> new Color(255, 210, 210);
                            case LOW -> new Color(255, 245, 190);
                            case NONE -> new Color(220, 245, 220);
                        });
            }
            return component;
        }
    }

    private static final class AlertTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Time", "Event", "Priority", "Cause"};
        private List<DeviceSnapshot.Alert> rows = new ArrayList<>();

        void setRows(List<DeviceSnapshot.Alert> values) {
            rows = values;
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int column) { return COLUMNS[column]; }

        @Override
        public Object getValueAt(int row, int column) {
            DeviceSnapshot.Alert alert = rows.get(row);
            return switch (column) {
                case 0 -> TIME.format(alert.time());
                case 1 -> alert.active() ? "Alert" : "Alerts cleared";
                case 2 -> alert.active() ? alert.severity() : "";
                case 3 -> alert.category() == null ? "" : alert.category();
                default -> throw new IndexOutOfBoundsException(column);
            };
        }
    }
}
