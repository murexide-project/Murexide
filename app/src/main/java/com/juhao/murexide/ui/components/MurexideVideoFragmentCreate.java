package com.juhao.murexide.ui.components;

import android.view.View;

import com.flyjingfish.openimagelib.BaseImageFragment;
import com.flyjingfish.openimagelib.listener.VideoFragmentCreate;

/** Creates the OpenImage page used for YunHu video messages. */
public final class MurexideVideoFragmentCreate implements VideoFragmentCreate {
    @Override
    public BaseImageFragment<? extends View> createVideoFragment() {
        return new MurexideVideoPlayerFragment();
    }
}
