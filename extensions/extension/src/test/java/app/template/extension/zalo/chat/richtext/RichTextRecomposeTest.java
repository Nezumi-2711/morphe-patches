package app.template.extension.zalo.chat.richtext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import app.template.extension.zalo.chat.richtext.RichTextRanges.Run;

/** IME recomposition (Telex/VNI diacritics, backspace in a composing word) keeps styles. */
public class RichTextRecomposeTest {
    private static final int NONE = RichTextRanges.NONE;

    private static Run run(int start, int end) {
        return new Run(start, end, 1);
    }

    private static List<Run> runs(Run... runs) {
        return new ArrayList<>(Arrays.asList(runs));
    }

    /**
     * Mirrors RichTextEditor.applyInsertedStyle for one on/off kind: {@code before} are the kind's runs
     * on the whole text before {@code [start, start + old.length())} is replaced by {@code inserted}.
     */
    private static final class Edit {
        RichTextRecompose.Plan plan;
        List<Run> after;
    }

    private static Edit edit(List<Run> before, int start, String old, String inserted, int caret, int pendingAnchor,
            boolean typingIsPending, int typingValue, int replacedValue) {
        int oldEnd = start + old.length();
        int shift = inserted.length() - old.length();
        // The replaced chars' runs, in old indices (what beforeTextChanged captures).
        List<Run> oldRuns = RichTextRanges.assign(RichTextRanges.assign(before, 0, start, true, 0),
            oldEnd, Integer.MAX_VALUE, true, 0);
        // What Android leaves after the replace: outside runs kept or shifted, the replaced range unstyled.
        List<Run> current = new ArrayList<>();
        for (Run run : before) {
            if (run.end <= start) current.add(run);
            else if (run.start >= oldEnd) current.add(new Run(run.start + shift, run.end + shift, run.value));
            else {
                if (run.start < start) current.add(new Run(run.start, start, run.value));
                if (run.end > oldEnd) current.add(new Run(oldEnd + shift, run.end + shift, run.value));
            }
        }
        Edit result = new Edit();
        result.plan = RichTextRecompose.plan(old, inserted, start, caret, pendingAnchor, typingIsPending);
        int value = RichTextRecompose.middleValue(result.plan, typingValue, oldRuns, replacedValue);
        result.after = RichTextRecompose.remap(RichTextRanges.normalize(current), oldRuns, start, old.length(),
            inserted.length(), result.plan.split, value);
        return result;
    }

    @Test
    public void telexDiacriticTakesPendingBold() {
        // Plain "vie", tap B (pending bold at 3), type "e": the IME replaces "vie" with "viê".
        Edit edit = edit(runs(), 0, "vie", "viê", 3, 3, true, 1, NONE);
        RichTextRecompose.Split split = edit.plan.split;
        assertEquals(2, split.prefix);
        assertEquals(0, split.suffix);
        assertEquals(1, split.oldMiddle);
        assertEquals(1, split.newMiddle);
        assertEquals("replace-middle", split.kind());
        assertTrue(edit.plan.consumesPending);
        assertEquals(runs(run(2, 3)), edit.after);
    }

    @Test
    public void toneMarkKeepsEachPartsStyle() {
        // "ch" plain, "ao" bold; the tone key turns "chao" into "chào" with the caret at the end.
        Edit edit = edit(runs(run(2, 4)), 0, "chao", "chào", 4, -1, false, 1, NONE);
        assertEquals(2, edit.plan.split.prefix);
        assertEquals(1, edit.plan.split.suffix);
        assertFalse(edit.plan.atCaret);
        assertEquals(runs(run(2, 4)), edit.after);

        // Reverse: "ch" bold, "ao" plain: still only "ch" bold.
        Edit reverse = edit(runs(run(0, 2)), 0, "chao", "chào", 4, -1, false, NONE, 1);
        assertEquals(runs(run(0, 2)), reverse.after);
    }

    @Test
    public void toneMarkBeforeThePendingAnchorKeepsPending() {
        // "chao" plain, tap B at 4, tone key: "chào". The middle is not at the anchor: pending stays.
        Edit edit = edit(runs(), 0, "chao", "chào", 4, 4, true, 1, NONE);
        assertFalse(edit.plan.consumesPending);
        assertEquals(runs(), edit.after);
        assertEquals(4, RichTextRecompose.shiftedAnchor(edit.plan, 0, 4));
    }

    @Test
    public void composingWordInsideTextKeepsOutsideRuns() {
        // "ab chao xy" with "ao" bold and "xy" bold; the word at 3 is recomposed.
        Edit edit = edit(runs(run(5, 7), run(8, 10)), 3, "chao", "chào", 7, -1, false, 1, NONE);
        assertEquals(runs(run(5, 7), run(8, 10)), edit.after);
    }

    @Test
    public void backspaceInComposingWordKeepsStyles() {
        // "hello" with "lo" bold; backspace inside the composing word: the IME replaces it with "hell".
        Edit edit = edit(runs(run(3, 5)), 0, "hello", "hell", 5, 5, true, 1, NONE);
        assertEquals(4, edit.plan.split.prefix);
        assertEquals(0, edit.plan.split.newMiddle);
        assertFalse(edit.plan.consumesPending);
        assertEquals(runs(run(3, 4)), edit.after);
    }

    @Test
    public void pureExtensionKeepsCurrentBehavior() {
        // "hel" bold, extended to "hello": the old chars keep bold, the tail gets the typing style.
        Edit plainTail = edit(runs(run(0, 3)), 0, "hel", "hello", 3, 3, true, NONE, 1);
        assertEquals("extend", plainTail.plan.split.kind());
        assertTrue(plainTail.plan.consumesPending);
        assertEquals(runs(run(0, 3)), plainTail.after);

        Edit boldTail = edit(runs(run(0, 3)), 0, "hel", "hello", 3, -1, false, 1, 1);
        assertFalse(boldTail.plan.consumesPending);
        assertEquals(runs(run(0, 5)), boldTail.after);

        // A plain insertion (nothing replaced) at the caret takes the typing style.
        Edit insert = edit(runs(), 2, "", "x", 2, 2, true, 1, 1);
        assertEquals("insert", insert.plan.split.kind());
        assertTrue(insert.plan.consumesPending);
        assertEquals(runs(run(2, 3)), insert.after);
    }

    @Test
    public void suffixShiftsByTheLengthDifference() {
        Edit edit = edit(runs(run(3, 6)), 0, "abcxyz", "abQQxyz", 6, -1, false, NONE, NONE);
        assertEquals(2, edit.plan.split.prefix);
        assertEquals(3, edit.plan.split.suffix);
        assertEquals(runs(run(4, 7)), edit.after);
        assertEquals(7, RichTextRecompose.shiftedAnchor(edit.plan, 0, 6));
    }

    @Test
    public void surrogatePairIsNotSplit() {
        RichTextRecompose.Split split = RichTextRecompose.split("a😀", "a😁");
        assertEquals(1, split.prefix);
        assertEquals(0, split.suffix);
        assertEquals(2, split.newMiddle);
    }

    @Test
    public void prefixAndSuffixNeverOverlap() {
        RichTextRecompose.Split split = RichTextRecompose.split("aa", "aaa");
        assertEquals(2, split.prefix);
        assertEquals(0, split.suffix);
        assertEquals(1, split.newMiddle);
        assertEquals(Collections.emptyList(), RichTextRecompose.remap(runs(), runs(), 0, 2, 3, split, NONE));
    }
}
