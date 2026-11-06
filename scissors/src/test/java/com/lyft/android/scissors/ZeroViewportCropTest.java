package com.lyft.android.scissors;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.view.View;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A default viewport ratio of 0 adopts the bitmap aspect ratio. {@code TouchManager}
 * then casts one viewport side to {@code int}. A very wide or very tall bitmap truncates
 * that side to 0, and {@code CropView.crop} passes it to {@code Bitmap.createBitmap}.
 *
 * These tests are the spec for that crash. They fail while a zero side is still passed
 * to {@code Bitmap.createBitmap}, and they pass once both viewport sides are at least 1
 * pixel, still fit inside the laid-out view after overlay padding, and {@code crop}
 * returns that bitmap.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class ZeroViewportCropTest {

    private static final int VIEW_SIZE = 400;
    private static final int EXTREME = 10000;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private CropView cropView;

    @Before
    public void layOutDefaultRatioView() {
        cropView = new CropView(RuntimeEnvironment.application);
        layOut(VIEW_SIZE, VIEW_SIZE);
    }

    @Test
    public void defaultRatio_ordinaryLandscapeBitmap_cropsToFittingViewport() {
        setBitmap(200, 100);

        Bitmap cropped = cropView.crop();

        // crop() asks for the source size, so the 400x200 viewport is scaled down by 2.
        assertViewport(VIEW_SIZE, 200);
        assertThat(cropped.getWidth()).isEqualTo(200);
        assertThat(cropped.getHeight()).isEqualTo(100);
    }

    @Test
    public void defaultRatio_ordinaryPortraitBitmap_cropsToFittingViewport() {
        setBitmap(100, 200);

        Bitmap cropped = cropView.crop();

        // crop() asks for the source size, so the 200x400 viewport is scaled down by 2.
        assertViewport(200, VIEW_SIZE);
        assertThat(cropped.getWidth()).isEqualTo(100);
        assertThat(cropped.getHeight()).isEqualTo(200);
    }

    @Test
    public void explicitRatioOfOne_extremeBitmap_doesNotTruncateViewport() {
        setBitmap(EXTREME, 1);
        cropView.setViewportRatio(1f);

        Bitmap cropped = cropView.crop();

        assertViewport(VIEW_SIZE, VIEW_SIZE);
        assertThat(cropped.getWidth()).isGreaterThanOrEqualTo(1);
        assertThat(cropped.getHeight()).isGreaterThanOrEqualTo(1);
    }

    @Test
    public void defaultRatio_extremelyWideBitmap_cropReturnsPositiveBitmap() {
        setBitmap(EXTREME, 1);

        Bitmap cropped = crop();

        assertViewport(VIEW_SIZE, 1);
        assertThat(cropped.getWidth()).isEqualTo(VIEW_SIZE);
        assertThat(cropped.getHeight()).isEqualTo(1);
    }

    @Test
    public void defaultRatio_extremelyTallBitmap_cropReturnsPositiveBitmap() {
        setBitmap(1, EXTREME);

        Bitmap cropped = crop();

        assertViewport(1, VIEW_SIZE);
        assertThat(cropped.getWidth()).isEqualTo(1);
        assertThat(cropped.getHeight()).isEqualTo(VIEW_SIZE);
    }

    @Test
    public void defaultRatio_bitmapJustWiderThanTheView_cropReturnsPositiveBitmap() {
        setBitmap(VIEW_SIZE + 1, 1);

        Bitmap cropped = crop();

        assertViewport(VIEW_SIZE, 1);
        assertThat(cropped.getWidth()).isEqualTo(VIEW_SIZE);
        assertThat(cropped.getHeight()).isEqualTo(1);
    }

    @Test
    public void defaultRatio_bitmapJustTallerThanTheView_cropReturnsPositiveBitmap() {
        setBitmap(1, VIEW_SIZE + 1);

        Bitmap cropped = crop();

        assertViewport(1, VIEW_SIZE);
        assertThat(cropped.getWidth()).isEqualTo(1);
        assertThat(cropped.getHeight()).isEqualTo(VIEW_SIZE);
    }

    @Test
    public void cropRequestedAtSourceSize_extremelyWideBitmap_returnsPositiveBitmap() {
        Bitmap source = setBitmap(EXTREME, 1);

        Bitmap cropped = crop(source.getWidth(), source.getHeight());

        assertViewport(VIEW_SIZE, 1);
        assertThat(cropped.getWidth()).isEqualTo(VIEW_SIZE);
        assertThat(cropped.getHeight()).isEqualTo(1);
    }

    @Test
    public void cropRequestedAtSourceSize_extremelyTallBitmap_returnsPositiveBitmap() {
        Bitmap source = setBitmap(1, EXTREME);

        Bitmap cropped = crop(source.getWidth(), source.getHeight());

        assertViewport(1, VIEW_SIZE);
        assertThat(cropped.getWidth()).isEqualTo(1);
        assertThat(cropped.getHeight()).isEqualTo(VIEW_SIZE);
    }

    @Test
    public void cropWithNoDownscale_extremelyWideBitmap_returnsPositiveBitmap() {
        setBitmap(EXTREME, 1);

        Bitmap cropped = crop(0, 0);

        assertViewport(VIEW_SIZE, 1);
        assertThat(cropped.getWidth()).isEqualTo(VIEW_SIZE);
        assertThat(cropped.getHeight()).isEqualTo(1);
    }

    @Test
    public void setViewportRatioZero_reappliesImageRatio_andCropStaysPositive() {
        setBitmap(EXTREME, 1);
        cropView.setViewportRatio(1f);
        assertThat(cropView.getViewportWidth()).isEqualTo(VIEW_SIZE);
        assertThat(cropView.getViewportHeight()).isEqualTo(VIEW_SIZE);

        cropView.setViewportRatio(0f);
        Bitmap cropped = crop();

        assertViewport(VIEW_SIZE, 1);
        assertThat(cropped.getWidth()).isEqualTo(VIEW_SIZE);
        assertThat(cropped.getHeight()).isEqualTo(1);
    }

    @Test
    public void explicitExtremeViewportRatio_squareBitmap_cropStaysPositive() {
        setBitmap(80, 80);

        cropView.setViewportRatio(EXTREME);
        // crop(0, 0) is the extensions default: no downscale, so the bitmap is the viewport.
        Bitmap cropped = crop(0, 0);

        assertViewport(VIEW_SIZE, 1);
        assertThat(cropped.getWidth()).isEqualTo(VIEW_SIZE);
        assertThat(cropped.getHeight()).isEqualTo(1);
    }

    @Test
    public void explicitTinyViewportRatio_squareBitmap_cropStaysPositive() {
        setBitmap(80, 80);

        cropView.setViewportRatio(1f / EXTREME);
        Bitmap cropped = crop(0, 0);

        assertViewport(1, VIEW_SIZE);
        assertThat(cropped.getWidth()).isEqualTo(1);
        assertThat(cropped.getHeight()).isEqualTo(VIEW_SIZE);
    }

    @Test
    public void setImageDrawable_extremeBitmap_cropStaysPositive() {
        Bitmap source = Bitmap.createBitmap(EXTREME, 1, Bitmap.Config.ARGB_8888);
        cropView.setImageDrawable(
                new BitmapDrawable(RuntimeEnvironment.application.getResources(), source));

        Bitmap cropped = crop();

        assertViewport(VIEW_SIZE, 1);
        assertThat(cropped.getWidth()).isEqualTo(VIEW_SIZE);
        assertThat(cropped.getHeight()).isEqualTo(1);
    }

    @Test
    public void setViewportOverlayPadding_recomputesExtremeViewportWithoutZeroSide() {
        setBitmap(EXTREME, 1);
        int padding = 10;

        cropView.setViewportOverlayPadding(padding);
        Bitmap cropped = crop();

        int inner = VIEW_SIZE - padding * 2;
        assertViewport(inner, 1);
        assertThat(cropped.getWidth()).isEqualTo(inner);
        assertThat(cropped.getHeight()).isEqualTo(1);
    }

    @Test
    public void onSizeChanged_recomputesExtremeViewportWithoutZeroSide() {
        layOut(200, 200);
        setBitmap(EXTREME, 1);

        layOut(VIEW_SIZE, VIEW_SIZE);
        Bitmap cropped = crop();

        assertViewport(VIEW_SIZE, 1);
        assertThat(cropped.getWidth()).isEqualTo(VIEW_SIZE);
        assertThat(cropped.getHeight()).isEqualTo(1);
    }

    @Test
    public void extensionsIntoFile_extremeBitmap_writesCroppedBytes() throws Exception {
        setBitmap(EXTREME, 1);
        File out = new File(folder.getRoot(), "cropped.jpg");
        assertThat(out.exists()).isFalse();

        Future<Void> pending = intoFile(out);
        pending.get(10, TimeUnit.SECONDS);

        assertViewport(VIEW_SIZE, 1);
        assertThat(out).exists();
        assertThat(out.length()).isGreaterThan(0L);
    }

    @Test
    public void extensionsIntoStream_extremeBitmap_writesCroppedBytes() throws Exception {
        setBitmap(1, EXTREME);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        Future<Void> pending = intoStream(out);
        pending.get(10, TimeUnit.SECONDS);

        assertViewport(1, VIEW_SIZE);
        assertThat(out.size()).isGreaterThan(0);
    }

    private Bitmap setBitmap(int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        cropView.setImageBitmap(bitmap);
        return bitmap;
    }

    private void layOut(int width, int height) {
        cropView.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        cropView.layout(0, 0, width, height);
        assertThat(cropView.getWidth()).isEqualTo(width);
        assertThat(cropView.getHeight()).isEqualTo(height);
    }

    private void assertViewport(int width, int height) {
        assertThat(width).isGreaterThanOrEqualTo(1);
        assertThat(height).isGreaterThanOrEqualTo(1);
        assertThat(cropView.getViewportWidth()).isEqualTo(width);
        assertThat(cropView.getViewportHeight()).isEqualTo(height);
        assertThat(cropView.getViewportWidth()).isLessThanOrEqualTo(cropView.getWidth());
        assertThat(cropView.getViewportHeight()).isLessThanOrEqualTo(cropView.getHeight());
    }

    private Bitmap crop() {
        try {
            return requirePositive(cropView.crop());
        } catch (IllegalArgumentException ex) {
            throw cropFailure(ex);
        }
    }

    private Bitmap crop(int requestedWidth, int requestedHeight) {
        try {
            return requirePositive(cropView.crop(requestedWidth, requestedHeight));
        } catch (IllegalArgumentException ex) {
            throw cropFailure(ex);
        }
    }

    private Future<Void> intoFile(File out) {
        try {
            return cropView.extensions().crop().into(out);
        } catch (RuntimeException ex) {
            if (out.exists()) {
                throw new AssertionError(state()
                        + " — into(File) created " + out.getName()
                        + " (" + out.length() + " bytes) before crop failed", ex);
            }
            throw cropFailure(ex);
        }
    }

    private Future<Void> intoStream(ByteArrayOutputStream out) {
        int sizeBefore = out.size();
        try {
            return cropView.extensions().crop().into(out, false);
        } catch (RuntimeException ex) {
            if (out.size() != sizeBefore) {
                throw new AssertionError(state()
                        + " — into(OutputStream) wrote bytes before crop failed", ex);
            }
            throw cropFailure(ex);
        }
    }

    private static Bitmap requirePositive(Bitmap cropped) {
        assertThat(cropped).isNotNull();
        assertThat(cropped.getWidth()).isGreaterThanOrEqualTo(1);
        assertThat(cropped.getHeight()).isGreaterThanOrEqualTo(1);
        return cropped;
    }

    private AssertionError cropFailure(RuntimeException ex) {
        return new AssertionError(state()
                + " — crop must not pass a zero-sized viewport to Bitmap.createBitmap ("
                + ex.getClass().getSimpleName() + ": " + ex.getMessage() + ")", ex);
    }

    private String state() {
        Bitmap bitmap = cropView.getImageBitmap();
        String bitmapSize = bitmap == null
                ? "none"
                : bitmap.getWidth() + "x" + bitmap.getHeight();
        return "bitmap " + bitmapSize
                + " view " + cropView.getWidth() + "x" + cropView.getHeight()
                + " viewport " + cropView.getViewportWidth() + "x" + cropView.getViewportHeight()
                + " ratio " + cropView.getViewportRatio();
    }
}
