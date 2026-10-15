package id.ruzakj.lumalauncher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * Boot-time launcher entry point for iFFALCON/TCL TV+ firmware.
 * Launches Luma explicitly as a HOME activity so the TV shell can treat it
 * like the selected launcher instead of an ordinary app task.
 */
public class LaunchOnBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !"android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences("luma_preferences", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("auto_start_luma", true)) return;

        Intent launch = new Intent(Intent.ACTION_MAIN);
        launch.addCategory(Intent.CATEGORY_HOME);
        launch.setClass(context, MainActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            context.startActivity(launch);
        } catch (Exception ignored) {
            // If this firmware blocks boot-time launches, the HOME default remains
            // the fallback handled by the TV system.
        }
    }
}
