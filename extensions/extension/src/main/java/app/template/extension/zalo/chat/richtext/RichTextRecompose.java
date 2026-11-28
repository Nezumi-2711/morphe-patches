package app.template.extension.zalo.chat.richtext;

import java.util.List;

import app.template.extension.zalo.chat.richtext.RichTextRanges.Run;

/**
 * Styles of a replaced range when an IME rewrites it ("recomposition"). Pure Java, so it runs in
 * plain JVM unit tests.
 *
 * <p>Vietnamese Telex/VNI keyboards replace the whole composing word on almost every diacritic
 * ({@code "vie" -> "viê"}, {@code "chao" -> "chào"}), and a backspace inside a composing word does the
 * same ({@code "hello" -> "hell"}). The replacement is split into the longest common prefix and the
 * longest common suffix of the old and new text (never overlapping); both keep the styles their old
 * chars had, the suffix shifted by the length difference. Only the differing middle gets a new
 * style. A pure extension ({@code "hel" -> "hello"}) is the special case prefix = old text.
 */
public final class RichTextRecompose {
    private RichTextRecompose() { }

    /** How the old text maps onto the new one. All values are lengths. */
    public static final class Split {
        public final int prefix;
        public final int suffix;
        /** Old chars in the middle (replaced). */
        public final int oldMiddle;
        /** New chars in the middle (to be styled). */
        public final int newMiddle;

        Split(int prefix, int suffix, int oldMiddle, int newMiddle) {
            this.prefix = prefix;
            this.suffix = suffix;
            this.oldMiddle = oldMiddle;
            this.newMiddle = newMiddle;
        }

        /** Log name: {@code insert} (nothing replaced), {@code extend} (old text kept whole), else {@code replace-middle}. */
        public String kind() {
            if (prefix + suffix + oldMiddle == 0) return "insert";
            return oldMiddle == 0 && suffix == 0 ? "extend" : "replace-middle";
        }

        @Override
        public String toString() {
            return "Split(prefix=" + prefix + ",suffix=" + suffix + ",middle=" + oldMiddle + "->" + newMiddle + ")";
        }
    }

    /**
     * Longest common prefix, then longest common suffix of what is left, of {@code old} and
     * {@code inserted}. A surrogate pair is never split between the kept part and the middle.
     */
    public static Split split(String old, String inserted) {
        int oldLength = old.length();
        int newLength = inserted.length();
        int max = Math.min(oldLength, newLength);
        int prefix = 0;
        while (prefix < max && old.charAt(prefix) == inserted.charAt(prefix)) prefix++;
        if (prefix > 0 && prefix < max && Character.isHighSurrogate(old.charAt(prefix - 1))) prefix--;
        int suffix = 0;
        while (suffix < max - prefix
                && old.charAt(oldLength - 1 - suffix) == inserted.charAt(newLength - 1 - suffix)) {
            suffix++;
        }
        if (suffix > 0 && suffix < max - prefix && Character.isLowSurrogate(old.charAt(oldLength - suffix))) suffix--;
        return new Split(prefix, suffix, oldLength - prefix - suffix, newLength - prefix - suffix);
    }

    /**
     * True when the old middle {@code [start + prefix, start + prefix + oldMiddle]} (both ends
     * inclusive) contains {@code position}: the edit happens at the caret or pending anchor.
     */
    public static boolean touches(int start, Split split, int position) {
        if (position < 0) return false;
        int from = start + split.prefix;
        return from <= position && position <= from + split.oldMiddle;
    }

    /**
     * Old-text index of the char whose style a middle that does not touch the caret keeps: the first
     * replaced char, else (pure insertion) the char before the middle, else -1 (use the style of the
     * first replaced char of the whole change).
     */
    public static int middleSource(int start, Split split) {
        if (split.oldMiddle > 0) return start + split.prefix;
        if (split.prefix > 0) return start + split.prefix - 1;
        return -1;
    }

    /** What one change does: the split, and where the middle takes its style from. */
    public static final class Plan {
        public final Split split;
        /** The middle is at the caret or the pending anchor: it gets the typing style. */
        public final boolean atCaret;
        /** Otherwise: old-text index of the char whose style it keeps, or -1 (style of the change's first old char). */
        public final int source;
        /** The pending typing style is applied to at least one inserted char. */
        public final boolean consumesPending;

        Plan(Split split, boolean atCaret, int source, boolean consumesPending) {
            this.split = split;
            this.atCaret = atCaret;
            this.source = source;
            this.consumesPending = consumesPending;
        }
    }

    /**
     * Plans the replacement of {@code old} at {@code start} by {@code inserted}.
     *
     * @param caret           selection end before the change, or -1
     * @param pendingAnchor   anchor of the pending typing style, or -1 when there is none
     * @param typingIsPending the typing style at the change end is the pending style
     */
    public static Plan plan(String old, String inserted, int start, int caret, int pendingAnchor, boolean typingIsPending) {
        Split split = split(old, inserted);
        boolean atCaret = touches(start, split, caret) || touches(start, split, pendingAnchor);
        int source = atCaret ? -1 : middleSource(start, split);
        return new Plan(split, atCaret, source, atCaret && typingIsPending && split.newMiddle > 0);
    }

    /** Value of one kind for the new middle (or {@link RichTextRanges#NONE}). */
    public static int middleValue(Plan plan, int typingValue, List<Run> oldRuns, int replacedValue) {
        if (plan.atCaret) return typingValue;
        return plan.source >= 0 ? RichTextRanges.valueAt(oldRuns, plan.source) : replacedValue;
    }

    /**
     * Pending anchor after the change when the pending style was not consumed: an anchor after the
     * middle follows the text; one at or before it stays.
     */
    public static int shiftedAnchor(Plan plan, int start, int anchor) {
        int middleEnd = start + plan.split.prefix + plan.split.oldMiddle;
        return anchor > middleEnd ? anchor + plan.split.newMiddle - plan.split.oldMiddle : anchor;
    }

    /**
     * Runs of one kind after {@code [start, start + oldLength)} was replaced by {@code newLength}
     * chars.
     *
     * @param current     runs read from the text after the change (outside spans already shifted)
     * @param oldRuns     the kind's runs over the replaced old chars, in old-text indices
     * @param middleValue value for the new middle, or {@link RichTextRanges#NONE} for unstyled
     */
    public static List<Run> remap(List<Run> current, List<Run> oldRuns, int start, int oldLength, int newLength,
            Split split, int middleValue) {
        List<Run> next = RichTextRanges.assign(current, start, start + newLength, true, 0);
        int prefixEnd = start + split.prefix;
        int oldEnd = start + oldLength;
        int oldSuffixStart = oldEnd - split.suffix;
        int shift = newLength - oldLength;
        for (Run run : oldRuns) {
            int from = Math.max(run.start, start);
            int to = Math.min(run.end, prefixEnd);
            if (from < to) next = RichTextRanges.assign(next, from, to, false, run.value);
            from = Math.max(run.start, oldSuffixStart);
            to = Math.min(run.end, oldEnd);
            if (from < to) next = RichTextRanges.assign(next, from + shift, to + shift, false, run.value);
        }
        if (middleValue != RichTextRanges.NONE && split.newMiddle > 0) {
            next = RichTextRanges.assign(next, prefixEnd, prefixEnd + split.newMiddle, false, middleValue);
        }
        return next;
    }
}
