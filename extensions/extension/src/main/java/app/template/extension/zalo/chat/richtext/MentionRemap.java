package app.template.extension.zalo.chat.richtext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Maps style runs from the composer text to the text Zalo sends, across mention flattening.
 *
 * <p>Zalo's composer flattening (ChatView.v8 in 26.11.01) copies the text, then for each mention,
 * sorted by start, replaces {@code [start, start + (end - start))} with the mention's wire text.
 * Before each replace it shifts the start of every later mention by
 * {@code wire.length() - display.length()}. This class replays exactly that, tracking every run
 * edge through each replace. An edge inside a replaced mention snaps outward (a start to the
 * mention start, an end to the mention end), so no style edge falls inside a mention. The replayed
 * text must equal Zalo's real result; otherwise the caller sends the message plain.
 *
 * <p>Pure Java so it can be unit tested.
 */
public final class MentionRemap {
    private MentionRemap() { }

    /** A composer mention, from Zalo's mention span fields (start, end, wire text, display text). */
    public static final class Mention {
        final int start;
        final int end;
        final String wire;
        final String display;

        public Mention(int start, int end, String wire, String display) {
            this.start = start;
            this.end = end;
            this.wire = wire;
            this.display = display;
        }
    }

    /**
     * {@code runs} (against {@code original}) remapped onto {@code flattened}, or null when the
     * replay does not reproduce {@code flattened} exactly. Mentions must be in the order Zalo's
     * {@code getSpans} returned them.
     */
    public static List<RichTextRun> remap(String original, List<RichTextRun> runs, List<Mention> mentions,
            String flattened) {
        if (original == null || flattened == null || runs == null) return null;
        if (mentions == null || mentions.isEmpty()) {
            return original.equals(flattened) ? new ArrayList<>(runs) : null;
        }
        int[] starts = new int[runs.size()];
        int[] ends = new int[runs.size()];
        for (int index = 0; index < runs.size(); index++) {
            starts[index] = runs.get(index).start;
            ends[index] = runs.get(index).end;
        }

        // Zalo's working copy: start (shifted) and replaced length; stable sort by start.
        List<int[]> order = new ArrayList<>();
        for (int index = 0; index < mentions.size(); index++) {
            Mention mention = mentions.get(index);
            if (mention.wire == null || mention.display == null) return null;
            order.add(new int[] { mention.start, mention.end - mention.start, index });
        }
        Collections.sort(order, (a, b) -> Integer.compare(a[0], b[0]));

        StringBuilder text = new StringBuilder(original);
        int[][] ranges = new int[order.size()][];
        for (int position = 0; position < order.size(); position++) {
            int[] current = order.get(position);
            Mention mention = mentions.get(current[2]);
            int delta = mention.wire.length() - mention.display.length();
            if (delta != 0) {
                for (int later = position + 1; later < order.size(); later++) order.get(later)[0] += delta;
            }
            int from = current[0];
            int to = from + current[1];
            // StringBuilder.replace: throws on these, clamps an end past the length.
            if (from < 0 || from > text.length() || from > to) return null;
            int clampedTo = Math.min(to, text.length());
            int inserted = mention.wire.length();
            text.replace(from, clampedTo, mention.wire);
            for (int index = 0; index < starts.length; index++) {
                starts[index] = moveEdge(starts[index], from, clampedTo, inserted, true);
                ends[index] = moveEdge(ends[index], from, clampedTo, inserted, false);
            }
            // The range Zalo records for this mention, shifted by later replaces.
            for (int earlier = 0; earlier < position; earlier++) {
                int[] range = ranges[earlier];
                range[0] = moveEdge(range[0], from, clampedTo, inserted, true);
                range[1] = moveEdge(range[1], from, clampedTo, inserted, false);
            }
            ranges[position] = new int[] { from, from + inserted };
        }
        if (!text.toString().equals(flattened)) return null;

        List<RichTextRun> mapped = new ArrayList<>(runs.size());
        for (int index = 0; index < runs.size(); index++) {
            int start = starts[index];
            int end = ends[index];
            for (int[] range : ranges) {
                if (start > range[0] && start < range[1]) start = range[0];
                if (end > range[0] && end < range[1]) end = range[1];
            }
            if (start < end) mapped.add(runs.get(index).withRange(start, end));
        }
        return mapped;
    }

    /** Position of edge {@code position} after replacing {@code [from, to)} with {@code inserted} chars. */
    private static int moveEdge(int position, int from, int to, int inserted, boolean isStart) {
        if (position <= from) return position;
        if (position >= to) return position + inserted - (to - from);
        return isStart ? from : from + inserted;
    }
}
