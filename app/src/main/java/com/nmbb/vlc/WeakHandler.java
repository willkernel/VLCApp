package com.nmbb.vlc;

import android.os.Handler;

import java.lang.ref.WeakReference;

/**
 * Created by willkernel on 2016/7/23.
 * Email:willkernejc@gmail.com
 */
public abstract class WeakHandler<T> extends Handler {
    private WeakReference<T> mOwner;

    public WeakHandler(T owner) {
        mOwner = new WeakReference<T>(owner);
    }

    public T getOwner() {
        return mOwner.get();
    }
}