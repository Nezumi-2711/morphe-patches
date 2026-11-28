package app.template.extension.zalo.buglog;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import app.template.extension.zalo.chat.richtext.RichTextConfig;
import app.template.extension.zalo.chat.richtext.RichTextLog;
import app.template.extension.zalo.chat.richtext.RichTextRun;
import app.template.extension.zalo.chat.richtext.RichTextRun.Kind;

/** The rich-text diagnostic lines carry no personal data, so the redactor must leave them intact. */
public class RichTextLogRedactionTest {
    private static void assertUntouched(String line) {
        assertEquals(line, LogRedactor.redact(line));
    }

    @Test
    public void sampleLinesPassTheRedactorUnchanged() {
        List<RichTextRun> runs = Arrays.asList(new RichTextRun(0, 5, Kind.BOLD), new RichTextRun(0, 12, Kind.LIST, 1),
            new RichTextRun(6, 12, Kind.COLOR, 0x050a19), new RichTextRun(6, 12, Kind.INDENT, 2));
        String params = "{\"styles\":[{\"start\":0,\"len\":5,\"st\":\"lst_1,ind_20\"},{\"start\":0,\"len\":5,\"st\":\"b\"},"
            + "{\"start\":6,\"len\":6,\"st\":\"c_050a19\"}],\"ver\":0}";
        assertUntouched(RichTextLog.SEND + "attached: textLen=12 len=12 runs=" + RichTextLog.kindCounts(runs)
            + " mentions=1 remap=ok(4->4) entries=" + RichTextLog.paramsEntries(params) + " paramsLen=" + params.length()
            + " st=" + RichTextLog.paramsTokens(params) + " trim=0/0 age=3ms");
        assertUntouched(RichTextLog.SEND + "mention remap did not reproduce the sent text, sending plain: originalLen=18"
            + " expectedLen=26 actualLen=27 mentions=1 {[3,8)w13d5} runs={" + RichTextLog.runShapes(runs, 12) + "}"
            + " fp original=" + RichTextLog.fingerprint("abc") + " flattened=" + RichTextLog.fingerprint("abcd"));
        assertUntouched(RichTextLog.SEND + "styles dropped or changed: color-not-whitelisted=1 {c_123456[1,3)}"
            + " size-clamped=1 {f_30[1,6)->f_20} textLen=123456 trim=1/2");
        assertUntouched(RichTextLog.CLIP + "paste: applied runs=3(b=1,c=1,lst=1) len=40 at=12345678 lineOverrides=2");
        assertUntouched(RichTextLog.UI + "action COLOR value=c_000000 target=selection[1200,1205) len=5 changed=true");
        assertUntouched(RichTextLog.UI + "input bar: toolbar installed composer=id chatinput_text inputRowIndex=1/4 open=false"
            + " attached=false");
        RichTextConfig config = RichTextConfig.DEFAULT;
        assertUntouched(RichTextLog.CONFIG + "size=" + config.sizeMin + "(def).." + config.sizeMax + "(def) colors="
            + config.colors.size() + "(def) [" + String.join(",", config.colors) + "] source=default values");
    }
}
