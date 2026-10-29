package fr.streakkeeper.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;

/** A small status dot; animation stops when the view is not visible. */
final class RecentRequestDot extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final boolean dark;
    private final float density;

    RecentRequestDot(Context context, boolean dark) {
        super(context);
        this.dark = dark;
        density = getResources().getDisplayMetrics().density;
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        boolean animated = ValueAnimator.areAnimatorsEnabled() && isShown()
                && getWindowVisibility() == View.VISIBLE;
        long cycle = SystemClock.uptimeMillis() % 4200;
        float phase = animated && cycle < 1200 ? cycle / 1200f : 0f;
        int base = dark ? Color.rgb(99, 220, 145) : Color.rgb(22, 128, 62);
        int lighter = dark ? Color.rgb(137, 232, 169) : Color.rgb(46, 158, 85);
        int color = blend(base, lighter, animated ? (float) ((1 + Math.sin(cycle * Math.PI / 2100)) * .25) : 0);
        float x = getWidth() / 2f, y = getHeight() / 2f;
        if (phase > 0) {
            paint.setColor(color);
            paint.setAlpha((int) (55 * (1 - phase)));
            canvas.drawCircle(x, y, density * (4 + 6 * phase), paint);
        }
        paint.setColor(color);
        paint.setAlpha(255);
        canvas.drawCircle(x, y, 3.5f * density, paint);
        if (animated) postInvalidateDelayed(80);
    }

    private static int blend(int a, int b, float amount) {
        return Color.rgb(Math.round(Color.red(a) * (1 - amount) + Color.red(b) * amount),
                Math.round(Color.green(a) * (1 - amount) + Color.green(b) * amount),
                Math.round(Color.blue(a) * (1 - amount) + Color.blue(b) * amount));
    }
}
