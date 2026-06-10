package audiometry;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Belirli bir (kulak, frekans) icin Hughson-Westlake algoritmasinin
 * o anki durumu. Immutable -- her degisiklik yeni bir TestState dondurur.
 *
 * YMH ekibinin "immutable data structure" gereksinimini karsilar.
 *
 * Tracking alanlari (BME spec'i geregi):
 *   - ascendingTotal:  her dB seviyesinde kac kez "yukari geldikten sonra" sunum yapildi
 *   - ascendingHeard:  bunlarin kacinda hasta cevap verdi
 *   - lastWasNotHeard: bir onceki sunum "duymadi" mi (bir sonrakinin ascending olup olmadigini belirler)
 *
 * Esik kriteri: en az 3 ascending sunum + en az 2 cevap olan en dusuk dB.
 */
public record TestState(
        Ear ear,
        int frequency,
        int intensityDb,
        Map<Integer, Integer> ascendingTotal,
        Map<Integer, Integer> ascendingHeard,
        boolean lastWasNotHeard
) {

    /** Bir (kulak, frekans) testine baslarken kullanilan baslangic durumu. */
    public static TestState initial(Ear ear, int frequency, int startDb) {
        return new TestState(
                ear,
                frequency,
                startDb,
                Collections.emptyMap(),
                Collections.emptyMap(),
                false  // ilk sunum ascending sayilmaz
        );
    }

    /**
     * O anki sunumun ardindan tracking'i gunceller.
     * lastWasNotHeard=true ise simdiki sunum "ascending" sayilir.
     */
    public TestState afterPresentation(boolean heard) {
        Map<Integer, Integer> newTotal = ascendingTotal;
        Map<Integer, Integer> newHeard = ascendingHeard;

        if (lastWasNotHeard) {
            // Simdiki sunum ascending: sayaclari guncelle
            Map<Integer, Integer> tmpTotal = new HashMap<>(ascendingTotal);
            tmpTotal.merge(intensityDb, 1, Integer::sum);
            newTotal = Collections.unmodifiableMap(tmpTotal);

            if (heard) {
                Map<Integer, Integer> tmpHeard = new HashMap<>(ascendingHeard);
                tmpHeard.merge(intensityDb, 1, Integer::sum);
                newHeard = Collections.unmodifiableMap(tmpHeard);
            }
        }

        return new TestState(
                ear, frequency, intensityDb,
                newTotal, newHeard,
                !heard  // bir sonraki tur icin: simdiki sunum duymadi mi
        );
    }

    /**
     * "Down 10 dB / up 5 dB" kurali ile bir sonraki dB seviyesini uretir.
     * Guvenli aralikta klipsler (-10..120 dB HL).
     */
    public TestState withNextHwLevel(boolean heard) {
        int delta = heard ? -10 : +5;
        int newDb = Math.max(-10, Math.min(120, intensityDb + delta));
        return new TestState(
                ear, frequency, newDb,
                ascendingTotal, ascendingHeard,
                lastWasNotHeard
        );
    }

    /**
     * Manuel mod icin: dB'yi dogrudan ayarla. Tracking'i temizler
     * (manuel mudahale algoritma sayaclarini bozar).
     */
    public TestState withManualLevel(int newDb) {
        int safe = Math.max(-10, Math.min(120, newDb));
        return TestState.initial(ear, frequency, safe);
    }

    /**
     * Esik bulundu mu? Bulunduysa hangi dB'de? (en dusuk uygun seviye)
     * Kriter (BME): >=3 ascending sunum, bunlarin >=2'sinde cevap.
     */
    public Optional<Integer> findThreshold() {
        return ascendingTotal.entrySet().stream()
                .filter(e -> e.getValue() >= 3)
                .filter(e -> ascendingHeard.getOrDefault(e.getKey(), 0) >= 2)
                .map(Map.Entry::getKey)
                .min(Integer::compareTo);
    }

    @Override
    public String toString() {
        return ear + " | " + frequency + " Hz | " + intensityDb + " dB HL";
    }
}
