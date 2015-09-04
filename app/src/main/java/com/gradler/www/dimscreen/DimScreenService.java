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
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextClock;
import android.widget.TextView;

public class DimScreenService extends Service {
	private static final String TAG = DimScreenService.class.getSimpleName();

	private static final String INTENT_ACTION_DIM_SCREEN = "DIM_SCREEN_ACTION";
	private static final String PREF_DIM_SCREEN_ALPHA = "PREF_DIM_SCREEN_ALPHA";
	//private static final int TOUCH_MOVE_SENSITIVITY_VALUE = 10;

	private WindowManager.LayoutParams mParams;
	private WindowManager mWindowManager;
	private SharedPreferences mPref;

	private boolean isAttachedLayout;
	private LinearLayout mLayout;
	private TextClock mTextClock;
	private TextView mBatteryLevel;

	private int mDeviceHeight;

	//private boolean mMovable = false;
	private BatteryStatusReceiver mReceiver;

	@Override
	public IBinder onBind(Intent arg0) { return null; }
	
	@Override
	public void onCreate() {
		super.onCreate();
		mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
		mPref = PreferenceManager.getDefaultSharedPreferences(this);

		/* register battry status receiver */
		mReceiver = new BatteryStatusReceiver();
		IntentFilter filter = new IntentFilter();
		filter.addAction(Intent.ACTION_BATTERY_CHANGED);
		registerReceiver(mReceiver, filter);

		startForegroundService();

//		addOpacityController();
//		isAttachedLayout = true;
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

	private void setMaxPosition() {
		DisplayMetrics matrix = new DisplayMetrics();
		mWindowManager.getDefaultDisplay().getMetrics(matrix);
		mDeviceHeight = matrix.heightPixels;
		Log.d(TAG, "mDeviceHeight : " + mDeviceHeight);
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
	private void addOpacityController() {
		removeWindowLayout();

		mLayout = new LinearLayout(this);
		mLayout.setPadding(60, 60, 60, 60);
		mLayout.setBackgroundColor(Color.BLACK);
		mLayout.setOrientation(LinearLayout.VERTICAL);
//		SeekBar sizeSeekBar = new SeekBar(this);
//		sizeSeekBar.setMax(100);
//		sizeSeekBar.setProgress(100);
//		sizeSeekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
//			@Override
//			public void onStopTrackingTouch(SeekBar seekBar) {
//			}
//
//			@Override
//			public void onStartTrackingTouch(SeekBar seekBar) {
//			}
//
//			@Override
//			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
//				if (progress > 80) {
//					mMovable = false;
//				} else {
//					mMovable = true;
//				}
//				if (progress > 10) {
//					mParams.height = mDeviceHeight * progress / 100;
//				}
//				mWindowManager.updateViewLayout(mLayout, mParams);
//			}
//		});

		float savedAlphaValue = (mPref != null) ? mPref.getFloat(PREF_DIM_SCREEN_ALPHA, .5f) : .5f;

		LinearLayout.LayoutParams param = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		SeekBar alphaSeekBar = new SeekBar(this);
		alphaSeekBar.setLayoutParams(param);
		//alphaSeekBar.setRotation(270);
		alphaSeekBar.setMax(100);
		alphaSeekBar.setProgress((int) (savedAlphaValue * 100));
		alphaSeekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
			}

			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				if (mParams != null) {
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
		mBatteryLevel.setText("Unknown");
		mBatteryLevel.setLayoutParams(textClockParam);

		mLayout.addView(alphaSeekBar);
		mLayout.addView(mTextClock);
		mLayout.addView(mBatteryLevel);
//		mLayout.addView(sizeSeekBar);

//		mLayout.setOnTouchListener(new View.OnTouchListener() {
//			float y;
//			@Override
//			public boolean onTouch(View v, MotionEvent event) {
//				if (mMovable) {
//					if (event.getAction() == MotionEvent.ACTION_DOWN) {
//						y = event.getY();
//						Log.i(TAG, "onTouch() ACTION_DOWN : " + y);
//					} else if (event.getAction() == MotionEvent.ACTION_MOVE) {
//						float gap = event.getY() - y;
//						float temp = (gap < 0) ? (gap * -1) : gap;
//						if (temp > TOUCH_MOVE_SENSITIVITY_VALUE) {
//							mParams.y = (int) (mParams.y + gap);
//							mWindowManager.updateViewLayout(mLayout, mParams);
//						}
//					}
//					return true;
//				}
//				return false;
//			}
//		});

		mParams = new WindowManager.LayoutParams(
			WindowManager.LayoutParams.MATCH_PARENT,
			WindowManager.LayoutParams.MATCH_PARENT,
			WindowManager.LayoutParams.TYPE_PHONE,
			WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
			PixelFormat.TRANSLUCENT);
		mParams.gravity = Gravity.LEFT | Gravity.TOP;
		mParams.alpha = savedAlphaValue;
		mWindowManager.addView(mLayout, mParams);
		setMaxPosition();
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		setMaxPosition();
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
				if (mBatteryLevel != null) mLayout.removeView(mTextClock);
				mTextClock = null;
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