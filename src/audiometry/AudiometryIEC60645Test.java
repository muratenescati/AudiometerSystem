package audiometry;

import net.jqwik.api.*;
import org.junit.jupiter.api.Assertions;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IEC 60645-1 Standardi Property-Based (Ozellik Tabanli) Testleri.
 * jqwik kutuphanesi kullanilarak, fonksiyonlarin yuzlerce rastgele
 * veriyle bile cokmedigi ve kurallara uydugu ispatlanir.
 */
public class AudiometryIEC60645Test {

    /**
     * KURAL 1: Güvenlik Sınırları.
     * Hangi dB seviyesinde olursak olalım, bir sonraki adim
     * ASLA -10 dB altina dusmemeli ve 120 dB HL uzerine cikmamalidir.
     */
    @Property
    void dbLevelShouldNeverExceedSafetyBounds(
            @ForAll("validDecibels") int currentDb,
            @ForAll boolean heard) {

        // Rastgele bir durum olustur
        TestState state = TestState.initial(Ear.RIGHT, 1000, currentDb);

        // Saf fonksiyonumuzla (Pure Function) sonraki durumu hesapla
        TestState nextState = state.withNextHwLevel(heard);
        int nextDb = nextState.intensityDb();

        // Dogrulama (Assertion)
        assertTrue(nextDb >= -10 && nextDb <= 120,
                "KURAL IHLALI! Güvenlik sınırı aşıldı: " + nextDb + " dB");
    }

    /**
     * KURAL 2: Hasta Sesi DUYARSA, seviye 10 dB DÜŞMELİDİR.
     * (Eğer -10 sınırına takılmazsa)
     */
    @Property
    void whenHeardLevelDecreasesBy10OrHitsMinimum(
            @ForAll("validDecibels") int currentDb) {

        TestState state = TestState.initial(Ear.RIGHT, 1000, currentDb);
        TestState nextState = state.withNextHwLevel(true); // true = Duydu

        int expectedDb = Math.max(-10, currentDb - 10);
        Assertions.assertEquals(expectedDb, nextState.intensityDb(),
                "KURAL IHLALI! Duyulan seste 10 dB dusus yapilmadi.");
    }

    /**
     * KURAL 3: Hasta Sesi DUYMAZSA, seviye 5 dB ÇIKMALIDIR.
     * (Eğer 120 sınırına takılmazsa)
     */
    @Property
    void whenNotHeardLevelIncreasesBy5OrHitsMaximum(
            @ForAll("validDecibels") int currentDb) {

        TestState state = TestState.initial(Ear.RIGHT, 1000, currentDb);
        TestState nextState = state.withNextHwLevel(false); // false = Duymadi

        int expectedDb = Math.min(120, currentDb + 5);
        Assertions.assertEquals(expectedDb, nextState.intensityDb(),
                "KURAL IHLALI! Duyulmayan seste 5 dB artis yapilmadi.");
    }

    @Property
    void hwLevelShouldNeverExceedSafetyBounds(
            @ForAll("validDecibels") int currentDb,
            @ForAll boolean heard) {

        TestState state = TestState.initial(Ear.RIGHT, 1000, currentDb);
        TestState nextState = state.withNextHwLevel(heard);

        // Detaylı loglama (her adımda çalışır)
        System.out.printf("Test Ediliyor: dB=%d, Duydu=%b -> Yeni dB=%d%n",
                currentDb, heard, nextState.intensityDb());

        assertTrue(nextState.intensityDb() >= -10 && nextState.intensityDb() <= 120);
    }
    /**
     * Veri Sağlayıcı (Data Provider):
     * Testlere -10 ile 120 arasında rastgele yüzlerce sayi gonderir.
     */
    @Provide
    Arbitrary<Integer> validDecibels() {
        return Arbitraries.integers().between(-10, 120);
    }
}