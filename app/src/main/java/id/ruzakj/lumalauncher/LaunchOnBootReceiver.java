package id.ruzakj.lumalauncher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * Best-effort fallback for TV firmware that does not restore its selected
 * Home activity automatically after a full reboot.
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

        Intent launch = new Intent(context, MainActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            context.startActivity(launch);
        } catch (Exception ignored) {
            // Some newer Android/OEM builds block background activity launches.
            // The default HOME role remains the supported way to open the launcher.
        }
    }
}
