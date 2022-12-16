package com.cardstackview.internal;

import android.view.animation.Interpolator;

import com.cardstackview.Direction;

public interface AnimationSetting {
    Direction getDirection();
    int getDuration();
    Interpolator getInterpolator();
}
