package qz.utils;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

class WatermarkTest {

    @AfterEach
    void reset() {
        Watermark.setTextProvider(null);
    }

    @Test
    void inactiveByDefault() {
        assertNull(Watermark.getText());
    }

    @Test
    void providerSuppliesText() {
        Watermark.setTextProvider(() -> "TRIAL EXPIRED");
        assertEquals("TRIAL EXPIRED", Watermark.getText());
    }

    @Test
    void blankTextMeansInactive() {
        Watermark.setTextProvider(() -> "   ");
        assertNull(Watermark.getText());
    }

    @Test
    void providerExceptionMeansInactive() {
        Watermark.setTextProvider(() -> { throw new RuntimeException("boom"); });
        assertNull(Watermark.getText());
    }

    @Test
    void htmlWatermarkInjectedBeforeBodyClose() {
        Watermark.setTextProvider(() -> "TRIAL EXPIRED");
        String out = Watermark.applyToHtml("<html><body><p>receipt</p></body></html>");
        assertTrue(out.contains("TRIAL EXPIRED"));
        assertTrue(out.indexOf("TRIAL EXPIRED") < out.indexOf("</body>"),
                "watermark must be injected before </body>");
        assertTrue(out.contains("rotate(-30deg)"), "diagonal overlay expected");
    }

    @Test
    void htmlWithoutBodyGetsAppendedWatermark() {
        Watermark.setTextProvider(() -> "TRIAL EXPIRED");
        String out = Watermark.applyToHtml("<p>receipt</p>");
        assertTrue(out.endsWith("</div>"));
        assertTrue(out.contains("TRIAL EXPIRED"));
    }

    @Test
    void htmlUntouchedWhenInactive() {
        String html = "<html><body>x</body></html>";
        assertEquals(html, Watermark.applyToHtml(html));
    }

    @Test
    void htmlIsEscaped() {
        Watermark.setTextProvider(() -> "<script>alert(1)</script>");
        String out = Watermark.applyToHtml("<html><body>x</body></html>");
        assertFalse(out.contains("<script>alert"));
        assertTrue(out.contains("&lt;script&gt;"));
    }

    @Test
    void imageModifiedWhenActive() {
        Watermark.setTextProvider(() -> "TRIAL EXPIRED");
        BufferedImage img = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
        BufferedImage before = copyOf(img);

        Watermark.applyToImage(img);

        assertTrue(differs(before, img), "watermark must alter pixels");
    }

    @Test
    void imageUntouchedWhenInactive() {
        BufferedImage img = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
        BufferedImage before = copyOf(img);

        Watermark.applyToImage(img);

        assertFalse(differs(before, img), "inactive watermark must not alter pixels");
    }

    @Test
    void pdfPagesGainContentWhenActive() throws Exception {
        Watermark.setTextProvider(() -> "TRIAL EXPIRED");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            long before = contentLength(doc);

            Watermark.applyToPdf(doc);

            long after = contentLength(doc);
            assertTrue(after > before, "watermark content stream must be appended");
        }
    }

    @Test
    void pdfUntouchedWhenInactive() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            long before = contentLength(doc);
            Watermark.applyToPdf(doc);
            assertEquals(before, contentLength(doc));
        }
    }

    private static BufferedImage copyOf(BufferedImage src) {
        BufferedImage copy = new BufferedImage(src.getWidth(), src.getHeight(), src.getType());
        copy.getGraphics().drawImage(src, 0, 0, null);
        return copy;
    }

    private static boolean differs(BufferedImage a, BufferedImage b) {
        for (int y = 0; y < a.getHeight(); y += 7) {
            for (int x = 0; x < a.getWidth(); x += 7) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static long contentLength(PDDocument doc) throws Exception {
        long total = 0;
        for (PDPage page : doc.getPages()) {
            if (page.getContents() != null) {
                total += org.apache.pdfbox.io.IOUtils.toByteArray(page.getContents()).length;
            }
        }
        return total;
    }
}
