package com.spendwise.ui.components;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;

/**
 * A {@link FlowLayout} that reports its preferred size with the correct number
 * of wrapped rows for the current container width. Standard FlowLayout
 * prefers a single row, which causes wrapped second rows to be clipped —
 * this subclass fixes exactly that (used by multi-row toolbars).
 *
 * <p>Based on the classic "WrapLayout" approach (Rob Camick), adapted.</p>
 */
public class WrapLayout extends FlowLayout {

    private Dimension preferredLayoutSize;

    public WrapLayout() {
        super();
    }

    public WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return layoutSize(target, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension minimum = layoutSize(target, false);
        minimum.width -= getHgap() + 1;
        return minimum;
    }

    private Dimension layoutSize(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            int targetWidth = target.getSize().width;
            if (targetWidth == 0) {
                targetWidth = Integer.MAX_VALUE;
            }
            int hgap = getHgap();
            int vgap = getVgap();
            Insets insets = target.getInsets();
            int horizontalInsetsAndGap = insets.left + insets.right + (hgap * 2);
            int maxWidth = targetWidth - horizontalInsetsAndGap;

            Dimension dimension = new Dimension(0, 0);
            int rowWidth = 0;
            int rowHeight = 0;

            int memberCount = target.getComponentCount();
            for (int i = 0; i < memberCount; i++) {
                Component component = target.getComponent(i);
                if (!component.isVisible()) {
                    continue;
                }
                Dimension size = preferred
                        ? component.getPreferredSize()
                        : component.getMinimumSize();

                if (rowWidth == 0) {
                    rowWidth = size.width;
                    rowHeight = size.height;
                } else if (rowWidth + hgap + size.width <= maxWidth) {
                    // fits on the current row
                    rowWidth += hgap + size.width;
                    rowHeight = Math.max(rowHeight, size.height);
                } else {
                    // start a new row
                    dimension.width = Math.max(dimension.width, rowWidth);
                    dimension.height += rowHeight + vgap;
                    rowWidth = size.width;
                    rowHeight = size.height;
                }
            }

            dimension.width = Math.max(dimension.width, rowWidth);
            dimension.height += rowHeight + vgap;
            dimension.width += horizontalInsetsAndGap;
            dimension.height += insets.top + insets.bottom + vgap * 2;
            preferredLayoutSize = dimension;
            return dimension;
        }
    }

    /** Ensures the container askes for the freshly computed preferred size. */
    @Override
    public void layoutContainer(Container target) {
        Dimension size = preferredLayoutSize(target);
        if (preferredLayoutSize != null && !size.equals(preferredLayoutSize)) {
            target.invalidate();
        }
        super.layoutContainer(target);
    }
}