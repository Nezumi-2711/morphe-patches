package app.template.extension.extension;

import android.animation.ValueAnimator;
import android.app.Dialog;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Constructor;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class NezumiSettings {
    private static final String PREFS = "nezumi_settings";
    private static final String REACTION = "unlock_icon_react";
    private static final String HIDE_TYPING = "hide_text_typing";
    private static final String HIDE_READ_RECEIPTS = "hide_read_receipts";
    private static final String HIDE_DELIVERY_RECEIPTS = "hide_delivery_receipts";
    private static final String HIDE_MEDIA_BOX = "hide_media_box";
    private static final String HIDE_BUSINESS_BOX = "hide_business_box";
    private static final String HIDE_OA_MESSAGES = "hide_oa_messages";
    private static final String MUTE_OA_MESSAGES = "mute_oa_messages";
    private static final String OA_MUTED_UIDS = "oa_muted_uids:";
    private static final String HIDE_MESSAGE_ADS = "hide_message_ads";
    private static final String HIDE_FEED_ADS = "hide_feed_ads";
    private static final String KEEP_RECALLED_MESSAGES = "keep_recalled_messages";
    private static final String HIDE_ONLINE_STATUS = "hide_online_status";
    private static final String HIDE_DISCOVERY = "hide_discovery";
    private static final String SAVED_ONLINE_SHARING = "saved_online_sharing";
    private static final String ENTRY_TAG = "nezumi-settings-entry";
    private static final int BLUE = 0xFF0068FF;
    private static final int BLUE_DARK = 0xFF3D8BFF;
    private static final int ROW_TITLE_KEY = 0x7E4E5A01;
    private static final int ROW_DIVIDER_KEY = 0x7E4E5A02;
    private static final int HEADER_BLUE = 0xFF008DFA;
    private static final int DIVIDER = 0xFFE9EAEC;
    private static final int SECTION = 0xFFF2F3F5;
    private static final int FEATURE_REACTION = 1;
    private static final int FEATURE_TYPING = 1 << 1;
    private static final int FEATURE_READ_RECEIPTS = 1 << 2;
    private static final int FEATURE_DELIVERY_RECEIPTS = 1 << 3;
    private static final int FEATURE_MEDIA_BOX = 1 << 4;
    private static final int FEATURE_BUSINESS_BOX = 1 << 10;
    private static final int FEATURE_OA_MESSAGES = 1 << 12;
    private static final int FEATURE_MESSAGE_ADS = 1 << 13;
    private static final int FEATURE_FEED_ADS = 1 << 11;
    private static final int FEATURE_RECALLED_MESSAGES = 1 << 8;
    private static final int FEATURE_ONLINE_STATUS = 1 << 5;
    private static final int FEATURE_CUSTOM_FONT = 1 << 6;
    private static final int FEATURE_DISCOVERY = 1 << 7;
    private static final int FEATURE_CUSTOM_SOUNDS = 1 << 9;
    private static final Object ONLINE_LOCK = new Object();
    private static final Set<String> ONLINE_USERS = new HashSet<>();
    private static final Object RECALL_LOCK = new Object();
    private static final Object OA_LOCK = new Object();
    private static final HashMap<String, Long> OA_ATTEMPTS = new HashMap<>();
    private static final WeakHashMap<View, Boolean> RECALL_ROWS = new WeakHashMap<>();
    private static String recallAccount;
    private static Set<String> recalledMessages = new HashSet<>();
    private static final WeakHashMap<TextView, ChatPresence> CHAT_SUBTITLES = new WeakHashMap<>();
    private static final WeakHashMap<Object, String> CHAT_TARGETS = new WeakHashMap<>();
    private static final ThreadLocal<Boolean> CHAT_REFRESH = new ThreadLocal<>();
    private static long onlineObservedAt;
    private static long lastChatRefresh;
    private static long lastChatAttempt;
    private static String onlineAccount;
    private static final String GEAR_ICON = "iVBORw0KGgoAAAANSUhEUgAAADAAAAAwCAQAAAD9CzEMAAADgklEQVR42u1Yv09UQRD+7viRozjACCZiYsRSQE2MgEQtjI2ggCgkiv+AdmDhj4LYKWo8FK7wB7HSKFFjY+dJCAhaaRQUC0iMCQGRAiGBxJPPgmFu99493oNgbNwt9u3szJuX2W++2X0B4u+2IP47WDMHQTSgB1OYQg/qV2BHfz2dT2i2Lqb5s/Tr4BqTW+taOsjlT4eDn8zxY5vuErn9aMAGxPECTzGPMwgDAEZwAEHEsBVAGKdxBSEcQxUyMI7H6PO7B1m8b3zpFAe4IM9NBMFmmS1wgD8MzU5m+QvRfaZuk1xPEMyzXkvLhQ8He12M53hIdao476JV4e2gXVQ/8AhbOC7B6OVuS6uUfaI3zhZWc1BmN70dPBfVKsF/EfdwY0qEFLCMJUwnCFaL1TNvFM3JuAsvAMQx5JqjYxjT551J1sugqFG+ZUK+zU/P4IRYnfQOUUgxUmLJi9nGIc5ylkOMsNha2644C/mB6RtR36OSTEYZt9ASZwczdb1CpAPeMM3hRUmrBd3aTMZSQjKmLgrU5kIygdiMeZ0zat6n8ijdWofq9BocddVk2sTrgxYhzynuizU4w6xlmGHWclgDVaR5MWeRecDpoMEihUTWtonsM3NVto5fRHpDZZUWgRx3OuiRpRE2Cecs9iGR11qxPSrSQUOWxyaOiLzb6WBKljYnbfvSroQtabZG3NYu1BiIJGhk3GILODJxJS3gXvQHZexGM/IMjW8yHrTslmZfDVk+mvFSnj86qaLe2KIfQnXmJg9z3bKbfFiDTJJ1qWDaZSjMszQFTI8ym9ms09cnYFpuVYhHqWAKprHVKO69PhKtXXVeq2yal1Mn2hI6zvumipdKFZvU5hyzvcnubQqy63CQ3S2D7JbKbL+fmpyga5uSixjhIGc4w4+8obFf7DtWQtentNaupOB8dys4zkNslYxRxH2n1y9E5akyeclZk7NkfAcAyMA2hDFqVN9EK8AWzOIT4gDeJ1kvU5PblcZqeImTWh1KLa0yrQATbGGNUmLEew/2uWB+npVG1rodvMr91OROj6NjvuvR8Y6/s2kW71mvfZ10+D0rs9/st1zddYLU/X5QwQi7+IAnGCJ4XotRIbdyVGbnCIbYyIfsYsRIy1VcQMKcXu0FxN9lbga3HbIoptfyEphmkTn5aK0vgWCAx/iKk5xkjHUJvvfqgf//Kv65gz/gmFMG8oKg/wAAAABJRU5ErkJggg==";
    private static final String CHAT_ICON = "iVBORw0KGgoAAAANSUhEUgAAADAAAAAwCAQAAAD9CzEMAAADI0lEQVR42u2YzWuVRxTGf29yb5SYkmLMImjAD2y7aItWDVUXJYJU8GNhay0trrppbf0bugkm1E2rLVZEELsRRPsVBRVEsN3YiAgtNBWKxi9oqtQmlCSV/rrI5HrN3Pe+N/e1dOPM5jJzzvPcmTnznDNvIv9ta+AJQUYr1GDTSBfdrOA5OmgBRrnNIJc4x0X+yfS2eu+0z5umtRv2uqA6QrXJdg84blYbd7/z6iF4y7slkDsecocv2WbRZjtd6bt+4W+l+d99c2YERQ+WnM+7ycaKVk1u90LJ7nOLtRI0eyo4XXWDWaf0uteD9UmbayEoluCP2JIJj9jiieDRH68iNp/anA9rAp/siT3Ba38Wwdt1wE/2PcFzezWC9hA5R2YMj4lHVR22LZ3gQDjaOXUQ4NPeUvWzNILOcK021AWPuEXVsfLbXT7dF+KeHP0HVXsrETQGzdmYi+ANVYdsiAlWB1Eo5CIoeE/VrqmRh/mgG4CTPJgmt71MpOJN0DvN+gFnAVgXy/VxVXdEKKNVtXQ0sn9H1WPxCp4F4KcoYexlIjWZ/M0n0djPZWiPrGDyirXlOgHEBeG6RStoAWAkdxK+C8BTcdL3MRUBs9OqivsAtD4mgpGYYBiA+ZFDpTAdpyeVYAkAd2KCHwFYHjnsohiNNbErleBFAAZjgisArKkpTCuF51R7GYCBOExXhPDKJxWz/UPVVbEWJd5QdXMugtdUvV5J7Ah59fsc8IkDqu6unA86QsJZl/P/jzk/LWX+qmp3nfBzHVJ1X1rKXBT0cVadBF+GMnJeGsFOVb+uE/6jIODb0suWb1TdWRd8X4D/NL3wanJE1SUzBm8NFZH2x7fo4c/uUBOVV6mvZEZU4tZwtPpt9eK3r2yJi33Pr/xT1YtuSynfZ7k1xP2kZ0UNSErv5MssAz6mwKssnaYv9zjLGQa5xn2KFHiGF1jL5pK4D/M+x7LeaH9VSOnXanhCjbnPubU8oc4/Uiv0+4FLxQ57SnsctyF3l9/a6lu0kMN0cZXTnOY7xssWmbCc9SzjedpppYFRbvILA5zjUvYzNnnyreJ/J/gXo3zphTXHOI0AAAAASUVORK5CYII=";

    private NezumiSettings() { }

    public static boolean isReactionEnabled(Context context) {
        return context != null && context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(REACTION, true);
    }

    public static boolean isDiscoveryHidden() {
        Context context = onlineContext();
        return context != null && context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(HIDE_DISCOVERY, true);
    }

    public static boolean isTypingHidden(Context context) {
        return context != null && context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(HIDE_TYPING, false);
    }

    public static boolean isReadReceiptsHidden(Context context) {
        return context != null && context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(HIDE_READ_RECEIPTS, false);
    }

    public static boolean isDeliveryReceiptsHidden(Context context) {
        return context != null && context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(HIDE_DELIVERY_RECEIPTS, false);
    }

    public static boolean isMediaBoxHidden(Context context) {
        return context != null && context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(HIDE_MEDIA_BOX, false);
    }

    public static boolean isBusinessBoxHidden(Context context) {
        return context != null && context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(HIDE_BUSINESS_BOX, false);
    }

    public static boolean isOAMessagesHidden(Context context) {
        return context != null && context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(HIDE_OA_MESSAGES, true);
    }

    public static boolean isMessageAdsHidden(Context context) {
        return context != null && context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(HIDE_MESSAGE_ADS, false);
    }

    public static boolean isOAMessagesMuted(Context context) {
        return context != null && context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(MUTE_OA_MESSAGES, true);
    }

    public static void setOAMessagesHidden(Context context, boolean hidden) {
        if (context == null || isOAMessagesHidden(context) == hidden) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(HIDE_OA_MESSAGES, hidden).apply();
        refreshOAMessages();
    }

    public static void setOAMessagesMuted(Context context, boolean muted) {
        if (context == null || isOAMessagesMuted(context) == muted) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(MUTE_OA_MESSAGES, muted).apply();
        refreshOAMessages();
    }

    private static void refreshOAMessages() {
        synchronized (OA_LOCK) {
            OA_ATTEMPTS.clear();
        }
        try {
            // MessageManager singleton p8g.w(); M() reschedules the tab message list rebuild.
            Object repository = Class.forName("p8g").getMethod("w").invoke(null);
            repository.getClass().getMethod("M").invoke(repository);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            android.util.Log.w("NezumiOA", "Messages refresh unavailable");
        }
    }

    private static boolean isOfficialAccount(Object conversation) {
        if (conversation == null) return false;
        try {
            String uid = (String) conversation.getClass().getField("b").get(conversation);
            if (uid == null || uid.isEmpty() || uid.startsWith("group_")) return false;
            Object profile = Class.forName("huj").getMethod("l", String.class).invoke(null, uid);
            if (profile == null) profile = conversation.getClass().getField("a").get(conversation);
            if (profile == null) return false;
            return Boolean.TRUE.equals(profile.getClass().getMethod("B2").invoke(profile));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    public static List filterOfficialAccounts(List items) {
        Context context = onlineContext();
        if (context == null || items == null) return items;
        boolean hide = isOAMessagesHidden(context);
        boolean mute = isOAMessagesMuted(context);
        String account = currentOnlineAccount();
        if (!mute) unmuteTrackedOfficialAccounts(context, account);
        if (!hide && !mute) return items;
        ArrayList filtered = hide ? new ArrayList(items.size()) : null;
        for (Object item : items) {
            boolean official = false;
            try {
                if (item != null && Class.forName("r24").isInstance(item)) {
                    Object conversation = item.getClass().getField("f").get(item);
                    official = isOfficialAccount(conversation);
                    if (official && mute) muteOfficialAccount(context, account, conversation);
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { }
            if (hide && !official) filtered.add(item);
        }
        return hide ? filtered : items;
    }

    private static boolean claimOAAttempt(String key) {
        long now = android.os.SystemClock.elapsedRealtime();
        synchronized (OA_LOCK) {
            Long attemptedAt = OA_ATTEMPTS.get(key);
            if (attemptedAt != null && now - attemptedAt < 60000) return false;
            OA_ATTEMPTS.put(key, now);
            return true;
        }
    }

    private static String oaMutedKey(String account) {
        return OA_MUTED_UIDS + account;
    }

    private static Set<String> trackedOfficialAccounts(Context context, String account) {
        Set<String> stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(oaMutedKey(account), null);
        return stored == null ? new HashSet<>() : new HashSet<>(stored);
    }

    private static void storeTrackedOfficialAccounts(Context context, String account, Set<String> uids) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        if (uids.isEmpty()) editor.remove(oaMutedKey(account));
        else editor.putStringSet(oaMutedKey(account), uids);
        editor.apply();
    }

    private static Object nativeMuteRepository() throws ReflectiveOperationException {
        Object dispatcher = Class.forName("kc7").getMethod("e").invoke(null);
        return dispatcher.getClass().getField("j").get(dispatcher);
    }

    private static void muteOfficialAccount(Context context, String account, Object conversation) {
        String uid;
        try {
            uid = (String) conversation.getClass().getField("b").get(conversation);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return;
        }
        if (uid == null || uid.isEmpty()) return;
        if (account == null || account.isEmpty() || uid.equals(account)) return;
        if (!claimOAAttempt("m:" + account + ":" + uid)) return;
        try {
            Object dispatcher = Class.forName("kc7").getMethod("e").invoke(null);
            Object nativeRepository = dispatcher.getClass().getField("j").get(dispatcher);
            Object existing = nativeRepository.getClass().getMethod("b", String.class).invoke(nativeRepository, uid);
            if (existing == null || existing.getClass().getField("d").getLong(existing) >= 0
                    || existing.getClass().getField("e").getInt(existing) != 0) {
                Class<?> itemClass = Class.forName(
                    "com.zing.zalo.comm.mutechatsetting.data.model.MuteSettingItem");
                Object item = itemClass.getConstructor(int.class, int.class, long.class, long.class,
                    String.class).newInstance(1, 0, System.currentTimeMillis(), -1L, uid);
                Class<?> trackingClass = Class.forName("v3h");
                Object tracking = Enum.valueOf((Class) trackingClass, "TAB_MSG");
                Class<?> entryClass = Class.forName("t1h");
                Object entry = Enum.valueOf((Class) entryClass, "MUTE_SHEET");
                Class<?> eventClass = Class.forName("ap");
                Object event = eventClass.getConstructor(itemClass, trackingClass, entryClass)
                    .newInstance(item, tracking, entry);
                Object flow = dispatcher.getClass().getMethod("p1", Object.class)
                    .invoke(dispatcher, event);
                collectNativeFlow(flow);
                Set<String> tracked = trackedOfficialAccounts(context, account);
                if (tracked.add(uid)) storeTrackedOfficialAccounts(context, account, tracked);
                android.util.Log.i("NezumiOA", "Permanent mute dispatched");
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            android.util.Log.w("NezumiOA", "Automatic mute unavailable");
        }
    }

    private static void unmuteTrackedOfficialAccounts(Context context, String account) {
        if (account == null || account.isEmpty()) return;
        Set<String> tracked = trackedOfficialAccounts(context, account);
        if (tracked.isEmpty()) return;
        boolean changed = false;
        try {
            Object nativeRepository = nativeMuteRepository();
            java.lang.reflect.Method lookup = nativeRepository.getClass().getMethod("b", String.class);
            for (String uid : new ArrayList<>(tracked)) {
                // Native success removes the uid from the mute map, so null confirms the unmute.
                if (lookup.invoke(nativeRepository, uid) == null) {
                    tracked.remove(uid);
                    changed = true;
                    continue;
                }
                if (!claimOAAttempt("u:" + account + ":" + uid)) continue;
                Object useCase = Class.forName("kc7").getMethod("i").invoke(null);
                Class<?> trackingClass = Class.forName("v3h");
                Object tracking = Enum.valueOf((Class) trackingClass, "TAB_MSG");
                Object event = Class.forName("bdl").getConstructor(String.class, int.class, trackingClass)
                    .newInstance(uid, 1, tracking);
                Object flow = useCase.getClass().getMethod("p1", Object.class).invoke(useCase, event);
                collectNativeFlow(flow);
                android.util.Log.i("NezumiOA", "Unmute dispatched");
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            android.util.Log.w("NezumiOA", "Automatic unmute unavailable");
        }
        if (changed) storeTrackedOfficialAccounts(context, account, tracked);
    }

    private static void collectNativeFlow(Object flow) throws ReflectiveOperationException {
        Object ioContext = Class.forName("kotlinx.coroutines.scheduling.DefaultIoScheduler")
            .getField("c").get(null);
        Class<?> scopeClass = Class.forName("kotlinx.coroutines.CoroutineScope");
        Object scope = null;
        for (java.lang.reflect.Method method : Class.forName("kotlinx.coroutines.CoroutineScopeKt").getMethods()) {
            Class<?>[] types = method.getParameterTypes();
            if (java.lang.reflect.Modifier.isStatic(method.getModifiers()) && types.length == 1
                && types[0].isInstance(ioContext) && scopeClass.isAssignableFrom(method.getReturnType())) {
                scope = method.invoke(null, ioContext);
                break;
            }
        }
        Class<?> flowClass = Class.forName("kotlinx.coroutines.flow.Flow");
        Class<?> flowKtClass = Class.forName("kotlinx.coroutines.flow.FlowKt");
        Class<?> jobClass = Class.forName("kotlinx.coroutines.Job");
        for (java.lang.reflect.Method method : flowKtClass.getMethods()) {
            Class<?>[] types = method.getParameterTypes();
            if (scope != null && java.lang.reflect.Modifier.isStatic(method.getModifiers())
                && types.length == 2 && types[0] == flowClass && types[1] == scopeClass
                && jobClass.isAssignableFrom(method.getReturnType())) {
                method.invoke(null, flow, scope);
                return;
            }
        }
        throw new NoSuchMethodException("launchIn");
    }

    public static List filterFeedAds(List items) {
        if (!isFeedAdsHidden(onlineContext()) || items == null || items.isEmpty()) return items;
        ArrayList filtered = new ArrayList(items.size());
        for (Object item : items) {
            if (!isFeedAdItem(item)) filtered.add(item);
        }
        return filtered;
    }

    private static boolean isFeedAdsHidden(Context context) {
        return context != null && context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(HIDE_FEED_ADS, false);
    }

    private static boolean isFeedAdItem(Object item) {
        try {
            return item != null && item.getClass().getField("c").getInt(item) == 0x51;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    public static boolean isRecalledMessagesKept(Context context) {
        return context != null && context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEEP_RECALLED_MESSAGES, false);
    }

    public static boolean shouldKeepIncomingRecall(String senderId, int eventType) {
        boolean enabled = isRecalledMessagesKept(onlineContext());
        String account = currentOnlineAccount();
        boolean incoming = account != null && !account.isEmpty()
            && senderId != null && !senderId.isEmpty() && !account.equals(senderId);
        boolean keep = enabled && incoming;
        android.util.Log.i("NezumiRecall", "event=" + eventType + " enabled=" + enabled
            + " incoming=" + incoming + " kept=" + keep);
        return keep;
    }

    private static String recallDigest(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(digest, Base64.NO_WRAP | Base64.URL_SAFE);
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static Set<String> recallKeys(Object messageId) {
        Set<String> keys = new HashSet<>();
        if (messageId == null) return keys;
        try {
            String clientId = (String) messageId.getClass().getField("a").get(messageId);
            String globalId = (String) messageId.getClass().getField("b").get(messageId);
            String ownerId = (String) messageId.getClass().getField("c").get(messageId);
            if (ownerId == null || ownerId.isEmpty()) return keys;
            if (clientId != null && !clientId.isEmpty() && !"0".equals(clientId)) {
                keys.add(recallDigest(new org.json.JSONArray().put(ownerId).put("client").put(clientId).toString()));
            }
            if (globalId != null && !globalId.isEmpty() && !"0".equals(globalId)) {
                keys.add(recallDigest(new org.json.JSONArray().put(ownerId).put("global").put(globalId).toString()));
            }
            return keys;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return keys;
        }
    }

    private static String loadRecallAccount(Context context) {
        String account = currentOnlineAccount();
        if (account == null || account.isEmpty()) return null;
        String key = "recalled_ids_" + recallDigest(account);
        if (!key.equals(recallAccount)) {
            recalledMessages = new HashSet<>(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet(key, java.util.Collections.emptySet()));
            recallAccount = key;
        }
        return key;
    }

    public static void markIncomingRecall(Object messageId) {
        Context context = onlineContext();
        Set<String> keys = recallKeys(messageId);
        if (context == null || keys.isEmpty()) return;
        ArrayList<View> rows;
        synchronized (RECALL_LOCK) {
            String accountKey = loadRecallAccount(context);
            if (accountKey == null || !recalledMessages.addAll(keys)) return;
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putStringSet(accountKey, new HashSet<>(recalledMessages)).apply();
            rows = new ArrayList<>(RECALL_ROWS.keySet());
        }
        for (View row : rows) {
            row.post(() -> refreshRecallRow(row));
        }
        android.util.Log.i("NezumiRecall", "target marked");
    }

    public static boolean isIncomingRecall(Object message) {
        Context context = onlineContext();
        if (context == null || message == null) {
            android.util.Log.i("NezumiRecall", "lookup miss no-context-or-message");
            return false;
        }
        try {
            Set<String> keys = recallKeys(message.getClass().getField("b").get(message));
            synchronized (RECALL_LOCK) {
                boolean hit = !keys.isEmpty() && loadRecallAccount(context) != null
                    && keys.stream().anyMatch(recalledMessages::contains);
                android.util.Log.i("NezumiRecall", "lookup " + (hit ? "hit" : "miss"));
                return hit;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            android.util.Log.i("NezumiRecall", "lookup miss reflection");
            return false;
        }
    }

    public static boolean hasRecallLabel(Object row) {
        try {
            return row != null && isIncomingRecall(row.getClass().getMethod("getMessage").invoke(row));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    public static String formatRecallTime(View row, String original) {
        if (!hasRecallLabel(row)) {
            android.util.Log.i("NezumiRecall", "footer miss");
            return original;
        }
        android.util.Log.i("NezumiRecall", "footer hit");
        String label = recalledLabel(row.getContext());
        return original == null || original.isEmpty() ? label : original + " - " + label;
    }

    public static void trackRecallRow(View row) {
        if (row == null) return;
        synchronized (RECALL_LOCK) {
            RECALL_ROWS.put(row, Boolean.TRUE);
        }
        android.util.Log.i("NezumiRecall", "row tracked");
    }

    private static void refreshRecallRow(View row) {
        if (!row.isAttachedToWindow() || !hasRecallLabel(row)) return;
        try {
            Class<?> base = Class.forName("com.zing.zalo.ui.chat.chatrow.ChatRow");
            Object message = base.getMethod("getMessage").invoke(row);
            Object binding = base.getField("j").get(row);
            if (message == null || binding == null) return;
            java.lang.reflect.Method bind = null;
            for (java.lang.reflect.Method candidate : base.getMethods()) {
                Class<?>[] types = candidate.getParameterTypes();
                if (candidate.getName().equals("p2") && types.length == 2
                    && types[0].isInstance(message) && types[1].isInstance(binding)) {
                    bind = candidate;
                    break;
                }
            }
            if (bind == null) throw new NoSuchMethodException("p2");
            bind.invoke(row, message, binding);
            // p2 rebinds and requests layout on its own.
            row.requestLayout();
            row.invalidate();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            android.util.Log.w("NezumiRecall", "row refresh unavailable");
        }
    }

    public static String recalledLabel(Context context) {
        return isVietnamese(context) ? "Đã thu hồi" : "Recalled";
    }

    public static boolean shouldFetchOnlineStatus() {
        return isOnlineHidden() || originalOnlineSharing();
    }

    public static void traceOnlineResponse(org.json.JSONObject data) {
        try {
            org.json.JSONArray onlines = data.optJSONArray("onlines");
            android.util.Log.i("NezumiOnline", "onlineResponse sharing=" + originalOnlineSharing()
                + " hidden=" + isOnlineHidden() + " hasOnlines=" + data.has("onlines")
                + " arrayPresent=" + (onlines != null)
                + " count=" + (onlines == null ? -1 : onlines.length()));
        } catch (RuntimeException ignored) {
            android.util.Log.w("NezumiOnline", "onlineResponse probe unavailable");
        }
    }

    public static void traceLastSeenResponse(org.json.JSONObject data) {
        try {
            android.util.Log.i("NezumiOnline", "lastSeenResponse sharing=" + originalOnlineSharing()
                + " hidden=" + isOnlineHidden() + " hasTimestamp=" + data.has("timestamp")
                + " timestampPositive=" + (data.optLong("timestamp") > 0)
                + " hasVisibility=" + data.has("settingOnlineStatus")
                + " visibility=" + data.optInt("settingOnlineStatus", -1));
        } catch (RuntimeException ignored) {
            android.util.Log.w("NezumiOnline", "lastSeenResponse probe unavailable");
        }
    }

    public static void traceFriendListResponse(org.json.JSONObject data) {
        try {
            org.json.JSONArray onlines = data.optJSONArray("onlines");
            android.util.Log.i("NezumiOnline", "friendListResponse sharing=" + originalOnlineSharing()
                + " hidden=" + isOnlineHidden() + " arrayPresent=" + (onlines != null)
                + " count=" + (onlines == null ? -1 : onlines.length()));
        } catch (RuntimeException ignored) {
            android.util.Log.w("NezumiOnline", "friendListResponse probe unavailable");
        }
    }

    public static void updateChatOnlineUsers(org.json.JSONObject data) {
        Set<String> onlineUsers = new HashSet<>();
        org.json.JSONArray onlines = data.optJSONArray("onlines");
        if (onlines != null) {
            for (int index = 0; index < onlines.length(); index++) {
                org.json.JSONObject entry = onlines.optJSONObject(index);
                if (entry == null) continue;
                String userId = entry.optString("userId");
                if (!android.text.TextUtils.isEmpty(userId)) onlineUsers.add(userId);
            }
        }
        ArrayList<TextView> views;
        synchronized (ONLINE_LOCK) {
            ONLINE_USERS.clear();
            ONLINE_USERS.addAll(onlineUsers);
            onlineObservedAt = android.os.SystemClock.elapsedRealtime();
            onlineAccount = currentOnlineAccount();
            views = new ArrayList<>(CHAT_SUBTITLES.keySet());
        }
        for (TextView view : views) {
            ChatPresence presence;
            synchronized (ONLINE_LOCK) {
                presence = CHAT_SUBTITLES.get(view);
            }
            if (presence != null) view.post(presence::render);
        }
    }

    public static void setChatPresenceTarget(Object chatView, android.os.Bundle arguments) {
        String userId = arguments == null ? null : arguments.getString("extra_chat_profile_uid");
        synchronized (ONLINE_LOCK) {
            if (android.text.TextUtils.isEmpty(userId)) CHAT_TARGETS.remove(chatView);
            else CHAT_TARGETS.put(chatView, userId);
        }
    }

    public static void bindChatSubtitle(Object chatView, TextView subtitle) {
        if (subtitle == null) return;
        synchronized (ONLINE_LOCK) {
            String userId = CHAT_TARGETS.get(chatView);
            ChatPresence presence = CHAT_SUBTITLES.get(subtitle);
            if (presence == null) {
                presence = new ChatPresence(subtitle);
                CHAT_SUBTITLES.put(subtitle, presence);
            }
            presence.userId = userId;
            presence.start();
        }
    }

    public static boolean isChatOnlineRefresh() {
        return Boolean.TRUE.equals(CHAT_REFRESH.get());
    }

    public static long onlineRefreshInterval() {
        return isChatOnlineRefresh() ? 10000L : 60000L;
    }

    public static void traceChatOnlineDispatch() {
        if (isChatOnlineRefresh()) {
            lastChatRefresh = android.os.SystemClock.elapsedRealtime();
            android.util.Log.i("NezumiOnline", "chatRefresh dispatched");
        }
    }

    public static void traceChatOnlineGate(Object repository) {
        if (!isChatOnlineRefresh()) return;
        try {
            boolean inFlight = repository.getClass().getField("n").getBoolean(repository);
            long requestedAt = repository.getClass().getField("l").getLong(repository);
            long remaining = Math.max(0, onlineRefreshInterval() - (System.currentTimeMillis() - requestedAt));
            android.util.Log.i("NezumiOnline", "chatRefresh gate inFlight=" + inFlight
                + " throttleRemainingMs=" + remaining);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            android.util.Log.w("NezumiOnline", "Chat refresh gate unavailable");
        }
    }

    public static boolean retainChatOnlineSubtitle(TextView subtitle) {
        ChatPresence presence;
        synchronized (ONLINE_LOCK) {
            presence = CHAT_SUBTITLES.get(subtitle);
        }
        return presence != null && presence.render(true);
    }

    public static void requestChatOnlineList() {
        throw new IllegalStateException("Chat online request bridge was not patched");
    }

    private static String currentOnlineAccount() {
        try {
            Object account = Class.forName("com.zing.zalocore.CoreUtility").getField("i").get(null);
            return account instanceof String ? (String) account : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static final class ChatPresence implements Runnable, View.OnAttachStateChangeListener {
        private final WeakReference<TextView> view;
        private final Object marker = new Object();
        private String userId;
        private boolean scheduled;

        private ChatPresence(TextView subtitle) {
            view = new WeakReference<>(subtitle);
            subtitle.addOnAttachStateChangeListener(this);
        }

        private void start() {
            TextView subtitle = view.get();
            if (subtitle == null || scheduled || !subtitle.isAttachedToWindow()) return;
            scheduled = true;
            subtitle.post(this);
        }

        @Override
        public void onViewAttachedToWindow(View attachedView) {
            start();
        }

        @Override
        public void onViewDetachedFromWindow(View detachedView) {
            detachedView.removeCallbacks(this);
            scheduled = false;
        }

        private void render() {
            render(false);
        }

        private boolean render(boolean nativeClear) {
            TextView subtitle = view.get();
            if (subtitle == null) return false;
            boolean online;
            synchronized (ONLINE_LOCK) {
                online = userId != null && ONLINE_USERS.contains(userId)
                    && onlineObservedAt > 0
                    && android.os.SystemClock.elapsedRealtime() - onlineObservedAt < 90000
                    && onlineAccount != null && onlineAccount.equals(currentOnlineAccount());
            }
            CharSequence text = subtitle.getText();
            boolean managed = text instanceof android.text.Spanned
                && ((android.text.Spanned) text).getSpanStart(marker) >= 0;
            if (isOnlineHidden() && online && (nativeClear || android.text.TextUtils.isEmpty(text) || managed)) {
                android.text.SpannableString label = new android.text.SpannableString(
                    isVietnamese(subtitle.getContext()) ? "Đang hoạt động" : "Online");
                label.setSpan(marker, 0, label.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (!managed) subtitle.setText(label, TextView.BufferType.SPANNABLE);
                subtitle.setVisibility(View.VISIBLE);
                return true;
            } else if (managed) {
                subtitle.setText("");
                subtitle.setVisibility(View.GONE);
            }
            return false;
        }

        @Override
        public void run() {
            TextView subtitle = view.get();
            if (subtitle == null) return;
            render();
            if (!subtitle.isAttachedToWindow()) {
                scheduled = false;
                return;
            }
            if (!android.text.TextUtils.isEmpty(userId) && isOnlineHidden()
                    && subtitle.getWindowVisibility() == View.VISIBLE && subtitle.hasWindowFocus()
                    && subtitle.getRootView().isShown()) {
                long now = android.os.SystemClock.elapsedRealtime();
                if ((lastChatRefresh == 0 || now - lastChatRefresh > 10000)
                        && (lastChatAttempt == 0 || now - lastChatAttempt > 5000)) {
                    lastChatAttempt = now;
                    CHAT_REFRESH.set(true);
                    try {
                        android.util.Log.i("NezumiOnline", "chatRefresh attempt");
                        requestChatOnlineList();
                    } catch (LinkageError | RuntimeException ignored) {
                        android.util.Log.w("NezumiOnline", "Chat online refresh unavailable");
                    } finally {
                        CHAT_REFRESH.remove();
                    }
                }
            }
            scheduled = true;
            subtitle.postDelayed(this, 2000);
        }
    }

    private static Context onlineContext() {
        try {
            return (Context) Class.forName("com.zing.zalo.MainApplication")
                .getMethod("getAppContext").invoke(null);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Activity activityContext(Context context) {
        Context current = context;
        while (current instanceof android.content.ContextWrapper) {
            if (current instanceof Activity) return (Activity) current;
            Context base = ((android.content.ContextWrapper) current).getBaseContext();
            if (base == current) break;
            current = base;
        }
        return current instanceof Activity ? (Activity) current : null;
    }

    private static void recreateForDiscovery(Context context) {
        refreshDiscoveryTabs();
        Activity activity = activityContext(context);
        if (activity != null && !activity.isFinishing() && !activity.isChangingConfigurations()) {
            activity.recreate();
        }
    }

    private static void refreshDiscoveryTabs() {
        try {
            Object configuration = Class.forName("y5f").getMethod("h").invoke(null);
            configuration.getClass().getMethod("o").invoke(configuration);
            configuration.getClass().getMethod("q").invoke(configuration);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
        }
    }

    private static boolean isOnlineHidden() {
        Context context = onlineContext();
        return context != null && context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(HIDE_ONLINE_STATUS, false);
    }

    public static int mapOnlineState(int state) {
        Context context = onlineContext();
        boolean hidden = context != null && context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(HIDE_ONLINE_STATUS, false);
        int mapped = state == 103 && hidden ? 102 : state;
        android.util.Log.i("NezumiOnline", "socketState=" + state + " mapped=" + mapped
            + " hidden=" + hidden + " contextAvailable=" + (context != null));
        return mapped;
    }

    public static void publishOnlineState(int state) {
        throw new IllegalStateException("Online state bridge was not patched");
    }

    public static boolean originalOnlineSharing() {
        return false;
    }

    public static void applyOriginalOnlineSharing(boolean enabled) {
        throw new IllegalStateException("Online sharing setter bridge was not patched");
    }

    public static boolean isOnlinePatchAvailable() {
        return false;
    }

    public static void attachReaction(View root, View anchor) {
        attach(root, anchor, FEATURE_REACTION);
    }

    public static void attachTyping(View root, View anchor) {
        attach(root, anchor, FEATURE_TYPING);
    }

    public static void attachReadReceipts(View root, View anchor) {
        attach(root, anchor, FEATURE_READ_RECEIPTS);
    }

    public static void attachDeliveryReceipts(View root, View anchor) {
        attach(root, anchor, FEATURE_DELIVERY_RECEIPTS);
    }

    public static void attachMediaBox(View root, View anchor) {
        attach(root, anchor, FEATURE_MEDIA_BOX);
    }

    public static void attachBusinessBox(View root, View anchor) {
        attach(root, anchor, FEATURE_BUSINESS_BOX);
    }

    public static void attachOAMessages(View root, View anchor) {
        attach(root, anchor, FEATURE_OA_MESSAGES);
    }

    public static void attachMessageAds(View root, View anchor) {
        attach(root, anchor, FEATURE_MESSAGE_ADS);
    }

    public static void attachFeedAds(View root, View anchor) {
        attach(root, anchor, FEATURE_FEED_ADS);
    }

    public static void attachRecalledMessages(View root, View anchor) {
        attach(root, anchor, FEATURE_RECALLED_MESSAGES);
    }

    public static void attachOnlineStatus(View root, View anchor) {
        attach(root, anchor, FEATURE_ONLINE_STATUS);
    }

    public static void attachDiscovery(View root, View anchor) {
        attach(root, anchor, FEATURE_DISCOVERY);
    }

    public static void attachCustomFont(View root, View anchor) {
        attach(root, anchor, FEATURE_CUSTOM_FONT);
    }

    public static void attachCustomSounds(View root, View anchor) {
        attach(root, anchor, FEATURE_CUSTOM_SOUNDS);
    }

    private static void attach(View root, View anchor, int feature) {
        if (root == null || anchor == null || !(anchor.getParent() instanceof ViewGroup)) return;
        ViewGroup parent = (ViewGroup) anchor.getParent();
        for (int index = 0; index < parent.getChildCount(); index++) {
            Object tag = parent.getChildAt(index).getTag();
            if (tag instanceof EntryState) {
                ((EntryState) tag).features |= feature;
                return;
            }
        }
        Context context = anchor.getContext();
        boolean darkMode = isDarkMode(context, anchor);
        int titleId = context.getResources().getIdentifier("list_title", "id", context.getPackageName());
        View title = titleId == 0 ? null : anchor.findViewById(titleId);
        TextView nativeTitle = title instanceof TextView ? (TextView) title : null;
        View entry = row(context, "Nezumi's Settings", vectorIcon(context), true, darkMode, nativeTitle, 49);
        Drawable background = anchor.getBackground();
        if (background != null && background.getConstantState() != null) {
            entry.setBackground(background.getConstantState().newDrawable(context.getResources()).mutate());
        }
        EntryState state = new EntryState(feature);
        entry.setTag(state);
        entry.setOnClickListener(view -> showNezumi(context, darkMode, state));
        parent.addView(entry, parent.indexOfChild(anchor) + 1);
        int dividerId = context.getResources().getIdentifier("list_divider", "id", context.getPackageName());
        View divider = dividerId == 0 ? null : anchor.findViewById(dividerId);
        if (divider != null) divider.post(() -> divider.setVisibility(View.VISIBLE));
    }

    private static final class EntryState {
        private int features;

        private EntryState(int features) {
            this.features = features;
        }
    }

    private static int dp(Context context, int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics());
    }

    private static boolean isDarkMode(Context context) {
        return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private static boolean isDarkMode(Context context, View reference) {
        if (isDarkMode(context)) return true;
        TextView nativeLabel = findTextView(reference);
        if (nativeLabel == null) return false;
        int color = nativeLabel.getCurrentTextColor();
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;
        return (red * 299 + green * 587 + blue * 114) / 1000 > 180;
    }

    private static TextView findTextView(View view) {
        if (view instanceof TextView) return (TextView) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            TextView text = findTextView(group.getChildAt(index));
            if (text != null) return text;
        }
        return null;
    }

    private static int themeColor(Context context, int attribute, int fallback) {
        TypedValue color = new TypedValue();
        if (!context.getTheme().resolveAttribute(attribute, color, true)) return fallback;
        try {
            return color.resourceId != 0
                    ? context.getColorStateList(color.resourceId).getDefaultColor() : color.data;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static int resourceColor(Context context, String name, int fallback) {
        int resourceId = context.getResources().getIdentifier(
                name, "color", context.getPackageName());
        if (resourceId == 0) return fallback;
        try {
            return context.getColorStateList(resourceId).getDefaultColor();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static int surfaceColor(Context context, boolean darkMode) {
        if (darkMode) return 0xFF121212;
        return resourceColor(context, "ui_background", themeColor(context, android.R.attr.colorBackground,
            0xFFFFFFFF));
    }

    private static int sectionColor(Context context, boolean darkMode) {
        return darkMode ? 0xFF1E1E1E : SECTION;
    }

    private static int textColor(Context context, boolean darkMode) {
        return darkMode ? 0xFFFFFFFF
                : resourceColor(context, "list_item_title",
                themeColor(context, android.R.attr.textColorPrimary, 0xFF222222));
    }

    private static TextView label(Context context, String value, boolean darkMode) {
        TextView text;
        try {
            Constructor<?> constructor = Class.forName("com.zing.zalo.ui.widget.RobotoTextView")
                    .getConstructor(Context.class);
            text = (TextView) constructor.newInstance(context);
        } catch (ReflectiveOperationException | ClassCastException | SecurityException ignored) {
            text = new TextView(context);
        }
        text.setText(value);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        text.setTextColor(textColor(context, darkMode));
        text.setGravity(Gravity.CENTER_VERTICAL);
        android.graphics.Typeface font = CustomFont.forMessageModule(context, text.getTypeface());
        if (font != text.getTypeface()) text.setTypeface(font);
        return text;
    }

    static boolean isVietnamese(Context context) {
        try {
            Object language = Class.forName("z0r").getField("b").get(null);
            if ("vi".equals(language)) return true;
            if ("en".equals(language)) return false;
        } catch (ReflectiveOperationException | SecurityException ignored) { }
        return "vi".equals(context.getResources().getConfiguration().locale.getLanguage());
    }

    private static View row(Context context, String title, Drawable icon, boolean chevron,
                            boolean darkMode, TextView nativeTitle) {
        return row(context, title, icon, chevron, darkMode, nativeTitle, 56);
    }

    private static View row(Context context, String title, Drawable icon, boolean chevron,
                            boolean darkMode, TextView nativeTitle, int heightDp) {
        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setBackgroundColor(surfaceColor(context, darkMode));
        LinearLayout content = new LinearLayout(context);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setPadding(dp(context, 16), 0, dp(context, 16), 0);
        TypedValue ripple = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)) {
            content.setBackgroundResource(ripple.resourceId);
        }
        if (icon != null) {
            View leading = new View(context);
            leading.setBackground(icon);
            LinearLayout.LayoutParams leadingParams = new LinearLayout.LayoutParams(dp(context, 24), dp(context, 24));
            leadingParams.rightMargin = dp(context, 18);
            content.addView(leading, leadingParams);
        }
        TextView text = nativeTitle == null ? label(context, title, darkMode) : new TextView(context);
        if (nativeTitle != null) {
            text.setText(title);
            text.setTextSize(TypedValue.COMPLEX_UNIT_PX, nativeTitle.getTextSize());
            text.setTypeface(nativeTitle.getTypeface());
            text.setTextColor(nativeTitle.getTextColors());
            text.setGravity(Gravity.CENTER_VERTICAL);
        }
        text.setSingleLine(true);
        text.setEllipsize(android.text.TextUtils.TruncateAt.END);
        content.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        wrapper.setTag(ROW_TITLE_KEY, text);
        if (chevron) {
            ImageView trailing = new ImageView(context);
            int chevronId = context.getResources().getIdentifier(
                    "zds_ic_chevron_right_line_16", "drawable", context.getPackageName());
            if (chevronId != 0) {
                trailing.setImageResource(chevronId);
                int colorAttribute = context.getResources().getIdentifier("icon_02", "attr", context.getPackageName());
                TypedValue color = new TypedValue();
                if (colorAttribute != 0 && context.getTheme().resolveAttribute(colorAttribute, color, true)) {
                    trailing.setImageTintList(ColorStateList.valueOf(color.resourceId != 0
                            ? context.getColorStateList(color.resourceId).getDefaultColor() : color.data));
                }
            } else {
                trailing.setImageDrawable(new LineIcon(0xFF999999, 1));
            }
            content.addView(trailing, new LinearLayout.LayoutParams(dp(context, 16), dp(context, 16)));
        }
        wrapper.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, heightDp)));
        View divider = new View(context);
        divider.setBackgroundColor(resourceColor(context, "divider_01",
            darkMode ? 0xFF303030 : DIVIDER));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1));
        // The entry row (49dp) keeps the inset that lines up with Zalo's native list.
        dividerParams.leftMargin = dp(context, icon == null ? 16 : heightDp < 56 ? 50 : 58);
        wrapper.addView(divider, dividerParams);
        wrapper.setTag(ROW_DIVIDER_KEY, divider);
        content.setOnClickListener(view -> wrapper.performClick());
        return wrapper;
    }

    private static Drawable vectorIcon(Context context) {
        try {
            byte[] bytes = Base64.decode(GEAR_ICON, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap == null) throw new IllegalStateException("Invalid icon image");
            Drawable icon = new BitmapDrawable(context.getResources(), bitmap);
            icon.setColorFilter(BLUE, PorterDuff.Mode.SRC_IN);
            return icon;
        } catch (Exception ignored) {
            return new LineIcon(BLUE, 0);
        }
    }

    private static void addGroup(LinearLayout list, Context context, String title, boolean darkMode) {
        View spacer = new View(context);
        spacer.setBackgroundColor(sectionColor(context, darkMode));
        list.addView(spacer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 8)));
        TextView heading = label(context, title, darkMode);
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        heading.setTextColor(0xFF1255B8);
        heading.setPadding(dp(context, 16), 0, 0, 0);
        heading.setBackgroundColor(surfaceColor(context, darkMode));
        list.addView(heading, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 48)));
    }

    private static void showNezumi(Context context, boolean darkMode, EntryState state) {
        boolean vietnamese = isVietnamese(context);
        int features = state.features;
        boolean hasChatFeatures = (features & (FEATURE_ONLINE_STATUS | FEATURE_REACTION |
            FEATURE_TYPING | FEATURE_READ_RECEIPTS | FEATURE_DELIVERY_RECEIPTS |
            FEATURE_RECALLED_MESSAGES)) != 0;
        String reactionLabel = vietnamese ? "Mở khóa biểu tượng cảm xúc" : "Unlock reaction icons";
        String typingLabel = vietnamese ? "Ẩn trạng thái đang nhập" : "Hide typing status";
        String readLabel = vietnamese ? "Ẩn trạng thái đã xem" : "Hide read receipts";
        String deliveryLabel = vietnamese ? "Ẩn trạng thái đã nhận" : "Hide delivery receipts";
        String mediaBoxLabel = vietnamese ? "Ẩn Media Box" : "Hide Media Box";
        String businessBoxLabel = vietnamese ? "Ẩn Business Box" : "Hide Business Box";
        String oaMessagesLabel = vietnamese ? "Ẩn tài khoản OA" : "Hide OA accounts";
        String oaMuteLabel = vietnamese ? "Tắt thông báo tài khoản OA" : "Mute OA accounts";
        String feedAdsLabel = vietnamese ? "Ẩn quảng cáo Bảng tin" : "Hide feed ads";
        String recalledMessagesLabel = vietnamese ? "Giữ tin nhắn đã thu hồi" :
            "Keep recalled messages";
        String onlineLabel = vietnamese ? "Ẩn trạng thái hoạt động" : "Hide activity status";
        String discoveryLabel = vietnamese ? "Ẩn tab Khám phá" : "Hide Discovery tab";
        String messageAdsLabel = vietnamese ? "Ẩn quảng cáo trong Messages" : "Hide ads in Messages";
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Dialog page = page(context, "Nezumi's Settings", darkMode);
        boolean[] discoveryChanged = { false };
        page.setOnDismissListener(dialog -> {
            if (discoveryChanged[0]) recreateForDiscovery(context);
        });
        LinearLayout list = page.findViewById(android.R.id.content).findViewWithTag("nezumi-list");
        if (hasChatFeatures) addGroup(list, context, vietnamese ? "Trò chuyện" : "Chat", darkMode);
        if ((features & FEATURE_ONLINE_STATUS) != 0) {
            View onlineItem = toggleRow(list, context, onlineLabel, "user_status",
                prefs.getBoolean(HIDE_ONLINE_STATUS, false), darkMode, (button, checked) -> {
                if (prefs.getBoolean(HIDE_ONLINE_STATUS, false) == checked) return;
                boolean originalSharing = originalOnlineSharing();
                boolean hasSavedSharing = prefs.contains(SAVED_ONLINE_SHARING);
                boolean savedSharing = hasSavedSharing ? prefs.getBoolean(SAVED_ONLINE_SHARING, true) : originalSharing;
                prefs.edit().putBoolean(HIDE_ONLINE_STATUS, checked).apply();
                try {
                    if (checked) {
                        if (!hasSavedSharing) {
                            prefs.edit().putBoolean(SAVED_ONLINE_SHARING, originalSharing).apply();
                        }
                        applyOriginalOnlineSharing(false);
                    } else {
                        applyOriginalOnlineSharing(savedSharing);
                        prefs.edit().remove(SAVED_ONLINE_SHARING).apply();
                    }
                    publishOnlineState(103);
                } catch (LinkageError | RuntimeException failure) {
                    applyOriginalOnlineSharing(originalSharing);
                    prefs.edit().putBoolean(HIDE_ONLINE_STATUS, !checked).apply();
                    button.setChecked(!checked);
                    Toast.makeText(context, vietnamese ? "Không thể cập nhật trạng thái kết nối" :
                        "Unable to update connection status", Toast.LENGTH_SHORT).show();
                }
            });
            onlineItem.setVisibility(isOnlinePatchAvailable() ? View.VISIBLE : View.GONE);
        }
        if ((features & FEATURE_REACTION) != 0) {
            toggleRow(list, context, reactionLabel, "face_smile", prefs.getBoolean(REACTION, true), darkMode,
                (button, checked) -> prefs.edit().putBoolean(REACTION, checked).apply());
        }
        if ((features & FEATURE_TYPING) != 0) {
            toggleRow(list, context, typingLabel, "more_horizontal", prefs.getBoolean(HIDE_TYPING, false), darkMode,
                (button, checked) -> prefs.edit().putBoolean(HIDE_TYPING, checked).apply());
        }
        if ((features & FEATURE_READ_RECEIPTS) != 0) {
            toggleRow(list, context, readLabel, "read_message", prefs.getBoolean(HIDE_READ_RECEIPTS, false), darkMode,
                (button, checked) -> prefs.edit().putBoolean(HIDE_READ_RECEIPTS, checked).apply());
        }
        if ((features & FEATURE_DELIVERY_RECEIPTS) != 0) {
            toggleRow(list, context, deliveryLabel, "check_circle", prefs.getBoolean(HIDE_DELIVERY_RECEIPTS, false),
                darkMode, (button, checked) -> prefs.edit().putBoolean(HIDE_DELIVERY_RECEIPTS, checked).apply());
        }
        if ((features & FEATURE_RECALLED_MESSAGES) != 0) {
            toggleRow(list, context, recalledMessagesLabel, "recall",
                prefs.getBoolean(KEEP_RECALLED_MESSAGES, false), darkMode,
                (button, checked) -> prefs.edit().putBoolean(KEEP_RECALLED_MESSAGES, checked).apply());
        }
        if ((features & FEATURE_DISCOVERY) != 0) {
            addGroup(list, context, vietnamese ? "Điều hướng" : "Navigation", darkMode);
            toggleRow(list, context, discoveryLabel, "grid_view", prefs.getBoolean(HIDE_DISCOVERY, true), darkMode,
                (button, checked) -> {
                if (prefs.getBoolean(HIDE_DISCOVERY, true) == checked) return;
                prefs.edit().putBoolean(HIDE_DISCOVERY, checked).apply();
                discoveryChanged[0] = true;
            });
        }
        if ((features & FEATURE_FEED_ADS) != 0) {
            addGroup(list, context, vietnamese ? "Bảng tin" : "Feed", darkMode);
            toggleRow(list, context, feedAdsLabel, "new_feed", prefs.getBoolean(HIDE_FEED_ADS, false), darkMode,
                (button, checked) -> prefs.edit().putBoolean(HIDE_FEED_ADS, checked).apply());
        }
        if ((features & (FEATURE_MEDIA_BOX | FEATURE_BUSINESS_BOX | FEATURE_OA_MESSAGES | FEATURE_MESSAGE_ADS)) != 0) {
            addGroup(list, context, vietnamese ? "Hội thoại" : "Conversation", darkMode);
        }
        if ((features & FEATURE_MEDIA_BOX) != 0) {
            toggleRow(list, context, mediaBoxLabel, "photo", prefs.getBoolean(HIDE_MEDIA_BOX, false), darkMode,
                (button, checked) -> prefs.edit().putBoolean(HIDE_MEDIA_BOX, checked).apply());
        }
        if ((features & FEATURE_BUSINESS_BOX) != 0) {
            toggleRow(list, context, businessBoxLabel, "business_suite", prefs.getBoolean(HIDE_BUSINESS_BOX, false),
                darkMode, (button, checked) -> prefs.edit().putBoolean(HIDE_BUSINESS_BOX, checked).apply());
        }
        if ((features & FEATURE_OA_MESSAGES) != 0) {
            toggleRow(list, context, oaMessagesLabel, "hide", prefs.getBoolean(HIDE_OA_MESSAGES, true), darkMode,
                (button, checked) -> setOAMessagesHidden(context, checked));
            toggleRow(list, context, oaMuteLabel, "notif_off", prefs.getBoolean(MUTE_OA_MESSAGES, true), darkMode,
                (button, checked) -> setOAMessagesMuted(context, checked));
        }
        if ((features & FEATURE_MESSAGE_ADS) != 0) {
            toggleRow(list, context, messageAdsLabel, "chat_block", prefs.getBoolean(HIDE_MESSAGE_ADS, false),
                darkMode, (button, checked) -> prefs.edit().putBoolean(HIDE_MESSAGE_ADS, checked).apply());
        }
        if ((features & FEATURE_CUSTOM_FONT) != 0) {
            addGroup(list, context, vietnamese ? "Giao diện" : "Appearance", darkMode);
            String fontTitle = vietnamese ? "Phông chữ tùy chọn" : "Custom font";
            View fontItem = row(context, fontTitle, zdsIcon(context, "edit_text", darkMode), true, darkMode, null);
            TextView fontLabel = rowTitle(fontItem);
            Runnable updateFontLabel = () -> fontLabel.setText(fontTitle + (CustomFont.isSelected(context)
                ? (vietnamese ? " (đã chọn)" : " (selected)") : ""));
            updateFontLabel.run();
            fontItem.setOnClickListener(view -> {
                if (!CustomFont.pick(context, updateFontLabel)) {
                    Toast.makeText(context, vietnamese ? "Không thể mở trình chọn phông chữ" : "Cannot open font picker",
                        Toast.LENGTH_SHORT).show();
                }
            });
            list.addView(fontItem);
            View resetFont = row(context, vietnamese ? "Dùng phông chữ mặc định" : "Restore default font",
                zdsIcon(context, "auto_counter_clockwise", darkMode), false, darkMode, null);
            resetFont.setOnClickListener(view -> {
                CustomFont.reset(context);
                updateFontLabel.run();
                Toast.makeText(context, vietnamese ? "Mở lại Zalo để cập nhật phông chữ"
                    : "Restart Zalo to refresh text", Toast.LENGTH_LONG).show();
            });
            list.addView(resetFont);
        }
        if ((features & FEATURE_CUSTOM_SOUNDS) != 0) {
            addGroup(list, context, vietnamese ? "Âm thanh" : "Sounds", darkMode);
            addSoundRows(list, context, CustomSounds.NOTIFICATION, "notif",
                vietnamese ? "Âm báo tin nhắn" : "Notification sound", darkMode, vietnamese);
            addSoundRows(list, context, CustomSounds.RINGTONE, "call",
                vietnamese ? "Nhạc chuông cuộc gọi" : "Incoming-call ringtone", darkMode, vietnamese);
        }
        trimGroupDividers(list);
        page.show();
        page.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private static void addSoundRows(LinearLayout list, Context context, String kind, String icon, String title,
            boolean darkMode, boolean vietnamese) {
        View item = row(context, title, zdsIcon(context, icon, darkMode), true, darkMode, null);
        TextView text = rowTitle(item);
        Runnable update = () -> text.setText(title + (CustomSounds.isSelected(context, kind)
            ? (vietnamese ? " (đã chọn)" : " (selected)") : ""));
        update.run();
        item.setOnClickListener(view -> {
            if (!CustomSounds.pick(context, kind, update)) {
                Toast.makeText(context, vietnamese ? "Không thể mở trình chọn âm thanh"
                    : "Cannot open audio picker", Toast.LENGTH_SHORT).show();
            }
        });
        list.addView(item);
        View reset = row(context, vietnamese ? "Khôi phục: " + title : "Restore: " + title,
            zdsIcon(context, "auto_counter_clockwise", darkMode), false, darkMode, null);
        reset.setOnClickListener(view -> {
            CustomSounds.reset(context, kind);
            update.run();
        });
        list.addView(reset);
    }

    /** Settings row with a leading Zalo icon, a label and a trailing Zalo-style switch. */
    private static View toggleRow(LinearLayout list, Context context, String title, String icon, boolean checked,
            boolean darkMode, CompoundButton.OnCheckedChangeListener listener) {
        View wrapper = row(context, title, zdsIcon(context, icon, darkMode), false, darkMode, null);
        LinearLayout content = (LinearLayout) ((ViewGroup) wrapper).getChildAt(0);
        CompoundButton toggle = zaloSwitch(context, darkMode);
        toggle.setContentDescription(title);
        toggle.setChecked(checked);
        toggle.setOnCheckedChangeListener(listener);
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        toggleParams.leftMargin = dp(context, 12);
        content.addView(toggle, toggleParams);
        wrapper.setOnClickListener(view -> toggle.setChecked(!toggle.isChecked()));
        list.addView(wrapper);
        return wrapper;
    }

    /** Prefer Zalo's own design-system switch; fall back to a look-alike when it is unavailable. */
    private static CompoundButton zaloSwitch(Context context, boolean darkMode) {
        try {
            CompoundButton native_ = (CompoundButton) Class.forName("com.zing.zalo.zdesign.component.Switch")
                .getConstructor(Context.class).newInstance(context);
            int unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
            native_.measure(unspecified, unspecified);
            if (native_.getMeasuredWidth() > 0 && native_.getMeasuredHeight() > 0) return native_;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { }
        return new ZaloToggle(context, darkMode);
    }

    private static Drawable zdsIcon(Context context, String name, boolean darkMode) {
        int id = context.getResources().getIdentifier("zds_ic_" + name + "_line_24", "drawable",
            context.getPackageName());
        if (id == 0) return null;
        try {
            Drawable icon = context.getDrawable(id);
            if (icon == null) return null;
            icon = icon.mutate();
            icon.setTint(darkMode ? BLUE_DARK : BLUE);
            return icon;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static TextView rowTitle(View row) {
        Object title = row.getTag(ROW_TITLE_KEY);
        return title instanceof TextView ? (TextView) title : findTextView(row);
    }

    /** Hide the separator under the last visible row of each group, like Zalo's own lists. */
    private static void trimGroupDividers(LinearLayout list) {
        View lastRow = null;
        for (int index = 0; index <= list.getChildCount(); index++) {
            View child = index < list.getChildCount() ? list.getChildAt(index) : null;
            if (child != null && child.getVisibility() == View.GONE) continue;
            Object divider = child == null ? null : child.getTag(ROW_DIVIDER_KEY);
            if (child != null && divider instanceof View) {
                if (lastRow != null) ((View) lastRow.getTag(ROW_DIVIDER_KEY)).setVisibility(View.VISIBLE);
                lastRow = child;
                continue;
            }
            if (lastRow != null) ((View) lastRow.getTag(ROW_DIVIDER_KEY)).setVisibility(View.GONE);
            lastRow = null;
        }
    }

    private static Dialog page(Context context, String title, boolean darkMode) {
        Dialog page = new Dialog(context);
        page.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout screen = new LinearLayout(context);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(HEADER_BLUE);
        screen.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(0, insets.getSystemWindowInsetTop(), 0, 0);
            return insets;
        });
        LinearLayout bar = new LinearLayout(context);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(HEADER_BLUE);
        ImageView back = new ImageView(context);
        int backId = context.getResources().getIdentifier(
            "zds_ic_arrow_left_line_24", "drawable", context.getPackageName());
        if (backId != 0) back.setImageResource(backId);
        else {
            back.setImageDrawable(new LineIcon(0xFFFFFFFF, 1));
            back.setRotation(180f);
        }
        back.setImageTintList(ColorStateList.valueOf(0xFFFFFFFF));
        back.setScaleType(ImageView.ScaleType.CENTER);
        back.setPadding(dp(context, 16), 0, dp(context, 16), 0);
        back.setContentDescription("Back");
        back.setOnClickListener(view -> page.dismiss());
        bar.addView(back, new LinearLayout.LayoutParams(dp(context, 56), ViewGroup.LayoutParams.MATCH_PARENT));
        TextView pageTitle = label(context, title, darkMode);
        pageTitle.setTextColor(0xFFFFFFFF);
        bar.addView(pageTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        screen.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 56)));
        ScrollView scroll = new ScrollView(context);
        scroll.setBackgroundColor(sectionColor(context, darkMode));
        LinearLayout list = new LinearLayout(context);
        list.setTag("nezumi-list");
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        screen.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        page.setContentView(screen);
        Window window = page.getWindow();
        window.setBackgroundDrawable(new ColorDrawable(surfaceColor(context, darkMode)));
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setDimAmount(0f);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.setStatusBarContrastEnforced(false);
        window.setStatusBarColor(0x00000000);
        window.getDecorView().setSystemUiVisibility(
            (window.getDecorView().getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE) & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        return page;
    }

    /** Fallback drawn to Zalo's Switch.Medium spec: 40x24dp pill track, 20dp thumb, 2dp inset. */
    private static final class ZaloToggle extends CompoundButton {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF track = new RectF();
        private final int onColor;
        private final int offColor;
        private final int thumbColor;
        private float progress;
        private ValueAnimator animator;

        ZaloToggle(Context context, boolean darkMode) {
            super(context);
            onColor = attrColor(context, "switch_on_track", BLUE);
            offColor = darkMode ? 0xFF4A4D52 : attrColor(context, "switch_off_track", 0xFFC4C9CF);
            thumbColor = attrColor(context, "switch_handle", 0xFFFFFFFF);
            setButtonDrawable(null);
            setBackground(null);
            setClickable(true);
            progress = isChecked() ? 1f : 0f;
        }

        @Override public CharSequence getAccessibilityClassName() {
            return android.widget.Switch.class.getName();
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(dp(getContext(), 40), dp(getContext(), 24));
        }

        @Override public void setChecked(boolean checked) {
            boolean changed = checked != isChecked();
            super.setChecked(checked);
            if (!changed) return;
            float target = checked ? 1f : 0f;
            if (animator != null) animator.cancel();
            if (!isAttachedToWindow()) {
                progress = target;
                invalidate();
                return;
            }
            animator = ValueAnimator.ofFloat(progress, target);
            animator.setDuration(180);
            animator.addUpdateListener(animation -> {
                progress = (float) animation.getAnimatedValue();
                invalidate();
            });
            animator.start();
        }

        @Override protected void onDraw(Canvas canvas) {
            float width = getWidth();
            float height = getHeight();
            float radius = height / 2f;
            paint.setColor(blend(offColor, onColor, progress));
            if (!isEnabled()) paint.setAlpha(110);
            track.set(0, 0, width, height);
            canvas.drawRoundRect(track, radius, radius, paint);
            float inset = dp(getContext(), 2);
            float thumbRadius = radius - inset;
            float start = inset + thumbRadius;
            float end = width - inset - thumbRadius;
            paint.setColor(thumbColor);
            paint.setShadowLayer(inset, 0, inset / 2f, 0x33000000);
            canvas.drawCircle(start + (end - start) * progress, radius, thumbRadius, paint);
            paint.clearShadowLayer();
        }

        private static int blend(int from, int to, float ratio) {
            int a = (int) (((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * ratio);
            int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * ratio);
            int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * ratio);
            int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * ratio);
            return (a << 24) | (r << 16) | (g << 8) | b;
        }
    }

    private static int attrColor(Context context, String name, int fallback) {
        int attribute = context.getResources().getIdentifier(name, "attr", context.getPackageName());
        return attribute == 0 ? fallback : themeColor(context, attribute, fallback);
    }

    private static final class LineIcon extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int kind;

        LineIcon(int color, int kind) {
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2);
            paint.setStrokeCap(Paint.Cap.ROUND);
            this.kind = kind;
        }

        @Override public void draw(Canvas canvas) {
            float cx = getBounds().exactCenterX();
            float cy = getBounds().exactCenterY();
            float scale = Math.min(getBounds().width(), getBounds().height()) / 24f;
            paint.setStrokeWidth(1.8f * scale);
            if (kind == 1) {
                canvas.drawLine(cx - 3 * scale, cy - 5 * scale, cx + 2 * scale, cy, paint);
                canvas.drawLine(cx + 2 * scale, cy, cx - 3 * scale, cy + 5 * scale, paint);
                return;
            }
            if (kind == 2) {
                canvas.drawRoundRect(cx - 9 * scale, cy - 7 * scale, cx + 9 * scale,
                        cy + 5 * scale, 4 * scale, 4 * scale, paint);
                canvas.drawLine(cx - 4 * scale, cy + 5 * scale, cx - 7 * scale, cy + 9 * scale, paint);
                canvas.drawLine(cx - 7 * scale, cy + 9 * scale, cx + 1 * scale, cy + 5 * scale, paint);
                return;
            }
            canvas.drawCircle(cx, cy, 8 * scale, paint);
            canvas.drawCircle(cx, cy, 3 * scale, paint);
            for (int spoke = 0; spoke < 8; spoke++) {
                double angle = spoke * Math.PI / 4;
                canvas.drawLine(cx + (float) Math.cos(angle) * 8 * scale,
                        cy + (float) Math.sin(angle) * 8 * scale,
                        cx + (float) Math.cos(angle) * 10 * scale,
                        cy + (float) Math.sin(angle) * 10 * scale, paint);
            }
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}