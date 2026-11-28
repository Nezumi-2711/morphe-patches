package app.template.extension.zalo.chat.richtext;

import android.text.Editable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

import app.template.extension.zalo.buglog.NezumiLog;
import app.template.extension.zalo.buglog.Patch;
import app.template.extension.zalo.chat.ChatOptions;
import app.template.extension.zalo.shared.ZaloEnv;

/**
 * Send core of the "Rich text formatting" patch.
 *
 * <p>Zalo's composer send flattens the composer text (losing spans) and then builds the outgoing
 * message from the flattened string, synchronously on the same thread. The patch calls
 * {@link #beforeFlatten} at the start of the flattening, {@link #afterFlatten} with its result, and
 * {@link #buildRtfContent} when the message is built. The styles travel through a single slot keyed
 * by the exact text and consumed by the next message build, so any other text send (link cards,
 * multi-text, shares) does not match and goes out plain.
 *
 * <p>Logging: each step adds to a short trace (lengths, counts, offsets and style tokens only) that
 * {@link #buildRtfContent} writes as one {@code [rtf/send]} line per send; mismatches and dropped
 * styles get an extra warning with the offsets needed to reproduce them.
 */
public final class RichTextSend {
    private RichTextSend() { }

    private static final String TAG = "RichTextSend";
    /** Zalo's mention span (stable class name). Fields: b start, c end, e wire text, f display text. */
    static final String MENTION_SPAN = "com.zing.zalo.social.presentation.callback_span.MentionSpan";
    /** How long a captured style set stays valid for the message build that follows it. */
    private static final long SLOT_TTL_NANOS = 5_000_000_000L;

    private static final class Capture {
        final String original;
        final List<RichTextRun> runs;
        final List<MentionRemap.Mention> mentions;
        final Thread thread = Thread.currentThread();

        Capture(String original, List<RichTextRun> runs, List<MentionRemap.Mention> mentions) {
            this.original = original;
            this.runs = runs;
            this.mentions = mentions;
        }
    }

    private static final class Slot {
        final String text;
        final List<RichTextRun> runs;
        final Thread thread = Thread.currentThread();
        final long createdAt = System.nanoTime();

        Slot(String text, List<RichTextRun> runs) {
            this.text = text;
            this.runs = runs;
        }
    }

    private static Capture capture;
    private static Slot slot;
    /** What the last flattening found (log only): consumed by the next build if it is recent. */
    private static String trace;
    private static long traceAt;

    private static Class<?> mentionClass;
    private static Field mentionStart;
    private static Field mentionEnd;
    private static Field mentionWire;
    private static Field mentionDisplay;

    /** True when the Nezumi toggle is on; also the toolbar's visibility switch. */
    public static boolean isEnabled() {
        return ChatOptions.isRichTextEnabled(ZaloEnv.appContext(Patch.RICH_TEXT_FORMATTING));
    }

    private static synchronized void setTrace(String value) {
        trace = value;
        traceAt = System.nanoTime();
    }

    private static synchronized void appendTrace(String value) {
        trace = trace == null ? value : trace + " " + value;
    }

    /**
     * Hook: start of the composer flattening, before any span is lost. Always drops the previous
     * capture and slot first: the flattening returns early ({@code ""}) for a null Editable without
     * reaching {@link #afterFlatten}, so a null composer must leave nothing behind.
     */
    public static void beforeFlatten(Editable composer) {
        synchronized (RichTextSend.class) {
            capture = null;
            slot = null;
        }
        setTrace(null);
        if (composer == null) {
            setTrace("editable=null");
            NezumiLog.i(Patch.RICH_TEXT_FORMATTING, TAG, RichTextLog.SEND + "flatten: editable=null, nothing captured");
            return;
        }
        try {
            List<RichTextRun> runs = RichTextSpans.collectRuns(composer);
            String found = "len=" + composer.length() + " runs=" + RichTextLog.kindCounts(runs);
            if (runs.isEmpty()) {
                setTrace(found);
                return;
            }
            if (!isEnabled()) {
                setTrace(found + " toggle=off");
                return;
            }
            Capture taken = new Capture(composer.toString(), runs, mentionsOf(composer));
            setTrace(found + " mentions=" + taken.mentions.size());
            synchronized (RichTextSend.class) {
                capture = taken;
            }
        } catch (Throwable failure) {
            setTrace("capture=exception(" + failure.getClass().getSimpleName() + ")");
            NezumiLog.swallowedOnce(Patch.RICH_TEXT_FORMATTING, "RichTextSend", "capturing composer styles failed, sending plain", failure);
        }
    }

    /** Hook: the flattening's result, the exact text the composer is about to send. */
    public static void afterFlatten(String flattened) {
        Capture taken;
        synchronized (RichTextSend.class) {
            taken = capture;
            capture = null;
        }
        try {
            if (taken == null) return;
            if (flattened == null) {
                appendTrace("flattened=null");
                NezumiLog.w(Patch.RICH_TEXT_FORMATTING, TAG, RichTextLog.SEND + "flatten returned null, sending plain");
                return;
            }
            if (taken.thread != Thread.currentThread()) {
                appendTrace("remap=skipped(thread)");
                NezumiLog.w(Patch.RICH_TEXT_FORMATTING, TAG, RichTextLog.SEND + "flatten result on another thread (captured on "
                    + taken.thread.getName() + "), sending plain");
                return;
            }
            List<RichTextRun> runs = MentionRemap.remap(taken.original, taken.runs, taken.mentions, flattened);
            if (runs == null) {
                appendTrace("remap=mismatch");
                NezumiLog.w(Patch.RICH_TEXT_FORMATTING, TAG, RichTextLog.SEND
                    + "mention remap did not reproduce the sent text, sending plain: originalLen=" + taken.original.length()
                    + " expectedLen=" + RichTextLog.expectedFlattenLength(taken.original.length(), taken.mentions)
                    + " actualLen=" + flattened.length()
                    + " mentions=" + taken.mentions.size() + " {" + RichTextLog.mentionShapes(taken.mentions, RichTextLog.MAX_SHAPES) + "}"
                    + " runs={" + RichTextLog.runShapes(taken.runs, RichTextLog.MAX_SHAPES) + "}"
                    + " fp original=" + RichTextLog.fingerprint(taken.original) + " flattened=" + RichTextLog.fingerprint(flattened));
                return;
            }
            appendTrace("remap=ok(" + taken.runs.size() + "->" + runs.size() + ")");
            if (runs.size() < taken.runs.size()) {
                NezumiLog.w(Patch.RICH_TEXT_FORMATTING, TAG, RichTextLog.SEND + "remap dropped " + (taken.runs.size() - runs.size())
                    + " run(s) collapsed by mentions: before={" + RichTextLog.runShapes(taken.runs, RichTextLog.MAX_SHAPES)
                    + "} after={" + RichTextLog.runShapes(runs, RichTextLog.MAX_SHAPES)
                    + "} mentions={" + RichTextLog.mentionShapes(taken.mentions, RichTextLog.MAX_SHAPES) + "}");
            }
            if (runs.isEmpty()) return;
            Slot filled = new Slot(flattened, runs);
            synchronized (RichTextSend.class) {
                slot = filled;
            }
        } catch (Throwable failure) {
            appendTrace("remap=exception(" + failure.getClass().getSimpleName() + ")");
            NezumiLog.swallowedOnce(Patch.RICH_TEXT_FORMATTING, "RichTextSend", "remapping composer styles failed, sending plain", failure);
        }
    }

    /**
     * Hook: the outgoing text message was just built from {@code text}. Returns the rich-text
     * content JSON ({@code action/params/title}) for it, or null to send it plain. Always consumes
     * the slot.
     */
    public static JSONObject buildRtfContent(String text) {
        Slot taken;
        String found;
        synchronized (RichTextSend.class) {
            taken = slot;
            slot = null;
            // A trace older than the slot TTL belongs to a flattening that never sent (draft save, ...).
            found = trace != null && System.nanoTime() - traceAt <= SLOT_TTL_NANOS ? trace : null;
            trace = null;
        }
        String head = "textLen=" + (text == null ? "null" : String.valueOf(text.length()))
            + " " + (found == null ? "flatten=none" : found);
        try {
            if (taken == null) {
                plain(head, found == null ? "no capture (not a composer send)" : "no capture", false);
                return null;
            }
            if (text == null || !text.equals(taken.text)) {
                plain(head, "text mismatch (built=" + RichTextLog.fingerprint(text) + " captured="
                    + RichTextLog.fingerprint(taken.text) + " sameAfterTrim="
                    + (text != null && text.trim().equals(taken.text.trim())) + ")", true);
                return null;
            }
            if (taken.thread != Thread.currentThread()) {
                plain(head, "thread mismatch (captured on " + taken.thread.getName() + ")", true);
                return null;
            }
            long ageMs = (System.nanoTime() - taken.createdAt) / 1_000_000L;
            if (System.nanoTime() - taken.createdAt > SLOT_TTL_NANOS) {
                plain(head, "stale capture (" + ageMs + "ms > 5000ms)", true);
                return null;
            }
            if (!isEnabled()) {
                plain(head, "toggle off", false);
                return null;
            }
            RichTextConfig config = RichTextConfig.current();
            String params = RichTextSerializer.serializeForSend(text, taken.runs, config);
            String dropped = RichTextLog.droppedStyles(text, taken.runs, config);
            if (dropped != null) {
                NezumiLog.w(Patch.RICH_TEXT_FORMATTING, TAG, RichTextLog.SEND + "styles dropped or changed: " + dropped
                    + " textLen=" + text.length() + " trim=" + RichTextLog.trimShift(text)
                    + " runs={" + RichTextLog.runShapes(taken.runs, RichTextLog.MAX_SHAPES) + "}");
            }
            if (params == null) {
                plain(head, "empty styles (trim=" + RichTextLog.trimShift(text) + " runs={"
                    + RichTextLog.runShapes(taken.runs, RichTextLog.MAX_SHAPES) + "})", true);
                return null;
            }
            JSONObject content = new JSONObject();
            content.put("title", text.trim());
            content.put("action", "rtf");
            content.put("params", params);
            NezumiLog.i(Patch.RICH_TEXT_FORMATTING, TAG, RichTextLog.SEND + "attached: " + head
                + " entries=" + RichTextLog.paramsEntries(params) + " paramsLen=" + params.length()
                + " st=" + RichTextLog.paramsTokens(params) + " trim=" + RichTextLog.trimShift(text) + " age=" + ageMs + "ms");
            return content;
        } catch (Throwable failure) {
            plain(head, "exception (" + failure.getClass().getSimpleName() + ")", true);
            NezumiLog.swallowedOnce(Patch.RICH_TEXT_FORMATTING, "RichTextSend", "building rtf content failed, sending plain", failure);
            return null;
        }
    }

    /** The one summary line for a send that goes out plain. */
    private static void plain(String head, String reason, boolean unexpected) {
        String line = RichTextLog.SEND + "plain: " + reason + " " + head;
        if (unexpected) NezumiLog.w(Patch.RICH_TEXT_FORMATTING, TAG, line);
        else NezumiLog.i(Patch.RICH_TEXT_FORMATTING, TAG, line);
    }

    /** Zalo's mention spans in {@code composer}, in the same getSpans order the flattening uses. */
    private static List<MentionRemap.Mention> mentionsOf(Editable composer) throws ReflectiveOperationException {
        Class<?> type = mentionClass;
        if (type == null) {
            type = Class.forName(MENTION_SPAN);
            mentionStart = type.getField("b");
            mentionEnd = type.getField("c");
            mentionWire = type.getField("e");
            mentionDisplay = type.getField("f");
            mentionClass = type;
        }
        Object[] spans = composer.getSpans(0, composer.length(), type);
        List<MentionRemap.Mention> mentions = new ArrayList<>(spans.length);
        for (Object span : spans) {
            mentions.add(new MentionRemap.Mention(mentionStart.getInt(span), mentionEnd.getInt(span),
                (String) mentionWire.get(span), (String) mentionDisplay.get(span)));
        }
        return mentions;
    }
}
