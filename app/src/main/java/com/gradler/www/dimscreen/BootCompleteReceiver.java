package com.gradler.www.dimscreen;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;

public class BootCompleteReceiver extends BroadcastReceiver {
    private static final String TAG = BootCompleteReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        Log.d(TAG, "onReceive() intentAction :" + action);
        if (!TextUtils.isEmpty(action) && action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            context.startService(new Intent(context, DimScreenService.class));
        }
    }
}
