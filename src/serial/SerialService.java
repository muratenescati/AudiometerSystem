package serial;

import com.fazecast.jSerialComm.*;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class SerialService {

    public enum ConnectionState { DISCONNECTED, CONNECTED, ERROR }

    private SerialPort port;
    private volatile ConnectionState state = ConnectionState.DISCONNECTED;

    // Stateful boolean bayraklar yerine yan etkisiz (side-effect free) Future nesnesi
    private CompletableFuture<Boolean> responseFuture = new CompletableFuture<>();
    private final StringBuilder serialBuffer = new StringBuilder();

    private Consumer<String> logger = s -> {};

    public void setLogger(Consumer<String> logger) {
        this.logger = (logger != null) ? logger : s -> {};
    }

    public ConnectionState getState() {
        return state;
    }

    // Hata durumlarında boolean dönmek yerine Optional dönerek FP prensiplerine uyum
    public Optional<SerialPort> connect(String portName) {
        try {
            port = SerialPort.getCommPort(portName);
            port.setComPortParameters(9600, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
            port.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0);

            if (!port.openPort()) {
                state = ConnectionState.ERROR;
                logger.accept("Port acilamadi: " + portName);
                return Optional.empty();
            }

            port.addDataListener(new SerialPortDataListener() {
                @Override
                public int getListeningEvents() { return SerialPort.LISTENING_EVENT_DATA_AVAILABLE; }

                @Override
                public void serialEvent(SerialPortEvent event) {
                    int avail = port.bytesAvailable();
                    if (avail <= 0) return;

                    byte[] data = new byte[avail];
                    port.readBytes(data, data.length);
                    serialBuffer.append(new String(data));

                    // Gelen veriyi Stream ile filtreleyerek yan etkisiz işleme
                    Stream.of(serialBuffer.toString())
                            .filter(s -> s.contains("RESPONSE"))
                            .findFirst()
                            .ifPresent(s -> {
                                responseFuture.complete(true); // Future'u tamamla
                                serialBuffer.setLength(0);
                            });

                    if (serialBuffer.length() > 256) {
                        serialBuffer.delete(0, serialBuffer.length() - 64);
                    }
                }
            });

            state = ConnectionState.CONNECTED;
            logger.accept("Baglanti kuruldu: " + portName);
            return Optional.of(port);

        } catch (Exception e) {
            state = ConnectionState.ERROR;
            logger.accept("Baglanti hatasi: " + e.getMessage());
            return Optional.empty();
        }
    }

    public void disconnect() {
        try {
            if (port != null) {
                port.removeDataListener();
                if (port.isOpen()) port.closePort();
            }
        } finally {
            state = ConnectionState.DISCONNECTED;
            responseFuture = new CompletableFuture<>(); // Durumu sıfırla
            serialBuffer.setLength(0);
            logger.accept("Baglanti kapatildi.");
        }
    }

    public void armResponseWindow() {
        responseFuture = new CompletableFuture<>();
        serialBuffer.setLength(0);
    }

    public void closeResponseWindow() {
        responseFuture.complete(false); // Pencereyi kapatırken bekleyenleri boşa çıkar
    }

    public boolean sendTone(int frequency, double amplitude) {
        double safeAmp = Math.max(0.0, Math.min(1.0, amplitude));
        String cmd = String.format(Locale.US, "TONE::%d::%.4f\n", frequency, safeAmp);
        return send(cmd);
    }

    public boolean stopTone() {
        return send("STOP\n");
    }

    private boolean send(String text) {
        if (state != ConnectionState.CONNECTED || port == null || !port.isOpen()) {
            logger.accept("Gonderim basarisiz, port acik degil: " + text.trim());
            return false;
        }
        try {
            byte[] bytes = text.getBytes();
            int written = port.writeBytes(bytes, bytes.length);
            logger.accept("TX -> " + text.trim());
            return written == bytes.length;
        } catch (Exception e) {
            logger.accept("Gonderim hatasi: " + e.getMessage());
            return false;
        }
    }

    // Thread.sleep() içeren while döngüsü yerine saf Future çözümü
    public boolean waitForResponse(int timeoutMillis) {
        try {
            return responseFuture.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            return false;
        }
    }
}