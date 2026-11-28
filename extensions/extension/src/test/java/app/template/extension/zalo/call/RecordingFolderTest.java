package app.template.extension.zalo.call;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RecordingFolderTest {
    @Test
    public void primaryVolumeShowsItsPath() {
        assertEquals("Download/Ghi âm Zalo", RecordingFolder.displayPath("primary:Download/Ghi âm Zalo", "Ghi âm Zalo"));
        assertEquals("Music", RecordingFolder.displayPath("primary:Music/", null));
    }

    @Test
    public void primaryRootUsesTheDisplayName() {
        assertEquals("Internal storage", RecordingFolder.displayPath("primary:", "Internal storage"));
        assertNull(RecordingFolder.displayPath("primary:", null));
    }

    @Test
    public void otherVolumesUseTheDisplayName() {
        assertEquals("Recordings", RecordingFolder.displayPath("1A2B-3C4D:Recordings", "Recordings"));
        assertEquals("Recordings", RecordingFolder.displayPath("1A2B-3C4D:Recordings", null));
        assertEquals("My folder", RecordingFolder.displayPath("acc=1;doc=xyz", "My folder"));
        assertEquals("acc=1;doc=xyz", RecordingFolder.displayPath("acc=1;doc=xyz", " "));
    }

    @Test
    public void documentsAndRawPathsAreReadable() {
        assertEquals("Documents/Calls", RecordingFolder.displayPath("home:Calls", null));
        assertEquals("Documents", RecordingFolder.displayPath("home:", null));
        assertEquals("Download/Calls", RecordingFolder.displayPath("raw:/storage/emulated/0/Download/Calls", "Calls"));
        assertEquals("Calls", RecordingFolder.displayPath("raw:/storage/1A2B-3C4D/Calls", "Calls"));
    }

    @Test
    public void missingIdFallsBack() {
        assertNull(RecordingFolder.displayPath(null, null));
        assertEquals("Name", RecordingFolder.displayPath("", "Name"));
    }

    @Test
    public void onlyANonEmptyUriSelectsACustomFolder() {
        assertFalse(RecordingFolder.isCustom((String) null));
        assertFalse(RecordingFolder.isCustom(""));
        assertFalse(RecordingFolder.isCustom("  "));
        assertTrue(RecordingFolder.isCustom("content://com.android.externalstorage.documents/tree/primary%3AMusic"));
    }
}
