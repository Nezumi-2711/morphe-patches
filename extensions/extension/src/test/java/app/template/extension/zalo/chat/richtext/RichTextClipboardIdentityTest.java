package app.template.extension.zalo.chat.richtext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** The stash belongs to one clip, identified by its ClipDescription timestamp. */
public class RichTextClipboardIdentityTest {
    private static final long UNPINNED = RichTextClipboard.UNPINNED;

    @Test
    public void pinnedClipMatchesOnlyItsTimestamp() {
        assertNull(RichTextClipboard.identityRejection(1_000, 900, true, 1_000));
        // A later plain copy of the same text is another clip: no styles.
        assertEquals("clip replaced (timestamp differs from the pinned clip)",
            RichTextClipboard.identityRejection(1_000, 900, true, 2_000));
    }

    @Test
    public void unpinnedAcceptsFirstNewerEqualClip() {
        // Message copy: Zalo sets the clip after the stash was created.
        assertNull(RichTextClipboard.identityRejection(UNPINNED, 500, true, 600));
        assertNull(RichTextClipboard.identityRejection(UNPINNED, 500, true, 500));
        assertEquals("clip older than the stash", RichTextClipboard.identityRejection(UNPINNED, 500, true, 400));
    }

    @Test
    public void otherTextOrUnknownTimestampIsRejected() {
        assertEquals("clip text differs", RichTextClipboard.identityRejection(1_000, 900, false, 1_000));
        assertEquals("clip text differs", RichTextClipboard.identityRejection(UNPINNED, 500, false, 600));
        assertNotNull(RichTextClipboard.identityRejection(UNPINNED, 500, true, 0));
        assertNotNull(RichTextClipboard.identityRejection(1_000, 900, true, 0));
    }
}
