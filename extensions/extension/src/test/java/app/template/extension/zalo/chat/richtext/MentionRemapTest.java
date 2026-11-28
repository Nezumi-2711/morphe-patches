package app.template.extension.zalo.chat.richtext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import app.template.extension.zalo.chat.richtext.MentionRemap.Mention;
import app.template.extension.zalo.chat.richtext.RichTextRun.Kind;

public class MentionRemapTest {
    private static RichTextRun run(int start, int end, Kind kind) {
        return new RichTextRun(start, end, kind);
    }

    private static String ranges(List<RichTextRun> runs) {
        StringBuilder out = new StringBuilder();
        for (RichTextRun run : runs) out.append(run.kind).append('[').append(run.start).append(',').append(run.end).append(") ");
        return out.toString().trim();
    }

    @Test
    public void noMentionsKeepsOffsetsWhenTextMatches() {
        List<RichTextRun> runs = Collections.singletonList(run(0, 5, Kind.BOLD));
        assertEquals("BOLD[0,5)", ranges(MentionRemap.remap("hello", runs, Collections.emptyList(), "hello")));
        assertNull(MentionRemap.remap("hello", runs, Collections.emptyList(), "hello!"));
    }

    @Test
    public void runsAfterAMentionShiftByTheWireLengthChange() {
        // Composer "Hi @An, ok": display "@An" (3 chars) at 3..6, wire "@Anh Tuan" (9 chars).
        String original = "Hi @An, ok";
        String sent = "Hi @Anh Tuan, ok";
        List<Mention> mentions = Collections.singletonList(new Mention(3, 6, "@Anh Tuan", "@An"));
        List<RichTextRun> runs = Arrays.asList(run(0, 2, Kind.BOLD), run(8, 10, Kind.ITALIC), run(3, 6, Kind.UNDERLINE));
        assertEquals("BOLD[0,2) ITALIC[14,16) UNDERLINE[3,12)", ranges(MentionRemap.remap(original, runs, mentions, sent)));
    }

    @Test
    public void edgesInsideAMentionSnapOutwardToCoverIt() {
        String original = "Hi @An, ok";
        String sent = "Hi @Anh Tuan, ok";
        List<Mention> mentions = Collections.singletonList(new Mention(3, 6, "@Anh Tuan", "@An"));
        // Starts inside the mention -> mention start; ends inside -> mention end.
        List<RichTextRun> runs = Arrays.asList(run(4, 9, Kind.BOLD), run(0, 5, Kind.ITALIC));
        assertEquals("BOLD[3,15) ITALIC[0,12)", ranges(MentionRemap.remap(original, runs, mentions, sent)));
    }

    @Test
    public void severalMentionsReplayZalosOrderAndShifts() {
        // getSpans order differs from text order; Zalo sorts by start.
        String original = "@B and @C!";
        String sent = "@Bob and @Carol!";
        List<Mention> mentions = Arrays.asList(new Mention(7, 9, "@Carol", "@C"), new Mention(0, 2, "@Bob", "@B"));
        List<RichTextRun> runs = Arrays.asList(run(3, 6, Kind.BOLD), run(9, 10, Kind.STRIKE), run(0, 10, Kind.UNDERLINE));
        assertEquals("BOLD[5,8) STRIKE[15,16) UNDERLINE[0,16)", ranges(MentionRemap.remap(original, runs, mentions, sent)));
    }

    @Test
    public void emojiBeforeAMentionCountsAsTwoUnits() {
        String original = "🔥 @A x";
        String sent = "🔥 @Alice x";
        List<Mention> mentions = Collections.singletonList(new Mention(3, 5, "@Alice", "@A"));
        List<RichTextRun> runs = Arrays.asList(run(0, 2, Kind.BOLD), run(6, 7, Kind.ITALIC));
        assertEquals("BOLD[0,2) ITALIC[10,11)", ranges(MentionRemap.remap(original, runs, mentions, sent)));
    }

    @Test
    public void mismatchWithTheRealResultDropsTheStyles() {
        List<Mention> mentions = Collections.singletonList(new Mention(3, 6, "@Anh Tuan", "@An"));
        assertNull(MentionRemap.remap("Hi @An, ok", Collections.singletonList(run(0, 2, Kind.BOLD)), mentions, "Hi @An, ok"));
        // Broken mention data (Zalo would throw and return a partial result).
        assertNull(MentionRemap.remap("Hi", Collections.singletonList(run(0, 2, Kind.BOLD)),
            Collections.singletonList(new Mention(5, 6, "@X", "@X")), "Hi"));
        assertNull(MentionRemap.remap("Hi", Collections.singletonList(run(0, 2, Kind.BOLD)),
            Collections.singletonList(new Mention(0, 1, null, "@X")), "Hi"));
    }

    @Test
    public void remapThenSerializeWithTrimAndMention() {
        String original = "  @An xin chao  ";
        String sent = "  @Anh Tuan xin chao  ";
        List<Mention> mentions = Collections.singletonList(new Mention(2, 5, "@Anh Tuan", "@An"));
        // Bold over "xin" and part of the mention; color over "chao".
        List<RichTextRun> runs = Arrays.asList(run(4, 9, Kind.BOLD), new RichTextRun(10, 14, Kind.COLOR, 0x15A85F));
        List<RichTextRun> mapped = MentionRemap.remap(original, runs, mentions, sent);
        assertEquals("{\"styles\":[{\"start\":0,\"len\":13,\"st\":\"b\"},{\"start\":14,\"len\":4,\"st\":\"c_15a85f\"}],\"ver\":0}",
            RichTextSerializer.serializeForSend(sent, mapped, RichTextConfig.DEFAULT));
    }
}
