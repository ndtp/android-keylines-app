package com.keylines.app.overlay.ruler;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/**
 * Created by chrismack on 2017-08-18.
 */

public class BasicRulerView extends FrameLayout {

    AreaPainter areaPainter;
    private View topHandle;
    private View bottomHandle;

    SpringAnimation topHandleAnimationX;
    SpringAnimation topHandleAnimationYWithUpdateListener;

    SpringAnimation bottomHandleAnimationX;
    SpringAnimation bottomHandleAnimationYWithUpdateListener;

    BasicRulerVisualsListener listener;

    public BasicRulerView(Context context, AreaPainter areaPainter, View topHandle, View bottomHandle) {
        super(context);

        this.areaPainter = areaPainter;
        addView(areaPainter);

        this.topHandle = topHandle;
        addView(topHandle);

        this.bottomHandle = bottomHandle;
        addView(bottomHandle);

        topHandleAnimationX = getSpringAnimation(topHandle, SpringAnimation.X);
        topHandleAnimationX.addEndListener(new DynamicAnimation.OnAnimationEndListener() {
            @Override
            public void onAnimationEnd(DynamicAnimation animation, boolean canceled, float value, float velocity) {
                if (listener != null) {
                    listener.topHandleMoved((int) BasicRulerView.this.topHandle.getX(), (int) BasicRulerView.this.topHandle.getY());
                }
            }
        });
        topHandleAnimationYWithUpdateListener = getSpringAnimation(topHandle, SpringAnimation.Y);
        topHandleAnimationYWithUpdateListener.addUpdateListener(new DynamicAnimation.OnAnimationUpdateListener() {
            @Override
            public void onAnimationUpdate(DynamicAnimation animation, float value, float velocity) {
                if (listener != null) {
                    listener.topHandleMoved((int) BasicRulerView.this.topHandle.getX(), (int) value);
                }
            }
        });

        bottomHandleAnimationX = getSpringAnimation(bottomHandle, SpringAnimation.X);
        bottomHandleAnimationX.addEndListener(new DynamicAnimation.OnAnimationEndListener() {
            @Override
            public void onAnimationEnd(DynamicAnimation animation, boolean canceled, float value, float velocity) {
                if (listener != null) {
                    listener.bottomHandleMoved((int) BasicRulerView.this.bottomHandle.getX(), (int) BasicRulerView.this.bottomHandle.getY());
                }
            }
        });
        bottomHandleAnimationYWithUpdateListener = getSpringAnimation(bottomHandle, SpringAnimation.Y);
        bottomHandleAnimationYWithUpdateListener.addUpdateListener(new DynamicAnimation.OnAnimationUpdateListener() {
            @Override
            public void onAnimationUpdate(DynamicAnimation animation, float value, float velocity) {
                if (listener != null) {
                    listener.bottomHandleMoved((int) BasicRulerView.this.bottomHandle.getX(), (int) value);
                }
            }
        });
    }

    private SpringAnimation getSpringAnimation(View view, DynamicAnimation.ViewProperty property) {
        SpringAnimation animation = new SpringAnimation(view, property, 0);
        animation.getSpring().setStiffness(SpringForce.STIFFNESS_MEDIUM);
        animation.getSpring().setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY);
        return animation;
    }

    public void setArea(int topEdge, int bottomEdge) {
        areaPainter.setArea(topEdge, bottomEdge);
    }

    public void updateTopHandlePosition(int x, int y, boolean animated) {
        if (animated) {
            topHandleAnimationX.animateToFinalPosition(x);
            topHandleAnimationYWithUpdateListener.animateToFinalPosition(y);
        } else {
            topHandle.setX(x);
            topHandle.setY(y);
        }
    }

    public void updateBottomHandlePosition(int x, int y, boolean animated) {
        if (animated) {
            bottomHandleAnimationX.animateToFinalPosition(x);
            bottomHandleAnimationYWithUpdateListener.animateToFinalPosition(y);
        } else {
            bottomHandle.setX(x);
            bottomHandle.setY(y);
        }
    }

    public void setRulerVisualsListener(BasicRulerVisualsListener listener) {
        this.listener = listener;
    }

    public interface BasicRulerVisualsListener {
        void topHandleMoved(int x, int y);

        void bottomHandleMoved(int x, int y);
    }


    public static abstract class AreaPainter extends View {
        public AreaPainter(Context context) {
            super(context);
        }
        abstract void setArea(int topEdge, int bottomEdge);
    }
}
