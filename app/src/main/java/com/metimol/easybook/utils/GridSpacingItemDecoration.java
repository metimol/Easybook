package com.metimol.easybook.utils;

import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class GridSpacingItemDecoration extends RecyclerView.ItemDecoration {

    private final int spanCount;
    private final int spacing;
    private final boolean includeEdge;
    private final int horizontalEdgeSpacing;
    private final int verticalEdgeSpacing;

    public GridSpacingItemDecoration(int spanCount, int spacing, boolean includeEdge) {
        this.spanCount = spanCount;
        this.spacing = spacing;
        this.includeEdge = includeEdge;
        this.horizontalEdgeSpacing = 0;
        this.verticalEdgeSpacing = 0;
    }

    public GridSpacingItemDecoration(int spanCount, int spacing, boolean includeEdge, int edgeSpacing) {
        this.spanCount = spanCount;
        this.spacing = spacing;
        this.includeEdge = includeEdge;
        this.horizontalEdgeSpacing = edgeSpacing;
        this.verticalEdgeSpacing = edgeSpacing;
    }

    public GridSpacingItemDecoration(int spanCount, int spacing, boolean includeEdge, int horizontalEdgeSpacing, int verticalEdgeSpacing) {
        this.spanCount = spanCount;
        this.spacing = spacing;
        this.includeEdge = includeEdge;
        this.horizontalEdgeSpacing = horizontalEdgeSpacing;
        this.verticalEdgeSpacing = verticalEdgeSpacing;
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        int position = parent.getChildAdapterPosition(view);
        if (position == RecyclerView.NO_POSITION) {
            return;
        }
        int column = position % spanCount;

        if (includeEdge) {
            outRect.left = spacing - column * spacing / spanCount;
            outRect.right = (column + 1) * spacing / spanCount;

            if (position < spanCount) {
                outRect.top = spacing;
            }
            outRect.bottom = spacing;
        } else {
            outRect.left = column * spacing / spanCount;
            outRect.right = spacing - (column + 1) * spacing / spanCount;
            if (position >= spanCount) {
                outRect.top = spacing;
            }
        }

        if (column == 0) {
            outRect.left += horizontalEdgeSpacing;
        }
        if (column == spanCount - 1) {
            outRect.right += horizontalEdgeSpacing;
        }

        if (position < spanCount) {
            outRect.top += verticalEdgeSpacing;
        }

        int itemCount = state.getItemCount();
        int rows = itemCount / spanCount + (itemCount % spanCount == 0 ? 0 : 1);
        int currentRow = position / spanCount;

        if (currentRow == rows - 1) {
            outRect.bottom += verticalEdgeSpacing;
        }
    }
}