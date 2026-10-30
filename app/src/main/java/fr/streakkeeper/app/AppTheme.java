package fr.streakkeeper.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
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

    final int background, surface, text, muted, border, accent, accentText, accentSoft, onAccent;
    final boolean dark;
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
        dark = luminance(surface) < .15;
        String savedAccent = context.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)
                .getString("accentColor", "indigo");
        int resolvedAccent = color(context, android.R.attr.colorAccent);
        if (savedAccent != null && savedAccent.matches("#[0-9a-fA-F]{6}")) {
            resolvedAccent = Color.parseColor(savedAccent);
        }
        accent = resolvedAccent;
        int readableAccent = accent;
        int toward = dark ? Color.WHITE : Color.BLACK;
        while (androidx.core.graphics.ColorUtils.calculateContrast(readableAccent, surface) < 4.5) {
            readableAccent = blend(readableAccent, toward, .08f);
        }
        accentText = readableAccent;
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

    GradientDrawable gradient(int start, int end, int stroke) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{start, end});
        drawable.setCornerRadius(dp(RADIUS));
        if (stroke != Color.TRANSPARENT) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    Drawable toolbarBackdrop() {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{surface, accentSoft});
        float radius = dp(RADIUS);
        drawable.setCornerRadii(new float[]{0, 0, 0, 0, radius, radius, radius, radius});
        return drawable;
    }

    Drawable profileBackdrop() { return new ProfileBackdrop(); }
    Drawable raisedSurface() { return new RaisedSurface(); }

    private final class RaisedSurface extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.isEmpty()) return;
            float inset = dp(1) / 2f, r = dp(RADIUS);
            float left = b.left + inset, top = b.top + inset;
            float right = b.right - inset, bottom = b.bottom - inset;
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(left, top, right, bottom,
                    surface, blend(surface, accent, .025f), Shader.TileMode.CLAMP));
            canvas.drawRoundRect(left, top, right, bottom, r, r, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(blend(border, accent, .12f));
            canvas.drawRoundRect(left, top, right, bottom, r, r, paint);
            paint.setStrokeWidth(dp(2));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(blend(surface, accent, .25f));
            canvas.drawArc(left + dp(2), top + dp(2), left + r * 2, top + r * 2,
                    185, 55, false, paint);
            paint.setColor(blend(border, accent, .27f));
            canvas.drawArc(right - r * 2, bottom - r * 2, right - dp(2), bottom - dp(2),
                    5, 55, false, paint);
            paint.setStrokeCap(Paint.Cap.BUTT);
            paint.setStyle(Paint.Style.FILL);
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private final class ProfileBackdrop extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path clip = new Path();
        @Override public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.isEmpty()) return;
            float left = b.left, top = b.top, right = b.right, bottom = b.bottom;
            float radius = dp(RADIUS);
            clip.reset();
            clip.addRoundRect(left, top, right, bottom, radius, radius, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(clip);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(left, top, right, bottom,
                    new int[]{surface, blend(surface, accent, .045f), blend(surface, accent, .12f)},
                    null, Shader.TileMode.CLAMP));
            canvas.drawRect(b, paint);
            paint.setShader(null);
            float cx = right - dp(10), cy = top + dp(10);
            paint.setColor(blend(surface, accent, .06f));
            canvas.drawCircle(cx, cy, dp(76), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(blend(surface, accent, .18f));
            canvas.drawCircle(cx, cy, dp(52), paint);
            canvas.drawCircle(cx, cy, dp(68), paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(blend(surface, accent, .25f));
            canvas.drawCircle(right - dp(42), top + dp(66), dp(3), paint);
            canvas.restore();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(blend(surface, accent, .16f));
            canvas.drawRoundRect(left + dp(1) / 2f, top + dp(1) / 2f,
                    right - dp(1) / 2f, bottom - dp(1) / 2f, radius, radius, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
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
        Drawable backgroundShape = style == ButtonStyle.PRIMARY
                ? gradient(fill, blend(fill, onAccent == Color.BLACK ? Color.WHITE : Color.BLACK, .10f), Color.TRANSPARENT)
                : shape(fill, Color.TRANSPARENT);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(blend(fill, foreground, .15f)),
                backgroundShape, null));
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
        states.addState(new int[]{android.R.attr.state_focused}, shape(surface, accentText));
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
                + ";--sk-border:" + hex(border) + ";--sk-accent:" + hex(accentText)
                + ";--sk-accent-fill:" + hex(accent)
                + ";--sk-on-accent:" + hex(onAccent) + ";";
        return ":root{color-scheme:" + (dark ? "dark;" : "light;") + palette + "}"
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
                + "background:#000000!important;color:#FFFFFF!important;"
                + "border:0!important;border-radius:14px!important;min-height:48px;padding:12px 20px;}"
                + "#new_request a{color:var(--sk-text);text-decoration:underline;}"
                + (dark
                ? "html,body{background-color:var(--sk-bg)!important;color:var(--sk-text)!important;}"
                // Zendesk has white panels around some fields outside #new_request itself.
                // Make those first-party page surfaces transparent so the dark page shows through.
                + "body *{color:var(--sk-text)!important;background-color:transparent!important;}"
                + "form#new_request{background:var(--sk-surface)!important;color:var(--sk-text)!important;"
                + "border-color:var(--sk-border)!important;}"
                + "#new_request input:not([type=hidden]):not([type=checkbox]):not([type=radio]):not([type=submit]),"
                + "#new_request textarea,#new_request select,#new_request option,#new_request .nesty-input,"
                + "#new_request [role=combobox]{background:var(--sk-surface)!important;"
                + "color:var(--sk-text)!important;}"
                + "#new_request :is(input,textarea)::placeholder{color:var(--sk-muted)!important;}"
                + "#new_request input[type=submit],#new_request button[type=submit]{"
                + "background:var(--sk-accent-fill)!important;color:var(--sk-on-accent)!important;}"
                : "");
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
