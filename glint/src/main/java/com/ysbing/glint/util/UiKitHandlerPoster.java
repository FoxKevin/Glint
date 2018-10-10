package com.ysbing.glint.util;
/*
 * Copyright (C) 2014 Qiujuer <qiujuer@live.cn>
 * WebSite http://www.qiujuer.net
 * Created 11/24/2014
 * Changed 1/07/2016
 * Version 3.0.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;

import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Queue;

/**
 * UiKit Handler Poster extends Handler
 * <p/>
 * In class have two queue with {@link #mAsyncPool,#mSyncPool}
 */
final class UiKitHandlerPoster extends Handler {
    private static final int ASYNC = 0x1;
    private static final int SYNC = 0x2;
    private final Queue<Runnable> mAsyncPool;
    private final Queue<UiKitSyncPost> mSyncPool;
    private final int mMaxMillisInsideHandleMessage;
    private boolean isAsyncActive;
    private boolean isSyncActive;

    /**
     * Init this
     *  @param looper                       Handler Looper
     *
     */
    UiKitHandlerPoster(Looper looper) {
        super(looper);
        this.mMaxMillisInsideHandleMessage = 16;
        mAsyncPool = new LinkedList<>();
        mSyncPool = new LinkedList<>();
    }

    /**
     * Pool clear
     */
    void dispose() {
        this.removeCallbacksAndMessages(null);
        this.mAsyncPool.clear();
        this.mSyncPool.clear();
    }


    void async(Runnable runnable) {
        synchronized (mAsyncPool) {
            mAsyncPool.offer(runnable);
            if (!isAsyncActive) {
                isAsyncActive = true;
                if (!sendMessage(obtainMessage(ASYNC))) {
                    throw new RuntimeException("Could not send handler message");
                }
            }
        }
    }

    void sync(UiKitSyncPost post) {
        synchronized (mSyncPool) {
            mSyncPool.offer(post);
            if (!isSyncActive) {
                isSyncActive = true;
                if (!sendMessage(obtainMessage(SYNC))) {
                    throw new RuntimeException("Could not send handler message");
                }
            }
        }
    }

    /**
     * Run in main thread
     *
     * @param msg call messages
     */
    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
            case ASYNC: {
                boolean rescheduled = false;
                try {
                    long started = SystemClock.uptimeMillis();
                    while (true) {
                        Runnable runnable = poolAsyncPost();
                        if (runnable == null) {
                            synchronized (mAsyncPool) {
                                // Check again, this time in synchronized
                                runnable = poolAsyncPost();
                                if (runnable == null) {
                                    isAsyncActive = false;
                                    return;
                                }
                            }
                        }
                        runnable.run();
                        long timeInMethod = SystemClock.uptimeMillis() - started;
                        if (timeInMethod >= mMaxMillisInsideHandleMessage) {
                            if (!sendMessage(obtainMessage(ASYNC))) {
                                throw new RuntimeException("Could not send handler message");
                            }
                            rescheduled = true;
                            return;
                        }
                    }
                } finally {
                    isAsyncActive = rescheduled;
                }
            }
            case SYNC: {
                boolean rescheduled = false;
                try {
                    long started = SystemClock.uptimeMillis();
                    while (true) {
                        UiKitSyncPost post = poolSyncPost();
                        if (post == null) {
                            synchronized (mSyncPool) {
                                // Check again, this time in synchronized
                                post = poolSyncPost();
                                if (post == null) {
                                    isSyncActive = false;
                                    return;
                                }
                            }
                        }
                        post.run();
                        long timeInMethod = SystemClock.uptimeMillis() - started;
                        if (timeInMethod >= mMaxMillisInsideHandleMessage) {
                            if (!sendMessage(obtainMessage(SYNC))) {
                                throw new RuntimeException("Could not send handler message");
                            }
                            rescheduled = true;
                            return;
                        }
                    }
                } finally {
                    isSyncActive = rescheduled;
                }
            }
            default:
                super.handleMessage(msg);
                break;
        }
    }

    private Runnable poolAsyncPost() {
        synchronized (mAsyncPool) {
            try {
                return mAsyncPool.poll();
            } catch (NoSuchElementException e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    private UiKitSyncPost poolSyncPost() {
        synchronized (mSyncPool) {
            try {
                return mSyncPool.poll();
            } catch (NoSuchElementException e) {
                e.printStackTrace();
                return null;
            }
        }
    }
}