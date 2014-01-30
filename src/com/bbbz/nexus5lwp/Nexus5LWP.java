
package com.bbbz.nexus5lwp;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;
import android.widget.Toast;

/*
 * This animated wallpaper draws the Nexus 5 wallpaper
 */
public class Nexus5LWP extends WallpaperService {

    public static final String SHARED_PREFS_NAME="nexus5lwpsettings";

    public static Random mRandom = new Random();

    @Override
    public Engine onCreateEngine() {
        SharedPreferences sp = getSharedPreferences(SHARED_PREFS_NAME, 0);
        SensorManager sm = (SensorManager)getSystemService(SENSOR_SERVICE);
        return new SnowEngine(sp, sm);
    }

    static class SnowHandler extends Handler {
        public SnowEngine mEngine;

        void setEngine(SnowEngine se) {
            mEngine = se;
        }

        public void handleMessage(Message msg) {
            // Background media scanning finished
            if (msg.what == 1) {
                mEngine.mBackground.loadImage();
            }
        }
    }

    class SnowEngine extends Engine implements SharedPreferences.OnSharedPreferenceChangeListener {

        private final SnowHandler mHandler = new SnowHandler();
        private final Paint mPaint = new Paint();

        private long then;
        private long now;

        private List<Thing> things;

        private SensorEventHandler seh;

        private boolean mVisible;
        private SharedPreferences mPrefs;

        private Background mBackground = new Background();

        private long target_fps = 60;
        private long orig_target_fps = -1;

        public int mWidth;
        public int mHeight;

        public double mPitch;
        public double mAzimuth;
        public double mRoll;

        SnowEngine(SharedPreferences prefs, SensorManager sm) {
            mPaint.setColor(0xffffffff);
            mPaint.setAntiAlias(false);
            mPaint.setStyle(Paint.Style.FILL);

            then = now = SystemClock.uptimeMillis();

            mHandler.setEngine(this);

            mPrefs = prefs;

            seh = new SensorEventHandler(this, sm);
        }

        private final Runnable mRestoreFPSCB = new Runnable() {
            public void run() {
                target_fps = orig_target_fps;
                orig_target_fps = -1;
            }
        };

        private void overrideTargetFPS(int fps, long howlong) {
            if (orig_target_fps == -1)
                orig_target_fps = target_fps;
            target_fps = fps;
            mHandler.removeCallbacks(mRestoreFPSCB);
            mHandler.postDelayed(mRestoreFPSCB, howlong);
        }

        private final Runnable mDrawCB = new Runnable() {
            public void run() {
                long start_frame = SystemClock.uptimeMillis();

                // Assume something went wrong if elapsed time is over 60ms
                long elapsed = Math.min((now - then), 60);
                then = now;

                tickFrame(elapsed);
                drawFrame();

                now = SystemClock.uptimeMillis();

                // Reschedule the next redraw
                mHandler.removeCallbacks(mDrawCB);
                if (mVisible) {
                    long delay = Math.max(0, 1000 / target_fps - (now - start_frame));
                    mHandler.postDelayed(mDrawCB, delay);
                }
            }
        };

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);

            // FIXME: investigate
            // surfaceHolder.setFormat(PixelFormat.RGBA_8888);

            mPrefs.registerOnSharedPreferenceChangeListener(this);
            seh.register();
        }

        @Override
        public void onDestroy() {
            for (int i = 0; i < things.size(); ++i) {
                Thing thing = things.get(i);
                thing.destroyEngine();
            }

            mHandler.removeCallbacks(mDrawCB);

            seh.unregister();
            mPrefs.unregisterOnSharedPreferenceChangeListener(this);
            super.onDestroy();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            mVisible = visible;
            mHandler.removeCallbacks(mDrawCB);
            if (visible) {
                seh.register();

                // Reset internal clock, not to deliver an insane elapsed time
                // to application (used to happen after waking up from idle)
                then = now = SystemClock.uptimeMillis();

                // Force a redraw immediately, should for instance we depend
                // on current time of day (i.e. drawing a clock) - this will
                // reschedule next draw as well.
                mDrawCB.run();
            } else {
                seh.unregister();
            }
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);

            things = new ArrayList<Thing>();
            things.add(mBackground);
            //things.add(new FPSCounter());

            mBackground.setImageAsset("data/nexus5bkg3.jpg");
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);

            mWidth = width;
            mHeight = height;

            for (int i = 0; i < things.size(); ++i) {
                Thing thing = things.get(i);
                thing.setSize(width, height);
            }

            // Emit synthetic changed events to read current value of prefs
            // (order matters, bkgimage needs to know surface sizes)
            onSharedPreferenceChanged(mPrefs, "target_fps");
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            mVisible = false;
            mHandler.removeCallbacks(mDrawCB);
            super.onSurfaceDestroyed(holder);
        }

        @Override
        public void onOffsetsChanged(float xOffset, float yOffset,
                float xStep, float yStep,
                int xPixels, int yPixels) {

            // FIXME: called here because the framework calls onOffsetsChanged
            // lots of times shortly after preview engine creation.
            if (isPreview()) {
                xOffset = 0.5f;
                yOffset = 0.0f;
            }

            // Temporarily (100ms) override target framerate to 25 fps, not to
            // cause lag when browsing through home screens.
            //overrideTargetFPS(25, 100);

            for (int i = 0; i < things.size(); ++i) {
                Thing thing = things.get(i);
                thing.setOffsets(xOffset, yOffset);
            }
        }

        @Override
        public void onDesiredSizeChanged(int desiredWidth, int desiredHeight) {
            super.onDesiredSizeChanged(desiredWidth, desiredHeight);

            for (int i = 0; i < things.size(); ++i) {
                Thing thing = things.get(i);
                thing.setLWPSize(desiredWidth, desiredHeight);
            }
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (key.equals("target_fps")) {
                String tfps = mPrefs.getString("target_fps", "0");
                if (tfps.equals("0")) {
                    target_fps = 60;
                } else if (tfps.equals("1")) {
                    target_fps = 40;
                } else if (tfps.equals("2")) {
                    target_fps = 30;
                }
            }
        }

        public void onShake(float x, float y, float z, float last_x, float last_y, float last_z) {
            for (int i = 0; i < things.size(); ++i) {
                Thing thing = things.get(i);
                thing.onShake(x, y, z, last_x, last_y, last_z);
            }
        }

        void tickFrame(long elapsed) {
            for (int i = 0; i < things.size(); ++i) {
                Thing thing = things.get(i);
                thing.setAngles(mPitch,
                                mAzimuth,
                                mRoll);
                thing.tick(elapsed);
            }
        }

        /*
         * Draw one frame of the animation. This method gets called repeatedly
         * by posting a delayed Runnable. You can do any drawing you want in
         * here.
         */
        void drawFrame() {
            final SurfaceHolder holder = getSurfaceHolder();

            Canvas c = null;
            try {
                c = holder.lockCanvas();
                if (c != null) {
                    // draw something
                    for (int i = 0; i < things.size(); ++i) {
                        Thing thing = things.get(i);
                        thing.draw(c, mPaint);
                    }
                }
            } finally {
                if (c != null)
                    holder.unlockCanvasAndPost(c);
            }
        }
    }

    class Thing {
        public int mWidth;
        public int mHeight;
        public int lwpWidth;
        public int lwpHeight;

        public float xOffset;
        public float yOffset;

        public double mPitch;
        public double mAzimuth;
        public double mRoll;

        public boolean mEnabled = true;

        public void setEnabled(boolean enabled) {
            mEnabled = enabled;
        }

        void setSize(int width, int height) {
            mWidth = width;
            mHeight = height;
            onSizeChanged();
        }

        public void setAngles(double pitch, double azimuth, double roll) {
            mPitch = pitch;
            mAzimuth = azimuth;
            mRoll = roll;
        }

        void onSizeChanged() {
        }

        void setOffsets(float ox, float oy) {
            xOffset = ox;
            yOffset = oy;
            onOffsetsChanged();
        }

        void onOffsetsChanged() {

        }

        void setLWPSize(int width, int height) {
            lwpWidth = width;
            lwpHeight = height;
            onLWPSizeChanged();
        }

        void onLWPSizeChanged() {

        }

        void onShake(float x, float y, float z,
                float last_x, float last_y, float last_z) {
        }

        void destroyEngine() {
        }

        void tick(long elapsed) {
        }

        void draw(Canvas c, Paint p) {
        }
    }

    class Background extends Thing {
        String mImgAsset = null;
        Bitmap image = null;

        int mSampleSize = -1;

        void loadImage() {
            if (mImgAsset == null) {
                return;
            }

            InputStream istr = null;
            try {
                istr = getAssets().open(mImgAsset);
            } catch (IOException e) {
                return;
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(istr, null, options);

            int imwidth = options.outWidth;
            int imheight = options.outHeight;

            int sample_size;
            if (imwidth > imheight) {
                sample_size = (int)Math.floor(imheight / (double)mHeight);
            } else {
                sample_size = (int)Math.floor(imwidth / (double)mWidth);
            }
            sample_size = Math.max(sample_size, 1);

            if (sample_size < mSampleSize || mSampleSize == -1) {
                // A new screen size (caused by rotation probably) causes a
                // higher definition reload.
                recycleImage();
                mSampleSize = sample_size;
            } else {
                // no need to decode once more, the current version is fine.
                return;
            }

            options.inJustDecodeBounds = false;
            options.inSampleSize = sample_size;

            try {
                image = BitmapFactory.decodeStream(istr, null, options);
            } catch (OutOfMemoryError e) {
                Toast.makeText(getBaseContext(), "Not enough memory to load image", Toast.LENGTH_SHORT).show();
            }
        }

        void recycleImage() {
            if (image != null)
                image.recycle();
            image = null;
            System.gc();
        }

        void destroyEngine() {
            // Force a recycle of the image when destroying the Engine.  This
            // is useful when closing the preview mode, otherwise memory stays
            // allocated for longer than expected and subsequent allocations
            // fail.  This caused the out of memory error on Galaxy S3.
            recycleImage();
        }

        void onSizeChanged() {
            // Size changes (rotation?) might cause us to want a higher
            // definition image.
            // Also, we don't know which definition to load until right now,
            // when we know the surface size.
            loadImage();
        }

        void draw(Canvas c, Paint p) {
            if (!mEnabled)
                return;

            if (image == null) {
                // Useful when the image hasn't been loaded yet (user just
                // powered on the phone and it's still scanning media).
                p.setColor(Color.BLACK);
                c.drawRect(0, 0, mWidth, mHeight, p);
                return;
            }

            double x_delta, y_delta;

            if (mWidth < mHeight) {
                x_delta = mPitch;
                y_delta = (mRoll - 45);
            } else {
                x_delta = -(mPitch);
                y_delta = -(mRoll + 45);
            }

            int imwidth = image.getWidth();
            int imheight = image.getHeight();

            int xbase = (int)((mWidth / 2.0) - (imwidth / 2.0));
            int ybase = (int)((mHeight / 2.0) - (imheight / 2.0));

            xbase -= (x_delta * 2.0f);
            ybase -= (y_delta * 2.0f);

            xbase += (0.5f - xOffset) * mWidth;

            if (imwidth < mWidth || imheight < mHeight) {
                // Cover rectangle to avoid previous frames trails
                p.setColor(Color.BLACK);
                c.drawRect(0, 0, mWidth, mHeight, p);
            } else {
                // Ensure image is capped to screen
                xbase = Math.max(xbase, -imwidth + mWidth);
                xbase = Math.min(xbase, 0);
                ybase = Math.max(ybase, -imheight + mHeight);
                ybase = Math.min(ybase, 0);
            }

            c.drawBitmap(image, xbase, ybase, p);
        }

        public void setImageAsset(String bkgimage) {
            mImgAsset = bkgimage;
            loadImage();
        }
    }

    class FPSCounter extends Thing {
        public long last;
        public long now;
        public int howmany_prev;
        public int howmany_cur;

        FPSCounter() {
            now = SystemClock.uptimeMillis();
            last = now;
            howmany_prev = 0;
            howmany_cur = 0;
        }
        void rearm() {
            last = now;
            howmany_prev = howmany_cur;
            howmany_cur = 0;
        }
        void draw(Canvas c, Paint p) {
            howmany_cur++;
            now = SystemClock.uptimeMillis();

            p.setColor(Color.WHITE);
            p.setStrokeWidth(1.f);
            p.setTextSize(20.f);
            c.drawText(String.valueOf(howmany_prev), 40, 120, p);

            if (now - last > 1000) {
                rearm();
            }
        }
    }
}
