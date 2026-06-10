package util;

import audiometry.ThresholdResult;
import gui.PatientInfo;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Esik sonuclarini CSV olarak dosyaya yazar.
 * Excel'in dogru acabilmesi icin UTF-8 BOM ekliyoruz.
 */
public class CsvExporter {

    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());

    public static void export(Path path,
                              PatientInfo patient,
                              List<ThresholdResult> results) throws IOException {
        try (PrintWriter pw = new PrintWriter(
                Files.newBufferedWriter(path, StandardCharsets.UTF_8))) {

            // UTF-8 BOM
            pw.print('\uFEFF');

            // Hasta bilgisi basligi
            pw.println("# Audiometer Report");
            pw.printf(Locale.US, "# Patient name,%s%n", csv(patient.name()));
            pw.printf(Locale.US, "# Patient id,%s%n",   csv(patient.id()));
            pw.printf(Locale.US, "# Age,%d%n",          patient.age());
            pw.printf(Locale.US, "# Note,%s%n",         csv(patient.note()));
            pw.println();

            // Sonuc tablosu
            pw.println("ear,frequency_hz,threshold_db_hl,timestamp");
            for (ThresholdResult r : results) {
                pw.printf(Locale.US, "%s,%d,%d,%s%n",
                        r.ear().name(),
                        r.frequency(),
                        r.thresholdDb(),
                        TS_FORMAT.format(r.timestamp()));
            }
        }
    }

    private static String csv(String s) {
        if (s == null) return "";
        String safe = s.replace("\"", "\"\"");
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n")) {
            return "\"" + safe + "\"";
        }
        return safe;
    }
}
