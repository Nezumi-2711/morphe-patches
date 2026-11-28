package app.template.extension.zalo.chat.richtext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/**
 * Builds the {@code params} string of an rtf text message, in the chat.zalo.me wire format:
 * {@code {"styles":[{"start":S,"len":L,"st":"b,i"},...],"ver":0}}.
 *
 * <ul>
 * <li>Offsets and lengths are UTF-16 indices into the text that is sent.</li>
 * <li>Inline tokens are combined per segment in the order {@code b,i,u,s,f_<n>,c_<hex>}. Adjacent
 *     segments with the same tokens are merged.</li>
 * <li>Line-level tokens get one entry per non-empty line, covering the line without its
 *     {@code \n}: {@code lst_<1|2>} and/or {@code ind_<level*10>}.</li>
 * <li>No zero-length entries. Sizes are clamped to the config range; colors outside the whitelist
 *     are dropped.</li>
 * </ul>
 *
 * Pure Java: no android.text and no org.json, so it runs in plain JVM unit tests.
 */
public final class RichTextSerializer {
    private RichTextSerializer() { }

    /**
     * Params for {@code fullText} as Zalo will send it. Zalo trims type-0 text before sending
     * ({@code String.trim()}) and shifts mentions but not styles, so the runs, given against
     * {@code fullText}, are shifted to the trimmed text and clipped to it here.
     * Returns null when nothing is left to style.
     */
    public static String serializeForSend(String fullText, List<RichTextRun> runs, RichTextConfig config) {
        if (fullText == null) return null;
        String trimmed = fullText.trim();
        if (trimmed.isEmpty()) return null;
        int shift = fullText.indexOf(trimmed);
        List<RichTextRun> shifted = new ArrayList<>(runs.size());
        for (RichTextRun run : runs) shifted.add(run.withRange(run.start - shift, run.end - shift));
        return serialize(trimmed, shifted, config);
    }

    /** Params for exactly {@code text}, or null when no style entry results. */
    public static String serialize(String text, List<RichTextRun> runs, RichTextConfig config) {
        if (text == null || text.isEmpty() || runs == null || runs.isEmpty()) return null;
        if (config == null) config = RichTextConfig.DEFAULT;
        int length = text.length();

        List<Entry> entries = new ArrayList<>();
        addLineEntries(text, runs, entries);
        addInlineEntries(text, length, runs, config, entries);
        if (entries.isEmpty()) return null;
        // By start; at the same start the line entry comes first, like web output.
        Collections.sort(entries, (a, b) -> a.start != b.start ? Integer.compare(a.start, b.start)
            : Boolean.compare(!a.line, !b.line));

        StringBuilder out = new StringBuilder("{\"styles\":[");
        for (int index = 0; index < entries.size(); index++) {
            Entry entry = entries.get(index);
            if (index > 0) out.append(',');
            out.append("{\"start\":").append(entry.start)
                .append(",\"len\":").append(entry.end - entry.start)
                .append(",\"st\":\"").append(entry.tokens).append("\"}");
        }
        return out.append("],\"ver\":0}").toString();
    }

    private static final class Entry {
        final int start;
        final int end;
        final String tokens;
        final boolean line;

        Entry(int start, int end, String tokens, boolean line) {
            this.start = start;
            this.end = end;
            this.tokens = tokens;
            this.line = line;
        }
    }

    private static void addLineEntries(String text, List<RichTextRun> runs, List<Entry> entries) {
        int length = text.length();
        int lineStart = 0;
        while (lineStart <= length) {
            int newline = text.indexOf('\n', lineStart);
            int lineEnd = newline < 0 ? length : newline;
            if (lineEnd > lineStart) {
                int list = 0;
                int indent = 0;
                for (RichTextRun run : runs) {
                    if (!run.kind.isLineLevel() || run.start >= lineEnd || run.end <= lineStart) continue;
                    if (run.kind == RichTextRun.Kind.LIST) {
                        if (run.value == RichTextRun.LIST_BULLET || run.value == RichTextRun.LIST_NUMBERED) list = run.value;
                    } else if (run.value > 0) {
                        indent = run.value;
                    }
                }
                StringBuilder tokens = new StringBuilder();
                if (list != 0) tokens.append("lst_").append(list);
                if (indent != 0) {
                    if (tokens.length() > 0) tokens.append(',');
                    tokens.append("ind_").append(indent * 10);
                }
                if (tokens.length() > 0) entries.add(new Entry(lineStart, lineEnd, tokens.toString(), true));
            }
            if (newline < 0) break;
            lineStart = newline + 1;
        }
    }

    private static void addInlineEntries(String text, int length, List<RichTextRun> runs, RichTextConfig config,
            List<Entry> entries) {
        List<RichTextRun> inline = new ArrayList<>();
        TreeSet<Integer> bounds = new TreeSet<>();
        for (RichTextRun run : runs) {
            if (run.kind.isLineLevel()) continue;
            if (run.kind == RichTextRun.Kind.COLOR && !config.allowsColor(run.value)) continue;
            int start = snapOutOfSurrogate(text, Math.max(0, run.start), true);
            int end = snapOutOfSurrogate(text, Math.min(length, run.end), false);
            if (start >= end) continue;
            inline.add(run.withRange(start, end));
            bounds.add(start);
            bounds.add(end);
        }
        if (inline.isEmpty()) return;

        int pendingStart = -1;
        int pendingEnd = -1;
        String pendingTokens = null;
        Integer previous = null;
        for (int bound : bounds) {
            if (previous != null) {
                String tokens = tokensFor(inline, previous, bound, config);
                if (tokens != null && tokens.equals(pendingTokens) && pendingEnd == previous) {
                    pendingEnd = bound;
                } else {
                    if (pendingTokens != null) entries.add(new Entry(pendingStart, pendingEnd, pendingTokens, false));
                    pendingTokens = tokens;
                    pendingStart = previous;
                    pendingEnd = bound;
                }
            }
            previous = bound;
        }
        if (pendingTokens != null) entries.add(new Entry(pendingStart, pendingEnd, pendingTokens, false));
    }

    /** Comma-joined tokens active over the whole segment {@code [start, end)}, or null for none. */
    private static String tokensFor(List<RichTextRun> inline, int start, int end, RichTextConfig config) {
        boolean bold = false;
        boolean italic = false;
        boolean underline = false;
        boolean strike = false;
        int size = 0;
        int color = -1;
        for (RichTextRun run : inline) {
            if (run.start > start || run.end < end) continue;
            switch (run.kind) {
                case BOLD: bold = true; break;
                case ITALIC: italic = true; break;
                case UNDERLINE: underline = true; break;
                case STRIKE: strike = true; break;
                case SIZE: if (run.value > 0) size = config.clampSize(run.value); break;
                case COLOR: color = run.value & 0xFFFFFF; break;
                default: break;
            }
        }
        StringBuilder tokens = new StringBuilder();
        if (bold) tokens.append("b,");
        if (italic) tokens.append("i,");
        if (underline) tokens.append("u,");
        if (strike) tokens.append("s,");
        if (size > 0) tokens.append("f_").append(size).append(',');
        if (color >= 0) tokens.append("c_").append(RichTextConfig.hex(color)).append(',');
        if (tokens.length() == 0) return null;
        tokens.setLength(tokens.length() - 1);
        return tokens.toString();
    }

    /** Moves an index that splits a surrogate pair outward: back for a start, forward for an end. */
    private static int snapOutOfSurrogate(String text, int index, boolean isStart) {
        if (index <= 0 || index >= text.length()) return index;
        if (Character.isHighSurrogate(text.charAt(index - 1)) && Character.isLowSurrogate(text.charAt(index))) {
            return isStart ? index - 1 : index + 1;
        }
        return index;
    }
}
