package audiometry;

import gui.AudiometerGUI;
import serial.SerialService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Test akisini yoneten controller.
 *
 * Eski versiyondan farkli olarak:
 *   - Esik tespiti AudiometryEngine'e tamamen devredildi (BME spec'ine uygun 2/3 ascending kurali).
 *   - TestState immutable; her gecisten sonra yeni state alinir.
 *   - Cevap penceresi tonu CALAMADAN once armResponseWindow ile aciliyor (race condition fix).
 *   - Tek thread ile koselen test akisi; ikinci kez Start basilirsa ihlal edilemez (running flag).
 *   - InterruptedException temiz sekilde ele alınıyor.
 *   - Bulunan esikler ThresholdResult listesi olarak GUI'ye doneriliyor.
 *   - Manuel mod desteklenir (manualSetState).
 */
public class AudiometryController {

    /** BME spec'inden gelen test sirasi. 1000 Hz retest istege bagli. */
    private static final int[] FREQ_SEQUENCE = {1000, 2000, 4000, 8000, 500, 250};
    private static final int START_DB = 40;

    /** Sunum suresi: BME 1.0-1.5 s onerdi. Cevap penceresi: 2-3 s. */
    private static final int TONE_DURATION_MS = 1200;
    private static final int RESPONSE_WINDOW_MS = 2500;
    private static final int INTER_TONE_GAP_MS = 600;

    private final SerialService serial;
    private final AudiometerGUI gui;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);

    private final List<ThresholdResult> results = new ArrayList<>();

    private Thread testThread;

    public AudiometryController(SerialService serial, AudiometerGUI gui) {
        this.serial = serial;
        this.gui = gui;
    }

    /** Tam otomatik test (sag kulak -> sol kulak, tum frekanslar). */
    public boolean startAutomaticTest() {
        if (running.get()) {
            gui.log("Test zaten devam ediyor.");
            return false;
        }
        if (serial.getState() != SerialService.ConnectionState.CONNECTED) {
            gui.log("Once bir COM portuna baglanin.");
            return false;
        }

        stopRequested.set(false);
        running.set(true);
        results.clear();
        gui.onTestStarted();

        testThread = new Thread(this::runAutomaticTest, "AudiometryTest");
        testThread.setDaemon(true);
        testThread.start();
        return true;
    }

    public void stopTest() {
        stopRequested.set(true);
        if (testThread != null) {
            testThread.interrupt();
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public List<ThresholdResult> getResults() {
        return List.copyOf(results);
    }

    /** Sonuclari sifirla (yeni hasta vb.). Test calismiyorsa cagrilmali. */
    public void resetResults() {
        if (running.get()) return;
        results.clear();
    }

    // ---------- ic akis ----------

    private void runAutomaticTest() {
        try {
            for (Ear ear : new Ear[]{Ear.RIGHT, Ear.LEFT}) {
                if (stopRequested.get()) break;
                gui.log("---- " + ear + " kulak basliyor ----");

                for (int freq : FREQ_SEQUENCE) {
                    if (stopRequested.get()) break;
                    runOneFrequency(ear, freq);
                }
            }

            gui.log(stopRequested.get() ? "TEST IPTAL EDILDI." : "TEST TAMAMLANDI.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            gui.log("TEST KESILDI.");
        } catch (Exception e) {
            gui.log("Test sirasinda hata: " + e.getMessage());
            e.printStackTrace();
        } finally {
            running.set(false);
            try { serial.stopTone(); } catch (Exception ignored) {}
            gui.onTestStopped();
        }
    }

    /** Tek bir (kulak, frekans) icin esik bulana kadar surdurur. */
    private void runOneFrequency(Ear ear, int freq) throws InterruptedException {
        TestState state = TestState.initial(ear, freq, START_DB);
        gui.updateState(state);

        // Sonsuz dongu olusmasin -- guvenli ust sinir
        int maxSteps = 60;

        for (int i = 0; i < maxSteps; i++) {
            if (stopRequested.get()) return;

            boolean heard = presentOneTone(state);
            StepResult step = AudiometryEngine.nextStep(state, heard);

            if (step.threshold().isPresent()) {
                ThresholdResult r = step.threshold().get();
                results.add(r);
                gui.plotThreshold(r.ear().name(), r.frequency(), r.thresholdDb());
                gui.log("ESIK: " + ear + " " + freq + " Hz -> " + r.thresholdDb() + " dB HL");
                return;
            }

            state = step.nextState();
            gui.updateState(state);
            Thread.sleep(INTER_TONE_GAP_MS);
        }

        gui.log("UYARI: " + ear + " " + freq + " Hz icin esik " + maxSteps + " adimda bulunamadi.");
    }

    /**
     * Tek bir tonu calar ve cevabi bekler.
     *
     * Sira KRITIK (race condition fix):
     *   1) armResponseWindow() -- cevap penceresi acilir
     *   2) sendTone() -- ton calmaya baslar
     *   3) bekle (ton suresi + cevap penceresi)
     *   4) stopTone() + closeResponseWindow()
     */
    private boolean presentOneTone(TestState state) throws InterruptedException {
        double amplitude = dbHlToAmplitude(state.intensityDb());
        gui.log(state.ear() + " | " + state.frequency() + " Hz @ " + state.intensityDb() + " dB HL  (amp=" + String.format(java.util.Locale.US, "%.4f", amplitude) + ")");

        serial.armResponseWindow();
        boolean sent = serial.sendTone(state.frequency(), amplitude);
        if (!sent) {
            serial.closeResponseWindow();
            throw new InterruptedException("Ton gonderilemedi -- baglanti kopmus olabilir.");
        }

        // Ton calma suresi
        Thread.sleep(TONE_DURATION_MS);

        // Cevap penceresinin geri kalani -- hasta ton bittikten sonra da basabilir
        int remaining = Math.max(0, RESPONSE_WINDOW_MS - TONE_DURATION_MS);
        boolean heard = serial.waitForResponse(remaining + 100);

        serial.stopTone();
        serial.closeResponseWindow();
        return heard;
    }

    /**
     * Simulasyon icin dB HL -> amplitude (0.0-1.0) mapping'i.
     *
     * NOT (BME ile dogrulanacak): Gercek bir audiometre ICI-389-1 RETSPL ile
     * frekansa bagli kalibrasyon yapar. Simulasyon ortaminda fiziksel transducer
     * yok, bu yuzden basit yarim-log bir mapping kullaniyoruz:
     *   amp = 10^((dB - 100) / 40)
     * Bu mapping 100 dB HL -> 1.0, 60 dB -> ~0.1, 20 dB -> ~0.01 verir.
     * Onceki 10^((dB-100)/20) formulu dusuk seviyelerde DAC'in altinda kaliyordu.
     */
    public static double dbHlToAmplitude(int dbHl) {
        double normalized = Math.pow(10.0, (dbHl - 100.0) / 40.0);
        return Math.max(0.0005, Math.min(1.0, normalized));
    }

    // ---------- manuel mod ----------

    /** Manuel modda kullanici secimine gore tek bir ton calar ve cevabi dondurur. */
    public Optional<Boolean> manualPlayOnce(Ear ear, int frequency, int dbHl) {
        if (serial.getState() != SerialService.ConnectionState.CONNECTED) {
            gui.log("Baglanti yok.");
            return Optional.empty();
        }
        if (running.get()) {
            gui.log("Otomatik test devam ediyor, manuel sunum yapilmaz.");
            return Optional.empty();
        }
        TestState s = TestState.initial(ear, frequency, dbHl);
        try {
            boolean heard = presentOneTone(s);
            gui.log("Manuel sunum sonucu: " + (heard ? "DUYDU" : "duymadi"));
            return Optional.of(heard);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }
}
