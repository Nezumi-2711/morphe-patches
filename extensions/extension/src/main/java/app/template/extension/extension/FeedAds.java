package app.template.extension.extension;

import java.util.ArrayList;
import java.util.List;

/** Removes sponsored items from the Timeline feed. */
final class FeedAds {
    private FeedAds() { }

    public static List filterFeedAds(List items) {
        if (!NezumiSettings.isFeedAdsHidden(ZaloEnv.appContext()) || items == null || items.isEmpty()) return items;
        ArrayList filtered = new ArrayList(items.size());
        for (Object item : items) {
            if (!isFeedAdItem(item)) filtered.add(item);
        }
        return filtered;
    }

    private static boolean isFeedAdItem(Object item) {
        try {
            return item != null && item.getClass().getField("c").getInt(item) == 0x51;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }
}
