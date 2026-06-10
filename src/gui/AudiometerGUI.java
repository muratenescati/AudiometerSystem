package gui;

import audiometry.AudiometryController;
import audiometry.Ear;
import audiometry.TestState;
import com.fazecast.jSerialComm.SerialPort;
import serial.SerialService;
import util.CsvExporter;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Audiometer ana penceresi.
 *
 * Onceki versiyondan farkli:
 *   - Hasta bilgisi giris alani (ad, ID, yas, not).
 *   - Manuel mod: kullanici tek bir frekans + seviye secip "Cal" diyebilir.
 *   - Disconnect, Reset, Export CSV butonlari.
 *   - Anlik durum etiketi: "Sag kulak | 1000 Hz | 40 dB HL".
 *   - Port listesini yenileme butonu.
 *   - JOptionPane ile hata / bilgi dialoglari.
 *   - Tum SerialService loglari log alanina yonlendiriliyor.
 */
public class AudiometerGUI extends JFrame {

    private final SerialService serial = new SerialService();
    private final AudiometryController controller;

    // Genel
    private final JTextArea logArea = new JTextArea();
    private final JLabel stateLabel = new JLabel("Hazir.");
    private final AudiogramPanel audiogramPanel = new AudiogramPanel();

    // Baglanti
    private final JComboBox<String> portCombo = new JComboBox<>();
    private final JButton refreshPortsBtn = new JButton("Portlari Yenile");
    private final JButton connectBtn = new JButton("Baglan");
    private final JButton disconnectBtn = new JButton("Baglantiyi Kes");

    // Hasta
    private final JTextField patientName = new JTextField(12);
    private final JTextField patientId = new JTextField(8);
    private final JSpinner patientAge = new JSpinner(new SpinnerNumberModel(30, 0, 120, 1));
    private final JTextField patientNote = new JTextField(15);

    // Test kontrol
    private final JButton startBtn = new JButton("Otomatik Test Basla");
    private final JButton stopBtn = new JButton("Durdur");
    private final JButton resetBtn = new JButton("Sifirla");
    private final JButton exportBtn = new JButton("CSV Olarak Kaydet");

    // Manuel mod
    private final JComboBox<Ear> manualEar = new JComboBox<>(Ear.values());
    private final JComboBox<Integer> manualFreq = new JComboBox<>(new Integer[]{250, 500, 1000, 2000, 4000, 8000});
    private final JSpinner manualDb = new JSpinner(new SpinnerNumberModel(40, -10, 100, 5));
    private final JButton manualPlayBtn = new JButton("Manuel Cal");

    public AudiometerGUI() {
        super("Audiometer");
        setSize(1180, 760);
        setMinimumSize(new Dimension(1000, 600));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        controller = new AudiometryController(serial, this);

        add(buildTopPanel(),    BorderLayout.NORTH);
        add(audiogramPanel,     BorderLayout.CENTER);
        add(buildRightPanel(),  BorderLayout.EAST);
        add(buildBottomPanel(), BorderLayout.SOUTH);

        serial.setLogger(this::log);
        refreshPorts();
        wireListeners();
        updateButtonStates(false);

        setLocationRelativeTo(null);
    }

    // -------- panel kurulumu --------

    private JPanel buildTopPanel() {
        JPanel root = new JPanel(new GridLayout(2, 1));

        // Satir 1 - baglanti
        JPanel conn = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        conn.setBorder(BorderFactory.createTitledBorder("Baglanti"));
        conn.add(new JLabel("COM Port:"));
        portCombo.setPreferredSize(new Dimension(120, 24));
        conn.add(portCombo);
        conn.add(refreshPortsBtn);
        conn.add(connectBtn);
        conn.add(disconnectBtn);
        root.add(conn);

        // Satir 2 - hasta
        JPanel pat = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        pat.setBorder(BorderFactory.createTitledBorder("Hasta Bilgisi"));
        pat.add(new JLabel("Ad:"));     pat.add(patientName);
        pat.add(new JLabel("ID:"));     pat.add(patientId);
        pat.add(new JLabel("Yas:"));    pat.add(patientAge);
        pat.add(new JLabel("Not:"));    pat.add(patientNote);
        root.add(pat);

        return root;
    }

    private JPanel buildRightPanel() {
        JPanel root = new JPanel(new BorderLayout(4, 4));
        root.setPreferredSize(new Dimension(360, 0));
        root.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        // Manuel mod kutusu
        JPanel manual = new JPanel(new GridBagLayout());
        manual.setBorder(BorderFactory.createTitledBorder("Manuel Mod"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 4, 2, 4);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0; c.gridy = 0; manual.add(new JLabel("Kulak:"), c);
        c.gridx = 1;              manual.add(manualEar, c);
        c.gridx = 0; c.gridy = 1; manual.add(new JLabel("Frekans (Hz):"), c);
        c.gridx = 1;              manual.add(manualFreq, c);
        c.gridx = 0; c.gridy = 2; manual.add(new JLabel("Seviye (dB HL):"), c);
        c.gridx = 1;              manual.add(manualDb, c);
        c.gridx = 0; c.gridy = 3; c.gridwidth = 2; manual.add(manualPlayBtn, c);
        root.add(manual, BorderLayout.NORTH);

        // Log
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Log"));
        root.add(logScroll, BorderLayout.CENTER);

        return root;
    }

    private JPanel buildBottomPanel() {
        JPanel root = new JPanel(new BorderLayout());

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        stateLabel.setFont(stateLabel.getFont().deriveFont(Font.BOLD, 13f));
        left.add(new JLabel("Durum:"));
        left.add(stateLabel);
        root.add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        right.add(startBtn);
        right.add(stopBtn);
        right.add(resetBtn);
        right.add(exportBtn);
        root.add(right, BorderLayout.EAST);

        return root;
    }

    private void wireListeners() {
        refreshPortsBtn.addActionListener(e -> refreshPorts());

        connectBtn.addActionListener(e -> {
            String port = (String) portCombo.getSelectedItem();
            if (port == null) {
                showError("Once bir COM port secin.");
                return;
            }
            boolean ok = serial.connect(port).isPresent();
            if (ok) {
                updateButtonStates(false);
            } else {
                showError("Baglanti basarisiz: " + port);
            }
        });

        disconnectBtn.addActionListener(e -> {
            if (controller.isRunning()) {
                controller.stopTest();
            }
            serial.disconnect();
            updateButtonStates(false);
        });

        startBtn.addActionListener(e -> {
            if (serial.getState() != SerialService.ConnectionState.CONNECTED) {
                showError("Once bir COM portuna baglanin.");
                return;
            }
            audiogramPanel.clear();
            controller.resetResults();
            controller.startAutomaticTest();
        });

        stopBtn.addActionListener(e -> controller.stopTest());

        resetBtn.addActionListener(e -> {
            if (controller.isRunning()) {
                showError("Test devam ederken sifirlanamaz.");
                return;
            }
            audiogramPanel.clear();
            controller.resetResults();
            stateLabel.setText("Hazir.");
            log("Sonuclar sifirlandi.");
        });

        exportBtn.addActionListener(e -> exportCsv());

        manualPlayBtn.addActionListener(e -> {
            if (serial.getState() != SerialService.ConnectionState.CONNECTED) {
                showError("Once bir COM portuna baglanin.");
                return;
            }
            if (controller.isRunning()) {
                showError("Otomatik test devam ederken manuel kullanilamaz.");
                return;
            }
            Ear ear = (Ear) manualEar.getSelectedItem();
            int freq = (Integer) manualFreq.getSelectedItem();
            int db = (Integer) manualDb.getValue();
            new Thread(() -> {
                controller.manualPlayOnce(ear, freq, db).ifPresent(heard -> {
                    if (heard) {
                        plotThreshold(ear.name(), freq, db);
                    }
                });
            }, "manual-play").start();
        });
    }

    private void refreshPorts() {
        portCombo.removeAllItems();
        for (SerialPort p : SerialPort.getCommPorts()) {
            portCombo.addItem(p.getSystemPortName());
        }
        if (portCombo.getItemCount() == 0) {
            log("Acik COM portu bulunamadi.");
        }
    }

    private void updateButtonStates(boolean ignored) {
        SerialService.ConnectionState s = serial.getState();
        boolean conn = (s == SerialService.ConnectionState.CONNECTED);
        connectBtn.setEnabled(!conn);
        disconnectBtn.setEnabled(conn);
        startBtn.setEnabled(conn);
        manualPlayBtn.setEnabled(conn);
    }

    // -------- controller -> GUI geri cagirimlari --------

    public void log(String text) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + ts + "] " + text + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public void updateState(TestState state) {
        SwingUtilities.invokeLater(() -> stateLabel.setText(state.toString()));
    }

    public void plotThreshold(String ear, int freq, int db) {
        SwingUtilities.invokeLater(() -> audiogramPanel.addThreshold(ear, freq, db));
    }

    public void onTestStarted() {
        SwingUtilities.invokeLater(() -> {
            startBtn.setEnabled(false);
            stopBtn.setEnabled(true);
            resetBtn.setEnabled(false);
            manualPlayBtn.setEnabled(false);
        });
    }

    public void onTestStopped() {
        SwingUtilities.invokeLater(() -> {
            stopBtn.setEnabled(false);
            resetBtn.setEnabled(true);
            startBtn.setEnabled(serial.getState() == SerialService.ConnectionState.CONNECTED);
            manualPlayBtn.setEnabled(serial.getState() == SerialService.ConnectionState.CONNECTED);
            stateLabel.setText("Test sonlandi.");
        });
    }

    // -------- yardimcilar --------

    private void exportCsv() {
        if (controller.isRunning()) {
            showError("Test devam ederken kaydedilemez.");
            return;
        }
        if (controller.getResults().isEmpty()) {
            showError("Kaydedilecek esik sonucu yok.");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File("audiometer_results.csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path path = chooser.getSelectedFile().toPath();
        try {
            CsvExporter.export(path, getPatientInfo(), controller.getResults());
            log("CSV kaydedildi: " + path);
            JOptionPane.showMessageDialog(this,
                    "Kaydedildi: " + path,
                    "CSV Export",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            showError("Kaydetme hatasi: " + ex.getMessage());
        }
    }

    private PatientInfo getPatientInfo() {
        return new PatientInfo(
                patientName.getText().trim(),
                patientId.getText().trim(),
                (Integer) patientAge.getValue(),
                patientNote.getText().trim()
        );
    }

    private void showError(String msg) {
        log("HATA: " + msg);
        JOptionPane.showMessageDialog(this, msg, "Hata", JOptionPane.ERROR_MESSAGE);
    }
}
