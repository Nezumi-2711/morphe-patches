package app.template.extension.zalo.chat.richtext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import app.template.extension.zalo.chat.richtext.RichTextRun.Kind;

public class RichTextSerializerTest {
    private static final RichTextConfig CONFIG = RichTextConfig.DEFAULT;

    private static RichTextRun run(int start, int end, Kind kind) {
        return new RichTextRun(start, end, kind);
    }

    private static RichTextRun run(int start, int end, Kind kind, int value) {
        return new RichTextRun(start, end, kind, value);
    }

    private static String repeat(char c, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, c);
        return new String(chars);
    }

    private static String serialize(String text, RichTextRun... runs) {
        return RichTextSerializer.serialize(text, Arrays.asList(runs), CONFIG);
    }

    @Test
    public void boldWholeTextMatchesSpikeFormat() {
        assertEquals("{\"styles\":[{\"start\":0,\"len\":5,\"st\":\"b\"}],\"ver\":0}",
            serialize("hello", run(0, 5, Kind.BOLD)));
    }

    @Test
    public void noRunsOrEmptyTextGiveNull() {
        assertNull(serialize("hello"));
        assertNull(serialize("", run(0, 0, Kind.BOLD)));
        assertNull(RichTextSerializer.serialize(null, Collections.singletonList(run(0, 1, Kind.BOLD)), CONFIG));
    }

    @Test
    public void inlineTokensAreCombinedInWebOrder() {
        // Added in a scrambled order; the output order is always b,i,u,s,f_,c_.
        assertEquals("{\"styles\":[{\"start\":0,\"len\":4,\"st\":\"b,i,u,s,f_18,c_db342e\"}],\"ver\":0}",
            serialize("text", run(0, 4, Kind.COLOR, 0xDB342E), run(0, 4, Kind.STRIKE), run(0, 4, Kind.SIZE, 18),
                run(0, 4, Kind.UNDERLINE), run(0, 4, Kind.ITALIC), run(0, 4, Kind.BOLD)));
    }

    @Test
    public void overlappingRunsAreSegmented() {
        // bold 0-6, italic 3-9: [0,3) b, [3,6) b,i, [6,9) i
        assertEquals("{\"styles\":[{\"start\":0,\"len\":3,\"st\":\"b\"},{\"start\":3,\"len\":3,\"st\":\"b,i\"},"
                + "{\"start\":6,\"len\":3,\"st\":\"i\"}],\"ver\":0}",
            serialize("abcdefghij", run(0, 6, Kind.BOLD), run(3, 9, Kind.ITALIC)));
    }

    @Test
    public void adjacentEqualSegmentsAreMergedAndGapsSkipped() {
        assertEquals("{\"styles\":[{\"start\":0,\"len\":6,\"st\":\"b\"},{\"start\":8,\"len\":2,\"st\":\"b\"}],\"ver\":0}",
            serialize("abcdefghij", run(0, 3, Kind.BOLD), run(3, 6, Kind.BOLD), run(8, 10, Kind.BOLD)));
    }

    @Test
    public void zeroLengthAndOutOfRangeRunsAreDropped() {
        assertEquals("{\"styles\":[{\"start\":2,\"len\":3,\"st\":\"u\"}],\"ver\":0}",
            serialize("hello", run(1, 1, Kind.BOLD), run(2, 50, Kind.UNDERLINE), run(7, 9, Kind.ITALIC),
                run(-3, 0, Kind.STRIKE)));
    }

    @Test
    public void sizeIsClampedToTheConfigRange() {
        assertEquals("{\"styles\":[{\"start\":0,\"len\":2,\"st\":\"f_13\"},{\"start\":2,\"len\":2,\"st\":\"f_20\"}],\"ver\":0}",
            serialize("abcd", run(0, 2, Kind.SIZE, 8), run(2, 4, Kind.SIZE, 40)));
        RichTextConfig narrow = RichTextConfig.parse(14, 16, null);
        assertEquals("{\"styles\":[{\"start\":0,\"len\":4,\"st\":\"f_16\"}],\"ver\":0}",
            RichTextSerializer.serialize("abcd", Collections.singletonList(run(0, 4, Kind.SIZE, 18)), narrow));
    }

    @Test
    public void colorsOutsideTheWhitelistAreDropped() {
        assertNull(serialize("abcd", run(0, 4, Kind.COLOR, 0x123456)));
        assertEquals("{\"styles\":[{\"start\":0,\"len\":4,\"st\":\"b\"}],\"ver\":0}",
            serialize("abcd", run(0, 4, Kind.COLOR, 0x123456), run(0, 4, Kind.BOLD)));
        RichTextConfig custom = RichTextConfig.parse(13, 20, "#123456, #ABCDEF");
        assertEquals("{\"styles\":[{\"start\":0,\"len\":4,\"st\":\"c_abcdef\"}],\"ver\":0}",
            RichTextSerializer.serialize("abcd", Collections.singletonList(run(0, 4, Kind.COLOR, 0xABCDEF)), custom));
    }

    @Test
    public void configFallsBackToDefaults() {
        RichTextConfig config = RichTextConfig.parse(30, 10, "");
        assertEquals(13, config.sizeMin);
        assertEquals(20, config.sizeMax);
        assertEquals(Arrays.asList("#050a19", "#15a85f", "#f7b503", "#f27806", "#db342e"),
            Arrays.asList(config.colors.toArray()));
    }

    @Test
    public void offsetsAreUtf16CodeUnits() {
        // "👨‍💼" is 5 UTF-16 units (2 + ZWJ + 2), as in the web sample's first style {0,5,"b"}.
        String text = "👨‍💼🔥 hi";
        assertEquals(10, text.length());
        assertEquals("{\"styles\":[{\"start\":0,\"len\":5,\"st\":\"b\"},{\"start\":5,\"len\":2,\"st\":\"i\"}],\"ver\":0}",
            serialize(text, run(0, 5, Kind.BOLD), run(5, 7, Kind.ITALIC)));
    }

    @Test
    public void edgesNeverSplitASurrogatePair() {
        String text = "a🔥b";
        // A start in the middle of the pair moves back, an end moves forward.
        assertEquals("{\"styles\":[{\"start\":1,\"len\":2,\"st\":\"b\"}],\"ver\":0}", serialize(text, run(2, 3, Kind.BOLD)));
        assertEquals("{\"styles\":[{\"start\":0,\"len\":3,\"st\":\"i\"}],\"ver\":0}", serialize(text, run(0, 2, Kind.ITALIC)));
    }

    @Test
    public void listAndIndentAreOneEntryPerLine() {
        String text = "one\ntwo\n\nthree";
        // Bullet over the first two lines (with indent level 2 on the first), then the empty line is
        // skipped, then an indent-only line. Each entry excludes the \n.
        assertEquals("{\"styles\":[{\"start\":0,\"len\":3,\"st\":\"lst_1,ind_20\"},{\"start\":4,\"len\":3,\"st\":\"lst_1\"},"
                + "{\"start\":9,\"len\":5,\"st\":\"ind_10\"}],\"ver\":0}",
            serialize(text, run(0, 7, Kind.LIST, RichTextRun.LIST_BULLET), run(1, 2, Kind.INDENT, 2),
                run(9, 14, Kind.INDENT, 1), run(8, 8, Kind.LIST, RichTextRun.LIST_NUMBERED)));
    }

    @Test
    public void lineEntryComesFirstAtTheSameStart() {
        assertEquals("{\"styles\":[{\"start\":0,\"len\":5,\"st\":\"lst_2\"},{\"start\":0,\"len\":2,\"st\":\"b,i,u\"}],\"ver\":0}",
            serialize("ab cd", run(0, 2, Kind.UNDERLINE), run(0, 2, Kind.ITALIC), run(0, 2, Kind.BOLD),
                run(0, 5, Kind.LIST, RichTextRun.LIST_NUMBERED)));
    }

    @Test
    public void invalidLineValuesAreIgnored() {
        assertNull(serialize("abc", run(0, 3, Kind.LIST, 7), run(0, 3, Kind.INDENT, 0)));
    }

    /**
     * Slice of the real chat.zalo.me sample (rich-text-web-payload.md), offsets 1000..1118: two numbered
     * items colored #f27806, then a #db342e line. The text is a stand-in with the same line lengths.
     * Web output (zero-length entries are web editor artifacts and not reproduced):
     * {1000,22,"lst_2"} {1000,22,"c_f27806"} {1023,43,"lst_2"} {1024,23,"c_f27806"} {1069,50,"c_db342e"}.
     */
    @Test
    public void reproducesAWebSampleSlice() {
        String prefix = repeat('p', 999) + "\n";
        String text = prefix + repeat('a', 22) + "\n" + repeat('b', 43) + "\n\n\n" + repeat('c', 50);
        assertEquals(1000, prefix.length());
        assertEquals(1119, text.length());
        String params = serialize(text,
            run(1000, 1022, Kind.COLOR, 0xF27806),
            run(1024, 1047, Kind.COLOR, 0xF27806),
            run(1069, 1119, Kind.COLOR, 0xDB342E),
            run(1000, 1066, Kind.LIST, RichTextRun.LIST_NUMBERED));
        assertEquals("{\"styles\":["
            + "{\"start\":1000,\"len\":22,\"st\":\"lst_2\"},"
            + "{\"start\":1000,\"len\":22,\"st\":\"c_f27806\"},"
            + "{\"start\":1023,\"len\":43,\"st\":\"lst_2\"},"
            + "{\"start\":1024,\"len\":23,\"st\":\"c_f27806\"},"
            + "{\"start\":1069,\"len\":50,\"st\":\"c_db342e\"}"
            + "],\"ver\":0}", params);
    }

    /** Sample offsets 635..720: indented bullet lines with b,i,u inside, as web sends them. */
    @Test
    public void reproducesTheWebIndentedListSlice() {
        String text = repeat('p', 634) + "\n" + repeat('x', 19) + "\n" + repeat('y', 65);
        String params = serialize(text,
            run(635, 720, Kind.LIST, RichTextRun.LIST_BULLET),
            run(635, 654, Kind.INDENT, 2),
            run(635, 654, Kind.BOLD), run(635, 654, Kind.ITALIC), run(635, 654, Kind.UNDERLINE),
            run(655, 695, Kind.BOLD), run(655, 695, Kind.ITALIC), run(655, 695, Kind.UNDERLINE));
        assertEquals("{\"styles\":["
            + "{\"start\":635,\"len\":19,\"st\":\"lst_1,ind_20\"},"
            + "{\"start\":635,\"len\":19,\"st\":\"b,i,u\"},"
            + "{\"start\":655,\"len\":65,\"st\":\"lst_1\"},"
            + "{\"start\":655,\"len\":40,\"st\":\"b,i,u\"}"
            + "],\"ver\":0}", params);
    }

    @Test
    public void serializeForSendAppliesTheZaloTrim() {
        // Zalo sends "hello world" (String.trim()); styles shift by the 3 leading blanks and clip.
        List<RichTextRun> runs = Arrays.asList(run(3, 8, Kind.BOLD), run(9, 16, Kind.ITALIC), run(0, 2, Kind.STRIKE));
        assertEquals("{\"styles\":[{\"start\":0,\"len\":5,\"st\":\"b\"},{\"start\":6,\"len\":5,\"st\":\"i\"}],\"ver\":0}",
            RichTextSerializer.serializeForSend(" \n hello world \n", runs, CONFIG));
        assertNull(RichTextSerializer.serializeForSend("   ", runs, CONFIG));
    }
}
