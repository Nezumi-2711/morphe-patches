package app.template.extension.zalo.chat.richtext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import app.template.extension.zalo.chat.richtext.MentionRemap.Mention;
import app.template.extension.zalo.chat.richtext.RichTextRun.Kind;

public class RichTextLogTest {
    private static final String SECRET = "Hẹn gặp lúc 7h nhé";

    private static RichTextRun run(int start, int end, Kind kind, int value) {
        return new RichTextRun(start, end, kind, value);
    }

    @Test
    public void kindCountsListsCountPerKindInKindOrder() {
        List<RichTextRun> runs = Arrays.asList(run(0, 3, Kind.COLOR, 0xdb342e), run(0, 5, Kind.BOLD, 0),
            run(4, 6, Kind.BOLD, 0), run(0, 9, Kind.LIST, 1));
        assertEquals("4(b=2,c=1,lst=1)", RichTextLog.kindCounts(runs));
        assertEquals("0", RichTextLog.kindCounts(Collections.emptyList()));
        assertEquals("0", RichTextLog.kindCounts(null));
    }

    @Test
    public void runShapesUseWireTokensAndCapTheList() {
        List<RichTextRun> runs = Arrays.asList(run(0, 5, Kind.BOLD, 0), run(3, 9, Kind.COLOR, 0xdb342e),
            run(0, 12, Kind.LIST, 2), run(0, 12, Kind.INDENT, 1), run(1, 2, Kind.SIZE, 18));
        assertEquals("b[0,5) c_db342e[3,9) lst_2[0,12) ind_10[0,12) f_18[1,2)", RichTextLog.runShapes(runs, 12));
        assertEquals("b[0,5) c_db342e[3,9) +3", RichTextLog.runShapes(runs, 2));
        assertEquals("-", RichTextLog.runShapes(Collections.emptyList(), 2));
    }

    @Test
    public void styleTokensFollowWireOrder() {
        assertEquals("plain", RichTextLog.styleTokens(RichTextStyle.PLAIN));
        assertEquals("b,u,f_18,c_15a85f",
            RichTextLog.styleTokens(new RichTextStyle(true, false, true, false, 18, 0x15a85f)));
    }

    @Test
    public void fingerprintIsLengthPlusSixHexAndNeverTheText() {
        String fingerprint = RichTextLog.fingerprint(SECRET);
        assertTrue(fingerprint, fingerprint.matches(SECRET.length() + "#[0-9a-f]{6}"));
        assertEquals(fingerprint, RichTextLog.fingerprint(SECRET));
        assertNotEquals(fingerprint, RichTextLog.fingerprint(SECRET + " "));
        assertFalse(fingerprint.contains("7h"));
        assertEquals("null", RichTextLog.fingerprint(null));
    }

    @Test
    public void paramsSummaryCountsEntriesAndDistinctTokens() {
        String params = RichTextSerializer.serialize("hello\nworld",
            Arrays.asList(run(0, 5, Kind.BOLD, 0), run(0, 11, Kind.LIST, 1), run(6, 11, Kind.COLOR, 0xdb342e),
                run(6, 11, Kind.BOLD, 0)), RichTextConfig.DEFAULT);
        assertEquals(4, RichTextLog.paramsEntries(params));
        assertEquals("b,c_db342e,lst_1", RichTextLog.paramsTokens(params));
        assertEquals(0, RichTextLog.paramsEntries(null));
        assertEquals("-", RichTextLog.paramsTokens(null));
    }

    @Test
    public void trimShiftCountsBothEnds() {
        assertEquals("2/1", RichTextLog.trimShift("  hi\n"));
        assertEquals("0/0", RichTextLog.trimShift("hi"));
        assertEquals("3/0", RichTextLog.trimShift("   "));
    }

    @Test
    public void expectedFlattenLengthAndMentionShapesUseLengthsOnly() {
        List<Mention> mentions = Arrays.asList(new Mention(3, 8, "@123456789012", "@Linh"),
            new Mention(10, 14, "@4567", null));
        assertEquals(-1, RichTextLog.expectedFlattenLength(20, mentions));
        assertEquals(20 + 13 - 5, RichTextLog.expectedFlattenLength(20, mentions.subList(0, 1)));
        String shapes = RichTextLog.mentionShapes(mentions, 12);
        assertEquals("[3,8)w13d5 [10,14)w5dnull", shapes);
        assertFalse(shapes.contains("Linh"));
        assertFalse(shapes.contains("123456789"));
    }

    @Test
    public void droppedStylesReportsColorsTrimAndClamping() {
        String text = " hello  ";
        List<RichTextRun> runs = new ArrayList<>(Arrays.asList(
            run(1, 6, Kind.BOLD, 0),
            run(1, 3, Kind.COLOR, 0x123456),
            run(6, 8, Kind.ITALIC, 0),
            run(1, 6, Kind.SIZE, 30),
            run(0, 8, Kind.LIST, 1)));
        assertEquals("color-not-whitelisted=1 {c_123456[1,3)} empty-after-trim=1 {i[6,8)}"
                + " size-clamped=1 {f_30[1,6)->f_20}",
            RichTextLog.droppedStyles(text, runs, RichTextConfig.DEFAULT));
        assertNull(RichTextLog.droppedStyles("hello", Collections.singletonList(run(0, 5, Kind.BOLD, 0)),
            RichTextConfig.DEFAULT));
    }

    @Test
    public void samplerLogsFirstTenthHundredth() {
        RichTextLog.Sampler sampler = new RichTextLog.Sampler();
        List<Integer> logged = new ArrayList<>();
        for (int index = 1; index <= 1000; index++) if (sampler.next()) logged.add(index);
        assertEquals(Arrays.asList(1, 10, 100, 1000), logged);
        assertEquals(1000, sampler.count());
    }

    @Test
    public void latchReportsOnlyChanges() {
        RichTextLog.Latch latch = new RichTextLog.Latch();
        assertTrue(latch.changed("a"));
        assertFalse(latch.changed("a"));
        assertTrue(latch.changed("b"));
        assertTrue(latch.changed("a"));
    }
}
