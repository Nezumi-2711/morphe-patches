package app.template.extension.zalo.chat.richtext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import app.template.extension.zalo.chat.richtext.RichTextRanges.Run;
import app.template.extension.zalo.chat.richtext.RichTextRun.Kind;

/**
 * Pure run-list helpers for copy and paste: the canonical form of a run list, slicing composer runs
 * to a selection, and the line styles a paste gives the composer's paragraphs.
 *
 * <p>Canonical form, against one text:
 * <ul>
 * <li>Inline kinds: per kind, sorted, non-overlapping, touching runs of the same value merged (later
 *     runs win where they overlap). On/off kinds have value 0, like the toolbar spans.</li>
 * <li>Line kinds: one run per non-empty line that has the style, covering the line without its
 *     {@code \n}. A line takes the last line run of each kind that overlaps it, like the serializer.</li>
 * <li>Order: by kind ({@link Kind} order), then by start.</li>
 * </ul>
 *
 * No android.text here, so it runs in plain JVM unit tests.
 */
public final class RichTextRuns {
    private RichTextRuns() { }

    /** {@code runs} in canonical form against {@code text}; runs outside the text are clipped. */
    public static List<RichTextRun> canonical(String text, List<RichTextRun> runs) {
        List<RichTextRun> result = new ArrayList<>();
        if (text == null || runs == null || runs.isEmpty()) return result;
        int length = text.length();
        for (Kind kind : Kind.values()) {
            if (kind.isLineLevel()) continue;
            boolean onOff = isOnOff(kind);
            List<Run> kindRuns = new ArrayList<>();
            for (RichTextRun run : runs) {
                if (run.kind != kind) continue;
                int start = Math.max(0, run.start);
                int end = Math.min(length, run.end);
                if (start < end) kindRuns.add(new Run(start, end, onOff ? 1 : run.value));
            }
            for (Run run : RichTextRanges.normalize(kindRuns)) {
                result.add(new RichTextRun(run.start, run.end, kind, onOff ? 0 : run.value));
            }
        }
        List<RichTextRun> lists = new ArrayList<>();
        List<RichTextRun> indents = new ArrayList<>();
        int lineStart = 0;
        while (lineStart <= length) {
            int newline = text.indexOf('\n', lineStart);
            int lineEnd = newline < 0 ? length : newline;
            if (lineEnd > lineStart) {
                int list = 0;
                int indent = 0;
                for (RichTextRun run : runs) {
                    if (!run.kind.isLineLevel() || run.start >= lineEnd || run.end <= lineStart) continue;
                    if (run.kind == Kind.LIST) {
                        if (run.value == RichTextRun.LIST_BULLET || run.value == RichTextRun.LIST_NUMBERED) list = run.value;
                    } else if (run.value > 0) {
                        indent = run.value;
                    }
                }
                if (list != 0) lists.add(new RichTextRun(lineStart, lineEnd, Kind.LIST, list));
                if (indent != 0) indents.add(new RichTextRun(lineStart, lineEnd, Kind.INDENT, indent));
            }
            if (newline < 0) break;
            lineStart = newline + 1;
        }
        result.addAll(lists);
        result.addAll(indents);
        return result;
    }

    /**
     * The runs of {@code text.substring(from, to)}, rebased to 0 and canonical against the slice.
     * {@code runs} are composer runs: inline spans anywhere, line markers covering whole paragraphs.
     * A line style is kept only for lines whose start is inside the selection, so copying from the
     * middle of a list item copies its inline styles but not its bullet.
     */
    public static List<RichTextRun> slice(String text, List<RichTextRun> runs, int from, int to) {
        List<RichTextRun> result = new ArrayList<>();
        if (text == null || runs == null) return result;
        from = Math.max(0, from);
        to = Math.min(text.length(), to);
        if (from >= to) return result;
        String slice = text.substring(from, to);
        List<RichTextRun> sliced = new ArrayList<>();
        for (RichTextRun run : runs) {
            if (run.kind.isLineLevel()) continue;
            int start = Math.max(from, run.start);
            int end = Math.min(to, run.end);
            if (start < end) sliced.add(run.withRange(start - from, end - from));
        }
        for (int lineStart = from; lineStart < to; ) {
            boolean isLineStart = lineStart == 0 || text.charAt(lineStart - 1) == '\n';
            int newline = text.indexOf('\n', lineStart);
            int lineEnd = newline < 0 ? text.length() : newline;
            if (isLineStart) {
                int sliceEnd = Math.min(lineEnd, to) - from;
                for (RichTextRun run : runs) {
                    if (!run.kind.isLineLevel() || !RichTextRanges.markerCovers(run.start, run.end, lineStart)) continue;
                    if (lineStart - from < sliceEnd) sliced.add(run.withRange(lineStart - from, sliceEnd));
                }
            }
            if (newline < 0) break;
            lineStart = newline + 1;
        }
        return canonical(slice, sliced);
    }

    /**
     * {@code [list, indent]} per line of {@code text} (split on {@code \n}), from canonical or any
     * runs, with line-run overlap semantics.
     */
    public static int[][] lineValues(String text, List<RichTextRun> runs) {
        List<int[]> lines = lines(text);
        int[][] values = new int[lines.size()][2];
        for (int index = 0; index < lines.size(); index++) {
            int lineStart = lines.get(index)[0];
            int lineEnd = lines.get(index)[1];
            if (lineEnd <= lineStart) continue;
            for (RichTextRun run : runs) {
                if (!run.kind.isLineLevel() || run.start >= lineEnd || run.end <= lineStart) continue;
                if (run.kind == Kind.LIST) values[index][0] = run.value;
                else values[index][1] = run.value;
            }
        }
        return values;
    }

    /**
     * Line styles for a paste of {@code pasted} (with {@code runs}) at {@code at} in {@code composer}:
     * composer paragraph start to {@code [list, indent]}.
     *
     * <ul>
     * <li>The first pasted line joins the paragraph the paste starts in. It only gets the pasted
     *     line's style when the paste starts that paragraph and the pasted line has a style, so a
     *     paste never removes the bullet of the line it lands in.</li>
     * <li>Every later pasted line starts a paragraph of its own and gets exactly the pasted line's
     *     style, none included.</li>
     * <li>An empty line after a final {@code \n} of the paste is the start of the composer text that
     *     followed the paste, and is left alone.</li>
     * </ul>
     */
    public static Map<Integer, int[]> pasteLineOverrides(CharSequence composer, int at, String pasted,
            List<RichTextRun> runs) {
        Map<Integer, int[]> overrides = new HashMap<>();
        if (composer == null || pasted == null || runs == null || pasted.isEmpty()) return overrides;
        if (at < 0 || at + pasted.length() > composer.length()) return overrides;
        List<int[]> lines = lines(pasted);
        int[][] values = lineValues(pasted, runs);
        for (int index = 0; index < lines.size(); index++) {
            int lineStart = lines.get(index)[0];
            int position = at + lineStart;
            int list = values[index][0];
            int indent = values[index][1];
            if (index == 0) {
                boolean startsParagraph = at == 0 || composer.charAt(at - 1) == '\n';
                if (startsParagraph && (list != 0 || indent != 0)) overrides.put(position, new int[] { list, indent });
            } else if (lineStart < pasted.length()) {
                overrides.put(position, new int[] { list, indent });
            }
        }
        return overrides;
    }

    /** Lines of {@code text} as {@code [start, end)} without the {@code \n}; always at least one. */
    static List<int[]> lines(String text) {
        List<int[]> lines = new ArrayList<>();
        int start = 0;
        while (true) {
            int newline = text.indexOf('\n', start);
            if (newline < 0) {
                lines.add(new int[] { start, text.length() });
                return lines;
            }
            lines.add(new int[] { start, newline });
            start = newline + 1;
        }
    }

    static boolean isOnOff(Kind kind) {
        return kind == Kind.BOLD || kind == Kind.ITALIC || kind == Kind.UNDERLINE || kind == Kind.STRIKE;
    }
}
