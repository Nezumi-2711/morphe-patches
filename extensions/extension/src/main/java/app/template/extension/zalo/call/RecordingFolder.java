package app.template.extension.zalo.call;

import android.app.Activity;
import android.app.Fragment;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import app.template.extension.zalo.buglog.NezumiLog;
import app.template.extension.zalo.buglog.Patch;
import app.template.extension.zalo.settings.NezumiPrefs;
import app.template.extension.zalo.shared.ZaloEnv;

/** User-chosen (SAF tree) folder for call recordings; the default is Music/ZaloRecordings. */
public final class RecordingFolder extends Fragment {
    private static final String TAG = "RecordingFolder";
    private static final int PICK_FOLDER = 1044;
    private static final int PERMISSION_FLAGS =
        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
    private Runnable onChanged;

    /** True when {@code stored} (the pref value) names a custom folder. */
    static boolean isCustom(String stored) {
        return stored != null && !stored.trim().isEmpty();
    }

    /**
     * Human-readable path for a tree document id such as {@code primary:Download/Ghi âm Zalo}.
     * Ids that are not plain shared-storage paths use {@code fallbackName} (the folder's display name).
     */
    static String displayPath(String documentId, String fallbackName) {
        String fallback = fallbackName == null || fallbackName.trim().isEmpty() ? null : fallbackName;
        if (documentId == null || documentId.isEmpty()) return fallback;
        int colon = documentId.indexOf(':');
        String root = colon < 0 ? "" : documentId.substring(0, colon);
        String path = trimSlashes(colon < 0 ? documentId : documentId.substring(colon + 1));
        if ("primary".equals(root)) return path.isEmpty() ? fallback : path;
        if ("home".equals(root)) return path.isEmpty() ? "Documents" : "Documents/" + path;
        if ("raw".equals(root)) {
            String prefix = "storage/emulated/0";
            if (path.equals(prefix)) return fallback;
            if (path.startsWith(prefix + "/")) return path.substring(prefix.length() + 1);
        }
        if (fallback != null) return fallback;
        return path.isEmpty() ? documentId : path;
    }

    private static String trimSlashes(String path) {
        int start = 0;
        int end = path.length();
        while (start < end && path.charAt(start) == '/') start++;
        while (end > start && path.charAt(end - 1) == '/') end--;
        return path.substring(start, end);
    }

    /** The chosen tree, or null for the default folder. */
    static Uri tree(Context context) {
        if (context == null) return null;
        String stored = NezumiPrefs.of(context).getString(NezumiPrefs.CALL_RECORDING_FOLDER, null);
        return isCustom(stored) ? Uri.parse(stored) : null;
    }

    public static boolean isCustom(Context context) {
        return tree(context) != null;
    }

    /** Readable path of the chosen folder, or null when the default folder is in use. */
    public static String describe(Context context) {
        Uri tree = tree(context);
        if (tree == null) return null;
        String documentId = null;
        try {
            documentId = DocumentsContract.getTreeDocumentId(tree);
        } catch (RuntimeException failure) {
            NezumiLog.swallowedOnce(Patch.CALL_RECORDING, TAG, "stored folder uri is not a tree", failure);
        }
        String readable = displayPath(documentId, null);
        if (readable != null && documentId != null && (documentId.startsWith("primary:")
                || documentId.startsWith("home:") || documentId.startsWith("raw:"))) {
            return readable;
        }
        String name = documentId == null ? null : displayName(context, folderDocument(tree));
        String path = displayPath(documentId, name);
        return path != null ? path : tree.toString();
    }

    private static String displayName(Context context, Uri document) {
        try (Cursor cursor = context.getContentResolver().query(document,
                new String[] { DocumentsContract.Document.COLUMN_DISPLAY_NAME }, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        } catch (RuntimeException failure) {
            NezumiLog.swallowedOnce(Patch.CALL_RECORDING, TAG, "folder name unavailable", failure);
        }
        return null;
    }

    static Uri folderDocument(Uri tree) {
        return DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
    }

    /** Back to Music/ZaloRecordings: forgets the chosen folder and its access grant. */
    public static void reset(Context context) {
        Uri previous = tree(context);
        NezumiPrefs.of(context).edit().remove(NezumiPrefs.CALL_RECORDING_FOLDER).commit();
        release(context, previous);
    }

    private static void release(Context context, Uri tree) {
        if (tree == null) return;
        try {
            context.getContentResolver().releasePersistableUriPermission(tree, PERMISSION_FLAGS);
        } catch (RuntimeException failure) {
            NezumiLog.swallowed(Patch.CALL_RECORDING, TAG, "releasing the folder permission failed", failure);
        }
    }

    /** MIME type the platform maps {@code extension} to, so providers keep the file name as given. */
    static String mimeFor(String extension, String fallback) {
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        return mime != null ? mime : fallback;
    }

    /** Creates {@code name} in the chosen tree and copies {@code source} into it; a partial document is deleted. */
    static void write(Context context, Uri tree, File source, String name, String mime) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        Uri document = DocumentsContract.createDocument(resolver, folderDocument(tree), mime, name);
        if (document == null) throw new IOException("createDocument failed in " + tree);
        boolean written = false;
        try {
            try (InputStream in = new FileInputStream(source);
                 OutputStream out = resolver.openOutputStream(document, "w")) {
                if (out == null) throw new IOException("cannot open " + document);
                byte[] buffer = new byte[64 * 1024];
                for (int read; (read = in.read(buffer)) > 0; ) out.write(buffer, 0, read);
            }
            written = true;
        } finally {
            if (!written) {
                try {
                    DocumentsContract.deleteDocument(resolver, document);
                } catch (Exception failure) {
                    NezumiLog.swallowed(Patch.CALL_RECORDING, TAG, "deleting the partial recording failed", failure);
                }
            }
        }
    }

    public static boolean pick(Context context, Runnable onChanged) {
        while (context instanceof ContextWrapper && !(context instanceof Activity)) {
            Context base = ((ContextWrapper) context).getBaseContext();
            if (base == context) return false;
            context = base;
        }
        if (!(context instanceof Activity)) return false;
        Activity activity = (Activity) context;
        if (activity.isFinishing() || (Build.VERSION.SDK_INT >= 26
            && activity.getFragmentManager().isStateSaved())) return false;
        RecordingFolder fragment = new RecordingFolder();
        fragment.onChanged = onChanged;
        activity.getFragmentManager().beginTransaction().add(fragment, "nezumi-recording-folder-picker").commit();
        return true;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(PERMISSION_FLAGS | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, PICK_FOLDER);
        } catch (ActivityNotFoundException failure) {
            NezumiLog.w(Patch.CALL_RECORDING, TAG, "no folder picker", failure);
            boolean vietnamese = ZaloEnv.isVietnamese(Patch.CALL_RECORDING, getActivity());
            Toast.makeText(getActivity(), vietnamese ? "Không thể mở trình chọn thư mục" : "Cannot open folder picker",
                Toast.LENGTH_SHORT).show();
            getFragmentManager().beginTransaction().remove(this).commit();
        }
    }

    @Override public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_FOLDER) return;
        Activity activity = getActivity();
        if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            boolean vietnamese = ZaloEnv.isVietnamese(Patch.CALL_RECORDING, activity);
            Uri chosen = data.getData();
            try {
                activity.getContentResolver().takePersistableUriPermission(chosen, PERMISSION_FLAGS);
                Uri previous = tree(activity);
                if (!NezumiPrefs.of(activity).edit()
                        .putString(NezumiPrefs.CALL_RECORDING_FOLDER, chosen.toString()).commit()) {
                    throw new IOException("folder not saved");
                }
                if (previous != null && !previous.equals(chosen)) release(activity, previous);
                if (onChanged != null) onChanged.run();
                String folder = describe(activity);
                Toast.makeText(activity, (vietnamese ? "Bản ghi mới sẽ lưu vào: " : "New recordings will be saved to: ")
                    + folder, Toast.LENGTH_LONG).show();
            } catch (Exception failure) {
                NezumiLog.e(Patch.CALL_RECORDING, TAG, "chosen folder could not be used", failure);
                Toast.makeText(activity, vietnamese ? "Không dùng được thư mục này" : "Cannot use this folder",
                    Toast.LENGTH_SHORT).show();
            }
        }
        getFragmentManager().beginTransaction().remove(this).commit();
    }
}
