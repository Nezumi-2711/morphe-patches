package app.template.extension.zalo.chat.richtext;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import app.template.extension.zalo.chat.richtext.RichTextRanges.Run;

public class RichTextRangesTest {
    private static Run run(int start, int end) {
        return new Run(start, end, 1);
    }

    private static Run run(int start, int end, int value) {
        return new Run(start, end, value);
    }

    private static List<Run> runs(Run... runs) {
        return new ArrayList<>(Arrays.asList(runs));
    }

    // ---- assign / normalize ----

    @Test
    public void assignAddsAndMergesTouchingRuns() {
        assertEquals(runs(run(0, 8)), RichTextRanges.assign(runs(run(0, 3), run(5, 8)), 3, 5, false, 1));
    }

    @Test
    public void assignClearSplitsARun() {
        assertEquals(runs(run(0, 2), run(6, 10)), RichTextRanges.assign(runs(run(0, 10)), 2, 6, true, 0));
    }

    @Test
    public void assignValueReplacesOtherValuesInRange() {
        // size 18 over [0,10), then 13 over [4,6): 18 / 13 / 18
        assertEquals(runs(run(0, 4, 18), run(4, 6, 13), run(6, 10, 18)),
            RichTextRanges.assign(runs(run(0, 10, 18)), 4, 6, false, 13));
    }

    @Test
    public void assignDoesNotMergeDifferentValues() {
        assertEquals(runs(run(0, 3, 13), run(3, 6, 18)),
            RichTextRanges.assign(runs(run(0, 3, 13)), 3, 6, false, 18));
    }

    @Test
    public void assignEmptyRangeIsNoOp() {
        assertEquals(runs(run(1, 4)), RichTextRanges.assign(runs(run(1, 4)), 2, 2, false, 1));
    }

    @Test
    public void normalizeLetsLaterRunsWinOverlaps() {
        assertEquals(runs(run(0, 2, 0xFF0000), run(2, 6, 0x00FF00), run(6, 8, 0xFF0000)),
            RichTextRanges.normalize(runs(run(0, 8, 0xFF0000), run(2, 6, 0x00FF00))));
        assertEquals(runs(run(0, 6)), RichTextRanges.normalize(runs(run(0, 4), run(2, 6))));
        assertEquals(runs(), RichTextRanges.normalize(runs(run(3, 3))));
    }

    // ---- toggle semantics ----

    @Test
    public void toggleRemovesWhenWholeSelectionHasIt() {
        assertEquals(runs(run(0, 2), run(5, 10)), RichTextRanges.toggle(runs(run(0, 10)), 2, 5));
    }

    @Test
    public void toggleAppliesWhenSelectionIsPartlyStyled() {
        assertEquals(runs(run(0, 8)), RichTextRanges.toggle(runs(run(0, 4)), 2, 8));
    }

    @Test
    public void toggleAppliesWhenSelectionHasAGap() {
        assertEquals(runs(run(0, 10)), RichTextRanges.toggle(runs(run(0, 3), run(6, 10)), 0, 10));
    }

    @Test
    public void toggleTwiceRestores() {
        List<Run> original = runs(run(4, 6));
        List<Run> once = RichTextRanges.toggle(original, 0, 10);
        assertEquals(runs(run(0, 10)), once);
        assertEquals(runs(), RichTextRanges.toggle(once, 0, 10));
    }

    @Test
    public void coversAndUniformValue() {
        assertTrue(RichTextRanges.covers(runs(run(0, 3), run(3, 6)), 1, 5));
        assertFalse(RichTextRanges.covers(runs(run(0, 3), run(4, 6)), 1, 5));
        assertFalse(RichTextRanges.covers(runs(run(0, 3)), 2, 2));
        assertEquals(18, RichTextRanges.uniformValue(runs(run(0, 5, 18)), 1, 4));
        assertEquals(RichTextRanges.NONE, RichTextRanges.uniformValue(runs(run(0, 2, 18), run(2, 5, 13)), 1, 4));
        assertEquals(RichTextRanges.NONE, RichTextRanges.uniformValue(runs(run(0, 2, 18)), 1, 4));
        assertEquals(13, RichTextRanges.valueAt(runs(run(2, 4, 13)), 3));
        assertEquals(RichTextRanges.NONE, RichTextRanges.valueAt(runs(run(2, 4, 13)), 4));
    }

    @Test
    public void sameRunsIgnoresSplitting() {
        assertTrue(RichTextRanges.sameRuns(runs(run(0, 2), run(2, 5)), runs(run(0, 5))));
        assertFalse(RichTextRanges.sameRuns(runs(run(0, 2)), runs(run(0, 3))));
    }

    // ---- caret style source ----

    @Test
    public void caretInheritsFromPreviousCharacterOnTheLine() {
        assertEquals(2, RichTextRanges.caretSource("abc", 3));
        assertEquals(0, RichTextRanges.caretSource("abc", 1));
        // Start of the text or a line: the next character on the same line.
        assertEquals(0, RichTextRanges.caretSource("abc", 0));
        assertEquals(3, RichTextRanges.caretSource("ab\ncd", 3));
        // Empty line or empty text: nothing.
        assertEquals(-1, RichTextRanges.caretSource("ab\n\ncd", 3));
        assertEquals(-1, RichTextRanges.caretSource("", 0));
        assertEquals(-1, RichTextRanges.caretSource("ab\n", 3));
    }

    // ---- paragraphs ----

    @Test
    public void paragraphsIncludeTheirLineBreak() {
        List<int[]> paragraphs = RichTextRanges.paragraphs("ab\n\ncd");
        assertEquals(3, paragraphs.size());
        assertArrayEquals(new int[] { 0, 3 }, paragraphs.get(0));
        assertArrayEquals(new int[] { 3, 4 }, paragraphs.get(1));
        assertArrayEquals(new int[] { 4, 6 }, paragraphs.get(2));
    }

    @Test
    public void trailingBreakLeavesAnEmptyLastParagraph() {
        List<int[]> paragraphs = RichTextRanges.paragraphs("ab\n");
        assertEquals(2, paragraphs.size());
        assertArrayEquals(new int[] { 3, 3 }, paragraphs.get(1));
        assertArrayEquals(new int[] { 0, 0 }, RichTextRanges.paragraphs("").get(0));
    }

    @Test
    public void caretTouchesItsParagraph() {
        String text = "one\ntwo\nthree";
        List<int[]> paragraphs = RichTextRanges.paragraphs(text);
        assertArrayEquals(new int[] { 0, 0 }, RichTextRanges.touchedParagraphs(paragraphs, text, 3, 3));
        assertArrayEquals(new int[] { 1, 1 }, RichTextRanges.touchedParagraphs(paragraphs, text, 4, 4));
        assertArrayEquals(new int[] { 2, 2 }, RichTextRanges.touchedParagraphs(paragraphs, text, 13, 13));
    }

    @Test
    public void selectionTouchesEveryParagraphItOverlaps() {
        String text = "one\ntwo\nthree";
        List<int[]> paragraphs = RichTextRanges.paragraphs(text);
        assertArrayEquals(new int[] { 0, 2 }, RichTextRanges.touchedParagraphs(paragraphs, text, 2, 9));
        // Reversed selection.
        assertArrayEquals(new int[] { 0, 1 }, RichTextRanges.touchedParagraphs(paragraphs, text, 5, 1));
        // A selection ending right after a line break does not touch the next paragraph.
        assertArrayEquals(new int[] { 0, 1 }, RichTextRanges.touchedParagraphs(paragraphs, text, 0, 8));
    }

    @Test
    public void caretOnEmptyLastParagraph() {
        String text = "a\n";
        List<int[]> paragraphs = RichTextRanges.paragraphs(text);
        assertArrayEquals(new int[] { 1, 1 }, RichTextRanges.touchedParagraphs(paragraphs, text, 2, 2));
    }

    @Test
    public void markerCoversParagraphStart() {
        assertTrue(RichTextRanges.markerCovers(0, 3, 0));
        // Marker grown over a newly split line (Enter in the middle) also covers the new paragraph.
        assertTrue(RichTextRanges.markerCovers(0, 5, 3));
        // Text typed right after a marker's end does not get its line style.
        assertFalse(RichTextRanges.markerCovers(0, 3, 3));
        // After a line merge the second paragraph's marker starts mid-paragraph: the first wins.
        assertFalse(RichTextRanges.markerCovers(2, 4, 0));
    }

    // ---- list / indent ----

    @Test
    public void listToggleAppliesUnlessAllTouchedLinesHaveIt() {
        int bullet = RichTextRun.LIST_BULLET;
        int numbered = RichTextRun.LIST_NUMBERED;
        assertEquals(bullet, RichTextRanges.toggledList(new int[] { 0, bullet, 0 }, 0, 1, bullet));
        assertEquals(0, RichTextRanges.toggledList(new int[] { bullet, bullet, 0 }, 0, 1, bullet));
        // Switching type: numbered lines become bullets.
        assertEquals(bullet, RichTextRanges.toggledList(new int[] { numbered, numbered }, 0, 1, bullet));
    }

    @Test
    public void indentStaysWithinLimits() {
        assertEquals(1, RichTextRanges.steppedIndent(0, 1));
        assertEquals(0, RichTextRanges.steppedIndent(0, -1));
        assertEquals(RichTextRanges.MAX_INDENT, RichTextRanges.steppedIndent(RichTextRanges.MAX_INDENT, 1));
        assertEquals(RichTextRanges.MAX_INDENT, RichTextRanges.steppedIndent(99, 0));
    }

    @Test
    public void numberedParagraphsCountConsecutively() {
        int n = RichTextRun.LIST_NUMBERED;
        int b = RichTextRun.LIST_BULLET;
        assertArrayEquals(new int[] { 1, 2, 0, 1, 0, 1 },
            RichTextRanges.listNumbers(new int[] { n, n, 0, n, b, n }, n));
        assertArrayEquals(new int[0], RichTextRanges.listNumbers(new int[0], n));
    }

    @Test
    public void emptyListItemRestartsNumberingLikeTheReceiver() {
        int n = RichTextRun.LIST_NUMBERED;
        // "a\n\nb", all numbered: the wire has no entry for the empty line and Zalo restarts after the gap.
        String text = "a\n\nb";
        List<int[]> paragraphs = RichTextRanges.paragraphs(text);
        boolean[] empty = RichTextRanges.emptyParagraphs(text, paragraphs);
        assertArrayEquals(new boolean[] { false, true, false }, empty);
        assertArrayEquals(new int[] { 1, 2, 1 }, RichTextRanges.listNumbers(new int[] { n, n, n }, empty, n));
        // Once text is typed into it, the item continues the list again.
        String filled = "a\nx\nb";
        assertArrayEquals(new int[] { 1, 2, 3 }, RichTextRanges.listNumbers(new int[] { n, n, n },
            RichTextRanges.emptyParagraphs(filled, RichTextRanges.paragraphs(filled)), n));
        // The empty last paragraph (line-style memory) keeps its next number.
        String trailing = "a\nb\n";
        boolean[] trailingEmpty = RichTextRanges.emptyParagraphs(trailing, RichTextRanges.paragraphs(trailing));
        assertArrayEquals(new boolean[] { false, false, true }, trailingEmpty);
        assertArrayEquals(new int[] { 1, 2, 3 }, RichTextRanges.listNumbers(new int[] { n, n, n }, trailingEmpty, n));
    }

    @Test
    public void toggleOfEmptySelectionKeepsRuns() {
        List<Run> original = Collections.singletonList(run(0, 2));
        assertEquals(original, RichTextRanges.toggle(original, 1, 1));
    }
}
