package qz.utils;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.blend.BlendMode;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.function.Supplier;

/**
 * Print watermarking support (expired-trial soft-lock, C1).
 *
 * <p>Follows the canonical watermark pattern (cf. PDFBox AddWatermarkText
 * example and Java2D compositing best practices): a single DIAGONAL,
 * semi-transparent (alpha ~0.2) red text overlay. Diagonal-over-content is
 * preferred over edge banners because it cannot be cropped away and remains
 * visible over dark content (MULTIPLY blend on PDF).
 *
 * <p>A static text provider decides at print time whether a watermark applies.
 * When the provider returns {@code null}/empty, printing is untouched.
 * The tray app wires this to its license state; printlib itself stays
 * licensing-agnostic.
 */
public final class Watermark {

    private static final float ALPHA = 0.2f;
    private static final Color WATERMARK_COLOR = new Color(196, 43, 28);
    /** Light watermark tint for PDF (works on raster paths where PDF alpha is not honored). */
    private static final Color WATERMARK_PDF_COLOR = new Color(240, 178, 170);
    /** Fraction of each dimension the watermark may occupy. */
    private static final float FIT_MARGIN = 0.90f;
    /** Approximate average glyph width of bold latin text, in em. */
    private static final float AVG_CHAR_EM = 0.55f;
    /** Fraction of the diagonal the longest line may occupy. */
    private static final float MAX_WIDTH_FRACTION = 0.85f;
    /** Line height as a multiple of the font size. */
    private static final float LINE_SPACING = 1.2f;

    private static volatile Supplier<String> textProvider = () -> null;

    private Watermark() {}

    /**
     * Installs the watermark text provider. Return {@code null}/empty for no watermark.
     */
    public static void setTextProvider(Supplier<String> provider) {
        textProvider = provider != null ? provider : () -> null;
    }

    /**
     * @return the active watermark text, or {@code null} when no watermark applies
     */
    public static String getText() {
        try {
            String text = textProvider.get();
            return (text == null || text.isBlank()) ? null : text;
        } catch (Exception e) {
            return null; // never break the print path
        }
    }

    // ==================== Images (Java2D) ====================

    /**
     * Draws the watermark diagonally across an image, in place (no-op when inactive).
     * Multi-line text ({@code \n} separated) is centered as a block; the font is
     * scaled so the LONGEST line always fits the image diagonal.
     */
    public static void applyToImage(BufferedImage image) {
        String text = getText();
        if (text == null || image == null) {
            return;
        }
        String[] lines = text.split("\\r?\\n");
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, ALPHA));
            g.setColor(WATERMARK_COLOR);

            int width = image.getWidth();
            int height = image.getHeight();
            double diagonal = Math.sqrt(width * width + height * height);

            // Scale font so the rotated block always fits the canvas
            float fontSize = fitFontSize(g, lines, width, height, (float) (diagonal * 0.10));
            Font font = g.getFont().deriveFont(Font.BOLD, fontSize);
            g.setFont(font);
            FontMetrics fm = g.getFontMetrics();
            float lineHeight = fontSize * LINE_SPACING;
            float blockHeight = lines.length * lineHeight;

            // Center and rotate around the image center
            AffineTransform original = g.getTransform();
            double angle = Math.atan2(height, width);
            g.translate(width / 2.0, height / 2.0);
            g.rotate(-angle);

            for (int i = 0; i < lines.length; i++) {
                float x = -fm.stringWidth(lines[i]) / 2.0f;
                float y = -blockHeight / 2.0f + i * lineHeight + fm.getAscent();
                g.drawString(lines[i], x, y);
            }

            g.setTransform(original);
        } finally {
            g.dispose();
        }
    }

    /**
     * Computes the largest font size (capped at startSize) such that the whole
     * rotated text block fits the canvas: longest line along the diagonal,
     * block horizontal span within 90% of width, block vertical span within
     * 90% of height. This is what keeps narrow/tall receipts from clipping.
     */
    static float fitFontSize(Graphics2D g, String[] lines, int width, int height, float startSize) {
        double angle = Math.atan2(height, width);
        double diagonal = Math.sqrt(width * width + height * height);
        int maxChars = 1;
        for (String line : lines) {
            maxChars = Math.max(maxChars, line.length());
        }
        double cosA = Math.abs(Math.cos(angle));
        double sinA = Math.abs(Math.sin(angle));

        // length of longest line ~= maxChars * AVG_CHAR_EM * fontSize
        float byDiagonal = (float) (MAX_WIDTH_FRACTION * diagonal / (maxChars * AVG_CHAR_EM));
        // horizontal: L*cos + lineHeight*sin <= FIT_MARGIN * width
        float byWidth = (float) (FIT_MARGIN * width
                / (maxChars * AVG_CHAR_EM * cosA + lines.length * LINE_SPACING * sinA));
        // vertical: L*sin + lineHeight*cos <= FIT_MARGIN * height
        float byHeight = (float) (FIT_MARGIN * height
                / (maxChars * AVG_CHAR_EM * sinA + lines.length * LINE_SPACING * cosA));

        float size = Math.min(startSize, Math.min(byDiagonal, Math.min(byWidth, byHeight)));
        return Math.max(8f, size);
    }

    // ==================== PDF (PDFBox) ====================

    /**
     * Adds a diagonal watermark to every page of a PDF document (no-op when inactive).
     * Mirrors the official PDFBox AddWatermarkText example: APPEND mode,
     * MULTIPLY blend, alpha 0.2, page-rotation aware.
     */
    public static void applyToPdf(PDDocument doc) {
        String text = getText();
        if (text == null || doc == null) {
            return;
        }
        for (PDPage page : doc.getPages()) {
            try {
                addDiagonalText(doc, page, text);
            } catch (Exception ignored) {
                // best-effort: never break the print path
            }
        }
    }

    private static void addDiagonalText(PDDocument doc, PDPage page, String text) throws IOException {
        try (PDPageContentStream cs = new PDPageContentStream(
                doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
            String[] lines = text.split("\\r?\\n");
            PDFont font = PDType1Font.HELVETICA_BOLD;
            PDRectangle box = page.getMediaBox();
            float width = box.getWidth();
            float height = box.getHeight();

            // Account for pre-rotated pages (per the official example)
            switch (page.getRotation()) {
                case 90:
                    width = box.getHeight();
                    height = box.getWidth();
                    cs.transform(Matrix.getRotateInstance(Math.toRadians(90), height, 0));
                    break;
                case 180:
                    cs.transform(Matrix.getRotateInstance(Math.toRadians(180), width, height));
                    break;
                case 270:
                    width = box.getHeight();
                    height = box.getWidth();
                    cs.transform(Matrix.getRotateInstance(Math.toRadians(270), 0, width));
                    break;
                default:
                    break;
            }

            // Scale font so the rotated block always fits the page
            float fontHeight = fitPdfFontSize(font, lines, width, height, 60f);
            float lineHeight = fontHeight * LINE_SPACING;
            float blockHeight = lines.length * lineHeight;

            float angle = (float) Math.atan2(height, width);
            float diagonalLength = (float) Math.sqrt(width * width + height * height);
            cs.transform(Matrix.getRotateInstance(angle, 0, 0));
            cs.setFont(font, fontHeight);

            // Light tint directly - no alpha dependency (raster paths and
            // PDFRenderer do not reliably honor alpha graphics states)
            cs.setNonStrokingColor(WATERMARK_PDF_COLOR);

            // Each line starts along the diagonal so the block stays on-page;
            // lines stack perpendicular to it, centered on the diagonal's midpoint
            float mid = (lines.length - 1) / 2.0f;
            for (int i = 0; i < lines.length; i++) {
                float stringWidth = font.getStringWidth(sanitizeForPdf(lines[i])) / 1000 * fontHeight;
                float x = (diagonalLength - stringWidth) / 2;
                float y = (i - mid) * lineHeight + fontHeight * 0.8f;
                cs.beginText();
                cs.newLineAtOffset(x, y);
                cs.showText(sanitizeForPdf(lines[i]));
                cs.endText();
            }
        }
    }

    /**
     * Computes the largest PDF font size (capped at startSize) such that the
     * rotated text block fits the page: longest line along the diagonal,
     * block spans within 90% of width and height.
     */
    private static float fitPdfFontSize(PDFont font, String[] lines, float width, float height,
                                        float startSize) throws IOException {
        double angle = Math.atan2(height, width);
        double diagonal = Math.sqrt(width * width + height * height);
        String widest = lines[0];
        int maxChars = 1;
        for (String line : lines) {
            if (line.length() >= widest.length()) {
                widest = line;
            }
            maxChars = Math.max(maxChars, line.length());
        }
        double cosA = Math.abs(Math.cos(angle));
        double sinA = Math.abs(Math.sin(angle));

        // exact width of the widest line at 1pt (PDFBox gives real metrics)
        float unitWidth = font.getStringWidth(sanitizeForPdf(widest)) / 1000f;

        float byDiagonal = (float) (MAX_WIDTH_FRACTION * diagonal / unitWidth);
        float byWidth = (float) (FIT_MARGIN * width
                / (unitWidth * cosA + lines.length * LINE_SPACING * sinA));
        float byHeight = (float) (FIT_MARGIN * height
                / (unitWidth * sinA + lines.length * LINE_SPACING * cosA));

        float size = Math.min(startSize, Math.min(byDiagonal, Math.min(byWidth, byHeight)));
        return Math.max(8f, size);
    }

    // ==================== HTML ====================

    /**
     * Injects a diagonal, semi-transparent watermark overlay into an HTML
     * document (no-op when inactive). Uses a fixed full-viewport rotated div;
     * pointer-events disabled so it never interferes with content.
     */
    public static String applyToHtml(String html) {
        String text = getText();
        if (text == null || html == null) {
            return html;
        }
        String banner = "<div style=\"position:fixed;top:50%;left:50%;z-index:99999;"
                + "transform:translate(-50%,-50%) rotate(-30deg);"
                + "color:rgba(196,43,28,0.2);font:bold 6vmin sans-serif;"
                + "text-align:center;line-height:1.2;max-width:90vw;overflow:hidden;"
                + "white-space:nowrap;pointer-events:none;\">"
                + escapeHtml(text).replace("\n", "<br/>") + "</div>";

        String lower = html.toLowerCase();
        int bodyClose = lower.lastIndexOf("</body>");
        if (bodyClose >= 0) {
            return html.substring(0, bodyClose) + banner + html.substring(bodyClose);
        }
        return html + banner;
    }

    // ==================== Helpers ====================

    private static String sanitizeForPdf(String text) {
        // WinAnsi-safe: flatten newlines and strip non-latin-1 characters
        String flat = text.replace('\n', ' ').replace('\r', ' ');
        StringBuilder sb = new StringBuilder(flat.length());
        for (char c : flat.toCharArray()) {
            sb.append(c < 256 ? c : '?');
        }
        return sb.toString();
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
