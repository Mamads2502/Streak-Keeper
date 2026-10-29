package fr.streakkeeper.app;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Keeps the same search field attached when switching between inline and stacked layouts. */
final class FriendToolsLayout extends ViewGroup {
    private final View searchButton, filterButton, field;
    private final int iconSize, gap;
    private boolean inline;
    private int rowHeight;

    FriendToolsLayout(Context context, View searchButton, View filterButton, View field) {
        super(context);
        this.searchButton = searchButton;
        this.filterButton = filterButton;
        this.field = field;
        float density = getResources().getDisplayMetrics().density;
        iconSize = Math.round(48 * density);
        gap = Math.round(8 * density);
        addView(searchButton, new LayoutParams(iconSize, iconSize));
        addView(filterButton, new LayoutParams(iconSize, iconSize));
        addView(field, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int middle = Math.max(0, width - 2 * (iconSize + gap));
        float density = getResources().getDisplayMetrics().density;
        float fontScale = getResources().getConfiguration().fontScale;
        inline = middle >= Math.round(220 * density * Math.max(1, fontScale));
        int exactIcon = MeasureSpec.makeMeasureSpec(iconSize, MeasureSpec.EXACTLY);
        searchButton.measure(exactIcon, exactIcon);
        filterButton.measure(exactIcon, exactIcon);
        rowHeight = iconSize;
        int height = rowHeight;
        if (field.getVisibility() != GONE) {
            field.measure(MeasureSpec.makeMeasureSpec(inline ? middle : width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            if (inline) height = rowHeight = Math.max(rowHeight, field.getMeasuredHeight());
            else height += gap + field.getMeasuredHeight();
        }
        setMeasuredDimension(resolveSize(width, widthSpec), resolveSize(height, heightSpec));
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;
        int iconTop = (rowHeight - iconSize) / 2;
        searchButton.layout(0, iconTop, iconSize, iconTop + iconSize);
        filterButton.layout(width - iconSize, iconTop, width, iconTop + iconSize);
        if (field.getVisibility() != GONE) {
            int x = inline ? iconSize + gap : 0;
            int y = inline ? (rowHeight - field.getMeasuredHeight()) / 2 : rowHeight + gap;
            field.layout(x, y, x + field.getMeasuredWidth(), y + field.getMeasuredHeight());
        }
    }
}
