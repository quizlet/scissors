package com.lyft.android.scissors;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

import com.nostra13.universalimageloader.core.assist.LoadedFrom;
import com.nostra13.universalimageloader.core.assist.ViewScaleType;
import com.nostra13.universalimageloader.core.imageaware.ImageAware;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowBitmap;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fill-viewport loading must not allocate a bitmap whose width or height is unbounded.
 *
 * <p>These tests fail while {@link CropViewExtensions#computeTargetSize} multiplies the source
 * aspect ratio by the viewport and the Picasso, Glide, and Universal Image Loader helpers pass
 * that size to {@link Bitmap#createScaledBitmap}. They pass once every requested dimension is
 * inside the bounds below, including a request that is checked and then issued again.
 *
 * <p>{@link #MAX_TARGET_SIDE} is large enough for an 8K side and for the modest upscales already
 * covered by {@link TargetSizeTest}. A 200 by 1 image in a 1080 viewport is not.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, shadows = FillViewportAllocationTest.AllocationRecordingShadowBitmap.class)
public class FillViewportAllocationTest {

    /** Largest width or height a fill-viewport load may request. */
    static final int MAX_TARGET_SIDE = 8192;

    /** Largest pixel count a fill-viewport load may request. */
    static final long MAX_TARGET_PIXELS = 8192L * 8192L;

    /** Viewport used by the reported load path. */
    private static final int VIEWPORT = 1080;

    /** Crafted source whose shorter side is one pixel. */
    private static final int EXTREME_LONG_SIDE = 200;
    private static final int EXTREME_SHORT_SIDE = 1;

    @Before
    public void clearAllocations() {
        AllocationRecordingShadowBitmap.requestedSizes.clear();
    }

    @Test
    public void computeTargetSize_extremeLandscape_isBounded() {
        Rect target = CropViewExtensions.computeTargetSize(
                EXTREME_LONG_SIDE, EXTREME_SHORT_SIDE, VIEWPORT, VIEWPORT);

        assertSafeSize(target.width(), target.height());
    }

    @Test
    public void computeTargetSize_extremePortrait_isBounded() {
        Rect target = CropViewExtensions.computeTargetSize(
                EXTREME_SHORT_SIDE, EXTREME_LONG_SIDE, VIEWPORT, VIEWPORT);

        assertSafeSize(target.width(), target.height());
    }

    @Test
    public void computeTargetSize_ordinaryPhoto_stillFillsViewport() {
        Rect target = CropViewExtensions.computeTargetSize(1920, 1080, VIEWPORT, VIEWPORT);

        assertSafeSize(target.width(), target.height());
        assertThat(target.width()).isEqualTo(1920);
        assertThat(target.height()).isEqualTo(VIEWPORT);
    }

    @Test
    public void computeTargetSize_nonPositiveDimension_isRejectedOrBounded() {
        assertRejectedOrBounded(EXTREME_LONG_SIDE, 0, VIEWPORT, VIEWPORT);
        assertRejectedOrBounded(0, EXTREME_LONG_SIDE, VIEWPORT, VIEWPORT);
        assertRejectedOrBounded(0, 0, VIEWPORT, VIEWPORT);
        assertRejectedOrBounded(EXTREME_LONG_SIDE, EXTREME_SHORT_SIDE, 0, 0);
    }

    @Test
    public void picassoTransform_extremeLandscape_allocatesBoundedBitmap() {
        Bitmap source = bitmap(EXTREME_LONG_SIDE, EXTREME_SHORT_SIDE);
        AllocationRecordingShadowBitmap.requestedSizes.clear();

        Bitmap result = new PicassoFillViewportTransformation(VIEWPORT, VIEWPORT).transform(source);

        assertSafeBitmap(result);
        assertEveryAllocationIsSafe();
    }

    @Test
    public void picassoTransform_extremePortrait_allocatesBoundedBitmap() {
        Bitmap source = bitmap(EXTREME_SHORT_SIDE, EXTREME_LONG_SIDE);
        AllocationRecordingShadowBitmap.requestedSizes.clear();

        Bitmap result = new PicassoFillViewportTransformation(VIEWPORT, VIEWPORT).transform(source);

        assertSafeBitmap(result);
        assertEveryAllocationIsSafe();
    }

    @Test
    public void picassoTransform_nonPositiveViewport_doesNotAllocateUnboundedBitmap() {
        Bitmap source = bitmap(EXTREME_LONG_SIDE, EXTREME_SHORT_SIDE);
        AllocationRecordingShadowBitmap.requestedSizes.clear();

        try {
            Bitmap result = new PicassoFillViewportTransformation(0, 0).transform(source);
            assertSafeBitmap(result);
        } catch (IllegalArgumentException expected) {
            // Rejected before an invalid size is used.
        }
        assertEveryAllocationIsSafe();
    }

    @Test
    public void glideTransform_extremeLandscape_allocatesBoundedBitmap() {
        Bitmap source = bitmap(EXTREME_LONG_SIDE, EXTREME_SHORT_SIDE);
        AllocationRecordingShadowBitmap.requestedSizes.clear();

        Bitmap result = new GlideFillViewportTransformation(VIEWPORT, VIEWPORT)
                .transform(null, source, VIEWPORT, VIEWPORT);

        assertSafeBitmap(result);
        assertEveryAllocationIsSafe();
    }

    @Test
    public void glideTransform_extremePortrait_allocatesBoundedBitmap() {
        Bitmap source = bitmap(EXTREME_SHORT_SIDE, EXTREME_LONG_SIDE);
        AllocationRecordingShadowBitmap.requestedSizes.clear();

        Bitmap result = new GlideFillViewportTransformation(VIEWPORT, VIEWPORT)
                .transform(null, source, VIEWPORT, VIEWPORT);

        assertSafeBitmap(result);
        assertEveryAllocationIsSafe();
    }

    @Test
    public void glideTransform_nonPositiveViewport_doesNotAllocateUnboundedBitmap() {
        Bitmap source = bitmap(EXTREME_LONG_SIDE, EXTREME_SHORT_SIDE);
        AllocationRecordingShadowBitmap.requestedSizes.clear();

        try {
            Bitmap result = new GlideFillViewportTransformation(0, 0)
                    .transform(null, source, 0, 0);
            assertSafeBitmap(result);
        } catch (IllegalArgumentException expected) {
            // Rejected before an invalid size is used.
        }
        assertEveryAllocationIsSafe();
    }

    @Test
    public void uilDisplay_extremeLandscape_allocatesBoundedBitmap() {
        Bitmap source = bitmap(EXTREME_LONG_SIDE, EXTREME_SHORT_SIDE);
        RecordingImageAware aware = new RecordingImageAware();
        AllocationRecordingShadowBitmap.requestedSizes.clear();

        new UILFillViewportDisplayer(VIEWPORT, VIEWPORT)
                .display(source, aware, LoadedFrom.NETWORK);

        assertSafeBitmap(aware.bitmap);
        assertEveryAllocationIsSafe();
    }

    @Test
    public void uilDisplay_extremePortrait_allocatesBoundedBitmap() {
        Bitmap source = bitmap(EXTREME_SHORT_SIDE, EXTREME_LONG_SIDE);
        RecordingImageAware aware = new RecordingImageAware();
        AllocationRecordingShadowBitmap.requestedSizes.clear();

        new UILFillViewportDisplayer(VIEWPORT, VIEWPORT)
                .display(source, aware, LoadedFrom.NETWORK);

        assertSafeBitmap(aware.bitmap);
        assertEveryAllocationIsSafe();
    }

    @Test
    public void uilDisplay_nonPositiveViewport_doesNotAllocateUnboundedBitmap() {
        Bitmap source = bitmap(EXTREME_LONG_SIDE, EXTREME_SHORT_SIDE);
        RecordingImageAware aware = new RecordingImageAware();
        AllocationRecordingShadowBitmap.requestedSizes.clear();

        try {
            new UILFillViewportDisplayer(0, 0).display(source, aware, LoadedFrom.NETWORK);
            assertSafeBitmap(aware.bitmap);
        } catch (IllegalArgumentException expected) {
            // Rejected before an invalid size is used.
        }
        assertEveryAllocationIsSafe();
    }

    private static Bitmap bitmap(int width, int height) {
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    }

    private static void assertRejectedOrBounded(int sourceWidth, int sourceHeight,
            int viewportWidth, int viewportHeight) {
        try {
            Rect target = CropViewExtensions.computeTargetSize(
                    sourceWidth, sourceHeight, viewportWidth, viewportHeight);
            assertSafeSize(target.width(), target.height());
        } catch (IllegalArgumentException expected) {
            // Rejected before an invalid size is used.
        }
    }

    private static void assertSafeBitmap(Bitmap bitmap) {
        assertThat(bitmap).isNotNull();
        assertSafeSize(bitmap.getWidth(), bitmap.getHeight());
    }

    private static void assertEveryAllocationIsSafe() {
        for (int[] size : AllocationRecordingShadowBitmap.requestedSizes) {
            assertSafeSize(size[0], size[1]);
        }
    }

    private static void assertSafeSize(int width, int height) {
        long pixels = (long) width * (long) height;
        assertThat(width)
                .overridingErrorMessage(
                        "width %s is outside 1..%s (size %sx%s, pixels %s)",
                        width, MAX_TARGET_SIDE, width, height, pixels)
                .isGreaterThan(0)
                .isLessThanOrEqualTo(MAX_TARGET_SIDE);
        assertThat(height)
                .overridingErrorMessage(
                        "height %s is outside 1..%s (size %sx%s, pixels %s)",
                        height, MAX_TARGET_SIDE, width, height, pixels)
                .isGreaterThan(0)
                .isLessThanOrEqualTo(MAX_TARGET_SIDE);
        assertThat(pixels)
                .overridingErrorMessage(
                        "pixel count %s exceeds %s (size %sx%s)",
                        pixels, MAX_TARGET_PIXELS, width, height)
                .isLessThanOrEqualTo(MAX_TARGET_PIXELS);
    }

    /**
     * Records every size passed to {@link Bitmap#createScaledBitmap} and
     * {@link Bitmap#createBitmap(int, int, Bitmap.Config)}. Built from Robolectric 3.0
     * {@link ShadowBitmap}, which stores those dimensions as the bitmap's width and height.
     * A later call with a safe size does not erase an earlier unsafe request.
     */
    @Implements(Bitmap.class)
    public static class AllocationRecordingShadowBitmap extends ShadowBitmap {
        static final List<int[]> requestedSizes = new ArrayList<int[]>();

        @Implementation
        public static Bitmap createScaledBitmap(Bitmap src, int dstWidth, int dstHeight, boolean filter) {
            requestedSizes.add(new int[] { dstWidth, dstHeight });
            return ShadowBitmap.createScaledBitmap(src, dstWidth, dstHeight, filter);
        }

        @Implementation
        public static Bitmap createBitmap(int width, int height, Bitmap.Config config) {
            requestedSizes.add(new int[] { width, height });
            return ShadowBitmap.createBitmap(width, height, config);
        }
    }

    /**
     * Sink used by {@link UILFillViewportDisplayer}. The displayer scales first and then calls
     * {@link ImageAware#setImageBitmap}; this only keeps that bitmap.
     */
    private static final class RecordingImageAware implements ImageAware {
        private Bitmap bitmap;

        @Override
        public int getWidth() {
            return 0;
        }

        @Override
        public int getHeight() {
            return 0;
        }

        @Override
        public ViewScaleType getScaleType() {
            return ViewScaleType.CROP;
        }

        @Override
        public View getWrappedView() {
            return null;
        }

        @Override
        public boolean isCollected() {
            return false;
        }

        @Override
        public int getId() {
            return System.identityHashCode(this);
        }

        @Override
        public boolean setImageDrawable(Drawable drawable) {
            return true;
        }

        @Override
        public boolean setImageBitmap(Bitmap bitmap) {
            this.bitmap = bitmap;
            return true;
        }
    }
}
