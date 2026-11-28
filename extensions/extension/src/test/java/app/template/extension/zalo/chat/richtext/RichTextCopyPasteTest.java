package app.template.extension.zalo.chat.richtext;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import app.template.extension.zalo.chat.richtext.RichTextRun.Kind;

public class RichTextCopyPasteTest {
    /** The params of the real chat.zalo.me message in analysis/zalo/notes/rich-text-web-payload.md. */
    private static final String WEB_PARAMS = "{\"styles\":["
        + "{\"start\":0,\"len\":5,\"st\":\"b\"},{\"start\":5,\"len\":2,\"st\":\"b\"},{\"start\":7,\"len\":71,\"st\":\"b\"},"
        + "{\"start\":78,\"len\":1,\"st\":\"b\"},{\"start\":79,\"len\":2,\"st\":\"b\"},{\"start\":83,\"len\":40,\"st\":\"f_20\"},"
        + "{\"start\":125,\"len\":112,\"st\":\"i,f_13\"},{\"start\":237,\"len\":162,\"st\":\"f_13\"},"
        + "{\"start\":400,\"len\":0,\"st\":\"f_13\"},{\"start\":401,\"len\":35,\"st\":\"u,f_13\"},"
        + "{\"start\":436,\"len\":26,\"st\":\"u\"},{\"start\":635,\"len\":19,\"st\":\"lst_1,ind_20\"},"
        + "{\"start\":635,\"len\":0,\"st\":\"b,i,u\"},{\"start\":635,\"len\":2,\"st\":\"b,i,u\"},"
        + "{\"start\":637,\"len\":17,\"st\":\"b,i,u\"},{\"start\":655,\"len\":65,\"st\":\"lst_1\"},"
        + "{\"start\":655,\"len\":0,\"st\":\"b,i,u\"},{\"start\":655,\"len\":2,\"st\":\"b,i,u\"},"
        + "{\"start\":657,\"len\":38,\"st\":\"b,i,u\"},{\"start\":721,\"len\":68,\"st\":\"lst_1,ind_10\"},"
        + "{\"start\":906,\"len\":0,\"st\":\"s\"},{\"start\":906,\"len\":1,\"st\":\"s\"},{\"start\":907,\"len\":43,\"st\":\"s\"},"
        + "{\"start\":953,\"len\":0,\"st\":\"f_18\"},{\"start\":953,\"len\":2,\"st\":\"f_18\"},{\"start\":955,\"len\":18,\"st\":\"f_18\"},"
        + "{\"start\":1000,\"len\":22,\"st\":\"lst_2\"},{\"start\":1000,\"len\":22,\"st\":\"c_f27806\"},"
        + "{\"start\":1023,\"len\":43,\"st\":\"lst_2\"},{\"start\":1024,\"len\":23,\"st\":\"c_f27806\"},"
        + "{\"start\":1069,\"len\":50,\"st\":\"c_db342e\"},{\"start\":1235,\"len\":39,\"st\":\"c_15a85f\"}],\"ver\":0}";

    /**
     * A text that fits the web sample: it starts with the sample's emoji (5 UTF-16 units before the
     * first plain char) and has its line breaks exactly where the sample's line entries end.
     */
    private static String webText() {
        char[] chars = new char[1300];
        Arrays.fill(chars, 'x');
        StringBuilder text = new StringBuilder(new String(chars));
        text.replace(0, 7, "👨‍💼🔥"); // man office worker, fire
        for (int newline : new int[] { 634, 654, 720, 789, 999, 1022, 1066 }) text.setCharAt(newline, '\n');
        return text.toString();
    }

    private static RichTextRun run(int start, int end, Kind kind) {
        return new RichTextRun(start, end, kind);
    }

    private static RichTextRun run(int start, int end, Kind kind, int value) {
        return new RichTextRun(start, end, kind, value);
    }

    private static String str(List<RichTextRun> runs) {
        return runs.toString();
    }

    // ---- Parser ----

    @Test
    public void parsesWebSampleIntoCanonicalRuns() {
        String text = webText();
        List<RichTextRun> runs = RichTextParser.parse(WEB_PARAMS, text);
        String all = str(runs);
        // Contiguous bold entries 0-5, 5-7, 7-78, 78-79, 79-81 merge into one run.
        assertEquals(all, "BOLD(0)[0,81)", runs.get(0).toString());
        assertTrue(all, all.contains("SIZE(13)[125,399)"));
        assertTrue(all, all.contains("SIZE(13)[401,436)"));
        assertTrue(all, all.contains("SIZE(20)[83,123)"));
        assertTrue(all, all.contains("SIZE(18)[953,973)"));
        assertTrue(all, all.contains("COLOR(15890438)[1000,1022)")); // 0xf27806
        assertTrue(all, all.contains("LIST(1)[635,654)"));
        assertTrue(all, all.contains("LIST(2)[1023,1066)"));
        assertTrue(all, all.contains("INDENT(2)[635,654)"));
        assertTrue(all, all.contains("INDENT(1)[721,789)"));
        assertFalse("zero-length entries are dropped", all.contains("[400,400)"));
    }

    @Test
    public void roundTripSerializeThenParseGivesTheSameRuns() {
        String text = webText();
        List<RichTextRun> parsed = RichTextParser.parse(WEB_PARAMS, text);
        String params = RichTextSerializer.serialize(text, parsed, RichTextConfig.DEFAULT);
        assertNotNull(params);
        assertEquals(str(parsed), str(RichTextParser.parse(params, text)));
    }

    @Test
    public void roundTripFromToolbarRuns() {
        String text = "Title\n- one\n- two\nend";
        List<RichTextRun> runs = RichTextRuns.canonical(text, Arrays.asList(
            run(0, 5, Kind.BOLD), run(2, 9, Kind.ITALIC), run(6, 12, Kind.LIST, 1), run(12, 18, Kind.LIST, 2),
            run(12, 18, Kind.INDENT, 3), run(18, 21, Kind.SIZE, 18), run(19, 21, Kind.COLOR, 0xDB342E)));
        String params = RichTextSerializer.serialize(text, runs, RichTextConfig.DEFAULT);
        assertEquals(str(runs), str(RichTextParser.parse(params, text)));
    }

    @Test
    public void parserFollowsTheReceiverClipping() {
        String text = "hello";
        String params = "{\"styles\":[{\"start\":3,\"len\":10,\"st\":\"b\"},{\"start\":5,\"len\":1,\"st\":\"i\"},"
            + "{\"start\":-1,\"len\":2,\"st\":\"u\"},{\"start\":0,\"len\":2,\"st\":\"zz,s,c_#DB342E,f_x,ind_37,lst_9\"}],\"ver\":0}";
        assertEquals("[BOLD(0)[3,5), STRIKE(0)[0,2), COLOR(14365742)[0,2), INDENT(4)[0,5)]",
            str(RichTextParser.parse(params, text)));
    }

    @Test
    public void malformedParamsGiveNoRuns() {
        assertTrue(RichTextParser.parse("{\"styles\":[{\"start\":0,", "hello").isEmpty());
        assertTrue(RichTextParser.parse("[]", "hello").isEmpty());
        assertTrue(RichTextParser.parse("{\"styles\":\"b\"}", "hello").isEmpty());
        assertTrue(RichTextParser.parse(null, "hello").isEmpty());
        assertTrue(RichTextParser.parse("{\"styles\":[{\"start\":0,\"len\":1,\"st\":\"b\"}]}", "").isEmpty());
    }

    @Test
    public void jsonReaderHandlesEscapesAndNumberStrings() {
        String params = "{ \"ver\" : 0 , \"x\":[true,false,null,1.5e1,\"a\\\"\\u0041\\n\"],"
            + " \"styles\" : [ { \"st\" : \"b\" , \"start\" : \"1\" , \"len\" : 2.0 } ] }";
        assertEquals("[BOLD(0)[1,3)]", str(RichTextParser.parse(params, "hello")));
    }

    // ---- Slicing composer runs ----

    @Test
    public void sliceRebasesInlineRunsToTheSelection() {
        String text = "abcdefgh";
        List<RichTextRun> runs = Arrays.asList(run(0, 4, Kind.BOLD), run(3, 8, Kind.COLOR, 0xDB342E), run(6, 7, Kind.SIZE, 20));
        assertEquals("[BOLD(0)[0,2), SIZE(20)[4,5), COLOR(14365742)[1,6)]", str(RichTextRuns.slice(text, runs, 2, 8)));
    }

    @Test
    public void sliceKeepsLineStylesOnlyForLinesStartingInTheSelection() {
        // Composer markers cover each paragraph and its line break.
        String text = "one\ntwo\nthree";
        List<RichTextRun> runs = Arrays.asList(run(0, 4, Kind.LIST, 1), run(4, 8, Kind.LIST, 2), run(4, 8, Kind.INDENT, 1),
            run(8, 13, Kind.LIST, 1));
        // "ne\ntwo\nth": "ne" is a partial line, "two" and "th" start inside the selection.
        assertEquals("[LIST(2)[3,6), LIST(1)[7,9), INDENT(1)[3,6)]", str(RichTextRuns.slice(text, runs, 1, 10)));
        // A whole first line keeps its bullet.
        assertEquals("[LIST(1)[0,3)]", str(RichTextRuns.slice(text, runs, 0, 4)));
        // Inside one line: no line style at all.
        assertTrue(RichTextRuns.slice(text, runs, 5, 7).isEmpty());
    }

    @Test
    public void sliceOfUnstyledTextIsEmpty() {
        assertTrue(RichTextRuns.slice("plain text", Collections.emptyList(), 0, 5).isEmpty());
        assertTrue(RichTextRuns.slice("plain", Collections.singletonList(run(0, 5, Kind.BOLD)), 3, 3).isEmpty());
    }

    @Test
    public void sliceThenPasteLinesRoundTrip() {
        String text = "a\n- b\n- c";
        List<RichTextRun> composer = Arrays.asList(run(2, 6, Kind.LIST, 1), run(6, 9, Kind.LIST, 1), run(6, 9, Kind.INDENT, 2));
        List<RichTextRun> copied = RichTextRuns.slice(text, composer, 2, 9);
        String pasted = text.substring(2, 9);
        int[][] values = RichTextRuns.lineValues(pasted, copied);
        assertArrayEquals(new int[] { 1, 0 }, values[0]);
        assertArrayEquals(new int[] { 1, 2 }, values[1]);
    }

    // ---- Paste line overrides ----

    @Test
    public void pasteAtParagraphStartAppliesEveryPastedLine() {
        String pasted = "x\ny\nz";
        List<RichTextRun> runs = Arrays.asList(run(0, 1, Kind.LIST, 2), run(4, 5, Kind.INDENT, 1));
        String composer = "ab\n" + pasted + "cd";
        Map<Integer, int[]> overrides = RichTextRuns.pasteLineOverrides(composer, 3, pasted, runs);
        assertEquals(3, overrides.size());
        assertArrayEquals(new int[] { 2, 0 }, overrides.get(3));
        assertArrayEquals(new int[] { 0, 0 }, overrides.get(5)); // later lines take exactly the pasted style
        assertArrayEquals(new int[] { 0, 1 }, overrides.get(7));
    }

    @Test
    public void pasteMidLineKeepsTheLandingParagraph() {
        String pasted = "x\ny";
        List<RichTextRun> runs = Arrays.asList(run(0, 1, Kind.LIST, 1), run(2, 3, Kind.LIST, 1));
        String composer = "ab" + pasted;
        Map<Integer, int[]> overrides = RichTextRuns.pasteLineOverrides(composer, 2, pasted, runs);
        assertNull(overrides.get(2));
        assertNull(overrides.get(0));
        assertArrayEquals(new int[] { 1, 0 }, overrides.get(4));
    }

    @Test
    public void pasteAtParagraphStartWithoutFirstLineStyleKeepsTheExistingStyle() {
        String pasted = "x";
        List<RichTextRun> runs = Collections.singletonList(run(0, 1, Kind.BOLD));
        assertTrue(RichTextRuns.pasteLineOverrides("x", 0, pasted, runs).isEmpty());
    }

    @Test
    public void pasteEndingWithLineBreakLeavesTheFollowingLine() {
        String pasted = "x\n";
        List<RichTextRun> runs = Collections.singletonList(run(0, 1, Kind.LIST, 1));
        String composer = pasted + "tail";
        Map<Integer, int[]> overrides = RichTextRuns.pasteLineOverrides(composer, 0, pasted, runs);
        assertEquals(1, overrides.size());
        assertArrayEquals(new int[] { 1, 0 }, overrides.get(0));
    }

    @Test
    public void pasteOverridesRejectAMisplacedPaste() {
        List<RichTextRun> runs = new ArrayList<>(Collections.singletonList(run(0, 1, Kind.LIST, 1)));
        assertTrue(RichTextRuns.pasteLineOverrides("ab", 1, "xyz", runs).isEmpty());
    }

    // ---- Canonical form ----

    @Test
    public void canonicalMergesSplitsAndOrdersRuns() {
        String text = "abcdef\nghi";
        List<RichTextRun> runs = Arrays.asList(run(3, 6, Kind.BOLD), run(0, 3, Kind.BOLD), run(0, 10, Kind.LIST, 1),
            run(2, 4, Kind.SIZE, 13), run(3, 5, Kind.SIZE, 20), run(8, 20, Kind.ITALIC));
        assertEquals("[BOLD(0)[0,6), ITALIC(0)[8,10), SIZE(13)[2,3), SIZE(20)[3,5), LIST(1)[0,6), LIST(1)[7,10)]",
            str(RichTextRuns.canonical(text, runs)));
    }
}
