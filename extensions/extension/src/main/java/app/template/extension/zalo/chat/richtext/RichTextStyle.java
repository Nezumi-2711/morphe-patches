package app.template.extension.zalo.chat.richtext;

/**
 * An inline style state: what the toolbar shows at the caret, and the pending "typing style" for
 * the characters typed next. Immutable and Android-free.
 */
public final class RichTextStyle {
    /** No size run: the composer's normal size. */
    public static final int NORMAL_SIZE = 0;
    /** No color run: the composer's text color. */
    public static final int NO_COLOR = -1;

    public static final RichTextStyle PLAIN = new RichTextStyle(false, false, false, false, NORMAL_SIZE, NO_COLOR);

    public final boolean bold;
    public final boolean italic;
    public final boolean underline;
    public final boolean strike;
    /** Size token, or {@link #NORMAL_SIZE}. */
    public final int size;
    /** 0xRRGGBB, or {@link #NO_COLOR}. */
    public final int color;

    public RichTextStyle(boolean bold, boolean italic, boolean underline, boolean strike, int size, int color) {
        this.bold = bold;
        this.italic = italic;
        this.underline = underline;
        this.strike = strike;
        this.size = size;
        this.color = color;
    }

    /** True when {@code kind} (an on/off kind) is on. */
    public boolean has(RichTextRun.Kind kind) {
        switch (kind) {
            case BOLD: return bold;
            case ITALIC: return italic;
            case UNDERLINE: return underline;
            case STRIKE: return strike;
            case SIZE: return size != NORMAL_SIZE;
            case COLOR: return color != NO_COLOR;
            default: return false;
        }
    }

    /**
     * The run value for {@code kind}, or {@link RichTextRanges#NONE} when the kind is off. On/off
     * kinds use 1.
     */
    public int valueOf(RichTextRun.Kind kind) {
        switch (kind) {
            case SIZE: return size == NORMAL_SIZE ? RichTextRanges.NONE : size;
            case COLOR: return color == NO_COLOR ? RichTextRanges.NONE : color;
            default: return has(kind) ? 1 : RichTextRanges.NONE;
        }
    }

    /** This style with the on/off {@code kind} flipped. */
    public RichTextStyle toggled(RichTextRun.Kind kind) {
        switch (kind) {
            case BOLD: return new RichTextStyle(!bold, italic, underline, strike, size, color);
            case ITALIC: return new RichTextStyle(bold, !italic, underline, strike, size, color);
            case UNDERLINE: return new RichTextStyle(bold, italic, !underline, strike, size, color);
            case STRIKE: return new RichTextStyle(bold, italic, underline, !strike, size, color);
            default: return this;
        }
    }

    public RichTextStyle withSize(int value) {
        return new RichTextStyle(bold, italic, underline, strike, value, color);
    }

    public RichTextStyle withColor(int value) {
        return new RichTextStyle(bold, italic, underline, strike, size, value);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof RichTextStyle)) return false;
        RichTextStyle style = (RichTextStyle) other;
        return style.bold == bold && style.italic == italic && style.underline == underline
            && style.strike == strike && style.size == size && style.color == color;
    }

    @Override
    public int hashCode() {
        int flags = (bold ? 1 : 0) | (italic ? 2 : 0) | (underline ? 4 : 0) | (strike ? 8 : 0);
        return (flags * 31 + size) * 31 + color;
    }

    @Override
    public String toString() {
        return "RichTextStyle(b=" + bold + ",i=" + italic + ",u=" + underline + ",s=" + strike
            + ",size=" + size + ",color=" + color + ")";
    }
}
