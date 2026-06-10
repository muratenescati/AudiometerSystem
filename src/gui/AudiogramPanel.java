package gui;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Standart odyogram cizimi.
 *
 * Eski versiyondan farkli:
 *   - Ayni kulagin esik noktalari arasi cizgilerle birlestiriliyor (klinik konvansiyon).
 *   - X ekseni: log frekans (250-8000 Hz)
 *   - Y ekseni: dB HL ters yonlu degil -- yukari cikinca DUSUK dB (iyi isitme), asagi yuksek dB.
 *     (Mevcut kodda zaten dogruydu, korundu.)
 *   - Sag kulak: kirmizi O, sol kulak: mavi X.
 *   - Eksen baslicalari ve cerceve eklendi.
 *   - clear() metodu eklendi (yeni hasta basladiginda).
 */
public class AudiogramPanel extends JPanel {

    public record Threshold(String ear, int freq, int db) {}

    private final List<Threshold> thresholds = new ArrayList<>();

    private static final int[] FREQS = {250, 500, 1000, 2000, 4000, 8000};
    private static final int[] DBS = {-10, 0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100};

    private static final int MARGIN_LEFT = 60;
    private static final int MARGIN_RIGHT = 30;
    private static final int MARGIN_TOP = 30;
    private static final int MARGIN_BOTTOM = 50;

    public AudiogramPanel() {
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(600, 450));
    }

    public void addThreshold(String ear, int freq, int db) {
        // Ayni (kulak, freq) varsa eskisini cikar (manuel mod retest icin)
        thresholds.removeIf(t -> t.ear().equalsIgnoreCase(ear) && t.freq() == freq);
        thresholds.add(new Threshold(ear, freq, db));
        repaint();
    }

    public void clear() {
        thresholds.clear();
        repaint();
    }

    public List<Threshold> getThresholds() {
        return List.copyOf(thresholds);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        drawGrid(g2, w, h);
        drawAxisLabels(g2, w, h);
        drawThresholds(g2, w, h);
        drawLegend(g2, w);

        g2.dispose();
    }

    private void drawGrid(Graphics2D g2, int w, int h) {
        g2.setColor(new Color(220, 220, 220));
        g2.setStroke(new BasicStroke(1f));

        // Dikey cizgiler (frekanslar)
        for (int f : FREQS) {
            int x = mapFreqToX(f, w);
            g2.drawLine(x, MARGIN_TOP, x, h - MARGIN_BOTTOM);
        }
        // Yatay cizgiler (dB)
        for (int db : DBS) {
            int y = mapDbToY(db, h);
            g2.drawLine(MARGIN_LEFT, y, w - MARGIN_RIGHT, y);
        }

        // Cerceve
        g2.setColor(Color.DARK_GRAY);
        g2.drawRect(MARGIN_LEFT, MARGIN_TOP,
                w - MARGIN_LEFT - MARGIN_RIGHT,
                h - MARGIN_TOP - MARGIN_BOTTOM);
    }

    private void drawAxisLabels(Graphics2D g2, int w, int h) {
        g2.setColor(Color.BLACK);
        Font small = getFont().deriveFont(11f);
        g2.setFont(small);

        FontMetrics fm = g2.getFontMetrics();

        for (int f : FREQS) {
            String label = (f >= 1000) ? (f / 1000) + "k" : String.valueOf(f);
            int x = mapFreqToX(f, w);
            int tw = fm.stringWidth(label);
            g2.drawString(label, x - tw / 2, h - MARGIN_BOTTOM + 15);
        }
        for (int db : DBS) {
            int y = mapDbToY(db, h);
            g2.drawString(String.valueOf(db), 25, y + 4);
        }

        // Eksen baslicari
        g2.setFont(getFont().deriveFont(Font.BOLD, 12f));
        g2.drawString("Frekans (Hz)", w / 2 - 35, h - 10);

        // Sol eksen baslici (dikey yazi)
        var orig = g2.getTransform();
        g2.rotate(-Math.PI / 2);
        g2.drawString("Isitme Seviyesi (dB HL)", -(h / 2 + 60), 18);
        g2.setTransform(orig);
    }

    private void drawThresholds(Graphics2D g2, int w, int h) {
        // Sag ve sol icin ayri sirali listeler hazirla, cizgilerle birlestir
        List<Threshold> right = thresholds.stream()
                .filter(t -> t.ear().equalsIgnoreCase("RIGHT"))
                .sorted(Comparator.comparingInt(Threshold::freq))
                .toList();
        List<Threshold> left = thresholds.stream()
                .filter(t -> t.ear().equalsIgnoreCase("LEFT"))
                .sorted(Comparator.comparingInt(Threshold::freq))
                .toList();

        drawConnectedSeries(g2, right, Color.RED, w, h, true);
        drawConnectedSeries(g2, left,  new Color(40, 80, 220), w, h, false);
    }

    private void drawConnectedSeries(Graphics2D g2, List<Threshold> series,
                                     Color color, int w, int h, boolean rightEar) {
        if (series.isEmpty()) return;

        g2.setColor(color);
        g2.setStroke(new BasicStroke(2f));

        // Cizgiler
        for (int i = 0; i < series.size() - 1; i++) {
            int x1 = mapFreqToX(series.get(i).freq(), w);
            int y1 = mapDbToY(series.get(i).db(), h);
            int x2 = mapFreqToX(series.get(i + 1).freq(), w);
            int y2 = mapDbToY(series.get(i + 1).db(), h);
            g2.drawLine(x1, y1, x2, y2);
        }

        // Sembol cizimi
        g2.setStroke(new BasicStroke(2.2f));
        for (Threshold t : series) {
            int x = mapFreqToX(t.freq(), w);
            int y = mapDbToY(t.db(), h);
            if (rightEar) {
                g2.drawOval(x - 7, y - 7, 14, 14);  // kirmizi O
            } else {
                g2.drawLine(x - 7, y - 7, x + 7, y + 7);  // mavi X
                g2.drawLine(x - 7, y + 7, x + 7, y - 7);
            }
        }
    }

    private void drawLegend(Graphics2D g2, int w) {
        Font small = getFont().deriveFont(11f);
        g2.setFont(small);

        int x = w - MARGIN_RIGHT - 110;
        int y = MARGIN_TOP + 12;

        g2.setColor(Color.RED);
        g2.drawOval(x, y - 8, 12, 12);
        g2.setColor(Color.BLACK);
        g2.drawString("Sag kulak (O)", x + 18, y + 2);

        g2.setColor(new Color(40, 80, 220));
        g2.drawLine(x, y + 10, x + 12, y + 22);
        g2.drawLine(x, y + 22, x + 12, y + 10);
        g2.setColor(Color.BLACK);
        g2.drawString("Sol kulak (X)", x + 18, y + 19);
    }

    private int mapFreqToX(int freq, int width) {
        double logMin = Math.log10(250);
        double logMax = Math.log10(8000);
        double t = (Math.log10(freq) - logMin) / (logMax - logMin);
        int plotW = width - MARGIN_LEFT - MARGIN_RIGHT;
        return (int) (MARGIN_LEFT + t * plotW);
    }

    private int mapDbToY(int db, int height) {
        double dbMin = -10;
        double dbMax = 100;
        double t = (db - dbMin) / (dbMax - dbMin);
        int plotH = height - MARGIN_TOP - MARGIN_BOTTOM;
        // Yukari = dusuk dB (iyi isitme)
        return (int) (MARGIN_TOP + t * plotH);
    }
}
