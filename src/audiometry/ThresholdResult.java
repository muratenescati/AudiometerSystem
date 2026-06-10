package audiometry;

import java.time.Instant;

/**
 * Bir (kulak, frekans) icin Hughson-Westlake esiginin nihai sonucu.
 * BME spec: cikti formati = {ear, frequency, threshold_dB_HL}.
 * Zaman damgasi raporlama / loglama icin eklendi.
 */
public record ThresholdResult(
        Ear ear,
        int frequency,
        int thresholdDb,
        Instant timestamp
) {
    public ThresholdResult(Ear ear, int frequency, int thresholdDb) {
        this(ear, frequency, thresholdDb, Instant.now());
    }
}
