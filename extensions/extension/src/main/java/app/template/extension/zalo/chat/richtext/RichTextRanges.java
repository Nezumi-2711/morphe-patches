package app.template.extension.zalo.chat.richtext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure range and paragraph logic behind the formatting toolbar: style runs per kind, toggle
 * semantics, caret style inheritance and the paragraphs a caret or selection touches.
 *
 * <p>No android.text here, so it runs in plain JVM unit tests. The composer adapter
 * ({@link RichTextEditor}) reads toolbar spans into {@link Run}s, transforms them here and writes
 * the result back as spans.
 */
public final class RichTextRanges {
    private RichTextRanges() { }

    /** Highest indent level the toolbar sets (web sends {@code ind_<level*10>}). */
    public static final int MAX_INDENT = 5;

    /** Returned by the value queries when no run applies. */
    public static final int NONE = Integer.MIN_VALUE;

    /** One run of a single style kind over {@code [start, end)}, with its value (1 for on/off kinds). */
    public static final class Run {
        public final int start;
        public final int end;
        public final int value;

        public Run(int start, int end, int value) {
            this.start = start;
            this.end = end;
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Run)) return false;
            Run run = (Run) other;
            return run.start == start && run.end == end && run.value == value;
        }

        @Override
        public int hashCode() {
            return (start * 31 + end) * 31 + value;
        }

        @Override
        public String toString() {
            return "[" + start + "," + end + ")=" + value;
        }
    }

    /**
     * {@code runs} with {@code [from, to)} cleared, then set to {@code value} unless {@code clear}.
     * The result is sorted, has no empty or overlapping runs, and merges touching runs of the same
     * value. Later runs win over earlier ones, so this also normalizes overlapping input when
     * applied run by run.
     */
    public static List<Run> assign(List<Run> runs, int from, int to, boolean clear, int value) {
        List<Run> result = new ArrayList<>();
        for (Run run : runs) {
            if (run.end <= from || run.start >= to || from >= to) {
                result.add(run);
                continue;
            }
            if (run.start < from) result.add(new Run(run.start, from, run.value));
            if (run.end > to) result.add(new Run(to, run.end, run.value));
        }
        if (!clear && from < to) result.add(new Run(from, to, value));
        return merge(result);
    }

    /** Normalizes runs read from spans in their span order: later runs win where they overlap. */
    public static List<Run> normalize(List<Run> runs) {
        List<Run> result = new ArrayList<>();
        for (Run run : runs) {
            if (run.start < run.end) result = assign(result, run.start, run.end, false, run.value);
        }
        return result;
    }

    private static List<Run> merge(List<Run> runs) {
        List<Run> sorted = new ArrayList<>();
        for (Run run : runs) if (run.start < run.end) sorted.add(run);
        Collections.sort(sorted, (a, b) -> a.start != b.start ? Integer.compare(a.start, b.start)
            : Integer.compare(a.end, b.end));
        List<Run> merged = new ArrayList<>();
        for (Run run : sorted) {
            Run last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
            if (last != null && last.value == run.value && last.end >= run.start) {
                merged.set(merged.size() - 1, new Run(last.start, Math.max(last.end, run.end), last.value));
            } else {
                merged.add(run);
            }
        }
        return merged;
    }

    /** True when the runs cover every index of the non-empty range {@code [from, to)}. */
    public static boolean covers(List<Run> runs, int from, int to) {
        if (from >= to) return false;
        int position = from;
        for (Run run : merge(new ArrayList<>(runs))) {
            if (run.end <= position) continue;
            if (run.start > position) return false;
            position = run.end;
            if (position >= to) return true;
        }
        return false;
    }

    /** The value of the run containing character {@code index}, or {@link #NONE}. */
    public static int valueAt(List<Run> runs, int index) {
        for (Run run : runs) {
            if (run.start <= index && index < run.end) return run.value;
        }
        return NONE;
    }

    /** The single value covering all of {@code [from, to)}, or {@link #NONE} when mixed or partly unstyled. */
    public static int uniformValue(List<Run> runs, int from, int to) {
        if (from >= to) return NONE;
        int value = valueAt(runs, from);
        if (value == NONE) return NONE;
        List<Run> same = new ArrayList<>();
        for (Run run : runs) if (run.value == value) same.add(run);
        return covers(same, from, to) ? value : NONE;
    }

    /** True when the two run lists are identical after normalization. */
    public static boolean sameRuns(List<Run> a, List<Run> b) {
        return merge(new ArrayList<>(a)).equals(merge(new ArrayList<>(b)));
    }

    /**
     * Toggle of an on/off style over a selection: removes it when the whole selection already has
     * it, otherwise applies it to the whole selection.
     */
    public static List<Run> toggle(List<Run> runs, int from, int to) {
        if (from >= to) return runs;
        return assign(runs, from, to, covers(runs, from, to), 1);
    }

    /**
     * Index of the character whose style the caret at {@code position} continues: the character
     * before it on the same line, else the first character after it on the same line, else -1.
     */
    public static int caretSource(CharSequence text, int position) {
        int length = text.length();
        if (position > 0 && position <= length && text.charAt(position - 1) != '\n') return position - 1;
        if (position >= 0 && position < length && text.charAt(position) != '\n') return position;
        return -1;
    }

    // ---- Paragraphs ----

    /**
     * Paragraphs of {@code text} as {@code [start, end)} pairs. Each paragraph's end includes its
     * trailing {@code \n}; the last paragraph ends at the text end and may be empty.
     */
    public static List<int[]> paragraphs(CharSequence text) {
        List<int[]> result = new ArrayList<>();
        int length = text.length();
        int start = 0;
        for (int index = 0; index < length; index++) {
            if (text.charAt(index) == '\n') {
                result.add(new int[] { start, index + 1 });
                start = index + 1;
            }
        }
        result.add(new int[] { start, length });
        return result;
    }

    /** Index into {@link #paragraphs} of the paragraph that contains {@code position}. */
    public static int paragraphIndex(List<int[]> paragraphs, int position) {
        for (int index = 0; index < paragraphs.size(); index++) {
            int[] paragraph = paragraphs.get(index);
            if (position < paragraph[1] || index == paragraphs.size() - 1) return index;
        }
        return paragraphs.size() - 1;
    }

    /**
     * Paragraph indexes a caret or selection touches. A non-empty selection that ends right after a
     * line break does not touch the paragraph that follows it.
     */
    public static int[] touchedParagraphs(List<int[]> paragraphs, CharSequence text, int selectionStart,
            int selectionEnd) {
        int from = Math.min(selectionStart, selectionEnd);
        int to = Math.max(selectionStart, selectionEnd);
        int first = paragraphIndex(paragraphs, from);
        int lastPosition = to > from && to > 0 && text.charAt(to - 1) == '\n' ? to - 1 : to;
        int last = Math.max(first, paragraphIndex(paragraphs, lastPosition));
        return new int[] { first, last };
    }

    /** True when a line marker over {@code [markerStart, markerEnd)} formats the paragraph starting at {@code paragraphStart}. */
    public static boolean markerCovers(int markerStart, int markerEnd, int paragraphStart) {
        return markerStart <= paragraphStart && paragraphStart < markerEnd;
    }

    /**
     * New list type for the touched paragraphs when the list button {@code type} is tapped: none
     * when every touched paragraph already has that type, otherwise {@code type}.
     */
    public static int toggledList(int[] lists, int first, int last, int type) {
        for (int index = first; index <= last; index++) {
            if (lists[index] != type) return type;
        }
        return 0;
    }

    /** Indent level after a {@code delta} step, kept within 0 and {@link #MAX_INDENT}. */
    public static int steppedIndent(int level, int delta) {
        return Math.max(0, Math.min(MAX_INDENT, level + delta));
    }

    /** Numbers for numbered-list paragraphs: consecutive numbered paragraphs count up from 1, others get 0. */
    public static int[] listNumbers(int[] lists, int numberedType) {
        return listNumbers(lists, null, numberedType);
    }

    /**
     * {@link #listNumbers(int[], int)}, where an empty paragraph ({@code empty[index]}) also ends the
     * sequence: it keeps its own next number (it is where the caret types the next item), but the
     * item after it starts again at 1. The wire has no entry for an empty line and Zalo's receiver
     * restarts the numbering after such a gap, so the composer shows what the receiver will show.
     */
    public static int[] listNumbers(int[] lists, boolean[] empty, int numberedType) {
        int[] numbers = new int[lists.length];
        int counter = 0;
        for (int index = 0; index < lists.length; index++) {
            counter = lists[index] == numberedType ? counter + 1 : 0;
            numbers[index] = lists[index] == numberedType ? counter : 0;
            if (empty != null && index < empty.length && empty[index]) counter = 0;
        }
        return numbers;
    }

    /** Per paragraph: true when it has no content (only its line break, or nothing). */
    public static boolean[] emptyParagraphs(CharSequence text, List<int[]> paragraphs) {
        boolean[] empty = new boolean[paragraphs.size()];
        for (int index = 0; index < empty.length; index++) {
            int[] paragraph = paragraphs.get(index);
            int contentEnd = paragraph[1] > paragraph[0] && text.charAt(paragraph[1] - 1) == '\n'
                ? paragraph[1] - 1 : paragraph[1];
            empty[index] = contentEnd == paragraph[0];
        }
        return empty;
    }
}
