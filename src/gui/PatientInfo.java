package gui;

/**
 * Hasta bilgisi (rapor / CSV cikti icin).
 * Immutable. Bos hasta varsayilani icin `empty()` kullanin.
 */
public record PatientInfo(String name, String id, int age, String note) {

    public static PatientInfo empty() {
        return new PatientInfo("", "", 0, "");
    }

    public boolean isEmpty() {
        return (name == null || name.isBlank())
                && (id == null || id.isBlank());
    }
}
