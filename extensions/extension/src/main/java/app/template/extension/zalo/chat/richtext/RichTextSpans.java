package app.template.extension.zalo.chat.richtext;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.LeadingMarginSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.text.style.UpdateAppearance;
import java.util.ArrayList;
import java.util.List;

/**
 * The spans the formatting toolbar puts in the composer, and the adapter that turns them into
 * {@link RichTextRun}s at send time.
 *
 * <p>Only spans implementing {@link ToolbarSpan} are serialized. Most are subclasses of the Android
 * spans, so the composer renders them as usual, but pasted or foreign spans (including Zalo's own
 * mention span, a {@code ForegroundColorSpan}) are ignored. Copying through the clipboard parcels
 * them as the plain framework type (or drops {@link Color}, which is not parcelable), so pasted spans
 * are never toolbar spans; only {@link RichTextClipboard}'s stash re-creates them on a matching paste.
 *
 * <p>{@link ListMarker} and {@link IndentMarker} mark lines and render as leading margins (bullet,
 * number, indent). A marker applies to every line it overlaps. The toolbar keeps one marker per
 * paragraph, covering the paragraph and its trailing line break.
 */
public final class RichTextSpans {
    private RichTextSpans() { }

    /** Implemented by every span the toolbar creates. */
    public interface ToolbarSpan {
        RichTextRun.Kind kind();

        /** The run value: size token, 0xRRGGBB color, list type or indent level; 0 when unused. */
        int value();
    }

    public static final class Bold extends StyleSpan implements ToolbarSpan {
        public Bold() { super(Typeface.BOLD); }
        @Override public RichTextRun.Kind kind() { return RichTextRun.Kind.BOLD; }
        @Override public int value() { return 0; }
    }

    public static final class Italic extends StyleSpan implements ToolbarSpan {
        public Italic() { super(Typeface.ITALIC); }
        @Override public RichTextRun.Kind kind() { return RichTextRun.Kind.ITALIC; }
        @Override public int value() { return 0; }
    }

    public static final class Underline extends UnderlineSpan implements ToolbarSpan {
        @Override public RichTextRun.Kind kind() { return RichTextRun.Kind.UNDERLINE; }
        @Override public int value() { return 0; }
    }

    public static final class Strike extends StrikethroughSpan implements ToolbarSpan {
        @Override public RichTextRun.Kind kind() { return RichTextRun.Kind.STRIKE; }
        @Override public int value() { return 0; }
    }

    /**
     * Font size token (13 / 18 / 20, the web presets). The token is what gets sent; receivers draw
     * it as an absolute dp size against a smaller normal bubble text, so the difference is clear
     * there. The composer's normal text is larger (about 18), where an absolute 18 looked the same
     * as normal, so the composer shows the token relative to its own text size instead, taking
     * {@link #NORMAL_REFERENCE} as the size of normal text.
     */
    public static final class Size extends RelativeSizeSpan implements ToolbarSpan {
        /** The web's normal text size; tokens scale against it in the composer. */
        static final float NORMAL_REFERENCE = 15f;

        private final int token;

        public Size(int token) {
            super(token / NORMAL_REFERENCE);
            this.token = token;
        }

        @Override public RichTextRun.Kind kind() { return RichTextRun.Kind.SIZE; }
        @Override public int value() { return token; }
    }

    /**
     * Text color 0xRRGGBB; only whitelist colors are sent.
     *
     * <p>Deliberately not a {@code ForegroundColorSpan}: Zalo's composer clears every non-mention
     * {@code ForegroundColorSpan} whenever its smart-suggestion highlight changes
     * ({@code ChatView.ac()} in 26.11.01, called from the presenter as the user types), which
     * dropped the toolbar color after typing a space.
     */
    public static final class Color extends CharacterStyle implements UpdateAppearance, ToolbarSpan {
        private final int rgb;

        public Color(int rgb) {
            this.rgb = rgb & 0xFFFFFF;
        }

        @Override
        public void updateDrawState(TextPaint paint) {
            paint.setColor(0xFF000000 | rgb);
        }

        @Override public RichTextRun.Kind kind() { return RichTextRun.Kind.COLOR; }
        @Override public int value() { return rgb; }
    }

    /**
     * List line marker: {@link RichTextRun#LIST_BULLET} or {@link RichTextRun#LIST_NUMBERED}. In the
     * composer it reserves a gutter and draws the bullet or the number on the paragraph's first line.
     * {@code number} (1-based) is only used for drawing; the toolbar keeps it in sequence.
     */
    public static final class ListMarker implements ToolbarSpan, LeadingMarginSpan {
        private static final float GUTTER_DP = 22f;
        private static final float BULLET_RADIUS_DP = 2.5f;
        private static final float BULLET_CENTER_DP = 10f;
        private static final float NUMBER_END_DP = 18f;

        private final int type;
        private final int number;

        public ListMarker(int type) { this(type, 1); }

        public ListMarker(int type, int number) {
            this.type = type;
            this.number = Math.max(1, number);
        }

        @Override public RichTextRun.Kind kind() { return RichTextRun.Kind.LIST; }
        @Override public int value() { return type; }

        public int number() { return number; }

        @Override
        public int getLeadingMargin(boolean first) {
            return Math.round(GUTTER_DP * density());
        }

        @Override
        public void drawLeadingMargin(Canvas canvas, Paint paint, int x, int dir, int top, int baseline, int bottom,
                CharSequence text, int start, int end, boolean first, Layout layout) {
            if (!first || !(text instanceof Spanned) || ((Spanned) text).getSpanStart(this) != start) return;
            float density = density();
            Paint.Style style = paint.getStyle();
            try {
                if (type == RichTextRun.LIST_NUMBERED) {
                    String label = number + ".";
                    float right = dir < 0 ? x - NUMBER_END_DP * density + paint.measureText(label)
                        : x + NUMBER_END_DP * density;
                    paint.setStyle(Paint.Style.FILL);
                    canvas.drawText(label, right - paint.measureText(label), baseline, paint);
                } else {
                    float centerX = x + dir * BULLET_CENTER_DP * density;
                    float centerY = baseline + (paint.ascent() + paint.descent()) / 2f;
                    paint.setStyle(Paint.Style.FILL);
                    canvas.drawCircle(centerX, centerY, BULLET_RADIUS_DP * density, paint);
                }
            } finally {
                paint.setStyle(style);
            }
        }
    }

    /**
     * Indent line marker, level 1 or more. In the composer it indents the paragraph by the margin
     * Zalo gives {@code ind_<level*10>} on display (10dp per level).
     */
    public static final class IndentMarker implements ToolbarSpan, LeadingMarginSpan {
        private static final float STEP_DP = 10f;

        private final int level;

        public IndentMarker(int level) { this.level = level; }

        @Override public RichTextRun.Kind kind() { return RichTextRun.Kind.INDENT; }
        @Override public int value() { return level; }

        @Override
        public int getLeadingMargin(boolean first) {
            return Math.round(level * STEP_DP * density());
        }

        @Override
        public void drawLeadingMargin(Canvas canvas, Paint paint, int x, int dir, int top, int baseline, int bottom,
                CharSequence text, int start, int end, boolean first, Layout layout) {
        }
    }

    private static float density() {
        return Resources.getSystem().getDisplayMetrics().density;
    }

    /** The toolbar span class for {@code kind}. */
    public static Class<? extends ToolbarSpan> spanClass(RichTextRun.Kind kind) {
        switch (kind) {
            case BOLD: return Bold.class;
            case ITALIC: return Italic.class;
            case UNDERLINE: return Underline.class;
            case STRIKE: return Strike.class;
            case SIZE: return Size.class;
            case COLOR: return Color.class;
            case LIST: return ListMarker.class;
            default: return IndentMarker.class;
        }
    }

    /** A new inline toolbar span of {@code kind} with run {@code value} (ignored for on/off kinds). */
    public static ToolbarSpan newInlineSpan(RichTextRun.Kind kind, int value) {
        switch (kind) {
            case BOLD: return new Bold();
            case ITALIC: return new Italic();
            case UNDERLINE: return new Underline();
            case STRIKE: return new Strike();
            case SIZE: return new Size(value);
            case COLOR: return new Color(value);
            default: throw new IllegalArgumentException("Not an inline kind: " + kind);
        }
    }

    /** Runs for every toolbar span in {@code text}; foreign spans are ignored. */
    public static List<RichTextRun> collectRuns(Spanned text) {
        List<RichTextRun> runs = new ArrayList<>();
        if (text == null) return runs;
        for (ToolbarSpan span : text.getSpans(0, text.length(), ToolbarSpan.class)) {
            int start = text.getSpanStart(span);
            int end = text.getSpanEnd(span);
            if (start < 0 || end < 0) continue;
            // Line markers on an empty line are still meaningful to the toolbar, but never serialized.
            if (start >= end && !span.kind().isLineLevel()) continue;
            runs.add(new RichTextRun(start, end, span.kind(), span.value()));
        }
        return runs;
    }
}
