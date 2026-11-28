package app.template.extension.zalo.chat.richtext;

import android.graphics.Path;
import java.util.HashMap;
import java.util.Map;

import app.template.extension.zalo.buglog.NezumiLog;
import app.template.extension.zalo.buglog.Patch;

/**
 * Material Design "format_*" icons (24 x 24 viewport, Apache License 2.0) as path data, drawn in
 * code so the patch needs no new resources. The Color button uses only the "A" of
 * format_color_text; its color bar is drawn separately.
 */
final class RichTextIcons {
    private RichTextIcons() { }

    static final String BOLD = "M15.6,10.79c0.97,-0.67 1.65,-1.77 1.65,-2.79 0,-2.26 -1.75,-4 -4,-4L7,4v14h7.04"
        + "c2.09,0 3.71,-1.7 3.71,-3.79 0,-1.52 -0.86,-2.82 -2.15,-3.42zM10,6.5h3c0.83,0 1.5,0.67 1.5,1.5"
        + "s-0.67,1.5 -1.5,1.5h-3v-3zM13.5,15.5L10,15.5v-3h3.5c0.83,0 1.5,0.67 1.5,1.5s-0.67,1.5 -1.5,1.5z";
    static final String ITALIC = "M10,4v3h2.21l-3.42,8H6v3h8v-3h-2.21l3.42,-8H18V4z";
    static final String UNDERLINE = "M12,17c3.31,0 6,-2.69 6,-6L18,3h-2.5v8c0,1.93 -1.57,3.5 -3.5,3.5S8.5,12.93 8.5,11"
        + "L8.5,3L6,3v8c0,3.31 2.69,6 6,6zM5,19v2h14v-2L5,19z";
    static final String STRIKE = "M10,19h4v-3h-4v3zM5,4v3h5v3h4L14,7h5L19,4L5,4zM3,14h18v-2L3,12v2z";
    static final String SIZE = "M9,4v3h5v12h3L17,7h5L22,4L9,4zM3,12h3v7h3v-7h3L12,9L3,9v3z";
    /** The "A" of format_color_text: spans y 3..17 of the viewport. */
    static final String COLOR_GLYPH = "M5.49,17h2.42l1.27,-3.58h5.65L16.09,17h2.42L13.24,3h-2.49L5.49,17z"
        + "M9.91,11.39l2.03,-5.79h0.12l2.03,5.79H9.91z";
    static final float COLOR_GLYPH_TOP = 3f;
    static final float COLOR_GLYPH_BOTTOM = 17f;
    static final String LIST_BULLETED = "M4,10.5c-0.83,0 -1.5,0.67 -1.5,1.5s0.67,1.5 1.5,1.5 1.5,-0.67 1.5,-1.5"
        + " -0.67,-1.5 -1.5,-1.5zM4,4.5c-0.83,0 -1.5,0.67 -1.5,1.5S3.17,7.5 4,7.5 5.5,6.83 5.5,6 4.83,4.5 4,4.5z"
        + "M4,16.67c-0.74,0 -1.33,0.6 -1.33,1.33s0.6,1.33 1.33,1.33 1.33,-0.6 1.33,-1.33 -0.59,-1.33 -1.33,-1.33z"
        + "M7,19h14v-2L7,17v2zM7,13h14v-2L7,11v2zM7,5v2h14L21,5L7,5z";
    static final String LIST_NUMBERED = "M2,17h2v0.5L3,17.5v1h1v0.5L2,19v1h3v-4L2,16v1zM3,8h1L4,4L2,4v1h1v3z"
        + "M2,11h1.8L2,13.1v0.9h3v-1L3.2,13L5,10.9L5,10L2,10v1zM7,5v2h14L21,5L7,5zM7,19h14v-2L7,17v2z"
        + "M7,13h14v-2L7,11v2z";
    static final String INDENT_DECREASE = "M11,17h10v-2L11,15v2zM3,12l4,4L7,8l-4,4zM3,21h18v-2L3,19v2z"
        + "M3,3v2h18L21,3L3,3zM11,9h10L21,7L11,7v2zM11,13h10v-2L11,11v2z";
    static final String INDENT_INCREASE = "M3,21h18v-2L3,19v2zM3,8v8l4,-4 -4,-4zM11,17h10v-2L11,15v2z"
        + "M3,3v2h18L21,3L3,3zM11,9h10L21,7L11,7v2zM11,13h10v-2L11,11v2z";
    static final String CLEAR = "M3.27,5L2,6.27l6.97,6.97L6.5,19h3l1.57,-3.66L16.73,21 18,19.73 3.55,5.27 3.27,5z"
        + "M6,5v0.18L8.82,8h2.4l-0.72,1.68 2.1,2.1L14.21,8H20V5H6z";

    private static final Map<String, Path> CACHE = new HashMap<>();

    /** The path for {@code data} in viewport units, or an empty path when it cannot be parsed. */
    static synchronized Path path(String data) {
        Path cached = CACHE.get(data);
        if (cached != null) return cached;
        Path path = new Path();
        try {
            SvgPath.parse(data, new SvgPath.Sink() {
                @Override public void moveTo(float x, float y) { path.moveTo(x, y); }
                @Override public void lineTo(float x, float y) { path.lineTo(x, y); }
                @Override public void cubicTo(float x1, float y1, float x2, float y2, float x, float y) {
                    path.cubicTo(x1, y1, x2, y2, x, y);
                }
                @Override public void quadTo(float x1, float y1, float x, float y) { path.quadTo(x1, y1, x, y); }
                @Override public void close() { path.close(); }
            });
        } catch (IllegalArgumentException failure) {
            NezumiLog.swallowedOnce(Patch.RICH_TEXT_FORMATTING, "RichTextIcons", "icon path unreadable", failure);
            path.reset();
        }
        CACHE.put(data, path);
        return path;
    }
}
