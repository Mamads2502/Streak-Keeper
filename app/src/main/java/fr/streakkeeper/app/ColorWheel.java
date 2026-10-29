package fr.streakkeeper.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.view.MotionEvent;
import android.view.View;

/** Hue and saturation wheel; brightness is controlled separately. */
final class ColorWheel extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] hsv = {240, 1, .7f};
    private Runnable onChange = () -> {};

    ColorWheel(Context context, int color) {
        super(context);
        Color.colorToHSV(color, hsv);
        setContentDescription("Roue de couleur : touche ou glisse pour choisir la teinte et la saturation");
    }
    void setOnColorChanged(Runnable listener) { onChange = listener; }
    int color() { return Color.HSVToColor(hsv); }
    float hue() { return hsv[0]; }
    float saturation() { return hsv[1]; }
    float brightness() { return hsv[2]; }
    void setHue(float value) { hsv[0] = value; invalidate(); onChange.run(); }
    void setSaturation(float value) { hsv[1] = value; invalidate(); onChange.run(); }
    void setBrightness(float value) { hsv[2] = value; invalidate(); onChange.run(); }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int size = Math.min(MeasureSpec.getSize(widthSpec), Math.round(240 * getResources().getDisplayMetrics().density));
        setMeasuredDimension(resolveSize(size, widthSpec), resolveSize(size, heightSpec));
    }
    @Override protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float radius = Math.min(cx, cy) - 8 * getResources().getDisplayMetrics().density;
        if (radius <= 0) return;
        int[] colors = {Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED};
        Shader hue = new SweepGradient(cx, cy, colors, null);
        Shader saturation = new RadialGradient(cx, cy, radius, Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP);
        paint.setShader(new ComposeShader(hue, saturation, PorterDuff.Mode.SRC_OVER));
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setShader(null);
        double angle = Math.toRadians(hsv[0]);
        float x = cx + (float) Math.cos(angle) * radius * hsv[1];
        float y = cy + (float) Math.sin(angle) * radius * hsv[1];
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3 * getResources().getDisplayMetrics().density);
        paint.setColor(Color.BLACK);
        canvas.drawCircle(x, y, 7 * getResources().getDisplayMetrics().density, paint);
        paint.setStrokeWidth(1.5f * getResources().getDisplayMetrics().density);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(x, y, 7 * getResources().getDisplayMetrics().density, paint);
        paint.setStyle(Paint.Style.FILL);
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN || event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            float x = event.getX() - getWidth() / 2f, y = event.getY() - getHeight() / 2f;
            float radius = Math.min(getWidth(), getHeight()) / 2f - 8 * getResources().getDisplayMetrics().density;
            if (radius <= 0) return false;
            hsv[0] = (float) ((Math.toDegrees(Math.atan2(y, x)) + 360) % 360);
            hsv[1] = Math.min(1, (float) Math.hypot(x, y) / radius);
            invalidate();
            onChange.run();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP) { performClick(); return true; }
        return event.getActionMasked() == MotionEvent.ACTION_CANCEL;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
}
