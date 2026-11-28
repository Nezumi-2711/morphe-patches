package app.template.extension.zalo.chat.richtext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import app.template.extension.zalo.chat.richtext.RichTextRun.Kind;

public class RichTextStyleAndPathTest {
    @Test
    public void pendingStyleToggles() {
        RichTextStyle bold = RichTextStyle.PLAIN.toggled(Kind.BOLD);
        assertTrue(bold.bold);
        assertFalse(bold.italic);
        assertEquals(RichTextStyle.PLAIN, bold.toggled(Kind.BOLD));
        assertEquals(18, bold.withSize(18).size);
        assertEquals(RichTextStyle.NO_COLOR, bold.withColor(0xDB342E).withColor(RichTextStyle.NO_COLOR).color);
    }

    @Test
    public void runValuesForStyle() {
        RichTextStyle style = RichTextStyle.PLAIN.toggled(Kind.ITALIC).withSize(20).withColor(0x15A85F);
        assertEquals(RichTextRanges.NONE, style.valueOf(Kind.BOLD));
        assertEquals(1, style.valueOf(Kind.ITALIC));
        assertEquals(20, style.valueOf(Kind.SIZE));
        assertEquals(0x15A85F, style.valueOf(Kind.COLOR));
        assertEquals(RichTextRanges.NONE, RichTextStyle.PLAIN.valueOf(Kind.SIZE));
        assertEquals(RichTextRanges.NONE, RichTextStyle.PLAIN.valueOf(Kind.COLOR));
    }

    /** Records commands as text, e.g. {@code M1,2 L3,4 Z}. */
    private static final class Recorder implements SvgPath.Sink {
        final List<String> commands = new ArrayList<>();

        /** Rounded to 3 decimals, without trailing zeros, so float sums compare exactly. */
        private static String f(float value) {
            return java.math.BigDecimal.valueOf(Math.round(value * 1000.0), 3).stripTrailingZeros().toPlainString();
        }

        @Override public void moveTo(float x, float y) { commands.add("M" + f(x) + "," + f(y)); }
        @Override public void lineTo(float x, float y) { commands.add("L" + f(x) + "," + f(y)); }
        @Override public void cubicTo(float x1, float y1, float x2, float y2, float x, float y) {
            commands.add("C" + f(x1) + "," + f(y1) + " " + f(x2) + "," + f(y2) + " " + f(x) + "," + f(y));
        }
        @Override public void quadTo(float x1, float y1, float x, float y) {
            commands.add("Q" + f(x1) + "," + f(y1) + " " + f(x) + "," + f(y));
        }
        @Override public void close() { commands.add("Z"); }
    }

    private static String parse(String data) {
        Recorder recorder = new Recorder();
        SvgPath.parse(data, recorder);
        return String.join(" ", recorder.commands);
    }

    @Test
    public void parsesAbsoluteAndRelativeLines() {
        // format_italic
        assertEquals("M10,4 L10,7 L12.21,7 L8.79,15 L6,15 L6,18 L14,18 L14,15 L11.79,15 L15.21,7 L18,7 L18,4 Z",
            parse("M10,4v3h2.21l-3.42,8H6v3h8v-3h-2.21l3.42,-8H18V4z"));
    }

    @Test
    public void implicitLinesAfterMoveAndRepeatedCommands() {
        assertEquals("M1,1 L2,2 L3,3", parse("M1 1 2 2 3 3"));
        assertEquals("M1,1 L2,2 L4,4", parse("m1,1 1,1 2,2"));
        assertEquals("M0,0 L1,0 L2,0", parse("M0,0h1 1"));
    }

    @Test
    public void smoothCubicReflectsTheLastControlPoint() {
        assertEquals("M0,0 C0,1 1,2 2,2 C3,2 4,1 4,0", parse("M0,0C0,1 1,2 2,2S4,1 4,0"));
        // Relative smooth cubic.
        assertEquals("M0,0 C0,1 1,2 2,2 C3,2 4,1 4,0", parse("M0,0c0,1 1,2 2,2s2,-1 2,-2"));
    }

    @Test
    public void closeReturnsToSubpathStart() {
        assertEquals("M2,2 L4,2 Z M3,3", parse("M2,2h2zm1,1"));
    }

    @Test
    public void compactNumbers() {
        assertEquals("M0.5,0.5 L-1,2", parse("M.5.5L-1,2"));
        assertEquals("M10,0", parse("M1e1,0"));
    }

    @Test
    public void everyToolbarIconParses() throws IllegalAccessException {
        int count = 0;
        for (Field field : RichTextIcons.class.getDeclaredFields()) {
            if (field.getType() != String.class || !Modifier.isStatic(field.getModifiers())) continue;
            String data = (String) field.get(null);
            Recorder recorder = new Recorder();
            SvgPath.parse(data, recorder);
            assertTrue(field.getName(), recorder.commands.size() > 3);
            assertEquals(field.getName(), "Z", recorder.commands.get(recorder.commands.size() - 1));
            count++;
        }
        assertEquals(11, count);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMissingCommand() {
        parse("1,2");
    }
}
