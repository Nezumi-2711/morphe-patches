package app.template.extension.zalo.chat.richtext;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Formatting helpers for the "Rich text formatting" bug-log lines. Pure Java so the output (and that
 * it never contains message text) can be unit tested.
 *
 * <p>Privacy: these helpers only ever turn text into lengths, offsets, counts and style tokens
 * ({@code b}, {@code c_db342e}, {@code lst_1}, ...). {@link #fingerprint} is the only function that
 * reads characters; it hashes with a random per-process salt that is never logged, so a fingerprint
 * shows equality within one log session but cannot be looked up offline, even for a short text.
 *
 * <p>Every line starts with one area prefix so a support reader can filter: {@link #SEND},
 * {@link #UI}, {@link #EDIT}, {@link #CLIP}, {@link #CONFIG}.
 */
public final class RichTextLog {
    private RichTextLog() { }

    public static final String SEND = "[rtf/send] ";
    public static final String UI = "[rtf/ui] ";
    public static final String EDIT = "[rtf/edit] ";
    public static final String CLIP = "[rtf/clip] ";
    public static final String CONFIG = "[rtf/config] ";

    /** Runs listed in detail lines before the rest is summarized as {@code +N}. */
    static final int MAX_SHAPES = 12;

    private static final byte[] SALT = new byte[16];

    static {
        new SecureRandom().nextBytes(SALT);
    }

    /** Wire-style short code of a kind: b, i, u, s, f, c, lst, ind. */
    static String code(RichTextRun.Kind kind) {
        switch (kind) {
            case BOLD: return "b";
            case ITALIC: return "i";
            case UNDERLINE: return "u";
            case STRIKE: return "s";
            case SIZE: return "f";
            case COLOR: return "c";
            case LIST: return "lst";
            case INDENT: return "ind";
            default: return kind.name();
        }
    }

    /** The wire token of one run: {@code b}, {@code f_18}, {@code c_db342e}, {@code lst_1}, {@code ind_10}. */
    static String token(RichTextRun run) {
        switch (run.kind) {
            case SIZE: return "f_" + run.value;
            case COLOR: return "c_" + RichTextConfig.hex(run.value);
            case LIST: return "lst_" + run.value;
            case INDENT: return "ind_" + run.value * 10;
            default: return code(run.kind);
        }
    }

    /** {@code 3(b=1,c=1,lst=1)}: run count, then the count per kind in kind order; {@code 0} for none. */
    public static String kindCounts(List<RichTextRun> runs) {
        if (runs == null || runs.isEmpty()) return "0";
        RichTextRun.Kind[] kinds = RichTextRun.Kind.values();
        int[] counts = new int[kinds.length];
        for (RichTextRun run : runs) counts[run.kind.ordinal()]++;
        StringBuilder out = new StringBuilder().append(runs.size()).append('(');
        boolean first = true;
        for (int index = 0; index < kinds.length; index++) {
            if (counts[index] == 0) continue;
            if (!first) out.append(',');
            out.append(code(kinds[index])).append('=').append(counts[index]);
            first = false;
        }
        return out.append(')').toString();
    }

    /** {@code b[0,5) c_db342e[3,9) +2}: token and range of the first {@code max} runs. Offsets only. */
    public static String runShapes(List<RichTextRun> runs, int max) {
        if (runs == null || runs.isEmpty()) return "-";
        StringBuilder out = new StringBuilder();
        int shown = Math.min(max, runs.size());
        for (int index = 0; index < shown; index++) {
            RichTextRun run = runs.get(index);
            if (index > 0) out.append(' ');
            out.append(token(run)).append('[').append(run.start).append(',').append(run.end).append(')');
        }
        if (runs.size() > shown) out.append(" +").append(runs.size() - shown);
        return out.toString();
    }

    /** Inline style tokens of a toolbar style ({@code b,i,f_18,c_db342e}), or {@code plain}. */
    public static String styleTokens(RichTextStyle style) {
        if (style == null) return "none";
        StringBuilder out = new StringBuilder();
        if (style.bold) out.append("b,");
        if (style.italic) out.append("i,");
        if (style.underline) out.append("u,");
        if (style.strike) out.append("s,");
        if (style.size != RichTextStyle.NORMAL_SIZE) out.append("f_").append(style.size).append(',');
        if (style.color != RichTextStyle.NO_COLOR) out.append("c_").append(RichTextConfig.hex(style.color)).append(',');
        if (out.length() == 0) return "plain";
        out.setLength(out.length() - 1);
        return out.toString();
    }

    /**
     * {@code <length>#<6 hex>}: the length plus the first 6 hex chars of a salted SHA-256. Equal texts
     * give equal fingerprints within this process only; the salt is random and never logged.
     */
    public static String fingerprint(String text) {
        if (text == null) return "null";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(SALT);
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return String.format(Locale.ROOT, "%d#%02x%02x%02x", text.length(), hash[0], hash[1], hash[2]);
        } catch (NoSuchAlgorithmException unavailable) {
            return text.length() + "#?";
        }
    }

    private static final Pattern STYLE_ENTRY = Pattern.compile("\"st\":\"([^\"]*)\"");

    /** Number of style entries in serialized {@code params}. */
    public static int paramsEntries(String params) {
        if (params == null) return 0;
        Matcher matcher = STYLE_ENTRY.matcher(params);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }

    /** Distinct style tokens used in serialized {@code params}, sorted and comma-joined, or {@code -}. */
    public static String paramsTokens(String params) {
        if (params == null) return "-";
        TreeSet<String> tokens = new TreeSet<>();
        Matcher matcher = STYLE_ENTRY.matcher(params);
        while (matcher.find()) {
            for (String token : matcher.group(1).split(",")) {
                if (!token.isEmpty()) tokens.add(token);
            }
        }
        return tokens.isEmpty() ? "-" : String.join(",", tokens);
    }

    /** {@code lead/trail}: chars {@link String#trim} removes from each end of {@code text}. */
    public static String trimShift(String text) {
        if (text == null) return "-";
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return text.length() + "/0";
        int lead = text.indexOf(trimmed);
        return lead + "/" + (text.length() - lead - trimmed.length());
    }

    /**
     * Length Zalo's flattening should produce for {@code originalLength} chars and these mentions
     * (each replaces {@code [start, end)} with its wire text), or -1 when a mention has no wire text.
     */
    public static int expectedFlattenLength(int originalLength, List<MentionRemap.Mention> mentions) {
        int length = originalLength;
        if (mentions == null) return length;
        for (MentionRemap.Mention mention : mentions) {
            if (mention.wire == null || mention.display == null) return -1;
            length += mention.wire.length() - (mention.end - mention.start);
        }
        return length;
    }

    /** {@code [3,8)w12d5}: range and wire/display lengths per mention, never the texts. */
    public static String mentionShapes(List<MentionRemap.Mention> mentions, int max) {
        if (mentions == null || mentions.isEmpty()) return "-";
        StringBuilder out = new StringBuilder();
        int shown = Math.min(max, mentions.size());
        for (int index = 0; index < shown; index++) {
            MentionRemap.Mention mention = mentions.get(index);
            if (index > 0) out.append(' ');
            out.append('[').append(mention.start).append(',').append(mention.end).append(")w")
                .append(mention.wire == null ? "null" : String.valueOf(mention.wire.length()))
                .append('d').append(mention.display == null ? "null" : String.valueOf(mention.display.length()));
        }
        if (mentions.size() > shown) out.append(" +").append(mentions.size() - shown);
        return out.toString();
    }

    /**
     * Inline runs the serializer drops or changes for {@code fullText}, mirroring
     * {@link RichTextSerializer#serializeForSend}: colors outside the whitelist, runs left empty by the
     * trim, sizes clamped to the config range. Null when nothing is dropped or changed.
     */
    public static String droppedStyles(String fullText, List<RichTextRun> runs, RichTextConfig config) {
        if (fullText == null || runs == null || runs.isEmpty()) return null;
        if (config == null) config = RichTextConfig.DEFAULT;
        String trimmed = fullText.trim();
        int shift = trimmed.isEmpty() ? 0 : fullText.indexOf(trimmed);
        int length = trimmed.length();
        StringBuilder colors = new StringBuilder();
        StringBuilder outside = new StringBuilder();
        StringBuilder clamped = new StringBuilder();
        int colorCount = 0;
        int outsideCount = 0;
        int clampedCount = 0;
        for (RichTextRun run : runs) {
            if (run.kind.isLineLevel()) continue;
            String shape = token(run) + "[" + run.start + "," + run.end + ")";
            if (run.kind == RichTextRun.Kind.COLOR && !config.allowsColor(run.value)) {
                if (colorCount++ < MAX_SHAPES) colors.append(colors.length() > 0 ? " " : "").append(shape);
                continue;
            }
            int start = Math.max(0, run.start - shift);
            int end = Math.min(length, run.end - shift);
            if (start >= end) {
                if (outsideCount++ < MAX_SHAPES) outside.append(outside.length() > 0 ? " " : "").append(shape);
                continue;
            }
            if (run.kind == RichTextRun.Kind.SIZE && run.value > 0 && config.clampSize(run.value) != run.value) {
                if (clampedCount++ < MAX_SHAPES) {
                    clamped.append(clamped.length() > 0 ? " " : "").append(shape).append("->f_")
                        .append(config.clampSize(run.value));
                }
            }
        }
        if (colorCount + outsideCount + clampedCount == 0) return null;
        StringBuilder out = new StringBuilder();
        if (colorCount > 0) out.append("color-not-whitelisted=").append(colorCount).append(" {").append(colors).append('}');
        if (outsideCount > 0) {
            if (out.length() > 0) out.append(' ');
            out.append("empty-after-trim=").append(outsideCount).append(" {").append(outside).append('}');
        }
        if (clampedCount > 0) {
            if (out.length() > 0) out.append(' ');
            out.append("size-clamped=").append(clampedCount).append(" {").append(clamped).append('}');
        }
        return out.toString();
    }

    /**
     * For events that can repeat quickly: {@link #next} is true for the 1st, 10th, 100th, 1000th, ...
     * occurrence, so the log shows that it happens and roughly how often, without flooding.
     */
    public static final class Sampler {
        private int count;
        private int nextLogged = 1;

        /** Counts one occurrence; true when this one should be logged. */
        public synchronized boolean next() {
            count++;
            if (count < nextLogged) return false;
            nextLogged = nextLogged >= 1_000_000_000 ? Integer.MAX_VALUE : nextLogged * 10;
            return true;
        }

        public synchronized int count() {
            return count;
        }
    }

    /** Remembers the last logged state; {@link #changed} is true only when the state differs from it. */
    public static final class Latch {
        private String last;

        public synchronized boolean changed(String state) {
            if (state == null ? last == null : state.equals(last)) return false;
            last = state;
            return true;
        }
    }
}
