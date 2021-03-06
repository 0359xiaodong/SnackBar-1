package com.kenny.snackbar;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.support.annotation.ColorRes;
import android.support.annotation.IntegerRes;
import android.support.annotation.InterpolatorRes;
import android.support.annotation.StringRes;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

/*
 * Copyright (C) 2014 Kenny Campagna
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
public class SnackBarItem {
    private static final int[] ATTR = new int[]
            {
                    R.attr.snack_bar_background_color,
                    R.attr.snack_bar_duration,
                    R.attr.snack_bar_interpolator,
                    R.attr.snack_bar_text_action_color,
                    R.attr.snack_bar_text_color
            };

    private View.OnClickListener mActionClickListener;

    private View mSnackBarView;

    // The animation set used for animation. Set as an object for compatibility
    private AnimatorSet mAnimator;

    private String mMessageString;

    private String mActionMessage;

    // The color of the background
    private int mSnackBarColor = -1;

    private int mMessageColor = -1;

    // The default color the action item will be
    private int mDefaultActionColor = -1;

    /* Flag for when the animation is canceled, should the item be disposed of. Will be set to false when
     the action button is selected so it removes immediately.*/
    private boolean mShouldDisposeOnCancel = true;

    private boolean mIsDisposed = false;

    private Activity mActivity;

    private SnackBarListener mSnackBarListener;

    private long mAnimationDuration = -1;

    @InterpolatorRes
    private int mInterpolatorId = -1;

    private Object mObject;

    private float mPreviousY;

    private SnackBarItem(Activity activty) {
        mActivity = activty;
    }

    /**
     * Shows the Snack Bar. This method is strictly for the SnackBarManager to call.
     *
     * @param activity
     */
    public void show(Activity activity) {
        if (TextUtils.isEmpty(mMessageString)) {
            throw new IllegalArgumentException("No message has been set for the Snack Bar");
        }

        mActivity = activity;
        FrameLayout parent = (FrameLayout) activity.findViewById(android.R.id.content);
        mSnackBarView = activity.getLayoutInflater().inflate(R.layout.snack_bar, parent, false);
        getAttributes(mActivity);

        // Setting up the background
        Drawable bg = mSnackBarView.getBackground();

        // Tablet SnackBars have a shape drawable as a background
        if (bg instanceof GradientDrawable) {
            ((GradientDrawable) bg).setColor(mSnackBarColor);
        } else {
            mSnackBarView.setBackgroundColor(mSnackBarColor);
        }

        setupGestureDetector();
        TextView messageTV = (TextView) mSnackBarView.findViewById(R.id.message);
        messageTV.setText(mMessageString);
        messageTV.setTextColor(mMessageColor);
        messageTV.setTypeface(Typeface.createFromAsset(mActivity.getAssets(), "RobotoCondensed-Regular.ttf"));

        if (!TextUtils.isEmpty(mActionMessage)) {
            // Only set up the action button when an action message ahs been supplied
            setupActionButton((Button) mSnackBarView.findViewById(R.id.action));
        }

        if (mAnimationDuration <= 0)
            mAnimationDuration = activity.getResources().getInteger(R.integer.snackbar_duration_length);
        if (mInterpolatorId == -1) mInterpolatorId = android.R.interpolator.accelerate_decelerate;
        parent.addView(mSnackBarView);
        createShowAnimation();
    }

    /**
     * Sets up the touch listener to allow the SnackBar to be swiped to dismissed
     * Code from https://github.com/MrEngineer13/SnackBar
     */
    private void setupGestureDetector() {
        mSnackBarView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (mIsDisposed) return false;

                float y = event.getY();

                switch (event.getAction()) {
                    case MotionEvent.ACTION_MOVE:
                        int[] location = new int[2];
                        view.getLocationInWindow(location);

                        if (y > mPreviousY && y - mPreviousY >= 50) {
                            mShouldDisposeOnCancel = false;
                            mAnimator.cancel();
                            createHideAnimation();
                        }
                }

                mPreviousY = y;
                return true;
            }
        });
    }

    /**
     * Sets up the action button if available
     *
     * @param action
     */
    private void setupActionButton(Button action) {
        action.setVisibility(View.VISIBLE);
        action.setText(mActionMessage.toUpperCase());
        action.setTextColor(mDefaultActionColor);

        action.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mShouldDisposeOnCancel = false;
                mAnimator.cancel();
                if (mActionClickListener != null) mActionClickListener.onClick(view);
                if (mSnackBarListener != null) mSnackBarListener.onSnackBarAction(mObject);
                createHideAnimation();
            }
        });
    }

    /**
     * Gets the attributes to be used for the SnackBar from the context style
     *
     * @param context
     */
    private void getAttributes(Context context) {
        TypedArray a = context.obtainStyledAttributes(ATTR);
        Resources res = context.getResources();

        if (mSnackBarColor == -1)
            mSnackBarColor = a.getColor(0, res.getColor(R.color.snack_bar_bg));
        if (mAnimationDuration == -1) mAnimationDuration = a.getInt(1, 3000);
        if (mInterpolatorId == -1)
            mInterpolatorId = a.getResourceId(2, android.R.anim.accelerate_decelerate_interpolator);
        if (mDefaultActionColor == -1)
            mDefaultActionColor = a.getColor(3, res.getColor(R.color.snack_bar_action_default));
        if (mMessageColor == -1) mMessageColor = a.getColor(4, Color.WHITE);
        a.recycle();
    }

    /**
     * Cancels the Snack Bar from being displayed
     */
    public void cancel() {
        if (mAnimator != null) mAnimator.cancel();
        dispose();
    }

    /**
     * Cleans up the Snack Bar when finished
     */
    private void dispose() {
        mIsDisposed = true;

        if (mSnackBarView != null) {
            FrameLayout parent = (FrameLayout) mSnackBarView.getParent();
            if (parent != null) parent.removeView(mSnackBarView);
        }

        mAnimator = null;
        mSnackBarView = null;
        mActionClickListener = null;
        SnackBar.dispose(mActivity, this);
    }

    /**
     * Sets up and starts the show animation
     */
    private void createShowAnimation() {
        mAnimator = new AnimatorSet();
        mAnimator.setInterpolator(AnimationUtils.loadInterpolator(mActivity, mInterpolatorId));
        Animator appear = getAppearAnimation();
        appear.setTarget(mSnackBarView);

        mAnimator.playSequentially(
                appear,
                ObjectAnimator.ofFloat(mSnackBarView, "alpha", 1.0f, 1.0f).setDuration(mAnimationDuration),
                ObjectAnimator.ofFloat(mSnackBarView, "alpha", 1.0f, 0.0f).setDuration(mActivity.getResources().getInteger(R.integer.snackbar_disappear_animation_length))
        );

        mAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (mShouldDisposeOnCancel) {
                    if (mSnackBarListener != null) mSnackBarListener.onSnackBarFinished(mObject);
                    dispose();
                }

            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (mShouldDisposeOnCancel) dispose();

            }

            @Override
            public void onAnimationStart(Animator animation) {
                if (mSnackBarListener != null) mSnackBarListener.onSnackBarStarted(mObject);
            }
        });

        mAnimator.start();
    }

    /**
     * Sets up and starts the hide animation
     */
    private void createHideAnimation() {

        ObjectAnimator anim = ObjectAnimator.ofFloat(mSnackBarView, "alpha", 1.0f, 0.0f)
                .setDuration(mActivity.getResources().getInteger(R.integer.snackbar_disappear_animation_length));

        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationCancel(Animator animation) {
                dispose();
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (mSnackBarListener != null) mSnackBarListener.onSnackBarFinished(mObject);
                dispose();
            }
        });

        anim.start();
    }

    /**
     * Returns the animator for the appear animation
     *
     * @return
     */
    private Animator getAppearAnimation() {
        // Only Kit-Kit+ devices can have a translucent style
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Resources res = mActivity.getResources();
            int[] attrs = new int[]{android.R.attr.windowTranslucentNavigation};
            TypedArray a = mActivity.getTheme().obtainStyledAttributes(attrs);
            boolean isTranslucent = a.getBoolean(0, false);
            a.recycle();

            if (isTranslucent) {
                boolean isLandscape = res.getBoolean(R.bool.sb_isLandscape);
                boolean isTablet = res.getBoolean(R.bool.sb_isTablet);

                // Translucent nav bars will appear on anything that isn't landscape, as well as tablets in landscape
                if (!isLandscape || isTablet) {
                    int resourceId = res.getIdentifier("navigation_bar_height", "dimen", "android");
                    float animationFrom = res.getDimension(R.dimen.snack_bar_height);
                    float animationTo = res.getDimension(R.dimen.snack_bar_animation_position);
                    if (resourceId > 0) animationTo -= res.getDimensionPixelSize(resourceId);

                    AnimatorSet set = new AnimatorSet();
                    set.playTogether(
                            ObjectAnimator.ofFloat(mSnackBarView, "translationY", animationFrom, animationTo),
                            ObjectAnimator.ofFloat(mSnackBarView, "alpha", 0.0f, 1.0f));
                    return set;
                }
            }
        }

        return AnimatorInflater.loadAnimator(mActivity, R.animator.appear);
    }

    /**
     * Factory for building custom SnackBarItems
     */
    public static class Builder {
        private SnackBarItem mSnackBarItem;

        /**
         * Factory for creating SnackBarItems
         */
        public Builder(Activity activity) {
            mSnackBarItem = new SnackBarItem(activity);
        }

        /**
         * Sets the message for the SnackBarItem
         *
         * @param message
         * @return
         */
        public Builder setMessage(String message) {
            mSnackBarItem.mMessageString = message;
            return this;
        }

        /**
         * Sets the message for the SnackBarItem
         *
         * @param message
         * @return
         */
        public Builder setMessageResource(@StringRes int message) {
            mSnackBarItem.mMessageString = mSnackBarItem.mActivity.getString(message);
            return this;
        }

        /**
         * Sets the Action Message of the SnackbarItem
         *
         * @param actionMessage
         * @return
         */
        public Builder setActionMessage(String actionMessage) {
            // guard against any null values being passed
            if (TextUtils.isEmpty(actionMessage)) return this;

            mSnackBarItem.mActionMessage = actionMessage.toUpperCase();
            return this;
        }

        /**
         * Sets the Action Message of the SnackbarItem
         *
         * @param actionMessage
         * @return
         */
        public Builder setActionMessageResource(@StringRes int actionMessage) {
            mSnackBarItem.mActionMessage = mSnackBarItem.mActivity.getString(actionMessage);
            return this;
        }

        /**
         * Sets the onClick listener for the action message
         *
         * @param onClickListener
         * @return
         */
        public Builder setActionClickListener(View.OnClickListener onClickListener) {
            mSnackBarItem.mActionClickListener = onClickListener;
            return this;
        }

        /**
         * Sets the default color of the action message
         *
         * @param color
         * @return
         */
        public Builder setActionMessageColor(int color) {
            mSnackBarItem.mDefaultActionColor = color;
            return this;
        }

        /**
         * Sets the default color of the action message
         *
         * @param color
         * @return
         */
        public Builder setActionMessageColorResource(@ColorRes int color) {
            mSnackBarItem.mDefaultActionColor = mSnackBarItem.mActivity.getResources().getColor(color);
            return this;
        }

        /**
         * Sets the background color of the SnackBar
         *
         * @param color
         * @return
         */
        public Builder setSnackBarBackgroundColor(int color) {
            mSnackBarItem.mSnackBarColor = color;
            return this;
        }

        /**
         * Sets the background color of the SnackBar
         *
         * @param color
         * @return
         */
        public Builder setSnackBarBackgroundColorResource(@ColorRes int color) {
            mSnackBarItem.mSnackBarColor = mSnackBarItem.mActivity.getResources().getColor(color);
            return this;
        }

        /**
         * Sets the color of the message of the SnackBar
         *
         * @param color
         * @return
         */
        public Builder setSnackBarMessageColor(int color) {
            mSnackBarItem.mMessageColor = color;
            return this;
        }

        /**
         * Sets the color of the message of the SnackBar
         *
         * @param color
         * @return
         */
        public Builder setSnackBarMessageColorResource(@ColorRes int color) {
            mSnackBarItem.mMessageColor = mSnackBarItem.mActivity.getResources().getColor(color);
            return this;
        }

        /**
         * Sets the duration of the SnackBar in milliseconds
         *
         * @param duration
         * @return
         */
        public Builder setDuration(long duration) {
            mSnackBarItem.mAnimationDuration = duration;
            return this;
        }

        /**
         * Sets the duration of the SnackBar in milliseconds
         *
         * @param duration
         * @return
         */
        public Builder setDurationResource(@IntegerRes int duration) {
            mSnackBarItem.mAnimationDuration = mSnackBarItem.mActivity.getResources().getInteger(duration);
            return this;
        }

        /**
         * Set the Interpolator of the SnackBar animation
         *
         * @param interpolator
         * @return
         */
        public Builder setInterpolatorResource(@InterpolatorRes int interpolator) {
            mSnackBarItem.mInterpolatorId = interpolator;
            return this;
        }

        /**
         * Set the SnackBars object that will be returned in the SnackBarListener call backs
         *
         * @param object
         * @return
         */
        public Builder setObject(Object object) {
            mSnackBarItem.mObject = object;
            return this;
        }

        /**
         * Sets the SnackBarListener
         *
         * @param listener
         * @return
         */
        public Builder setSnackBarListener(SnackBarListener listener) {
            mSnackBarItem.mSnackBarListener = listener;
            return this;
        }

        /**
         * Shows the SnackBar
         */
        public void show() {
            SnackBar.show(mSnackBarItem.mActivity, build());
        }

        /**
         * Creates the SnackBarItem
         *
         * @return
         */
        public SnackBarItem build() {
            return mSnackBarItem;
        }
    }

}