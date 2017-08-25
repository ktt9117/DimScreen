package com.gradler.www.dimscreen;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;

import io.fabric.sdk.android.Fabric;

public class DimScreenService extends Service {
	private static final String TAG = DimScreenService.class.getSimpleName();

	private static final String INTENT_ACTION_DIM_SCREEN = "DIM_SCREEN_ACTION";
	private static final String PREF_DIM_SCREEN_ALPHA = "PREF_DIM_SCREEN_ALPHA";

	private WindowManager.LayoutParams mParams;
	private WindowManager mWindowManager;
	private SharedPreferences mPref;

	private boolean isAttachedLayout;
	private LinearLayout mLayout;
	private SeekBar mAlphaSeekBar;

	private Animation fadeInAnimation;
	private Animation fadeOutAnimation;
	private int touchDownCount;

	private static final int HANDLE_WHAT_COUNT_INIT = 100;
	private int removeWindowCheckCount = 0;
	private boolean sendHandleMessage = false;
	private Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			removeWindowCheckCount = 0;
			sendHandleMessage = false;
		}
	};

	@Override
	public IBinder onBind(Intent arg0) { return null; }
	
	@Override
	public void onCreate() {
		super.onCreate();
		mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
		mPref = PreferenceManager.getDefaultSharedPreferences(this);

		fadeInAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in_ani);
		fadeOutAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_out_ani);
		fadeInAnimation.setAnimationListener(mAnimationListener);
		fadeOutAnimation.setAnimationListener(mAnimationListener);

		startForegroundService();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (!Fabric.isInitialized()) {
			Fabric.with(getApplicationContext(), new Crashlytics());
		}

		String action = intent != null && intent.getAction() != null ? intent.getAction() : null;
		Log.d(TAG, "intent action : " + action);
		if (!TextUtils.isEmpty(action) && action.equals(INTENT_ACTION_DIM_SCREEN)) {
			toggleWindowView();
		}
		return START_STICKY;
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
	private void addOpacityController() {
		removeWindowLayout();

		mLayout = new LinearLayout(this);
		mLayout.setPadding(60, 60, 60, 60);
		mLayout.setBackgroundColor(Color.BLACK);
		mLayout.setOrientation(LinearLayout.VERTICAL);

		float savedAlphaValue = (mPref != null) ? mPref.getFloat(PREF_DIM_SCREEN_ALPHA, .5f) : .5f;

		LinearLayout.LayoutParams param = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		mAlphaSeekBar = new SeekBar(this);
		mAlphaSeekBar.setLayoutParams(param);
		mAlphaSeekBar.setMax(100);
		mAlphaSeekBar.setProgress((int) (savedAlphaValue * 100));
		mAlphaSeekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
			}

			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				if (mParams != null) {
					if (progress < 30) {
						progress = 30;
					}
					mParams.alpha = progress / 100.0f;
					if (mWindowManager != null) {
						mWindowManager.updateViewLayout(mLayout, mParams);
					}
				}
				if (mPref != null) {
					mPref.edit().putFloat(PREF_DIM_SCREEN_ALPHA, mParams.alpha).commit();
				}
			}
		});

		mLayout.addView(mAlphaSeekBar);
		mLayout.setOnTouchListener(onLayoutTouchListener);

		mParams = new WindowManager.LayoutParams(
				WindowManager.LayoutParams.MATCH_PARENT,
				WindowManager.LayoutParams.MATCH_PARENT,
				WindowManager.LayoutParams.TYPE_PHONE,
				WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
				PixelFormat.TRANSLUCENT);

		mParams.gravity = Gravity.LEFT | Gravity.TOP;
		mParams.alpha = savedAlphaValue;
		mWindowManager.addView(mLayout, mParams);
	}

	@Override
	public void onDestroy() {
		removeWindowLayout();
		stopForeground(true);
		super.onDestroy();
	}

	public void toggleWindowView() {
		if (isAttachedLayout) {
			removeWindowLayout();
			isAttachedLayout = false;
		} else {
			addOpacityController();
			isAttachedLayout = true;
		}

		CustomEvent event = new CustomEvent("toggleWindowView");
		event.putCustomAttribute("toggle", isAttachedLayout ? "on" : "off");
		Answers.getInstance().logCustom(event);
	}

	private void removeWindowLayout() {
		if (mWindowManager == null) {
			return;
		}

		if (mLayout != null) {
			if (mAlphaSeekBar != null) mLayout.removeView(mAlphaSeekBar);

			mAlphaSeekBar = null;
			mWindowManager.removeView(mLayout);
		}

		mLayout = null;
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	private void startForegroundService() {
		Intent btnIntent = new Intent(this, DimScreenService.class);
		btnIntent.setAction(INTENT_ACTION_DIM_SCREEN);
		PendingIntent contentPending = PendingIntent.getService(this, 0, btnIntent, PendingIntent.FLAG_UPDATE_CURRENT);

		Notification notification = new Notification.Builder(this)
				.setSmallIcon(R.mipmap.ic_launcher)
				.setContentTitle(getString(R.string.app_name))
				.setContentIntent(contentPending)
				.setOngoing(true)
				.setWhen(System.currentTimeMillis())
				.build();

		startForeground(1, notification);
	}

	private View.OnTouchListener onLayoutTouchListener = new View.OnTouchListener() {
		@Override
		public boolean onTouch(View v, MotionEvent event) {
			if (event.getAction() == MotionEvent.ACTION_DOWN) {
				Log.i(TAG, "touch down action call");
				touchDownCount++;
				removeWindowCheckCount++;
				if (sendHandleMessage == false) {
					if (mHandler != null) mHandler.sendEmptyMessageDelayed(HANDLE_WHAT_COUNT_INIT, 1000);
					sendHandleMessage = true;
				}
			}

			if (removeWindowCheckCount > 2) {
				removeWindowCheckCount = 0;
				if (mHandler != null) mHandler.removeMessages(HANDLE_WHAT_COUNT_INIT);
				sendHandleMessage = false;
				toggleWindowView();
				return true;
			}

			if ((touchDownCount % 2) == 0) {
				toggleSeekBarVisibility();
				return true;
			}

			return false;
		}
	};

	private void toggleSeekBarVisibility() {
		if (mAlphaSeekBar == null) {
			return;
		}

		mAlphaSeekBar.startAnimation(
				(mAlphaSeekBar.getVisibility()) == View.VISIBLE ? fadeOutAnimation : fadeInAnimation);
	}

	private Animation.AnimationListener mAnimationListener = new Animation.AnimationListener() {
		@Override
		public void onAnimationStart(Animation animation) {}
		@Override
		public void onAnimationRepeat(Animation animation) {}
		@Override
		public void onAnimationEnd(Animation animation) {
			if (mAlphaSeekBar == null) {
				return;
			}
			mAlphaSeekBar.setVisibility(
					(mAlphaSeekBar.getVisibility()) == View.VISIBLE ? View.INVISIBLE : View.VISIBLE);
		}
	};
}