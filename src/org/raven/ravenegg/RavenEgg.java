/*
 * Copyright (C) 2026 The RavenEgg Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.raven.ravenegg;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A quiet night sky: a flock of ravens glides past a rising moon while stars
 * twinkle over the hills. Tap the sky to make the flock flurry.
 */
public class RavenEgg extends Activity {

    private RavenSky mSky;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        setImmersive();
        getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(vis -> {
            if ((vis & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                getWindow().getDecorView().post(this::setImmersive);
            }
        });
        mSky = new RavenSky(this);
        setContentView(mSky);
    }

    private void setImmersive() {
        final Window window = getWindow();
        final View decor = window.getDecorView();
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            final WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(attrs);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            final WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars()
                        | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        setImmersive();
        mSky.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSky.stop();
    }

    public static class RavenSky extends View {
        private static final int RAVEN_COUNT = 9;
        private static final int STAR_COUNT = 90;

        private final Random mRandom = new Random();
        private final List<Raven> mRavens = new ArrayList<>();
        private final List<Star> mStars = new ArrayList<>();
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final int mRavenBodyColor;
        private final int mRavenWingColor;
        private final int mRavenEyeColor;

        private final Choreographer mChoreographer = Choreographer.getInstance();
        private final Choreographer.FrameCallback mFrameCallback = this::onFrame;

        private boolean mRunning;
        private long mLastFrameNanos;
        private float mAgeSec;
        private float mFlurry;

        private float mWidth;
        private float mHeight;
        private float mDensity;

        private LinearGradient mSkyGradient;
        private RadialGradient mMoonGlow;
        private float mMoonX;
        private float mMoonY;
        private float mMoonR;

        private final Path mStarPath = new Path();
        private final Path mRavenBody = new Path();
        private final Path mRavenWing = new Path();
        private final Path mHillsBack = new Path();
        private final Path mHillsFront = new Path();

        public RavenSky(Context context) {
            super(context);
            setLayerType(View.LAYER_TYPE_HARDWARE, null);
            mRavenBodyColor = getResources().getColor(R.color.raven_body);
            mRavenWingColor = getResources().getColor(R.color.raven_wing);
            mRavenEyeColor = getResources().getColor(R.color.raven_eye);
            buildStarPath();
            buildRavenPaths();
        }

        private static float lerp(float a, float b, float f) {
            return (b - a) * f + a;
        }

        private float rand(float a, float b) {
            return lerp(a, b, mRandom.nextFloat());
        }

        private void buildStarPath() {
            final float r = 10f;
            mStarPath.moveTo(0, -r);
            mStarPath.quadTo(1, -1, r, 0);
            mStarPath.quadTo(1, 1, 0, r);
            mStarPath.quadTo(-1, 1, -r, 0);
            mStarPath.quadTo(-1, -1, 0, -r);
            mStarPath.close();
        }

        private void buildRavenPaths() {
            mRavenBody.moveTo(6, 46);
            mRavenBody.lineTo(20, 44);
            mRavenBody.quadTo(26, 38, 36, 37);
            mRavenBody.cubicTo(50, 33, 68, 35, 82, 41);
            mRavenBody.lineTo(90, 38);
            mRavenBody.lineTo(85, 48);
            mRavenBody.lineTo(90, 54);
            mRavenBody.lineTo(78, 52);
            mRavenBody.cubicTo(62, 58, 46, 59, 34, 53);
            mRavenBody.quadTo(20, 50, 16, 46);
            mRavenBody.lineTo(8, 48);
            mRavenBody.close();

            mRavenWing.moveTo(40, 42);
            mRavenWing.quadTo(50, 16, 72, 10);
            mRavenWing.quadTo(64, 30, 60, 40);
            mRavenWing.close();
        }

        private void buildScene(float w, float h) {
            mSkyGradient = new LinearGradient(0, 0, 0, h,
                    0xFF0A0E23, 0xFF24365E, Shader.TileMode.CLAMP);

            mMoonX = w * 0.8f;
            mMoonY = h * 0.16f;
            mMoonR = 34 * mDensity;
            mMoonGlow = new RadialGradient(mMoonX, mMoonY, mMoonR * 3f,
                    new int[]{0x66F4EFD8, 0x00F4EFD8}, null, Shader.TileMode.CLAMP);

            mHillsBack.reset();
            mHillsBack.moveTo(0, h);
            mHillsBack.lineTo(0, h * 0.72f);
            mHillsBack.quadTo(w * 0.25f, h * 0.60f, w * 0.5f, h * 0.72f);
            mHillsBack.quadTo(w * 0.75f, h * 0.66f, w, h * 0.74f);
            mHillsBack.lineTo(w, h);
            mHillsBack.close();

            mHillsFront.reset();
            mHillsFront.moveTo(0, h);
            mHillsFront.lineTo(0, h * 0.86f);
            mHillsFront.quadTo(w * 0.3f, h * 0.78f, w * 0.6f, h * 0.86f);
            mHillsFront.quadTo(w * 0.85f, h * 0.80f, w, h * 0.87f);
            mHillsFront.lineTo(w, h);
            mHillsFront.close();

            mStars.clear();
            for (int i = 0; i < STAR_COUNT; i++) {
                Star s = new Star();
                s.x = rand(0, w);
                s.y = rand(0, h * 0.70f);
                s.size = rand(2.5f, 5.5f) * mDensity;
                s.phase = rand(0, (float) (Math.PI * 2));
                s.speed = rand(0.6f, 2.2f);
                s.baseAlpha = rand(0.25f, 1f);
                mStars.add(s);
            }
        }

        private void spawnFlock() {
            mRavens.clear();
            for (int i = 0; i < RAVEN_COUNT; i++) {
                Raven r = new Raven();
                r.z = mRandom.nextFloat();
                r.z *= r.z;
                r.scale = lerp(48, 150, r.z) * mDensity / 2f;
                r.speed = lerp(50, 170, r.z) * mDensity / 2f;
                r.vx = (mRandom.nextBoolean() ? 1 : -1) * r.speed;
                r.baseY = rand(mHeight * 0.14f, mHeight * 0.60f);
                r.x = mRandom.nextBoolean()
                        ? rand(-mWidth * 0.4f, 0)
                        : rand(mWidth, mWidth * 1.4f);
                r.bobPhase = rand(0, (float) (Math.PI * 2));
                r.bobSpeed = rand(1.4f, 2.6f);
                r.flapPhase = rand(0, (float) (Math.PI * 2));
                r.flapSpeed = rand(7f, 11f);
                mRavens.add(r);
            }
        }

        private void onFrame(long frameTimeNanos) {
            if (!mRunning) {
                return;
            }
            float dt = mLastFrameNanos == 0
                    ? 0f
                    : (frameTimeNanos - mLastFrameNanos) / 1e9f;
            mLastFrameNanos = frameTimeNanos;
            dt = Math.min(dt, 0.05f);

            mAgeSec += dt;
            mFlurry = Math.max(0f, mFlurry - dt * 1.5f);

            for (Raven r : mRavens) {
                r.x += r.vx * (1f + mFlurry * 2.5f) * dt;
                r.bobPhase += r.bobSpeed * dt;
                r.flapPhase += r.flapSpeed * (1f + mFlurry * 3f) * dt;
                final float margin = r.scale * 2f;
                if (r.vx > 0 && r.x > mWidth + margin) {
                    respawn(r, false);
                } else if (r.vx < 0 && r.x < -margin) {
                    respawn(r, true);
                }
            }

            invalidate();
            mChoreographer.postFrameCallback(mFrameCallback);
        }

        private void respawn(Raven r, boolean fromLeft) {
            r.x = fromLeft ? -r.scale * 2f : mWidth + r.scale * 2f;
            r.baseY = rand(mHeight * 0.14f, mHeight * 0.60f);
            r.vx = (fromLeft ? 1 : -1) * r.speed;
            r.bobPhase = rand(0, (float) (Math.PI * 2));
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            mWidth = w;
            mHeight = h;
            mDensity = getResources().getDisplayMetrics().density;
            buildScene(w, h);
            spawnFlock();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            start();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            stop();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            mPaint.setShader(mSkyGradient);
            mPaint.setAlpha(255);
            canvas.drawRect(0, 0, mWidth, mHeight, mPaint);
            mPaint.setShader(null);

            drawMoon(canvas);

            for (Star s : mStars) {
                final float twinkle = (float) (0.55 + 0.45 * Math.sin(mAgeSec * s.speed + s.phase));
                mPaint.setShader(null);
                mPaint.setColor(0xFFFFE9B8);
                mPaint.setAlpha((int) (255 * s.baseAlpha * twinkle));
                canvas.save();
                canvas.translate(s.x, s.y);
                canvas.scale(s.size / 10f, s.size / 10f);
                canvas.drawPath(mStarPath, mPaint);
                canvas.restore();
            }

            mPaint.setColor(0xFF17203F);
            mPaint.setAlpha(255);
            canvas.drawPath(mHillsBack, mPaint);
            mPaint.setColor(0xFF0E142C);
            canvas.drawPath(mHillsFront, mPaint);

            for (Raven r : mRavens) {
                drawRaven(canvas, r);
            }
        }

        private void drawMoon(Canvas canvas) {
            mPaint.setShader(mMoonGlow);
            mPaint.setAlpha(255);
            canvas.drawCircle(mMoonX, mMoonY, mMoonR * 3f, mPaint);
            mPaint.setShader(null);

            mPaint.setColor(0xFFF4EFD8);
            canvas.drawCircle(mMoonX, mMoonY, mMoonR, mPaint);

            mPaint.setColor(0x1A0E142C);
            canvas.drawCircle(mMoonX - mMoonR * 0.3f, mMoonY - mMoonR * 0.25f,
                    mMoonR * 0.22f, mPaint);
            canvas.drawCircle(mMoonX + mMoonR * 0.2f, mMoonY + mMoonR * 0.35f,
                    mMoonR * 0.15f, mPaint);
            canvas.drawCircle(mMoonX + mMoonR * 0.4f, mMoonY - mMoonR * 0.05f,
                    mMoonR * 0.12f, mPaint);
        }

        private void drawRaven(Canvas canvas, Raven r) {
            final float flap = (float) Math.sin(r.flapPhase) * 24f;
            final float bob = (float) Math.sin(r.bobPhase) * r.scale * 0.18f;
            final boolean facingLeft = r.vx < 0;
            final float unit = r.scale / 100f;

            canvas.save();
            canvas.translate(r.x, r.baseY + bob);
            canvas.rotate((facingLeft ? 1 : -1) * r.scale * 0.02f);
            canvas.scale(unit * (facingLeft ? 1 : -1), unit);

            mPaint.setShader(null);
            mPaint.setAlpha(255);

            mPaint.setColor(mRavenBodyColor);
            canvas.drawPath(mRavenBody, mPaint);

            canvas.save();
            canvas.rotate(flap, 42, 42);
            mPaint.setColor(mRavenWingColor);
            canvas.drawPath(mRavenWing, mPaint);
            canvas.restore();

            mPaint.setColor(mRavenEyeColor);
            canvas.drawCircle(28, 40, 2.6f, mPaint);
            canvas.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                mFlurry = 1f;
            }
            return true;
        }

        @Override
        public boolean isOpaque() {
            return true;
        }

        private void start() {
            if (mRunning) {
                return;
            }
            mRunning = true;
            mLastFrameNanos = 0;
            mChoreographer.postFrameCallback(mFrameCallback);
        }

        private void stop() {
            mRunning = false;
            mChoreographer.removeFrameCallback(mFrameCallback);
        }
    }

    private static class Raven {
        float x;
        float baseY;
        float vx;
        float z;
        float scale;
        float speed;
        float bobPhase;
        float bobSpeed;
        float flapPhase;
        float flapSpeed;
    }

    private static class Star {
        float x;
        float y;
        float size;
        float phase;
        float speed;
        float baseAlpha;
    }
}
