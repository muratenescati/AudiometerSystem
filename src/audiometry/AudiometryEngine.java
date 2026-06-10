package audiometry;

import java.util.Optional;

/**
 * Hughson-Westlake "down 10 dB / up 5 dB" prosedurunu uygulayan saf fonksiyonel motor.
 *
 * BME (Biyomedikal Muh.) ekibinden gelen kural seti:
 *   1) Hasta duyduysa: -10 dB
 *   2) Duymadiysa:     +5 dB
 *   3) Esik: en az 3 ascending sunum + bunlarin en az 2'sinde cevap olan EN DUSUK dB seviyesi
 *      ("ascending sunum" = bir onceki "duymadi" sunumunun ardindan 5 dB yukari cikilip yapilan sunum)
 *
 * Yan etki yok. YMH (Software Eng.) ekibi bu metodu pure-function olarak
 * test edebilir (property-based test'ler dahil).
 *
 * Eski engine'deki "if (heard && db <= 20) threshold" sablonu BME spec'ine
 * uymadigi icin tamamen kaldirildi.
 */
public class AudiometryEngine {

    /** Yapilandirma: ascending sunum ve heard cevap esikleri. BME spec'i. */
    public static final int MIN_ASCENDING_TRIALS = 3;
    public static final int MIN_HEARD_AT_THRESHOLD = 2;

    private AudiometryEngine() {
        // static utility -- instance gerekli degil
    }

    /**
     * Tek bir sunum sonucundaki gecisi hesaplar.
     *
     * @param current  o anki TestState (sunum yapilmis seviye)
     * @param heard    hasta bu seviyede duydu mu
     * @return         bir sonraki state + (esik bulunduysa) ThresholdResult
     */
    public static StepResult nextStep(TestState current, boolean heard) {
        // 1) Tracking'i guncelle (ascending sayaci, lastWasNotHeard)
        TestState afterTracking = current.afterPresentation(heard);

        // 2) Esik kriteri saglandi mi?
        Optional<Integer> threshold = afterTracking.findThreshold();
        if (threshold.isPresent()) {
            ThresholdResult result = new ThresholdResult(
                    current.ear(),
                    current.frequency(),
                    threshold.get()
            );
            return new StepResult(afterTracking, Optional.of(result));
        }

        // 3) Esik henuz yok -- bir sonraki seviyeye gec (down 10 / up 5)
        TestState next = afterTracking.withNextHwLevel(heard);
        return new StepResult(next, Optional.empty());
    }
}
