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
     */
    public static void applyToImage(BufferedImage image) {
        String text = getText();
        if (text == null || image == null) {
            return;
        }
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, ALPHA));
            g.setColor(WATERMARK_COLOR);

            int width = image.getWidth();
            int height = image.getHeight();

            // Scale the font so the text spans most of the diagonal
            double diagonal = Math.sqrt(width * width + height * height);
            float fontSize = (float) Math.max(12.0, diagonal * 0.08);
            Font font = g.getFont().deriveFont(Font.BOLD, fontSize);
            g.setFont(font);

            // Center and rotate around the image center (best practice:
            // transform the graphics context rather than computing glyph paths)
            AffineTransform original = g.getTransform();
            double angle = Math.atan2(height, width);
            g.translate(width / 2.0, height / 2.0);
            g.rotate(-angle);

            FontMetrics fm = g.getFontMetrics();
            float x = -fm.stringWidth(text) / 2.0f;
            float y = fm.getAscent() / 2.0f;
            g.drawString(text, x, y);

            g.setTransform(original);
        } finally {
            g.dispose();
        }
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
            PDFont font = PDType1Font.HELVETICA_BOLD;
            float fontHeight = 60;
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

            float stringWidth = font.getStringWidth(sanitizeForPdf(text)) / 1000 * fontHeight;
            float diagonalLength = (float) Math.sqrt(width * width + height * height);
            if (stringWidth > diagonalLength * 0.9f) {
                // Scale down so the text always fits the diagonal
                fontHeight *= (float) (diagonalLength * 0.9f / stringWidth);
                stringWidth = font.getStringWidth(sanitizeForPdf(text)) / 1000 * fontHeight;
            }
            float angle = (float) Math.atan2(height, width);
            float x = (diagonalLength - stringWidth) / 2;
            float y = -fontHeight / 4;
            cs.transform(Matrix.getRotateInstance(angle, 0, 0));
            cs.setFont(font, fontHeight);

            PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
            gs.setNonStrokingAlphaConstant(ALPHA);
            gs.setStrokingAlphaConstant(ALPHA);
            gs.setBlendMode(BlendMode.MULTIPLY);
            cs.setGraphicsStateParameters(gs);
            cs.setNonStrokingColor(WATERMARK_COLOR);

            cs.beginText();
            cs.newLineAtOffset(x, y);
            cs.showText(sanitizeForPdf(text));
            cs.endText();
        }
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
                + "color:rgba(196,43,28,0.2);font:bold 48px sans-serif;"
                + "white-space:nowrap;pointer-events:none;\">"
                + escapeHtml(text) + "</div>";

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
