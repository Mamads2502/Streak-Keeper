package fr.streakkeeper.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.util.Locale;

/** Shared visual roles and components; no profile or navigation logic. */
final class AppTheme {
    enum ButtonStyle { PRIMARY, SECONDARY, QUIET }

    final int background, surface, text, muted, border, accent, accentSoft, onAccent;
    private final float density;
    static final int PAGE_PADDING = 20;
    static final int RADIUS = 18;
    static final int BODY_SIZE = 15;

    AppTheme(Context context) {
        density = context.getResources().getDisplayMetrics().density;
        background = color(context, R.attr.skBackground);
        surface = color(context, R.attr.skSurface);
        text = color(context, R.attr.skText);
        muted = color(context, R.attr.skMuted);
        border = color(context, R.attr.skBorder);
        accent = color(context, android.R.attr.colorAccent);
        accentSoft = blend(surface, accent, .09f);
        // Pick the higher-contrast foreground even if a future accent is very light.
        onAccent = luminance(accent) > .179 ? Color.BLACK : Color.WHITE;
    }

    private static int color(Context context, int attribute) {
        TypedValue value = new TypedValue();
        if (!context.getTheme().resolveAttribute(attribute, value, true)) {
            throw new IllegalArgumentException("Missing theme attribute: " + attribute);
        }
        return value.data;
    }

    int dp(int value) { return Math.round(value * density); }

    GradientDrawable shape(int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(RADIUS));
        if (stroke != Color.TRANSPARENT) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    void text(TextView view, int size, boolean emphasized) {
        view.setTextColor(text);
        view.setTextSize(size);
        view.setTypeface(Typeface.create(emphasized ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        view.setLineSpacing(dp(2), 1f);
    }

    void button(Button button, ButtonStyle style) {
        int fill = style == ButtonStyle.PRIMARY ? accent
                : style == ButtonStyle.SECONDARY ? accentSoft : surface;
        int foreground = style == ButtonStyle.PRIMARY ? onAccent : text;
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(blend(fill, foreground, .15f)),
                shape(fill, Color.TRANSPARENT), null));
        text(button, BODY_SIZE, true);
        button.setTextColor(foreground);
        button.setAllCaps(false);
        button.setPadding(dp(16), dp(12), dp(16), dp(12));
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setElevation(0);
        button.setStateListAnimator(null);
    }

    void input(EditText input) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, shape(surface, accent));
        states.addState(new int[]{}, shape(background, border));
        input.setBackground(states);
        input.setBackgroundTintList(null);
        text(input, BODY_SIZE, false);
        input.setHintTextColor(muted);
        input.setPadding(dp(16), dp(16), dp(16), dp(16));
        input.setMinHeight(dp(56));
    }

    /** Visual CSS only: keep every field, iframe, CAPTCHA and submit interaction intact. */
    String formCss() {
        String palette = "--sk-bg:" + hex(background) + ";--sk-surface:" + hex(surface)
                + ";--sk-text:" + hex(text) + ";--sk-muted:" + hex(muted)
                + ";--sk-border:" + hex(border) + ";--sk-accent:" + hex(accent)
                + ";--sk-on-accent:" + hex(onAccent) + ";";
        return ":root{color-scheme:light;" + palette + "}"
                + "body{background:var(--sk-bg)!important;color:var(--sk-text)!important;"
                + "font-family:system-ui,sans-serif!important;line-height:1.5;}"
                + "form#new_request{background:var(--sk-surface);border:1px solid var(--sk-border);"
                + "border-radius:18px;padding:20px;box-sizing:border-box;max-width:100%;}"
                + "#new_request .form-field{margin-top:20px;}"
                + "#new_request label{color:var(--sk-text);font-weight:600;}"
                + "#new_request input:not([type=hidden]):not([type=checkbox]):not([type=radio]):not([type=submit]),"
                + "#new_request textarea,#new_request select{background:var(--sk-surface)!important;"
                + "color:var(--sk-text)!important;border:1px solid var(--sk-border)!important;"
                + "border-radius:12px!important;padding:12px!important;font-size:16px!important;"
                + "min-height:48px;box-sizing:border-box;max-width:100%;}"
                + "#new_request :is(input,textarea,select):focus-visible{outline:2px solid var(--sk-accent);outline-offset:2px;}"
                + "#new_request input[type=submit],#new_request button[type=submit]{"
                + "background:var(--sk-accent)!important;color:var(--sk-on-accent)!important;"
                + "border:0!important;border-radius:14px!important;min-height:48px;padding:12px 20px;}"
                + "#new_request a{color:var(--sk-text);text-decoration:underline;}";
    }

    private static String hex(int color) { return String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF); }
    private static int blend(int first, int second, float amount) {
        return Color.rgb(Math.round(Color.red(first) * (1 - amount) + Color.red(second) * amount),
                Math.round(Color.green(first) * (1 - amount) + Color.green(second) * amount),
                Math.round(Color.blue(first) * (1 - amount) + Color.blue(second) * amount));
    }
    private static double luminance(int color) {
        return .2126 * linear(Color.red(color)) + .7152 * linear(Color.green(color)) + .0722 * linear(Color.blue(color));
    }
    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= .04045 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4);
    }
}
