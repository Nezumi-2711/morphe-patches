package app.template.extension.zalo.chat.richtext;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

import app.template.extension.zalo.buglog.NezumiLog;
import app.template.extension.zalo.buglog.Patch;
import app.template.extension.zalo.chat.richtext.RichTextRun.Kind;
import app.template.extension.zalo.settings.NezumiPrefs;
import app.template.extension.zalo.shared.ZaloEnv;

/**
 * Formatting toolbar UI of the "Rich text formatting" patch, in Zalo's chat input bar.
 *
 * <p>Hooks: {@link #onRightFunctions} at the end of {@code ChatInputBar.setupLayoutRightFunctions}
 * puts the "Aa" button first in the right-functions group (more / mic / image), so it hides with
 * them while the input has text. {@link #onInputBarCreated} at the end of the input bar constructor
 * adds the toolbar row at the bottom of the bar, directly under the input row, and gives the input
 * row a matching bottom margin while the toolbar is open. The composer's own selection popup (cut /
 * copy / paste) opens above the selected text, so it no longer covers the toolbar. Popovers (size,
 * color) are PopupWindows above the toolbar, over the input row.
 */
public final class RichTextToolbar {
    private RichTextToolbar() { }

    private static final String TAG = "RichTextToolbar";
    private static final int CONTROLLER_KEY = 0x7E4E5A21;
    private static final String TOGGLE_TAG = "nezumi_rich_text_aa";

    private static final int BLUE = 0xFF0068FF;
    private static final int BLUE_DARK = 0xFF3D8BFF;

    private static final int ROW_HEIGHT_DP = 40;
    private static final int BUTTON_WIDTH_DP = 40;
    private static final int BUTTON_HEIGHT_DP = 36;
    private static final int ICON_DP = 20;
    private static final long OPEN_MS = 200;
    private static final long CLOSE_MS = 160;
    private static final long POPOVER_OPEN_MS = 180;
    private static final long POPOVER_CLOSE_MS = 120;
    /** A tap on the open popover's own button right after the outside touch closed it must not reopen it. */
    private static final long REOPEN_GUARD_MS = 400;

    private static final int POPOVER_SIZE = 1;
    private static final int POPOVER_COLOR = 2;

    /** Web size presets; normal has no token. */
    private static final int[] SIZE_PRESETS = { 13, RichTextStyle.NORMAL_SIZE, 18, 20 };

    // ---- Hooks ----

    /** Hook: end of the ChatInputBar constructor (all child views exist). */
    public static void onInputBarCreated(FrameLayout bar) {
        try {
            if (bar == null) {
                NezumiLog.w(Patch.RICH_TEXT_FORMATTING, TAG, RichTextLog.UI + "input bar: skipped (bar is null)");
                return;
            }
            if (!RichTextSend.isEnabled()) {
                NezumiLog.i(Patch.RICH_TEXT_FORMATTING, TAG, RichTextLog.UI + "input bar: skipped (feature disabled)");
                return;
            }
            Controller controller = Controller.of(bar, true);
            if (controller != null) controller.install();
        } catch (RuntimeException | LinkageError failure) {
            NezumiLog.swallowedOnce(Patch.RICH_TEXT_FORMATTING, TAG, "formatting toolbar setup failed", failure);
        }
    }

    /** Hook: end of ChatInputBar.setupLayoutRightFunctions, after Zalo refilled {@code functions}. */
    public static void onRightFunctions(FrameLayout bar, LinearLayout functions) {
        try {
            String skip = bar == null ? "bar is null" : functions == null ? "functions group is null"
                : !RichTextSend.isEnabled() ? "feature disabled" : null;
            // Zalo refills the group often: log the skip reason only when it changes.
            if (RIGHT_FUNCTIONS.changed(skip == null ? "ok" : skip) && skip != null) {
                NezumiLog.i(Patch.RICH_TEXT_FORMATTING, TAG, RichTextLog.UI + "Aa: skipped (" + skip + ")");
            }
            if (skip != null) {
                // Turned off after the bar was built: drop the Aa button and fold the toolbar away.
                Controller existing = bar == null || functions == null ? null : Controller.of(bar, false);
                if (existing != null) existing.onFeatureDisabled();
                return;
            }
            Controller controller = Controller.of(bar, true);
            if (controller != null) controller.placeToggle(functions);
        } catch (RuntimeException | LinkageError failure) {
            NezumiLog.swallowedOnce(Patch.RICH_TEXT_FORMATTING, TAG, "Aa button setup failed", failure);
        }
    }

    private static final RichTextLog.Latch RIGHT_FUNCTIONS = new RichTextLog.Latch();
    /** Both hooks look the composer up again while it is missing: sampled. */
    private static final RichTextLog.Sampler NO_COMPOSER = new RichTextLog.Sampler();

    // ---- Controller ----

    private static final class Controller implements RichTextEditor.Listener {
        private final FrameLayout bar;
        private final ViewGroup inputRow;
        private final EditText edit;
        private final Context context;
        private final float density;
        private final boolean dark;
        private final boolean vietnamese;
        private final int accent;
        private final int accentFill;
        private final int iconColor;
        private final int dividerColor;
        private final int separatorColor;
        private final int textColor;
        private final int popoverColor;
        private final int rowHeight;

        private RichTextEditor editor;
        private FrameLayout host;
        private ToggleButton toggle;
        private ToolButton bold;
        private ToolButton italic;
        private ToolButton underline;
        private ToolButton strike;
        private ToolButton size;
        private ToolButton color;
        private ToolButton bullets;
        private ToolButton numbers;
        private ToolButton outdent;
        private ToolButton indent;
        private boolean installed;
        private boolean open;
        private int revealed;
        private ValueAnimator animator;
        private int animatorTarget = -1;

        private PopupWindow popover;
        private View popoverCard;
        private int popoverKind;
        private View popoverAnchor;
        private int guardKind;
        private long guardUntil;

        private final ViewTreeObserver.OnGlobalLayoutListener layoutListener = this::onGlobalLayout;
        /** How the composer was found (log only). */
        private String composerSource = "?";
        private final RichTextLog.Sampler placements = new RichTextLog.Sampler();
        private final RichTextLog.Latch visibility = new RichTextLog.Latch();

        static Controller of(FrameLayout bar, boolean create) {
            Object existing = bar.getTag(CONTROLLER_KEY);
            if (existing instanceof Controller) return (Controller) existing;
            if (!create) return null;
            EditText edit = findComposer(bar);
            if (edit == null || !(edit.getParent() instanceof ViewGroup)) {
                if (NO_COMPOSER.next()) {
                    NezumiLog.w(Patch.RICH_TEXT_FORMATTING, TAG, RichTextLog.UI + "input bar: skipped (composer EditText not found"
                        + " in the input bar" + (edit != null ? ", it has no parent group" : "") + ") seen=" + NO_COMPOSER.count());
                }
                return null;
            }
            Controller controller = new Controller(bar, edit);
            bar.setTag(CONTROLLER_KEY, controller);
            int id = bar.getContext().getResources().getIdentifier("chatinput_text", "id", bar.getContext().getPackageName());
            controller.composerSource = id != 0 && edit.getId() == id ? "id chatinput_text" : "first EditText (id lookup failed)";
            return controller;
        }

        private static EditText findComposer(FrameLayout bar) {
            Context context = bar.getContext();
            int id = context.getResources().getIdentifier("chatinput_text", "id", context.getPackageName());
            View view = id == 0 ? null : bar.findViewById(id);
            if (view instanceof EditText) return (EditText) view;
            return firstEditText(bar);
        }

        private static EditText firstEditText(View view) {
            if (view instanceof EditText) return (EditText) view;
            if (!(view instanceof ViewGroup)) return null;
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                EditText found = firstEditText(group.getChildAt(index));
                if (found != null) return found;
            }
            return null;
        }

        private Controller(FrameLayout bar, EditText edit) {
            this.bar = bar;
            this.edit = edit;
            this.inputRow = (ViewGroup) edit.getParent();
            this.context = bar.getContext();
            this.density = context.getResources().getDisplayMetrics().density;
            this.dark = isDarkTheme(context);
            this.vietnamese = ZaloEnv.isVietnamese(Patch.RICH_TEXT_FORMATTING, context);
            this.accent = dark ? BLUE_DARK : BLUE;
            this.accentFill = (accent & 0x00FFFFFF) | (dark ? 0x33000000 : 0x1F000000);
            this.iconColor = colorResource(context, "chat_bar_icon_color_statelist",
                attrColor(context, "icon_01", dark ? 0xFFB9BDC1 : 0xFF5A6981));
            this.dividerColor = attrColor(context, "divider_01", dark ? 0xFF3A3D41 : 0xFFE1E4E8);
            this.separatorColor = attrColor(context, "ChatSeparator", dividerColor);
            this.textColor = attrColor(context, "text_01", dark ? 0xFFE6E8EA : 0xFF081C36);
            this.popoverColor = dark ? 0xFF2B2D31 : 0xFFFFFFFF;
            this.rowHeight = dp(ROW_HEIGHT_DP);
            this.open = NezumiPrefs.bool(context, NezumiPrefs.RICH_TEXT_TOOLBAR_OPEN, false);
        }

        private int dp(float value) {
            return Math.round(value * density);
        }

        private String text(String vi, String en) {
            return vietnamese ? vi : en;
        }

        // ---- Aa button ----

        void placeToggle(LinearLayout functions) {
            if (toggle == null) {
                toggle = new ToggleButton(context, iconColor, accent);
                toggle.setTag(TOGGLE_TAG);
                String label = text("Định dạng văn bản", "Text formatting");
                toggle.setContentDescription(label);
                toggle.setTooltipText(label);
                TypedValue ripple = new TypedValue();
                if (context.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)) {
                    toggle.setBackgroundResource(ripple.resourceId);
                }
                toggle.setOnClickListener(view -> onToggleClicked());
            }
            if (!installed) {
                // The feature was off when the input bar was built: install now, outside Zalo's refill.
                bar.post(() -> {
                    try {
                        ensureInstalled("Aa placed");
                    } catch (RuntimeException failure) {
                        NezumiLog.swallowedOnce(Patch.RICH_TEXT_FORMATTING, TAG, "lazy toolbar setup failed", failure);
                    }
                });
            }
            toggle.setVisibility(View.VISIBLE);
            if (toggle.getParent() instanceof ViewGroup) ((ViewGroup) toggle.getParent()).removeView(toggle);
            int width = dimension("input_bar_button_width", functions.getChildCount() > 0
                && functions.getChildAt(0).getLayoutParams() != null ? functions.getChildAt(0).getLayoutParams().width : dp(48));
            ViewGroup.LayoutParams functionsParams = functions.getLayoutParams();
            int height = functionsParams != null && functionsParams.height > 0 ? functionsParams.height
                : dimension("min_height_input_row", dp(48));
            toggle.setPadding(dp(8), 0, dp(8), 0);
            toggle.setActive(open);
            functions.addView(toggle, 0, new LinearLayout.LayoutParams(width, height));
            if (placements.next()) {
                NezumiLog.i(Patch.RICH_TEXT_FORMATTING, TAG, RichTextLog.UI + "Aa: added at 0 of " + functions.getChildCount()
                    + " size=" + width + "x" + height + " active=" + open + " placements=" + placements.count());
            }
        }

        private void onToggleClicked() {
            try {
                if (!RichTextSend.isEnabled()) {
                    NezumiLog.i(Patch.RICH_TEXT_FORMATTING, TAG, RichTextLog.UI + "Aa: tapped while the feature is off, hiding");
                    onFeatureDisabled();
                    return;
                }
                ensureInstalled("Aa tapped");
                setOpen(!open, true);
            } catch (RuntimeException failure) {
                NezumiLog.swallowedOnce(Patch.RICH_TEXT_FORMATTING, TAG, "Aa action failed", failure);
            }
        }

        /** Installs the toolbar if {@link #onInputBarCreated} skipped it because the feature was off then. */
        private void ensureInstalled(String reason) {
            if (installed || !RichTextSend.isEnabled()) return;
            NezumiLog.i(Patch.RICH_TEXT_FORMATTING, TAG, RichTextLog.UI + "input bar: lazy install (" + reason + ")");
            install();
        }

        /**
         * The feature was turned off while this bar exists: hide the Aa button and fold the toolbar
         * away. The saved open state is kept for when the feature is turned back on.
         */
        void onFeatureDisabled() {
            boolean wasShown = toggle != null && toggle.getVisibility() == View.VISIBLE;
            boolean wasRevealed = revealed > 0;
            if (toggle != null) {
                toggle.setVisibility(View.GONE);
                if (toggle.getParent() instanceof ViewGroup) ((ViewGroup) toggle.getParent()).removeView(toggle);
            }
            closePopover(false);
            sync(false);
            if (wasShown || wasRevealed) {
                NezumiLog.i(Patch.RICH_TEXT_FORMATTING, TAG, RichTextLog.UI + "feature turned off: Aa removed, toolbar hidden"
                    + " installed=" + installed + " wasOpen=" + open);
            }
        }

        private int dimension(String name, int fallback) {
            Resources resources = context.getResources();
            int id = resources.getIdentifier(name, "dimen", context.getPackageName());
            if (id == 0) return fallback;
            try {
                return resources.getDimensionPixelSize(id);
            } catch (Resources.NotFoundException failure) {
                return fallback;
            }
        }

        // ---- Toolbar row ----

        void install() {
            if (installed) return;
            installed = true;
            editor = new RichTextEditor(edit, this);
            host = new FrameLayout(context);
            host.setClipChildren(true);
            host.setVisibility(View.GONE);
            // The row hangs from the host's top edge, so it unrolls downward from the input row.
            host.addView(buildRow(), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowHeight,
                Gravity.TOP));
            View border = new View(context);
            border.setBackgroundColor(separatorColor);
            border.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            host.addView(border, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1, Gravity.TOP));
            int index = bar.indexOfChild(inputRow);
            bar.addView(host, index < 0 ? bar.getChildCount() : index + 1,
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, Gravity.BOTTOM));
            bar.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View view) {
                    try {
                        view.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
                        sync(false);
                    } catch (RuntimeException failure) {
                        NezumiLog.swallowedOnce(Patch.RICH_TEXT_FORMATTING, TAG, "toolbar attach failed", failure);
                    }
                }

                @Override
                public void onViewDetachedFromWindow(View view) {
                    try {
                        view.getViewTreeObserver().removeOnGlobalLayoutListener(layoutListener);
                        closePopover(false);
                        if (animator != null) animator.cancel();
                    } catch (RuntimeException failure) {
                        NezumiLog.swallowedOnce(Patch.RICH_TEXT_FORMATTING, TAG, "toolbar detach failed", failure);
                    }
                }
            });
            if (bar.isAttachedToWindow()) bar.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
            sync(false);
            onStateChanged();
            NezumiLog.i(Patch.RICH_TEXT_FORMATTING, TAG, RichTextLog.UI + "input bar: toolbar installed composer=" + composerSource
                + " inputRowIndex=" + index + "/" + bar.getChildCount() + " open=" + open + " attached=" + bar.isAttachedToWindow());
        }

        private View buildRow() {
            HorizontalScrollView scroll = new HorizontalScrollView(context);
            scroll.setHorizontalScrollBarEnabled(false);
            scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
            scroll.setHorizontalFadingEdgeEnabled(true);
            scroll.setFadingEdgeLength(dp(24));
            scroll.setFillViewport(false);
            // The size popover follows its button while the row scrolls.
            scroll.setOnScrollChangeListener((view, x, y, oldX, oldY) -> {
                if (popover != null && popover.isShowing()) positionPopover(false);
            });
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(4), 0, dp(4), 0);
            bold = addButton(row, RichTextIcons.BOLD, text("In đậm", "Bold"), () -> editor.toggleInline(Kind.BOLD));
            italic = addButton(row, RichTextIcons.ITALIC, text("In nghiêng", "Italic"), () -> editor.toggleInline(Kind.ITALIC));
            underline = addButton(row, RichTextIcons.UNDERLINE, text("Gạch chân", "Underline"),
                () -> editor.toggleInline(Kind.UNDERLINE));
            strike = addButton(row, RichTextIcons.STRIKE, text("Gạch ngang", "Strikethrough"),
                () -> editor.toggleInline(Kind.STRIKE));
            addSeparator(row);
            size = addButton(row, RichTextIcons.SIZE, text("Cỡ chữ", "Text size"), null);
            size.setOnClickListener(view -> togglePopover(POPOVER_SIZE, view));
            color = addButton(row, null, text("Màu chữ", "Text color"), null);
            color.setOnClickListener(view -> togglePopover(POPOVER_COLOR, view));
            addSeparator(row);
            bullets = addButton(row, RichTextIcons.LIST_BULLETED, text("Danh sách dấu đầu dòng", "Bulleted list"),
                () -> editor.toggleList(RichTextRun.LIST_BULLET));
            numbers = addButton(row, RichTextIcons.LIST_NUMBERED, text("Danh sách đánh số", "Numbered list"),
                () -> editor.toggleList(RichTextRun.LIST_NUMBERED));
            addSeparator(row);
            outdent = addButton(row, RichTextIcons.INDENT_DECREASE, text("Giảm thụt lề", "Decrease indent"),
                () -> editor.changeIndent(-1));
            indent = addButton(row, RichTextIcons.INDENT_INCREASE, text("Tăng thụt lề", "Increase indent"),
                () -> editor.changeIndent(1));
            addSeparator(row);
            addButton(row, RichTextIcons.CLEAR, text("Xóa định dạng", "Clear formatting"), editor::clearFormatting);
            scroll.addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, rowHeight));
            return scroll;
        }

        private ToolButton addButton(LinearLayout row, String icon, String label, Runnable action) {
            ToolButton button = new ToolButton(context, icon, density, iconColor, accent, accentFill);
            button.setContentDescription(label);
            button.setTooltipText(label);
            if (action != null) {
                button.setOnClickListener(view -> {
                    try {
                        closePopover(true);
                        action.run();
                    } catch (RuntimeException failure) {
                        NezumiLog.swallowedOnce(Patch.RICH_TEXT_FORMATTING, TAG, "formatting action failed", failure);
                    }
                });
            }
            row.addView(button, new LinearLayout.LayoutParams(dp(BUTTON_WIDTH_DP), dp(BUTTON_HEIGHT_DP)));
            return button;
        }

        private void addSeparator(LinearLayout row) {
            View separator = new View(context);
            separator.setBackgroundColor(dividerColor);
            separator.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(Math.max(1, dp(1)), dp(20));
            params.leftMargin = dp(4);
            params.rightMargin = dp(4);
            row.addView(separator, params);
        }

        private void setOpen(boolean value, boolean animate) {
            NezumiLog.i(Patch.RICH_TEXT_FORMATTING, TAG, RichTextLog.UI + "toolbar " + (value ? "opened" : "closed")
                + " inputRowVisible=" + (inputRow.getVisibility() == View.VISIBLE));
            open = value;
            NezumiPrefs.of(context).edit().putBoolean(NezumiPrefs.RICH_TEXT_TOOLBAR_OPEN, value).apply();
            if (toggle != null) toggle.setActive(value);
            if (!value) closePopover(true);
            sync(animate);
        }

        private boolean shouldShow() {
            return installed && open && inputRow.getVisibility() == View.VISIBLE && RichTextSend.isEnabled();
        }

        /** Moves the toolbar towards its open or closed height, animated or at once. */
        private void sync(boolean animate) {
            if (!installed) return;
            int target = shouldShow() ? rowHeight : 0;
            if (animator != null) {
                if (animatorTarget == target) return;
                animator.cancel();
            }
            if (revealed == target && (target > 0) == (host.getVisibility() == View.VISIBLE)) return;
            if (!animate || !bar.isAttachedToWindow() || bar.getWidth() == 0) {
                applyReveal(target);
                return;
            }
            int from = revealed;
            ValueAnimator next = ValueAnimator.ofInt(from, target);
            next.setDuration(target > from ? OPEN_MS : CLOSE_MS);
            next.setInterpolator(new PathInterpolator(0.4f, 0f, 0.2f, 1f));
            next.addUpdateListener(animation -> applyReveal((int) animation.getAnimatedValue()));
            next.addListener(new AnimatorListenerAdapter() {
                private boolean cancelled;

                @Override
                public void onAnimationCancel(Animator animation) {
                    cancelled = true;
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (animator == animation) {
                        animator = null;
                        animatorTarget = -1;
                    }
                    if (!cancelled) applyReveal(target);
                }
            });
            animator = next;
            animatorTarget = target;
            next.start();
        }

        /**
         * The toolbar shows {@code pixels} of its height under the input row; the input row's bottom
         * margin grows by the same amount so the bar's wrap-content height includes the toolbar.
         */
        private void applyReveal(int pixels) {
            revealed = Math.max(0, Math.min(rowHeight, pixels));
            host.setVisibility(revealed > 0 ? View.VISIBLE : View.GONE);
            host.setAlpha(rowHeight == 0 ? 1f : revealed / (float) rowHeight);
            ViewGroup.LayoutParams hostParams = host.getLayoutParams();
            if (hostParams.height != revealed) {
                hostParams.height = revealed;
                host.setLayoutParams(hostParams);
            }
            if (inputRow.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams rowParams = (ViewGroup.MarginLayoutParams) inputRow.getLayoutParams();
                if (rowParams.bottomMargin != revealed) {
                    rowParams.bottomMargin = revealed;
                    inputRow.setLayoutParams(rowParams);
                }
            }
        }

        private void onGlobalLayout() {
            try {
                // Zalo swaps the input row for other panels (photo quality, blocked notice): follow it.
                if (installed && animator == null) sync(false);
                if (popover != null && popover.isShowing() && !RichTextSend.isEnabled()) closePopover(false);
                if (popover != null && popover.isShowing()) {
                    if (!host.isShown()) closePopover(false);
                    else positionPopover(false);
                }
                logVisibility();
            } catch (RuntimeException failure) {
                NezumiLog.swallowedOnce(Patch.RICH_TEXT_FORMATTING, TAG, "toolbar layout sync failed", failure);
            }
        }

        /**
         * Logs where the toolbar and the Aa button stand when that changes (not per layout pass):
         * the row follows the input row, which Zalo swaps out for photo / blocked panels, and Zalo
         * hides the right-functions group (with Aa) while the composer has text.
         */
        private void logVisibility() {
            if (!installed) return;
            String row = !open ? "closed" : !RichTextSend.isEnabled() ? "hidden (feature disabled)"
                : inputRow.getVisibility() != View.VISIBLE ? "hidden (input row not visible)" : "open";
            String aa = toggle == null ? "none" : toggle.isShown() ? "shown" : "hidden";
            String state = "toolbar=" + row + " aa=" + aa;
            if (visibility.changed(state)) NezumiLog.i(Patch.RICH_TEXT_FORMATTING, TAG, RichTextLog.UI + state);
        }

        @Override
        public void onStateChanged() {
            if (!installed || editor == null) return;
            RichTextEditor.State state = editor.state();
            bold.setActive(state.style.bold);
            italic.setActive(state.style.italic);
            underline.setActive(state.style.underline);
            strike.setActive(state.style.strike);
            size.setActive(popoverKind == POPOVER_SIZE || state.style.size != RichTextStyle.NORMAL_SIZE);
            color.setActive(popoverKind == POPOVER_COLOR);
            color.setColorBar(state.style.color == RichTextStyle.NO_COLOR ? edit.getCurrentTextColor()
                : 0xFF000000 | state.style.color);
            bullets.setActive(state.bulletList);
            numbers.setActive(state.numberedList);
            outdent.setEnabled(state.canOutdent);
            indent.setEnabled(state.canIndent);
            if (popover != null && popoverCard != null) updatePopoverSelection(state.style);
        }

        // ---- Popovers ----

        private void togglePopover(int kind, View anchor) {
            try {
                if (popover != null && popoverKind == kind) {
                    closePopover(true);
                    return;
                }
                if (guardKind == kind && SystemClock.uptimeMillis() < guardUntil) {
                    guardKind = 0;
                    return;
                }
                closePopover(false);
                openPopover(kind, anchor);
            } catch (RuntimeException failure) {
                NezumiLog.w(Patch.RICH_TEXT_FORMATTING, TAG, RichTextLog.UI + "popover " + popoverName(kind) + " open failed: "
                    + failure.getClass().getSimpleName());
                NezumiLog.swallowedOnce(Patch.RICH_TEXT_FORMATTING, TAG, "popover failed", failure);
            }
        }

        private void openPopover(int kind, View anchor) {
            RichTextConfig config = RichTextConfig.current();
            int pad = dp(8);
            FrameLayout root = new FrameLayout(context);
            root.setPadding(pad, pad, pad, pad);
            root.setClipToPadding(false);
            root.setClipChildren(false);
            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            GradientDrawable background = new GradientDrawable();
            background.setColor(popoverColor);
            background.setCornerRadius(dp(12));
            if (dark) background.setStroke(Math.max(1, dp(1) / 2), dividerColor);
            card.setBackground(background);
            card.setElevation(dp(6));
            card.setPadding(dp(6), dp(6), dp(6), dp(6));
            RichTextStyle style = editor.state().style;
            int width;
            if (kind == POPOVER_SIZE) {
                buildSizeChips(card, config, style);
                width = ViewGroup.LayoutParams.WRAP_CONTENT;
            } else {
                buildSwatches(card, config, style);
                width = Math.max(host.getWidth(), dp(240));
            }
            root.addView(card, new FrameLayout.LayoutParams(
                kind == POPOVER_SIZE ? ViewGroup.LayoutParams.WRAP_CONTENT : ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
            int widthSpec = width == ViewGroup.LayoutParams.WRAP_CONTENT
                ? View.MeasureSpec.makeMeasureSpec(bar.getRootView().getWidth(), View.MeasureSpec.AT_MOST)
                : View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
            root.measure(widthSpec, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));

            PopupWindow window = new PopupWindow(root, root.getMeasuredWidth(), root.getMeasuredHeight(), false);
            window.setBackgroundDrawable(new ColorDrawable(0));
            window.setOutsideTouchable(true);
            window.setTouchable(true);
            window.setAnimationStyle(0);
            window.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
            window.setTouchInterceptor((view, event) -> {
                if (event.getAction() != MotionEvent.ACTION_OUTSIDE) return false;
                guardKind = kind;
                guardUntil = SystemClock.uptimeMillis() + REOPEN_GUARD_MS;
                closePopover(true);
                return true;
            });
            window.setOnDismissListener(() -> {
                if (popover == window) {
                    popover = null;
                    popoverCard = null;
                    popoverKind = 0;
                    onStateChanged();
                }
            });
            popover = window;
            popoverCard = card;
            popoverKind = kind;
            popoverAnchor = anchor;
            positionPopover(true);
            onStateChanged();
            NezumiLog.i(Patch.RICH_TEXT_FORMATTING, TAG, RichTextLog.UI + "popover " + popoverName(kind) + " opened items="
                + card.getChildCount() + " size=" + config.sizeMin + ".." + config.sizeMax + " colors=" + config.colors.size());

            // Open: 8dp slide up, scale .97 -> 1 and fade in, pivoting on the edge next to the toolbar.
            card.setAlpha(0f);
            card.setTranslationY(dp(8));
            card.setScaleX(0.97f);
            card.setScaleY(0.97f);
            card.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View view, int left, int top, int right, int bottom, int oldLeft,
                        int oldTop, int oldRight, int oldBottom) {
                    view.removeOnLayoutChangeListener(this);
                    view.setPivotX(anchorCenterIn(view));
                    view.setPivotY(view.getHeight());
                    view.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                        .setDuration(POPOVER_OPEN_MS).setInterpolator(new DecelerateInterpolator()).start();
                }
            });
        }

        /** Anchor button center in {@code card} coordinates (pivot for the open animation). */
        private float anchorCenterIn(View card) {
            if (popoverAnchor == null) return card.getWidth() / 2f;
            int[] anchor = new int[2];
            int[] target = new int[2];
            popoverAnchor.getLocationOnScreen(anchor);
            card.getLocationOnScreen(target);
            float center = anchor[0] + popoverAnchor.getWidth() / 2f - target[0];
            return Math.max(0f, Math.min(card.getWidth(), center));
        }

        private void positionPopover(boolean show) {
            PopupWindow window = popover;
            if (window == null) return;
            View root = window.getContentView();
            int[] hostLocation = new int[2];
            host.getLocationInWindow(hostLocation);
            int windowWidth = bar.getRootView().getWidth();
            int width = root.getMeasuredWidth();
            int x;
            if (popoverKind == POPOVER_COLOR || popoverAnchor == null) {
                x = hostLocation[0];
            } else {
                int[] anchorLocation = new int[2];
                popoverAnchor.getLocationInWindow(anchorLocation);
                x = anchorLocation[0] + popoverAnchor.getWidth() / 2 - width / 2;
            }
            x = Math.max(0, Math.min(Math.max(0, windowWidth - width), x));
            // The card's bottom edge sits 4dp above the toolbar (over the input row); the root's bottom
            // padding is shadow room.
            int height = root.getMeasuredHeight();
            int y = hostLocation[1] - height + dp(8) - dp(4);
            // Keep it inside the visible window area (status bar on top, keyboard below).
            Rect visible = new Rect();
            bar.getWindowVisibleDisplayFrame(visible);
            int[] barScreen = new int[2];
            int[] barWindow = new int[2];
            bar.getLocationOnScreen(barScreen);
            bar.getLocationInWindow(barWindow);
            int windowTop = barScreen[1] - barWindow[1];
            int minY = Math.max(0, visible.top - windowTop);
            int maxY = visible.bottom - windowTop - height;
            if (visible.isEmpty() || maxY < minY) maxY = Integer.MAX_VALUE;
            y = Math.max(minY, Math.min(maxY, y));
            if (show) window.showAtLocation(bar, Gravity.TOP | Gravity.START, x, y);
            else window.update(x, y, -1, -1);
        }

        private static String popoverName(int kind) {
            return kind == POPOVER_SIZE ? "SIZE" : kind == POPOVER_COLOR ? "COLOR" : String.valueOf(kind);
        }

        private void closePopover(boolean animate) {
            PopupWindow window = popover;
            View card = popoverCard;
            popover = null;
            popoverCard = null;
            popoverKind = 0;
            popoverAnchor = null;
            if (window == null) return;
            if (!animate || card == null || !window.isShowing()) {
                dismissQuietly(window);
            } else {
                card.animate().cancel();
                card.animate().alpha(0f).setDuration(POPOVER_CLOSE_MS).setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> dismissQuietly(window)).start();
            }
            onStateChanged();
        }

        private static void dismissQuietly(PopupWindow window) {
            try {
                if (window.isShowing()) window.dismiss();
            } catch (RuntimeException failure) {
                NezumiLog.swallowedOnce(Patch.RICH_TEXT_FORMATTING, TAG, "popover dismiss failed", failure);
            }
        }

        private void buildSizeChips(LinearLayout card, RichTextConfig config, RichTextStyle style) {
            String[] labels = {
                text("Nhỏ", "Small"), text("Thường", "Normal"), text("Lớn", "Large"), text("Rất lớn", "Extra large"),
            };
            for (int index = 0; index < SIZE_PRESETS.length; index++) {
                int token = SIZE_PRESETS[index] == RichTextStyle.NORMAL_SIZE ? RichTextStyle.NORMAL_SIZE
                    : config.clampSize(SIZE_PRESETS[index]);
                TextView chip = new TextView(context);
                chip.setText(labels[index]);
                chip.setGravity(Gravity.CENTER);
                chip.setSingleLine(true);
                chip.setTextColor(textColor);
                chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, token == RichTextStyle.NORMAL_SIZE ? 15 : token);
                chip.setMinHeight(dp(36));
                chip.setMinWidth(dp(44));
                chip.setPadding(dp(12), dp(4), dp(12), dp(4));
                chip.setContentDescription(token == RichTextStyle.NORMAL_SIZE ? labels[index]
                    : labels[index] + " " + token);
                chip.setTag(new int[] { POPOVER_SIZE, token });
                chip.setOnClickListener(view -> {
                    try {
                        editor.setSize(token);
                        closePopover(true);
                    } catch (RuntimeException failure) {
                        NezumiLog.swallowedOnce(Patch.RICH_TEXT_FORMATTING, TAG, "size action failed", failure);
                    }
                });
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (index > 0) params.leftMargin = dp(2);
                card.addView(chip, params);
            }
            updatePopoverSelection(card, style);
        }

        private void buildSwatches(LinearLayout card, RichTextConfig config, RichTextStyle style) {
            for (String hex : config.colors) {
                int rgb;
                try {
                    rgb = Integer.parseInt(hex.substring(1), 16);
                } catch (NumberFormatException failure) {
                    continue;
                }
                FrameLayout cell = new FrameLayout(context);
                Swatch swatch = new Swatch(context, 0xFF000000 | rgb, accent, dividerColor, density);
                swatch.setContentDescription(text("Màu ", "Color ") + hex);
                swatch.setTag(new int[] { POPOVER_COLOR, rgb });
                swatch.setOnClickListener(view -> {
                    try {
                        editor.setColor(rgb);
                        closePopover(true);
                    } catch (RuntimeException failure) {
                        NezumiLog.swallowedOnce(Patch.RICH_TEXT_FORMATTING, TAG, "color action failed", failure);
                    }
                });
                cell.addView(swatch, new FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER));
                card.addView(cell, new LinearLayout.LayoutParams(0, dp(40), 1f));
            }
            TextView none = new TextView(context);
            none.setText(text("Bỏ màu", "No color"));
            none.setTextColor(textColor);
            none.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            none.setGravity(Gravity.CENTER);
            none.setSingleLine(true);
            none.setPadding(dp(12), 0, dp(12), 0);
            none.setTag(new int[] { POPOVER_COLOR, RichTextStyle.NO_COLOR });
            none.setOnClickListener(view -> {
                try {
                    editor.setColor(RichTextStyle.NO_COLOR);
                    closePopover(true);
                } catch (RuntimeException failure) {
                    NezumiLog.swallowedOnce(Patch.RICH_TEXT_FORMATTING, TAG, "no-color action failed", failure);
                }
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(30));
            params.leftMargin = dp(4);
            params.rightMargin = dp(4);
            card.addView(none, params);
            updatePopoverSelection(card, style);
        }

        private void updatePopoverSelection(RichTextStyle style) {
            if (popoverCard instanceof LinearLayout) updatePopoverSelection((LinearLayout) popoverCard, style);
        }

        private void updatePopoverSelection(LinearLayout card, RichTextStyle style) {
            List<View> items = new ArrayList<>();
            for (int index = 0; index < card.getChildCount(); index++) {
                View child = card.getChildAt(index);
                if (child instanceof FrameLayout && ((FrameLayout) child).getChildCount() > 0) {
                    items.add(((FrameLayout) child).getChildAt(0));
                } else {
                    items.add(child);
                }
            }
            for (View item : items) {
                if (!(item.getTag() instanceof int[])) continue;
                int[] tag = (int[]) item.getTag();
                boolean selected = tag[0] == POPOVER_SIZE ? style.size == tag[1] : style.color == tag[1];
                item.setSelected(selected);
                if (item instanceof Swatch) {
                    item.invalidate();
                } else if (item instanceof TextView) {
                    TextView chip = (TextView) item;
                    chip.setTextColor(selected ? accent : textColor);
                    GradientDrawable fill = new GradientDrawable();
                    fill.setCornerRadius(dp(tag[0] == POPOVER_SIZE ? 8 : 15));
                    if (selected) fill.setColor(accentFill);
                    else if (tag[0] == POPOVER_COLOR) fill.setColor(dark ? 0xFF3A3D42 : 0xFFEEF0F2);
                    else fill.setColor(0);
                    chip.setBackground(fill);
                }
            }
        }
    }

    // ---- Theme ----

    /** Zalo's own Light/Dark/System choice ({@code themeId} attr, 1 = dark), else the system night mode. */
    static boolean isDarkTheme(Context context) {
        int attr = context.getResources().getIdentifier("themeId", "attr", context.getPackageName());
        if (attr != 0) {
            TypedValue value = new TypedValue();
            if (context.getTheme().resolveAttribute(attr, value, true)
                    && value.type >= TypedValue.TYPE_FIRST_INT && value.type <= TypedValue.TYPE_LAST_INT) {
                return value.data == 1;
            }
        }
        return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
            == Configuration.UI_MODE_NIGHT_YES;
    }

    private static int attrColor(Context context, String name, int fallback) {
        int attr = context.getResources().getIdentifier(name, "attr", context.getPackageName());
        if (attr == 0) return fallback;
        TypedValue value = new TypedValue();
        if (!context.getTheme().resolveAttribute(attr, value, true)) return fallback;
        try {
            if (value.resourceId != 0) return context.getColorStateList(value.resourceId).getDefaultColor();
            if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                return value.data;
            }
        } catch (RuntimeException failure) {
            NezumiLog.swallowedOnce(Patch.RICH_TEXT_FORMATTING, TAG, "theme color " + name + " unavailable", failure);
        }
        return fallback;
    }

    private static int colorResource(Context context, String name, int fallback) {
        int id = context.getResources().getIdentifier(name, "color", context.getPackageName());
        if (id == 0) return fallback;
        try {
            return context.getColorStateList(id).getDefaultColor();
        } catch (RuntimeException failure) {
            NezumiLog.swallowedOnce(Patch.RICH_TEXT_FORMATTING, TAG, "color " + name + " unavailable", failure);
            return fallback;
        }
    }

    // ---- Views ----

    /**
     * A 40x36dp toolbar button: a 24dp-viewport icon drawn at 20dp, centered. Active or pressed: a
     * tinted rounded background and the accent icon color. Disabled: a faded icon.
     */
    private static final class ToolButton extends View {
        private final Path icon;
        private final float density;
        private final int iconColor;
        private final int accent;
        private final int accentFill;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bounds = new RectF();
        private boolean active;
        /** Color button only: the "A" glyph with a color bar under it. */
        private final boolean colorButton;
        private int barColor = 0xFF000000;

        ToolButton(Context context, String iconData, float density, int iconColor, int accent, int accentFill) {
            super(context);
            this.colorButton = iconData == null;
            this.icon = RichTextIcons.path(colorButton ? RichTextIcons.COLOR_GLYPH : iconData);
            this.density = density;
            this.iconColor = iconColor;
            this.accent = accent;
            this.accentFill = accentFill;
            setClickable(true);
            setFocusable(false);
        }

        void setActive(boolean value) {
            if (active == value) return;
            active = value;
            setSelected(value);
            invalidate();
        }

        void setColorBar(int value) {
            if (barColor == value) return;
            barColor = value;
            invalidate();
        }

        @Override
        public void setEnabled(boolean enabled) {
            if (enabled == isEnabled()) return;
            super.setEnabled(enabled);
            invalidate();
        }

        @Override
        protected void drawableStateChanged() {
            super.drawableStateChanged();
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            boolean highlighted = isEnabled() && (active || isPressed());
            if (highlighted) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(accentFill);
                float inset = 2 * density;
                bounds.set(inset, inset, getWidth() - inset, getHeight() - inset);
                canvas.drawRoundRect(bounds, 8 * density, 8 * density, paint);
            }
            int color = highlighted ? accent : iconColor;
            if (!isEnabled()) color = (color & 0x00FFFFFF) | 0x61000000;
            float iconSize = ICON_DP * density;
            float scale = iconSize / 24f;
            float left = (getWidth() - iconSize) / 2f;
            float top = (getHeight() - iconSize) / 2f;
            if (colorButton) {
                // Glyph (viewport y 3..17), a 5dp gap, then a 3dp bar: the whole stack is centered.
                float glyph = (RichTextIcons.COLOR_GLYPH_BOTTOM - RichTextIcons.COLOR_GLYPH_TOP) * scale;
                float stack = glyph + 5 * density + 3 * density;
                top = (getHeight() - stack) / 2f - RichTextIcons.COLOR_GLYPH_TOP * scale;
                float barTop = top + RichTextIcons.COLOR_GLYPH_BOTTOM * scale + 5 * density;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(barColor);
                bounds.set(left + 4 * scale, barTop, left + 20 * scale, barTop + 3 * density);
                canvas.drawRoundRect(bounds, 1.5f * density, 1.5f * density, paint);
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            int save = canvas.save();
            canvas.translate(left, top);
            canvas.scale(scale, scale);
            canvas.drawPath(icon, paint);
            canvas.restoreToCount(save);
        }
    }

    /** The "Aa" input bar button: Zalo bar icon color, accent while the toolbar is open. */
    private static final class ToggleButton extends ImageView {
        private final AaDrawable drawable;
        private final int iconColor;
        private final int accent;

        ToggleButton(Context context, int iconColor, int accent) {
            super(context);
            this.iconColor = iconColor;
            this.accent = accent;
            this.drawable = new AaDrawable(context.getResources().getDisplayMetrics().density);
            drawable.setColor(iconColor);
            setImageDrawable(drawable);
            setScaleType(ScaleType.CENTER);
            setClickable(true);
            setFocusable(false);
        }

        void setActive(boolean value) {
            setSelected(value);
            drawable.setColor(value ? accent : iconColor);
        }
    }

    /** "Aa" glyph, 24dp. */
    private static final class AaDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float density;

        AaDrawable(float density) {
            this.density = density;
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextAlign(Paint.Align.LEFT);
        }

        void setColor(int color) {
            paint.setColor(color);
            invalidateSelf();
        }

        @Override
        public int getIntrinsicWidth() {
            return Math.round(24 * density);
        }

        @Override
        public int getIntrinsicHeight() {
            return Math.round(24 * density);
        }

        @Override
        public void draw(Canvas canvas) {
            float big = 17 * density;
            float small = 13 * density;
            paint.setTextSize(big);
            float widthA = paint.measureText("A");
            paint.setTextSize(small);
            float widthB = paint.measureText("a");
            float spacing = 0.5f * density;
            float total = widthA + spacing + widthB;
            float left = getBounds().exactCenterX() - total / 2f;
            paint.setTextSize(big);
            float baseline = getBounds().exactCenterY() - (paint.ascent() + paint.descent()) / 2f;
            canvas.drawText("A", left, baseline, paint);
            paint.setTextSize(small);
            canvas.drawText("a", left + widthA + spacing, baseline, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter filter) {
            paint.setColorFilter(filter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /** A round 30dp color swatch; a ring marks the current color. */
    private static final class Swatch extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int color;
        private final int accent;
        private final int border;
        private final float density;

        Swatch(Context context, int color, int accent, int border, float density) {
            super(context);
            this.color = color;
            this.accent = accent;
            this.border = border;
            this.density = density;
            setClickable(true);
            setFocusable(false);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float radius = Math.min(cx, cy);
            float fill = isSelected() ? radius - 4 * density : radius - 0.5f * density;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            canvas.drawCircle(cx, cy, fill, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, density / 2f));
            paint.setColor(border);
            canvas.drawCircle(cx, cy, fill, paint);
            if (isSelected()) {
                paint.setStrokeWidth(2 * density);
                paint.setColor(accent);
                canvas.drawCircle(cx, cy, radius - density, paint);
            }
        }
    }
}
