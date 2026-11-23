package app.template.extension.extension;

import android.content.Context;
import android.view.View;
import android.widget.TextView;
import java.util.List;

/**
 * Patch-facing entry points. Patches reference these static methods by name, and
 * ViewOnlineStatusPrivatelyPatch rewrites the bridge stubs here, so signatures must stay stable.
 * Logic lives in the feature classes this facade delegates to.
 */
public final class NezumiSettings {
    private NezumiSettings() { }

    public static boolean isReactionEnabled(Context context) {
        return NezumiPrefs.bool(context, NezumiPrefs.REACTION, true);
    }

    public static boolean isDiscoveryHidden() {
        return NezumiPrefs.bool(ZaloEnv.appContext(), NezumiPrefs.HIDE_DISCOVERY, true);
    }

    public static boolean isTypingHidden(Context context) {
        return NezumiPrefs.bool(context, NezumiPrefs.HIDE_TYPING, false);
    }

    public static boolean isReadReceiptsHidden(Context context) {
        return NezumiPrefs.bool(context, NezumiPrefs.HIDE_READ_RECEIPTS, false);
    }

    public static boolean isDeliveryReceiptsHidden(Context context) {
        return NezumiPrefs.bool(context, NezumiPrefs.HIDE_DELIVERY_RECEIPTS, false);
    }

    public static boolean isMediaBoxHidden(Context context) {
        return NezumiPrefs.bool(context, NezumiPrefs.HIDE_MEDIA_BOX, false);
    }

    public static boolean isBusinessBoxHidden(Context context) {
        return NezumiPrefs.bool(context, NezumiPrefs.HIDE_BUSINESS_BOX, false);
    }

    public static boolean isOAMessagesHidden(Context context) {
        return NezumiPrefs.bool(context, NezumiPrefs.HIDE_OA_MESSAGES, true);
    }

    public static boolean isMessageAdsHidden(Context context) {
        return NezumiPrefs.bool(context, NezumiPrefs.HIDE_MESSAGE_ADS, false);
    }

    public static boolean isOAMessagesMuted(Context context) {
        return NezumiPrefs.bool(context, NezumiPrefs.MUTE_OA_MESSAGES, true);
    }

    static boolean isFeedAdsHidden(Context context) {
        return NezumiPrefs.bool(context, NezumiPrefs.HIDE_FEED_ADS, false);
    }

    public static boolean isRecalledMessagesKept(Context context) {
        return NezumiPrefs.bool(context, NezumiPrefs.KEEP_RECALLED_MESSAGES, false);
    }

    public static void requestChatOnlineList() {
        throw new IllegalStateException("Chat online request bridge was not patched");
    }

    static boolean isOnlineHidden() {
        return NezumiPrefs.bool(ZaloEnv.appContext(), NezumiPrefs.HIDE_ONLINE_STATUS, false);
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
        NezumiSettingsPage.attach(root, anchor, NezumiSettingsPage.FEATURE_REACTION);
    }

    public static void attachTyping(View root, View anchor) {
        NezumiSettingsPage.attach(root, anchor, NezumiSettingsPage.FEATURE_TYPING);
    }

    public static void attachReadReceipts(View root, View anchor) {
        NezumiSettingsPage.attach(root, anchor, NezumiSettingsPage.FEATURE_READ_RECEIPTS);
    }

    public static void attachDeliveryReceipts(View root, View anchor) {
        NezumiSettingsPage.attach(root, anchor, NezumiSettingsPage.FEATURE_DELIVERY_RECEIPTS);
    }

    public static void attachMediaBox(View root, View anchor) {
        NezumiSettingsPage.attach(root, anchor, NezumiSettingsPage.FEATURE_MEDIA_BOX);
    }

    public static void attachBusinessBox(View root, View anchor) {
        NezumiSettingsPage.attach(root, anchor, NezumiSettingsPage.FEATURE_BUSINESS_BOX);
    }

    public static void attachOAMessages(View root, View anchor) {
        NezumiSettingsPage.attach(root, anchor, NezumiSettingsPage.FEATURE_OA_MESSAGES);
    }

    public static void attachMessageAds(View root, View anchor) {
        NezumiSettingsPage.attach(root, anchor, NezumiSettingsPage.FEATURE_MESSAGE_ADS);
    }

    public static void attachFeedAds(View root, View anchor) {
        NezumiSettingsPage.attach(root, anchor, NezumiSettingsPage.FEATURE_FEED_ADS);
    }

    public static void attachRecalledMessages(View root, View anchor) {
        NezumiSettingsPage.attach(root, anchor, NezumiSettingsPage.FEATURE_RECALLED_MESSAGES);
    }

    public static void attachOnlineStatus(View root, View anchor) {
        NezumiSettingsPage.attach(root, anchor, NezumiSettingsPage.FEATURE_ONLINE_STATUS);
    }

    public static void attachDiscovery(View root, View anchor) {
        NezumiSettingsPage.attach(root, anchor, NezumiSettingsPage.FEATURE_DISCOVERY);
    }

    public static void attachCustomFont(View root, View anchor) {
        NezumiSettingsPage.attach(root, anchor, NezumiSettingsPage.FEATURE_CUSTOM_FONT);
    }

    public static void attachCustomSounds(View root, View anchor) {
        NezumiSettingsPage.attach(root, anchor, NezumiSettingsPage.FEATURE_CUSTOM_SOUNDS);
    }

    public static void setOAMessagesHidden(Context context, boolean hidden) {
        OfficialAccounts.setOAMessagesHidden(context, hidden);
    }

    public static void setOAMessagesMuted(Context context, boolean muted) {
        OfficialAccounts.setOAMessagesMuted(context, muted);
    }

    public static List filterOfficialAccounts(List items) {
        return OfficialAccounts.filterOfficialAccounts(items);
    }

    public static List filterFeedAds(List items) {
        return FeedAds.filterFeedAds(items);
    }

    public static boolean shouldKeepIncomingRecall(String senderId, int eventType) {
        return RecalledMessages.shouldKeepIncomingRecall(senderId, eventType);
    }

    public static void markIncomingRecall(Object messageId) {
        RecalledMessages.markIncomingRecall(messageId);
    }

    public static boolean isIncomingRecall(Object message) {
        return RecalledMessages.isIncomingRecall(message);
    }

    public static boolean hasRecallLabel(Object row) {
        return RecalledMessages.hasRecallLabel(row);
    }

    public static String formatRecallTime(View row, String original) {
        return RecalledMessages.formatRecallTime(row, original);
    }

    public static void trackRecallRow(View row) {
        RecalledMessages.trackRecallRow(row);
    }

    public static String recalledLabel(Context context) {
        return RecalledMessages.recalledLabel(context);
    }

    public static boolean shouldFetchOnlineStatus() {
        return OnlineStatus.shouldFetchOnlineStatus();
    }

    public static void traceOnlineResponse(org.json.JSONObject data) {
        OnlineStatus.traceOnlineResponse(data);
    }

    public static void traceLastSeenResponse(org.json.JSONObject data) {
        OnlineStatus.traceLastSeenResponse(data);
    }

    public static void traceFriendListResponse(org.json.JSONObject data) {
        OnlineStatus.traceFriendListResponse(data);
    }

    public static void updateChatOnlineUsers(org.json.JSONObject data) {
        OnlineStatus.updateChatOnlineUsers(data);
    }

    public static void setChatPresenceTarget(Object chatView, android.os.Bundle arguments) {
        OnlineStatus.setChatPresenceTarget(chatView, arguments);
    }

    public static void bindChatSubtitle(Object chatView, TextView subtitle) {
        OnlineStatus.bindChatSubtitle(chatView, subtitle);
    }

    public static boolean isChatOnlineRefresh() {
        return OnlineStatus.isChatOnlineRefresh();
    }

    public static long onlineRefreshInterval() {
        return OnlineStatus.onlineRefreshInterval();
    }

    public static void traceChatOnlineDispatch() {
        OnlineStatus.traceChatOnlineDispatch();
    }

    public static void traceChatOnlineGate(Object repository) {
        OnlineStatus.traceChatOnlineGate(repository);
    }

    public static boolean retainChatOnlineSubtitle(TextView subtitle) {
        return OnlineStatus.retainChatOnlineSubtitle(subtitle);
    }

    public static int mapOnlineState(int state) {
        return OnlineStatus.mapOnlineState(state);
    }
}
