package app.template.extension.zalo.chat.richtext;

/**
 * One style over {@code [start, end)} of a message text, in UTF-16 indices. Pure Java (no
 * android.text) so the serializer and the mention remap can be unit tested.
 *
 * <p>Inline kinds apply to the characters in the range. {@link Kind#LIST} and {@link Kind#INDENT}
 * are line-level: every non-empty line the range overlaps gets the style for the whole line.
 */
public final class RichTextRun {
    public enum Kind {
        BOLD, ITALIC, UNDERLINE, STRIKE,
        /** {@code value} = font size token (13, 18 or 20; normal size has no run). */
        SIZE,
        /** {@code value} = 0xRRGGBB, must be in Zalo's color whitelist to be sent. */
        COLOR,
        /** {@code value} = {@link #LIST_BULLET} or {@link #LIST_NUMBERED}. Line-level. */
        LIST,
        /** {@code value} = indent level, 1 or more; sent as {@code ind_<level*10>}. Line-level. */
        INDENT;

        public boolean isLineLevel() {
            return this == LIST || this == INDENT;
        }
    }

    public static final int LIST_BULLET = 1;
    public static final int LIST_NUMBERED = 2;

    public final int start;
    public final int end;
    public final Kind kind;
    public final int value;

    public RichTextRun(int start, int end, Kind kind, int value) {
        this.start = start;
        this.end = end;
        this.kind = kind;
        this.value = value;
    }

    public RichTextRun(int start, int end, Kind kind) {
        this(start, end, kind, 0);
    }

    RichTextRun withRange(int newStart, int newEnd) {
        return new RichTextRun(newStart, newEnd, kind, value);
    }

    @Override
    public String toString() {
        return kind + "(" + value + ")[" + start + "," + end + ")";
    }
}
