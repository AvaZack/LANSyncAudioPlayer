/*
 * Copyright 2013 Google Inc. All rights reserved.
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

package com.ava.zack.lansyncaudioplayer.playback;

import android.media.MediaExtractor;
import android.util.Log;

/**
 * Movie player callback.
 * <p>
 * The goal here is to play back frames at the original rate.  This is done by introducing
 * a pause before the frame is submitted to the renderer.
 * <p>
 * This is not coordinated with VSYNC.  Since we can't control the display's refresh rate, and
 * the source material has time stamps that specify when each frame should be presented,
 * we will have to drop or repeat frames occasionally.
 * <p>
 * Thread restrictions are noted in the method descriptions.  The FrameCallback overrides should
 * only be called from the MoviePlayer.
 */
public class SpeedControlCallback implements MoviePlayer.FrameCallback {
    private static final String TAG = "SpeedControlCallback";
    private static final boolean CHECK_SLEEP_TIME = false;
    private static final boolean VERBOSE = false;

    private static final long ONE_MILLION = 1000000L;
    //2000,1,1
//    private static final long BENCHMARK_TIMES = Long.valueOf("949334400000000");

    private long mPrevPresentUsec;
    private long mPrevMonoUsec;
    private long mMaxFrameDelta;
    private long mFixedFrameDurationUsec;
    private boolean mLoopReset = false;
    private long mRegionDurationUsec;
    private long mItemsPresentationUsec;

    /**
     * Sets a fixed playback rate.  If set, this will ignore the presentation time stamp
     * in the video file.  Must be called before playback thread starts.
     */
    public void setFixedPlaybackRate(int fps) {
        mFixedFrameDurationUsec = ONE_MILLION / fps;
    }

    // runs on decode thread
    /**
     */
    @Override
    public boolean preRender(long presentationTimeUsec, long durationUsec, MediaExtractor extractor) {
        // For the first frame, we grab the presentation time from the video
        // and the current monotonic clock time.  For subsequent frames, we
        // sleep for a bit to try to ensure that we're rendering frames at the
        // pace dictated by the video stream.
        //
        // If the frame rate is faster than v-sync we should be dropping frames.  On
        // Android 4.4 this may not be happening.

        if (VERBOSE)
            Log.d(TAG, "preRender. mPrevMonoUsec= " + mPrevMonoUsec + ", presentationTimeUsec= " + presentationTimeUsec);
        if (mPrevMonoUsec == 0) {
            // Latch current values, then return immediately.
//            mPrevMonoUsec = System.nanoTime() / 1000;
            mPrevMonoUsec = System.currentTimeMillis() * 1000;
            mPrevPresentUsec = presentationTimeUsec;

        } else {

            // Compute the desired time delta between the previous frame and this frame.
//            long desiredFrameDelta;
//            if (mLoopReset) {
//                // We don't get an indication of how long the last frame should appear
//                // on-screen, so we just throw a reasonable value in.  We could probably
//                // do better by using a previous frame duration or some sort of average;
//                // for now we just use 30fps.
//                Log.d(TAG, "Execute loopReset. Thread=" + Thread.currentThread().getName());
//                mPrevPresentUsec = presentationTimeUsec - ONE_MILLION / 30;
//                mLoopReset = false;
//            }
            if(mMaxFrameDelta < presentationTimeUsec - mPrevPresentUsec)
                mMaxFrameDelta = presentationTimeUsec - mPrevPresentUsec;

            if(VERBOSE)
                Log.d(TAG, "presentationTimeUsec=" + presentationTimeUsec
                        + ", mPrevPresentUsec=" + mPrevPresentUsec);

//            long offsetUsec = (System.currentTimeMillis() * 1000 - BENCHMARK_TIMES) % durationUsec;
//            if(VERBOSE)
//                Log.d(TAG, " seekOffset:" + offsetUsec
//                        + " preRender" );

            long relativeOffsetUsec = getRelativeOffsetUsec(durationUsec);
            if (VERBOSE)
                Log.d(TAG, "relativeOffsetUsec= " + relativeOffsetUsec + ", durationUsec= " + durationUsec
                + ", mRegionDurationUsec= " + mRegionDurationUsec + ", mItemsPresentationUsec= " + mItemsPresentationUsec);

            if (relativeOffsetUsec < 0 || relativeOffsetUsec > durationUsec){
                Log.d(TAG, "should not play this video item this moment. durationUsec= "  +durationUsec
                + ", relativeOffsetUsec= " + relativeOffsetUsec);
                return false;
            }

            long sleepTimeUsec = presentationTimeUsec - relativeOffsetUsec;
            long chaseTimeUsec = -sleepTimeUsec;
//            long desiredPresentUsec = mPrevMonoUsec + presentationTimeUsec - offsetUsec;
//
//            long nowUsec = System.currentTimeMillis() * 1000;

//            if(VERBOSE)
//                Log.d(TAG, "desiredPresentUsec : " + desiredPresentUsec + ",  nowUs : " + nowUsec );

            //TODO  Fix bug : Show last few frames after seeking to the start.
            if(VERBOSE)
                Log.d(TAG, "presentationTimeUsec =" + presentationTimeUsec + " sleepTimeUsec =" + sleepTimeUsec +
                        ", chaseTimeUsec= " + chaseTimeUsec +
                        ", mPrevPresentUsec =" + mPrevPresentUsec +
                        " , durationUsec=" + durationUsec);

            // presentationTimeUsec > mPrevPresentUsec means offset is too close to the end.
            // queueInputBuffer() is 3 frames further than dequeueOutputBuffer().So we do not seek at the last 4 frames.

            if(chaseTimeUsec > 10000000 && durationUsec - relativeOffsetUsec > 4 * mMaxFrameDelta){
//                if(VERBOSE)
                    Log.w(TAG, "It may play the video fast for a long time. Do a seeking.");
                //fast play will last for more than 10sec, then seek to destination
//                extractor.seekTo((System.currentTimeMillis() * 1000 - BENCHMARK_TIMES) % durationUsec, MediaExtractor.SEEK_TO_NEXT_SYNC);
                extractor.seekTo(getRelativeOffsetUsec(durationUsec), MediaExtractor.SEEK_TO_NEXT_SYNC);
            }

            if (sleepTimeUsec > 100   /*&& mState == RUNNING*/) {
                // Sleep until it's time to wake up.  To be responsive to "stop" commands
                // we're going to wake up every half a second even if the sleep is supposed
                // to be longer (which should be rare).  The alternative would be
                // to interrupt the thread, but that requires more work.
                //
                // The precision of the sleep call varies widely from one device to another;
                // we may wake early or late.  Different devices will have a minimum possible
                // sleep time. If we're within 100us of the target time, we'll probably
                // overshoot if we try to sleep, so just go ahead and continue on.
//                long sleepTimeUsec = desiredPresentUsec - nowUsec ;
//                if (sleepTimeUsec > 500000) {
//                    sleepTimeUsec = 500000;
                if(VERBOSE)
                    Log.d(TAG, "sleepTime: " + sleepTimeUsec);

                //time goes backward or be reset to somewhere distant now
                if(sleepTimeUsec >= 2000000) {
                        Log.w(TAG, "Time goes backward or be reset to somewhere distant now.. Do a seeking.");
                    extractor.seekTo(getRelativeOffsetUsec(durationUsec), MediaExtractor.SEEK_TO_NEXT_SYNC);
                } else {
                    try {
                        // Thread.sleep(sleepTimeUsec / 1000);
                        Thread.sleep(sleepTimeUsec / 1000, (int) (sleepTimeUsec % 1000 * 1000));

                    } catch (InterruptedException ie) {}
                }
//                relativeOffsetUsec = getRelativeOffsetUsec(durationUsec);
//                sleepTimeUsec = presentationTimeUsec - relativeOffsetUsec;
//                if (VERBOSE)
//                    Log.d(TAG, "relativeOffsetUsec= " + relativeOffsetUsec + ", presentationTimeUsec= " + presentationTimeUsec
//                            + ", sleepTimeUsec= " + sleepTimeUsec);
//                offsetUsec = (System.currentTimeMillis() * 1000 - BENCHMARK_TIMES) % durationUsec;
//                sleepTimeUsec = presentationTimeUsec - offsetUsec;
//                nowUsec = System.nanoTime() / 1000;
//                nowUsec = System.currentTimeMillis() * 1000;
            }

            // Advance times using calculated time values, not the post-sleep monotonic
            // clock time, to avoid drifting.
            mPrevMonoUsec = System.currentTimeMillis() * 1000 ;
            mPrevPresentUsec = presentationTimeUsec;
        }

        if (VERBOSE)
            Log.d(TAG, "preRender end.");

        return true;
    }

    private long getRelativeOffsetUsec(long durationUsec) {
        if (VERBOSE)
            Log.d(TAG, "getRelativeOffsetUsec. mRegionDurationUsec= " + mRegionDurationUsec
                    +", mItemsPresentationUsec= " + mItemsPresentationUsec
                    + ", durationUsec= " + durationUsec
            + ", absolute offset= " + (System.currentTimeMillis() * 1000) % mRegionDurationUsec);

        return ((System.currentTimeMillis() * 1000) % mRegionDurationUsec) - (mItemsPresentationUsec - durationUsec);
    }

    // runs on decode thread
    @Override
    public void postRender() {
        if(VERBOSE)
            Log.d(TAG, "postRender..");
    }

    @Override
    public void loopReset() {

        Log.d(TAG, "loopReset. Thread=" + Thread.currentThread().getName());
        mLoopReset = true;
    }

    public void setSyncRegionDurationUsec(long regionDurationUsec) {
        mRegionDurationUsec = regionDurationUsec;
    }

    public void setSyncItemsPresentationTimeUsec(long syncItemsPresentationUsec) {
        mItemsPresentationUsec = syncItemsPresentationUsec;
    }
}
