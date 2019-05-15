package mega.privacy.android.app.components.voiceClip;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.widget.Chronometer;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import java.io.IOException;
import java.util.Locale;

import io.supercharge.shimmerlayout.ShimmerLayout;
import mega.privacy.android.app.R;
import mega.privacy.android.app.utils.Util;

public class RecordView extends RelativeLayout {

    public enum UserBehaviour {
        CANCELING,
        LOCKING,
        NONE
    }

    public static final int DEFAULT_CANCEL_BOUNDS = 1; //8dp
    private ImageView smallBlinkingMic, basketImg;
    private Chronometer counterTime;
    private TextView slideToCancel;
    private ShimmerLayout slideToCancelLayout;
    private RelativeLayout cancelRecordLayout;
    private TextView textCancelRecord;
    private float initialX=0;
    private float basketInitialX = 0;

    private float cancelBounds = DEFAULT_CANCEL_BOUNDS;
    private long startTime, elapsedTime = 0;
    private Context context;
    private OnRecordListener recordListener;
    private boolean isSwiped, isLessThanSecondAllowed = false;
    private boolean isSoundEnabled = true;
    private int RECORD_START = R.raw.record_start;
    private int RECORD_FINISHED = R.raw.record_finished;
    private int RECORD_ERROR = R.raw.record_error;
    private MediaPlayer player;
    private AudioManager audioManager;;
    private AnimationHelper animationHelper;
    private View layoutLock;
    private ImageView imageLock, imageArrow;
    private boolean flagRB = false;
    private boolean isLockpadShown = false;
    private boolean isRecordingNow = false;

    Handler handlerStartRecord = new Handler();
    Handler handlerShowPadLock = new Handler();
    float previewX = 0;
    private Animation animJump, animJumpFast;
    int cont = 0;

    float density;
    DisplayMetrics outMetrics;
    Display display;

    private float lastX, lastY;
    private float firstX, firstY;

    private UserBehaviour userBehaviour = UserBehaviour.NONE;

    public RecordView(Context context) {
        super(context);
        this.context = context;
        init(context, null, -1, -1);
    }

    public RecordView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        init(context, attrs, -1, -1);
    }

    public RecordView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.context = context;
        init(context, attrs, defStyleAttr, -1);
    }

    private void init(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        View view = View.inflate(context, R.layout.record_view_layout, null);
        addView(view);

        ViewGroup viewGroup = (ViewGroup) view.getParent();
        viewGroup.setClipChildren(false);

        display = ((Activity)context).getWindowManager().getDefaultDisplay();
        outMetrics = new DisplayMetrics ();
        display.getMetrics(outMetrics);
        density  = getResources().getDisplayMetrics().density;

        slideToCancel = view.findViewById(R.id.slide_to_cancel);
        slideToCancel.setText(context.getString(R.string.slide_to_cancel).toUpperCase(Locale.getDefault()));
        smallBlinkingMic = view.findViewById(R.id.glowing_mic);
        smallBlinkingMic.setVisibility(GONE);
        counterTime = view.findViewById(R.id.chrono_voice_clip);
        counterTime.setVisibility(GONE);
        basketImg = view.findViewById(R.id.basket_img);
        slideToCancelLayout = view.findViewById(R.id.shimmer_layout);
        slideToCancelLayout.setVisibility(GONE);
        cancelRecordLayout = view.findViewById(R.id.rl_cancel_record);
        cancelRecordLayout.setVisibility(GONE);
        textCancelRecord = view.findViewById(R.id.text_cancel_record);
        textCancelRecord.setText(context.getString(R.string.button_cancel).toUpperCase(Locale.getDefault()));
        layoutLock = view.findViewById(R.id.layout_lock);
        imageLock = view.findViewById(R.id.image_lock);
        imageArrow = view.findViewById(R.id.image_arrow);
        imageLock.setVisibility(GONE);
        imageArrow.setVisibility(GONE);
        animJump = AnimationUtils.loadAnimation(getContext(), R.anim.jump);
        animJumpFast = AnimationUtils.loadAnimation(getContext(), R.anim.jump_fast);
        layoutLock.setVisibility(GONE);
        cancelRecordLayout.setVisibility(GONE);
        cancelRecordLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                log("cancelRecordLayout. onClick()");
                hideViews(false);
            }
        });

        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        animationHelper = new AnimationHelper(context, basketImg, smallBlinkingMic);
    }

    public void setRecordingNow(boolean recordingNow) {
        isRecordingNow = recordingNow;
    }

    private void hideViews(boolean hideSmallMic) {
        slideToCancelLayout.setVisibility(GONE);
        cancelRecordLayout.setVisibility(GONE);
        startStopCounterTime(false);

        if (hideSmallMic){
            smallBlinkingMic.setVisibility(GONE);
            if(animationHelper!=null){
                animationHelper.clearAlphaAnimation(false);
                animationHelper.onAnimationEnd();
                animationHelper.setStartRecorded(false);
            }
        }else{
            if(animationHelper!=null){
                animationHelper.animateBasket(basketInitialX);
                animationHelper.setStartRecorded(false);
                return;
            }
        }
        if (recordListener != null) {
            recordListener.onCancel();
        }
    }


    public void showLock(boolean flag){
        if(flag){
            cont = 0;
            if(layoutLock.getVisibility() == View.GONE){
                layoutLock.setVisibility(View.VISIBLE);
                imageArrow.setVisibility(VISIBLE);
                imageLock.setVisibility(VISIBLE);
                createAnimation(flag,Util.px2dp(175, outMetrics), 500);
            }
        }else{
            flagRB = false;
            cont ++;
            if(cont == 1) {
                if (layoutLock.getVisibility() == View.VISIBLE) {
                    layoutLock.setVisibility(View.GONE);
                    isLockpadShown = false;
                    createAnimation(flag, 10, 250);
                }
            }
        }
    }

    private void createAnimation(final boolean toOpen, int value, int duration){
        log("createAnimation");

        int prevHeight  = layoutLock.getHeight();
        ValueAnimator valueAnimator = ValueAnimator.ofInt(prevHeight, value);
        if(!toOpen){
            valueAnimator.setInterpolator(new DecelerateInterpolator());
        }
        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                layoutLock.getLayoutParams().height = (int) animation.getAnimatedValue();
                layoutLock.requestLayout();
            }
        });

        valueAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if((imageArrow!=null)&& (imageLock!=null)){
                    if(toOpen){
                        //It is expanded
                        isLockpadShown = true;
                        imageArrow.startAnimation(animJumpFast);
                        imageLock.startAnimation(animJump);
                    }else{
                        //It is compressed
                        imageLock.clearAnimation();
                        imageArrow.clearAnimation();
                        imageArrow.setVisibility(GONE);
                        imageLock.setVisibility(GONE);
                        layoutLock.setVisibility(View.GONE);

                    }
                }
            }
        });
        valueAnimator.setInterpolator(new DecelerateInterpolator());
        valueAnimator.setDuration(duration);
        valueAnimator.start();
    }

    private boolean isLessThanOneSecond(long time) {
        return time <= 1500;
    }

    public void playSound(int soundRes) {
        if (isSoundEnabled) {
            if (soundRes == 0)
                return;
            try {
                if(audioManager.getRingerMode()!=AudioManager.RINGER_MODE_SILENT){
                    if (audioManager.getStreamVolume(AudioManager.STREAM_RING) != 0){
                        int volume_level1= audioManager.getStreamVolume(AudioManager.STREAM_RING);
                        if(volume_level1!=0) {
                            log("playSound()");
                            int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                            player = new MediaPlayer();
                            AssetFileDescriptor afd = context.getResources().openRawResourceFd(soundRes);
                            if (afd == null) return;
                            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                            afd.close();
                            player.setAudioStreamType(AudioManager.STREAM_MUSIC);
                            float log1 = (float) (1 - Math.log(maxVolume - volume_level1) / Math.log(maxVolume));
                            player.setVolume(log1, log1);

                            player.prepare();
                            player.start();
                            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                                @Override
                                public void onCompletion(MediaPlayer mp) {
                                    mp.release();
                                }
                            });
                            player.setLooping(false);
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    final Runnable runStartRecord = new Runnable(){
        @Override
        public void run() {
            if(flagRB){
                startStopCounterTime(true);
                handlerShowPadLock.postDelayed(runPadLock, 3000);
                if (recordListener != null) {
                    recordListener.onStart();
                }
            }
        }
    };

    final Runnable runPadLock = new Runnable(){
        @Override
        public void run() {
            if(flagRB){
                showLock(true);
            }
        }
    };

    protected void onActionDown(RelativeLayout recordBtnLayout, MotionEvent motionEvent) {
        log("onActionDown()");
        animationHelper.setStartRecorded(true);
        animationHelper.resetBasketAnimation();
        animationHelper.resetSmallMic();

        RelativeLayout.LayoutParams paramsSlide = (RelativeLayout.LayoutParams) slideToCancelLayout.getLayoutParams();
        paramsSlide.addRule(RelativeLayout.RIGHT_OF, R.id.chrono_voice_clip);
        paramsSlide.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        paramsSlide.setMargins(Util.px2dp(20, outMetrics),0,0,0);
        slideToCancelLayout.setLayoutParams(paramsSlide);
        slideToCancelLayout.setShimmerColor(Color.WHITE);
        slideToCancelLayout.setShimmerAnimationDuration(1500);
        slideToCancelLayout.setAnimationReversed(true);
        slideToCancelLayout.startShimmerAnimation();
        slideToCancelLayout.setVisibility(VISIBLE);

        initialX = recordBtnLayout.getX();

        firstX = motionEvent.getRawX();
        firstY = motionEvent.getRawY();
        lastX = motionEvent.getRawX();
        lastY = motionEvent.getRawY();

        playSound(RECORD_START);
        userBehaviour = UserBehaviour.NONE;
        basketInitialX = basketImg.getX() - 90;

        cancelRecordLayout.setVisibility(GONE);
        smallBlinkingMic.setVisibility(VISIBLE);
        startTime = System.currentTimeMillis();
        isSwiped = false;
        animationHelper.animateSmallMicAlpha();
        handlerStartRecord.postDelayed(runStartRecord, 500); //500 milliseconds delay to record
        flagRB = true;
    }


    protected void onActionMove(RelativeLayout recordBtnLayout, MotionEvent motionEvent) {
        log("onActionMove()");

        long time = System.currentTimeMillis() - startTime;
        if (!isSwiped) {
            UserBehaviour direction;
            float motionX = Math.abs(firstX - motionEvent.getRawX());
            float motionY = Math.abs(firstY - motionEvent.getRawY());

            if(motionX > motionY && lastX < firstX) {
                direction = UserBehaviour.CANCELING;
            }else if (motionY > motionX && lastY < firstY) {
                direction = UserBehaviour.LOCKING;
            }else{
                direction = UserBehaviour.NONE;
            }

            if ((direction == UserBehaviour.CANCELING) && ((userBehaviour!= UserBehaviour.CANCELING) || (motionEvent.getRawY() + recordBtnLayout.getWidth() / 2 > firstY)) && (isRecordingNow) ){
                if ((recordBtnLayout!=null)&&(slideToCancelLayout.getX() <= (counterTime.getLeft()))){
                    log("CANCELING");

                    hideViews(isLessThanOneSecond(time));
                    animationHelper.moveRecordButtonAndSlideToCancelBack(recordBtnLayout, initialX);

                    slideToCancelLayout.setTranslationY(0);
                    slideToCancelLayout.setTranslationX(0);
                    slideToCancelLayout.stopShimmerAnimation();
                    slideToCancelLayout.setVisibility(GONE);

                    isSwiped = true;
                    userBehaviour = UserBehaviour.CANCELING;
                    return;
                }

                if(previewX == 0){
                    previewX = recordBtnLayout.getX();
                }else{
                    if(recordBtnLayout.getX() <= (previewX - 150)){
                        showLock(false);
                    }
                }

                recordBtnLayout.setTranslationX(-(firstX - motionEvent.getRawX()));
                recordBtnLayout.setTranslationY(0);
                slideToCancelLayout.setTranslationX(-(firstX - motionEvent.getRawX()));
                slideToCancelLayout.setTranslationY(0);

            } else if((direction == UserBehaviour.LOCKING) && ((userBehaviour != UserBehaviour.LOCKING) || (motionEvent.getRawX() + recordBtnLayout.getWidth() / 2 > firstX)) && ((layoutLock.getVisibility() == VISIBLE) && (isLockpadShown))) {
                if(((firstY - motionEvent.getRawY()) >= (layoutLock.getHeight() - (recordBtnLayout.getHeight()/2))) && (recordListener != null)){
                    log("LOCKING");

                    recordListener.onLock();

                    recordBtnLayout.setTranslationY(0);
                    recordBtnLayout.setTranslationX(0);

                    slideToCancelLayout.setTranslationY(0);
                    slideToCancelLayout.setTranslationX(0);
                    slideToCancelLayout.stopShimmerAnimation();
                    slideToCancelLayout.setVisibility(GONE);

                    cancelRecordLayout.setVisibility(VISIBLE);
                    userBehaviour = UserBehaviour.LOCKING;

                    return;

                }
                recordBtnLayout.setTranslationY(-(firstY - motionEvent.getRawY()));
                recordBtnLayout.setTranslationX(0);
            }

            lastX = motionEvent.getRawX();
            lastY = motionEvent.getRawY();
        }
    }

    protected void onActionUp(RelativeLayout recordBtnLayout) {
        log("onActionUp()");
        elapsedTime = System.currentTimeMillis() - startTime;
        if (handlerShowPadLock != null){
            handlerShowPadLock.removeCallbacksAndMessages(null);
        }
        flagRB = false;

        userBehaviour = UserBehaviour.NONE;
        recordBtnLayout.setTranslationY(0);
        recordBtnLayout.setTranslationX(0);

        firstX = 0;
        firstY = 0;
        lastX = 0;
        lastY = 0;

        if(slideToCancelLayout!=null){
            slideToCancelLayout.stopShimmerAnimation();
            slideToCancelLayout.setTranslationX(0);
            slideToCancelLayout.setTranslationY(0);
        }

        if (!isLessThanSecondAllowed && isLessThanOneSecond(elapsedTime) && !isSwiped) {
            log("onActionUp:less than a second");
            if (recordListener != null){
                recordListener.onLessThanSecond();
            }
            if(animationHelper!=null){
                animationHelper.clearAlphaAnimation(false);
                animationHelper.onAnimationEnd();
                animationHelper.setStartRecorded(false);

            }
            playSound(RECORD_ERROR);

        }else{
            log("onActionUp:more than a second");
            if (recordListener != null && !isSwiped) {
                recordListener.onFinish(elapsedTime);
            }
            if (!isSwiped) {
                playSound(RECORD_FINISHED);
            }
            if (!isSwiped) {
                animationHelper.clearAlphaAnimation(true);
                animationHelper.setStartRecorded(false);
            }
            showLock(false);
            startStopCounterTime(false);
        }
    }

    private void startStopCounterTime(boolean start){
        if(counterTime!=null){
            if(start){
                if(counterTime.getVisibility() == GONE){
                    counterTime.setVisibility(VISIBLE);
                    counterTime.setBase(SystemClock.elapsedRealtime());
                    counterTime.start();
                }
            }else{
                counterTime.stop();
                counterTime.setVisibility(GONE);
            }
        }

    }

    public void setOnRecordListener(OnRecordListener recordListener) {
        this.recordListener = recordListener;
    }
    public void setOnBasketAnimationEndListener(OnBasketAnimationEnd onBasketAnimationEndListener) {
        animationHelper.setOnBasketAnimationEndListener(onBasketAnimationEndListener);
    }
    public void setLessThanSecondAllowed(boolean isAllowed) {
        isLessThanSecondAllowed = isAllowed;
    }

    public void setCustomSounds(int startSound, int finishedSound, int errorSound) {
        //0 means do not play sound
        RECORD_START = startSound;
        RECORD_FINISHED = finishedSound;
        RECORD_ERROR = errorSound;
    }

    public void setCancelBounds(float cancelBounds) {
        setCancelBounds(cancelBounds, true);
    }
    private void setCancelBounds(float cancelBounds, boolean convertDpToPixel) {
        float bounds = convertDpToPixel ? Util.toPixel(cancelBounds, context) : cancelBounds;
        this.cancelBounds = bounds;
    }

    public static void collapse(final View v, int duration, int targetHeight) {
        int prevHeight  = v.getHeight();
        ValueAnimator valueAnimator = ValueAnimator.ofInt(prevHeight, targetHeight);
        valueAnimator.setInterpolator(new DecelerateInterpolator());
        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                v.getLayoutParams().height = (int) animation.getAnimatedValue();
                v.requestLayout();
            }
        });
        valueAnimator.setInterpolator(new DecelerateInterpolator());
        valueAnimator.setDuration(duration);
        valueAnimator.start();
    }

    public void destroyHandlers(){
        log("destroyHandlers");
        if (handlerStartRecord != null){
            if(runStartRecord!=null){
                handlerStartRecord.removeCallbacks(runStartRecord);
            }
            handlerStartRecord.removeCallbacksAndMessages(null);
        }
        if (handlerShowPadLock != null){
            if(runPadLock!=null){
                handlerShowPadLock.removeCallbacks(runPadLock);
            }
            handlerShowPadLock.removeCallbacksAndMessages(null);
        }

        if(imageLock!=null){
            imageLock.clearAnimation();
        }
        if(imageArrow!=null){
            imageArrow.clearAnimation();
        }
    }
    public static void log(String message) {
        Util.log("RecordView",message);
    }

}
