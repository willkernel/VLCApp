package com.nmbb.vlc.ui;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.nmbb.vlc.R;
import com.nmbb.vlc.WeakHandler;

import org.videolan.libvlc.EventHandler;
import org.videolan.libvlc.IVideoPlayer;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.LibVlcException;
import org.videolan.libvlc.Media;
import org.videolan.vlc.util.VLCInstance;

import java.io.File;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Date;
import java.util.Locale;

@SuppressWarnings("unused")
public class VlcVideoActivity extends Activity implements SurfaceHolder.Callback, IVideoPlayer {

    private final static String TAG = "[VlcVideoActivity]";
    private SurfaceView mSurfaceView;
    private LibVLC mMediaPlayer;
    private SurfaceHolder mSurfaceHolder;
    private AudioManager mAudioManager;
    /**
     * Overlay
     */
    private View mOverlayHeader;
    private View mOverlayOption;
    private View mOverlayProgress;
    private static final int OVERLAY_TIMEOUT = 4000;
    private static final int OVERLAY_INFINITE = 3600000;
    private static final int FADE_OUT = 0;
    private static final int SHOW_PROGRESS = 1;
    private static final int FADE_OUT_INFO = 2;
    private static final int HANDLER_BUFFER_START = 3;
    private static final int HANDLER_BUFFER_END = 4;
    private static final int HANDLER_SURFACE_SIZE = 5;
    private static final int SURFACE_BEST_FIT = 0;
    private static final int SURFACE_FIT_HORIZONTAL = 1;
    private static final int SURFACE_FIT_VERTICAL = 2;
    private static final int SURFACE_FILL = 3;
    private static final int SURFACE_16_9 = 4;
    private static final int SURFACE_4_3 = 5;
    private static final int SURFACE_ORIGINAL = 6;
    private int mCurrentSize = SURFACE_BEST_FIT;

    private boolean mDragging;
    private boolean mShowing;
    private SeekBar mSeekBar;
    private VerticalSeekBar audio_overlay_SeekBar;
    private ImageView audio_sound_icon;
    private TextView audio_value_text, mTitle, mSysTime, mTime, mLength, mInfo;
    private ImageButton mPlayPause;
    private ImageButton mBackward;
    private ImageButton mForward;
    private boolean mDisplayRemainingTime, playPaused, mIsFirstBrightnessGesture = true;

    private int mVideoHeight;
    private int mVideoWidth;
    private int mVideoVisibleHeight;
    private int mVideoVisibleWidth;
    private int mSarNum;
    private int mSarDen;
    private int maxVolume, currentVolume;
    // Touch Events
    private static final int TOUCH_NONE = 0;
    private static final int TOUCH_VOLUME = 1;
    private static final int TOUCH_BRIGHTNESS = 2;
    private static final int TOUCH_SEEK = 3;
    private int mTouchAction;
    private int mSurfaceYDisplayRange;
    private float mTouchY, mTouchX, mVol;
    private String mrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_vlc);
        setController();
        setVideoView();
    }

    private void setController() {
        /** initialize Views an their Events */
        mOverlayHeader = findViewById(R.id.player_overlay_header);
        mOverlayOption = findViewById(R.id.option_overlay);
        mOverlayProgress = findViewById(R.id.progress_overlay);
        /* header */
        mTitle = (TextView) findViewById(R.id.player_overlay_title);
        mSysTime = (TextView) findViewById(R.id.player_overlay_systime);

        // Position and remaining time
        mTime = (TextView) findViewById(R.id.player_overlay_time);
        mTime.setOnClickListener(mRemainingTimeListener);
        mLength = (TextView) findViewById(R.id.player_overlay_length);
        mLength.setOnClickListener(mRemainingTimeListener);

        // the info textView is not on the overlay
        mInfo = (TextView) findViewById(R.id.player_overlay_info);

        mPlayPause = (ImageButton) findViewById(R.id.player_overlay_play);
        mPlayPause.setOnClickListener(mPlayPauseListener);
        mBackward = (ImageButton) findViewById(R.id.player_overlay_backward);
        mBackward.setOnClickListener(mBackwardListener);
        mForward = (ImageButton) findViewById(R.id.player_overlay_forward);
        mForward.setOnClickListener(mForwardListener);

        audio_overlay_SeekBar = (VerticalSeekBar) findViewById(R.id.audio_overlay_seekbar);
        audio_overlay_SeekBar.setOnSeekBarChangeListener(mAudioSeekListener);
        audio_value_text = (TextView) findViewById(R.id.audio_value_text);
        audio_sound_icon = (ImageView) findViewById(R.id.audio_sound_icon);
        ImageButton mSize = (ImageButton) findViewById(R.id.player_overlay_size);
        mSize.setOnClickListener(mSizeListener);
        mSeekBar = (SeekBar) findViewById(R.id.player_overlay_seekbar);
        mSeekBar.setOnSeekBarChangeListener(mSeekListener);

        mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        maxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC); // 获取系统最大音量
        audio_overlay_SeekBar.setMax(maxVolume);
        currentVolume = maxVolume * 2 / 3;
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume, 0);
        getAudioValue();
        this.setVolumeControlStream(AudioManager.STREAM_MUSIC);
    }

    private void setVideoView() {
        mSurfaceView = (SurfaceView) findViewById(R.id.video);
        try {
            mMediaPlayer = VLCInstance.getLibVlcInstance();
        } catch (LibVlcException e) {
            e.printStackTrace();
        }

        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.setFormat(PixelFormat.RGBX_8888);
//		mSurfaceHolder.setFormat(PixelFormat.RGBA_8888);
        mSurfaceHolder.addCallback(this);

        mMediaPlayer.eventVideoPlayerActivityCreated(true);

        EventHandler em = EventHandler.getInstance();
        em.addHandler(mVlcHandler);

        mSurfaceView.setKeepScreenOn(true);
        //		mMediaPlayer.setMediaList();
        //		mMediaPlayer.getMediaList().add(new Media(mMediaPlayer, "http://live.3gv.ifeng.com/zixun.m3u8"), false);
        //		mMediaPlayer.playIndex(0);
//		mMediaPlayer.playMRL("http://live.3gv.ifeng.com/zixun.m3u8");
        File video = new File(Environment.getExternalStorageDirectory(), "willkernel/Video");
        if (!video.exists()) //noinspection ResultOfMethodCallIgnored
            video.mkdirs();
        File[] files = video.listFiles();
        mrl = "file://" + files[0].getAbsolutePath();
        mMediaPlayer.playMRL(mrl);
        mTitle.setText(files[0].getName());
    }

    private void getAudioValue() {
        currentVolume = mAudioManager
                .getStreamVolume(AudioManager.STREAM_MUSIC); // 获取当前值
        audio_overlay_SeekBar.setProgress(currentVolume);
        audio_value_text.setText(String.format(Locale.CHINA, "%d %%", currentVolume * 100 / maxVolume));
        showInfo(getString(R.string.volume) + '\u00A0' + currentVolume * 100
                / maxVolume + " %", 1000);
        updateAudioIcon();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (playPaused) {
            play();
        }
    }

    private final View.OnClickListener mPlayPauseListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mMediaPlayer.isPlaying())
                pause();
            else
                play();
            showOverlay();
        }
    };

    private void play() {
        mMediaPlayer.play();
        playPaused = false;
        mSurfaceView.setKeepScreenOn(true);
    }

    private void pause() {
        mMediaPlayer.pause();
        playPaused = true;
        mSurfaceView.setKeepScreenOn(false);
    }

    /**
     * handle changes of the seekbar (slicer)
     */
    private final SeekBar.OnSeekBarChangeListener mAudioSeekListener = new SeekBar.OnSeekBarChangeListener() {

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            mDragging = true;
            showOverlay(OVERLAY_INFINITE);
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            mDragging = false;
            showOverlay();
            hideInfo();
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress,
                                      boolean fromUser) {
            mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress,
                    0);
            getAudioValue();
        }
    };

    /**
     * hide the info view with "delay" milliseconds delay
     */
    private void hideInfo() {
        mHandler.sendEmptyMessageDelayed(FADE_OUT_INFO, 0);
    }

    private final View.OnClickListener mBackwardListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            preAd();
//            seek(-10000);
        }
    };

    private void preAd() {

    }

    private final View.OnClickListener mForwardListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            nextAd();
//            seek(10000);
        }
    };

    private void nextAd() {

    }

    public void seek(int delta) {
        // unseekable stream
        if (mMediaPlayer.getLength() <= 0)
            return;

        long position = mMediaPlayer.getTime() + delta;
        if (position < 0)
            position = 0;
        mMediaPlayer.setTime(position);
        showOverlay();
    }

    /**
     * handle changes of the seekbar (slicer)
     */
    private final SeekBar.OnSeekBarChangeListener mSeekListener = new SeekBar.OnSeekBarChangeListener() {

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            mDragging = true;
            showOverlay(OVERLAY_INFINITE);
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            mDragging = false;
            showOverlay();
            hideInfo();
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress,
                                      boolean fromUser) {
            if (fromUser) {
                mMediaPlayer.setTime(progress);
                setOverlayProgress();
                mTime.setText(millisToString(progress));
                showInfo(millisToString(progress));
            }
        }
    };

    private final View.OnClickListener mSizeListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mCurrentSize < SURFACE_ORIGINAL) {
                mCurrentSize++;
            } else {
                mCurrentSize = 0;
            }
            changeSurfaceSize();
            switch (mCurrentSize) {
                case SURFACE_BEST_FIT:
                    showInfo(R.string.surface_best_fit, 1000);
                    break;
                case SURFACE_FIT_HORIZONTAL:
                    showInfo(R.string.surface_fit_horizontal, 1000);
                    break;
                case SURFACE_FIT_VERTICAL:
                    showInfo(R.string.surface_fit_vertical, 1000);
                    break;
                case SURFACE_FILL:
                    showInfo(R.string.surface_fill, 1000);
                    break;
                case SURFACE_16_9:
                    showInfo("16:9");
                    break;
                case SURFACE_4_3:
                    showInfo("4:3");
                    break;
                case SURFACE_ORIGINAL:
                    showInfo(R.string.surface_original, 1000);
                    break;
            }
            showOverlay();
        }
    };

    private final View.OnClickListener mRemainingTimeListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            mDisplayRemainingTime = !mDisplayRemainingTime;
            showOverlay();
        }
    };

    /**
     * show overlay the the default timeout
     */
    private void showOverlay() {
        showOverlay(OVERLAY_TIMEOUT);
    }

    /**
     * show overlay
     */
    private void showOverlay(int timeout) {
        mHandler.sendEmptyMessage(SHOW_PROGRESS);
        if (!mShowing) {
            mShowing = true;
            mOverlayHeader.setVisibility(View.VISIBLE);
            mOverlayProgress.setVisibility(View.VISIBLE);
            mOverlayOption.setVisibility(View.VISIBLE);
            mPlayPause.setVisibility(View.VISIBLE);
        }
        Message msg = mHandler.obtainMessage(FADE_OUT);
        if (timeout != 0) {
            mHandler.removeMessages(FADE_OUT);
            mHandler.sendMessageDelayed(msg, timeout);
        }
        updateOverlayPausePlay();
    }

    /**
     * hider overlay
     */
    private void hideOverlay(boolean fromUser) {
        if (mShowing) {
            mHandler.removeMessages(SHOW_PROGRESS);
            Log.e(TAG, "remove View!");
            if (!fromUser) {
                mOverlayHeader.startAnimation(AnimationUtils.loadAnimation(
                        this, android.R.anim.fade_out));
                mOverlayOption.startAnimation(AnimationUtils.loadAnimation(
                        this, android.R.anim.fade_out));
                mOverlayProgress.startAnimation(AnimationUtils.loadAnimation(
                        this, android.R.anim.fade_out));
                mPlayPause.startAnimation(AnimationUtils.loadAnimation(this,
                        android.R.anim.fade_out));
            }
            mOverlayHeader.setVisibility(View.INVISIBLE);
            mOverlayOption.setVisibility(View.INVISIBLE);
            mOverlayProgress.setVisibility(View.INVISIBLE);
            mPlayPause.setVisibility(View.INVISIBLE);
            mShowing = false;
        }
    }

    private void updateOverlayPausePlay() {
        if (mMediaPlayer == null) {
            return;
        }
        mPlayPause
                .setBackgroundResource(mMediaPlayer.isPlaying() ? R.drawable.ic_pause
                        : R.drawable.ic_play);
    }

    @SuppressWarnings("deprecation")
    private void updateAudioIcon() {
        audio_overlay_SeekBar.setProgress(currentVolume);
        if (currentVolume == 0) {
            audio_sound_icon.setBackground(this.getResources().getDrawable(
                    R.drawable.sound_zero));
        }
        if (currentVolume >= 1 && currentVolume <= 6) {
            audio_sound_icon.setBackground(this.getResources().getDrawable(
                    R.drawable.sound_one));
        }
        if (currentVolume > 6 && currentVolume < 10) {
            audio_sound_icon.setBackground(this.getResources().getDrawable(
                    R.drawable.sound_two));
        }
        if (currentVolume > 10) {
            audio_sound_icon.setBackground(this.getResources().getDrawable(
                    R.drawable.sound_three));
        }
        audio_value_text.setText(String.format(Locale.CHINA, "%d %%", currentVolume * 100 / maxVolume));
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            // 音量减小
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        getAudioValue();
                    }
                }, 100);
                return false;

            // 音量增大
            case KeyEvent.KEYCODE_VOLUME_UP:
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        getAudioValue();
                    }
                }, 100);
                return false;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            playPaused = true;
            mSurfaceView.setKeepScreenOn(false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mMediaPlayer != null) {
            mMediaPlayer.eventVideoPlayerActivityCreated(false);
            EventHandler em = EventHandler.getInstance();
            em.removeHandler(mVlcHandler);
            mAudioManager = null;
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        setSurfaceSize(mVideoWidth, mVideoHeight, mVideoVisibleWidth, mVideoVisibleHeight, mSarNum, mSarDen);
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (mMediaPlayer != null) {
            mSurfaceHolder = holder;
            mMediaPlayer.attachSurface(holder.getSurface(), this);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        mSurfaceHolder = holder;
        if (mMediaPlayer != null) {
            mMediaPlayer.attachSurface(holder.getSurface(), this);//, width, height
        }
        if (width > 0) {
            mVideoHeight = height;
            mVideoWidth = width;
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (mMediaPlayer != null) {
            mMediaPlayer.detachSurface();
        }
    }

    @Override
    public void setSurfaceSize(int width, int height, int visible_width, int visible_height, int sar_num, int sar_den) {
        mVideoHeight = height;
        mVideoWidth = width;
        mVideoVisibleHeight = visible_height;
        mVideoVisibleWidth = visible_width;
        mSarNum = sar_num;
        mSarDen = sar_den;
        mHandler.removeMessages(HANDLER_SURFACE_SIZE);
        mHandler.sendEmptyMessage(HANDLER_SURFACE_SIZE);
    }

    private Handler mVlcHandler = new VLCHandler(this);

    private static class VLCHandler extends WeakHandler<VlcVideoActivity> {
        public VLCHandler(VlcVideoActivity owner) {
            super(owner);
        }

        @Override
        public void handleMessage(Message msg) {
            VlcVideoActivity activity = getOwner();
            if (activity == null)
                return;
            if (msg == null || msg.getData() == null)
                return;

            switch (msg.getData().getInt("event")) {
                case EventHandler.MediaPlayerTimeChanged:
                    break;
                case EventHandler.MediaPlayerPositionChanged:
                    break;
                case EventHandler.MediaPlayerPlaying:
                    activity.playPaused = false;
                    activity.showOverlay();
                    Log.e(TAG, "playing");
                    break;
                case EventHandler.MediaPlayerPaused:
                    activity.playPaused = true;
                    Log.e(TAG, "pause");
                    break;
                case EventHandler.MediaPlayerBuffering:
                    break;
                case EventHandler.MediaPlayerLengthChanged:
                    break;
                case EventHandler.MediaPlayerEndReached:
                    //播放完成
                    break;
            }
            activity.updateOverlayPausePlay();
        }
    }

    private Handler mHandler = new MHandler(this);

    private static class MHandler extends WeakHandler<VlcVideoActivity> {
        public MHandler(VlcVideoActivity owner) {
            super(owner);
        }

        @Override
        public void handleMessage(Message msg) {
            VlcVideoActivity activity = getOwner();
            if (activity == null)
                return;
            switch (msg.what) {
                case FADE_OUT:
                    activity.hideOverlay(false);
                    break;
                case SHOW_PROGRESS:
                    int pos = activity.setOverlayProgress();
                    if (activity.canShowProgress()) {
                        msg = obtainMessage(SHOW_PROGRESS);
                        sendMessageDelayed(msg, 1000 - (pos % 1000));
                    }
                    break;
                case FADE_OUT_INFO:
                    activity.fadeOutInfo();
                    break;
                case HANDLER_BUFFER_START:
                    break;
                case HANDLER_BUFFER_END:
                    break;
                case HANDLER_SURFACE_SIZE:
                    activity.changeSurfaceSize();
                    break;
            }
        }
    }

    /**
     * show/hide the overlay
     */

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        DisplayMetrics screen = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(screen);
        if (mSurfaceYDisplayRange == 0)
            mSurfaceYDisplayRange = Math.min(screen.widthPixels,
                    screen.heightPixels);
        float y_changed = event.getRawY() - mTouchY;
        float x_changed = event.getRawX() - mTouchX;

        // coef is the gradient's move to determine a neutral zone
        float coef = Math.abs(y_changed / x_changed);
        float xgesturesize = ((x_changed / screen.xdpi) * 2.54f);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // Audio
                mTouchY = event.getRawY();
                mVol = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                mTouchAction = TOUCH_NONE;
                // Seek
                mTouchX = event.getRawX();
                Log.i(TAG, "mTouchX:" + mTouchX);
                Log.i(TAG, "mVol:" + mVol);
                break;
            case MotionEvent.ACTION_MOVE:
                // No volume/brightness action if coef < 2
                Log.i(TAG, "coef:" + coef);
                Log.i(TAG, "mTouchX:" + mTouchX);
                Log.i(TAG, "mEnableBrightnessGesture:" + screen.widthPixels / 2);
                if (coef > 2) {
                    // Volume (Up or Down - Right side)
                    if (mTouchX > (screen.widthPixels / 2)) {
                        doVolumeTouch(y_changed);
                    }
                    // Brightness (Up or Down - Left side)
                    if (mTouchX < (screen.widthPixels / 2)) {
                        doBrightnessTouch(y_changed);
                    }
                    // Extend the overlay for a little while, so that it doesn't
                    // disappear on the user if more adjustment is needed. This
                    // is because on devices with soft navigation (e.g. Galaxy
                    // Nexus), gestures can't be made without activating the UI.
                    showOverlay();
                }
                // Seek (Right or Left move)
                doSeekTouch(coef, xgesturesize, false);
                break;
            case MotionEvent.ACTION_UP:
                // Audio or Brightness
                if (mTouchAction == TOUCH_NONE) {
                    if (!mShowing) {
                        showOverlay();
                    } else {
                        hideOverlay(true);
                    }
                }
                // Seek
                doSeekTouch(coef, xgesturesize, true);
                break;
        }
        return mTouchAction != TOUCH_NONE;
    }

    private void initBrightnessTouch() {
        float brightnessTemp = 0.01f;
        // Initialize the layoutParams screen brightness
        try {
            brightnessTemp = android.provider.Settings.System.getInt(
                    getContentResolver(),
                    android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255.0f;
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = brightnessTemp;
        getWindow().setAttributes(lp);
        mIsFirstBrightnessGesture = false;
    }

    private void doBrightnessTouch(float y_changed) {
        if (mTouchAction != TOUCH_NONE && mTouchAction != TOUCH_BRIGHTNESS)
            return;
        if (mIsFirstBrightnessGesture)
            initBrightnessTouch();
        mTouchAction = TOUCH_BRIGHTNESS;

        // Set delta : 0.07f is arbitrary for now, it possibly will change in
        // the future
        float delta = -y_changed / mSurfaceYDisplayRange * 0.07f;

        // Estimate and adjust Brightness
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = Math.min(
                Math.max(lp.screenBrightness + delta, 0.01f), 1);

        // Set Brightness
        getWindow().setAttributes(lp);
        showInfo(
                getString(R.string.brightness) + '\u00A0'
                        + Math.round(lp.screenBrightness * 15), 1000);
    }

    private void doSeekTouch(float coef, float gesturesize, boolean seek) {
        // No seek action if coef > 0.5 and gesturesize < 1cm
        if (coef > 0.5 || Math.abs(gesturesize) < 1)
            return;

        if (mTouchAction != TOUCH_NONE && mTouchAction != TOUCH_SEEK)
            return;
        mTouchAction = TOUCH_SEEK;

        // Always show seekbar when searching
        if (!mShowing)
            showOverlay();

        long length = mMediaPlayer.getLength();
        long time = mMediaPlayer.getTime();

        // Size of the jump, 10 minutes max (600000), with a bi-cubic
        // progression, for a 8cm gesture
        int jump = (int) (Math.signum(gesturesize) * ((600000 * Math.pow(
                (gesturesize / 8), 4)) + 3000));

        // Adjust the jump
        if ((jump > 0) && ((time + jump) > length))
            jump = (int) (length - time);
        if ((jump < 0) && ((time + jump) < 0))
            jump = (int) -time;

        // Jump !
        if (seek && length > 0)
            mMediaPlayer.setTime(time + jump);

        if (length > 0)
            // Show the jump's size
            showInfo(
                    String.format("%s%s (%s)", jump >= 0 ? "+" : "",
                            millisToString(jump),
                            millisToString(time + jump)), 1000);
    }

    private void doVolumeTouch(float y_changed) {
        Log.i(TAG, "doVolumeTouch");
        if (mTouchAction != TOUCH_NONE && mTouchAction != TOUCH_VOLUME)
            return;
        int delta = -(int) ((y_changed / mSurfaceYDisplayRange) * maxVolume);
        int vol = (int) Math.min(Math.max(mVol + delta, 0), maxVolume);
        Log.i(TAG, "delta=" + delta);
        Log.i(TAG, "vol=" + vol);
        if (delta != 0) {
            mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0);
            getAudioValue();
        }
    }

    /**
     * update the overlay
     */
    private int setOverlayProgress() {
        if (mMediaPlayer == null) {
            return 0;
        }
        int time = (int) mMediaPlayer.getTime();
        int length = (int) mMediaPlayer.getLength();
        if (length == 0) {
            Media media = new Media(mMediaPlayer, mrl);
            //noinspection ConstantConditions
            if (media != null)
                length = (int) media.getLength();
        }

        mBackward.setVisibility(View.VISIBLE);
        mForward.setVisibility(View.VISIBLE);
        mSeekBar.setMax(length);
        mSeekBar.setProgress(time);
        mSysTime.setText(DateFormat.getTimeFormat(this).format(
                new Date(System.currentTimeMillis())));
        if (time >= 0)
            mTime.setText(millisToString(time));
        if (length >= 0)
            mLength.setText(mDisplayRemainingTime && length > 0 ? "- "
                    + millisToString(length - time) : millisToString(length));
        return time;
    }

    private void fadeOutInfo() {
        if (mInfo.getVisibility() == View.VISIBLE)
            mInfo.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_out));
        mInfo.setVisibility(View.INVISIBLE);
    }

    private boolean canShowProgress() {
        return !mDragging && mShowing && mMediaPlayer.isPlaying();
    }

    private void changeSurfaceSize() {
        // get screen size
        int dw = getResources().getDisplayMetrics().widthPixels;
        int dh = getResources().getDisplayMetrics().heightPixels;

        // calculate aspect ratio
        double ar = (double) mVideoWidth / (double) mVideoHeight;
        // calculate display aspect ratio
        double dar = (double) dw / (double) dh;

        switch (mCurrentSize) {
            case SURFACE_BEST_FIT:
                if (dar < ar)
                    dh = (int) (dw / ar);
                else
                    dw = (int) (dh * ar);
                break;
            case SURFACE_FIT_HORIZONTAL:
                dh = (int) (dw / ar);
                break;
            case SURFACE_FIT_VERTICAL:
                dw = (int) (dh * ar);
                break;
            case SURFACE_FILL:
                break;
            case SURFACE_16_9:
                ar = 16.0 / 9.0;
                if (dar < ar)
                    dh = (int) (dw / ar);
                else
                    dw = (int) (dh * ar);
                break;
            case SURFACE_4_3:
                ar = 4.0 / 3.0;
                if (dar < ar)
                    dh = (int) (dw / ar);
                else
                    dw = (int) (dh * ar);
                break;
            case SURFACE_ORIGINAL:
                dh = mVideoHeight;
                dw = mVideoWidth;
                break;
        }

        mSurfaceHolder.setFixedSize(mVideoWidth, mVideoHeight);
        ViewGroup.LayoutParams lp = mSurfaceView.getLayoutParams();
        lp.width = dw;
        lp.height = dh;
        mSurfaceView.setLayoutParams(lp);
        mSurfaceView.invalidate();
    }

    /**
     * Show text in the info view for "duration" milliseconds
     */
    private void showInfo(String text, int duration) {
        mInfo.setVisibility(View.VISIBLE);
        mInfo.setText(text);
        mHandler.removeMessages(FADE_OUT_INFO);
        mHandler.sendEmptyMessageDelayed(FADE_OUT_INFO, duration);
    }

    private void showInfo(int textid, int duration) {
        mInfo.setVisibility(View.VISIBLE);
        mInfo.setText(textid);
        mHandler.removeMessages(FADE_OUT_INFO);
        mHandler.sendEmptyMessageDelayed(FADE_OUT_INFO, duration);
    }

    /**
     * Show text in the info view
     */
    private void showInfo(String text) {
        mInfo.setVisibility(View.VISIBLE);
        mInfo.setText(text);
        mHandler.removeMessages(FADE_OUT_INFO);
    }

    private static String millisToString(long millis) {
        boolean negative = millis < 0;
        millis = Math.abs(millis);
        millis /= 1000;
        int sec = (int) (millis % 60);
        millis /= 60;
        int min = (int) (millis % 60);
        millis /= 60;
        int hours = (int) millis;
        String time;
        DecimalFormat format = (DecimalFormat) NumberFormat.getInstance(Locale.US);
        format.applyPattern("00");
        if (millis > 0)
            time = (negative ? "-" : "") + hours + ":" + format.format(min) + ":" + format.format(sec);
        else
            time = (negative ? "-" : "") + min + ":" + format.format(sec);
        return time;
    }
}
