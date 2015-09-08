package com.gradler.www.dimscreen;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.DisplayMetrics;
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
import android.widget.TextClock;
import android.widget.TextView;

public class DimScreenService extends Service {
	private static final String TAG = DimScreenService.class.getSimpleName();

	private static final String INTENT_ACTION_DIM_SCREEN = "DIM_SCREEN_ACTION";
	private static final String PREF_DIM_SCREEN_ALPHA = "PREF_DIM_SCREEN_ALPHA";

	private WindowManager.LayoutParams mParams;
	private WindowManager mWindowManager;
	private SharedPreferences mPref;

	private boolean isAttachedLayout;
	private LinearLayout mLayout;
	private TextClock mTextClock;
	private TextView mBatteryLevel;
	private SeekBar mAlphaSeekBar;

	private Animation fadeInAnimation;
	private Animation fadeOutAnimation;
	private BatteryStatusReceiver mReceiver;
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

		/* register battery status receiver */
		mReceiver = new BatteryStatusReceiver();
		IntentFilter filter = new IntentFilter();
		filter.addAction(Intent.ACTION_BATTERY_CHANGED);
		registerReceiver(mReceiver, filter);

		fadeInAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in_ani);
		fadeOutAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_out_ani);
		fadeInAnimation.setAnimationListener(mAnimationListener);
		fadeOutAnimation.setAnimationListener(mAnimationListener);

		startForegroundService();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
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
		//alphaSeekBar.setRotation(270);
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
					if (progress < 40) {
						progress = 40;
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

		mTextClock = new TextClock(this);
		mTextClock.setTextSize(40);
		mTextClock.setTextColor(Color.WHITE);
		LinearLayout.LayoutParams textClockParam = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		textClockParam.gravity = Gravity.CENTER_HORIZONTAL;
		mTextClock.setLayoutParams(textClockParam);

		mBatteryLevel = new TextView(this);
		mBatteryLevel.setTextColor(Color.WHITE);
		mBatteryLevel.setTextSize(30);
		mBatteryLevel.setLayoutParams(textClockParam);

		mLayout.addView(mAlphaSeekBar);
		mLayout.addView(mTextClock);
		mLayout.addView(mBatteryLevel);
		mLayout.setOnTouchListener(onLayoutTouchListener);

		mParams = new WindowManager.LayoutParams(
				WindowManager.LayoutParams.MATCH_PARENT,
				WindowManager.LayoutParams.MATCH_PARENT,
				WindowManager.LayoutParams.TYPE_PHONE,
//				WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
				WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
//				WindowManager.LayoutParams.FLAG_LAYOUT_ATTACHED_IN_DECOR,
				PixelFormat.TRANSLUCENT);

		mParams.gravity = Gravity.LEFT | Gravity.TOP;
		mParams.alpha = savedAlphaValue;
		mWindowManager.addView(mLayout, mParams);

	}

	@Override
	public void onDestroy() {
		removeWindowLayout();

		if (mReceiver != null) {
			unregisterReceiver(mReceiver);
			mReceiver = null;
		}

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
	}

	private void removeWindowLayout() {
		if (mWindowManager != null) {
			if (mLayout != null) {
				if (mBatteryLevel != null) mLayout.removeView(mBatteryLevel);
				if (mTextClock != null) mLayout.removeView(mTextClock);
				if (mAlphaSeekBar != null) mLayout.removeView(mAlphaSeekBar);
				mBatteryLevel = null;
				mTextClock = null;
				mAlphaSeekBar = null;
				mWindowManager.removeView(mLayout);
			}
			mLayout = null;
		}
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
		mAlphaSeekBar.startAnimation((mAlphaSeekBar.getVisibility()) == View.VISIBLE ? fadeOutAnimation : fadeInAnimation);
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
			mAlphaSeekBar.setVisibility((mAlphaSeekBar.getVisibility()) == View.VISIBLE ? View.INVISIBLE : View.VISIBLE);
		}
	};

	private class BatteryStatusReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (mBatteryLevel != null) {
				int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
				//int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
				//float batteryPct = level / (float) scale;
				if (level > 0) {
					StringBuffer sb = new StringBuffer();
					sb.append("(");
					sb.append(Integer.toString(level));
					sb.append("%)");
					mBatteryLevel.setText(sb.toString());
				}
			}
		}
	}
}