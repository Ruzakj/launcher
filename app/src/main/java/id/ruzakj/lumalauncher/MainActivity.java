package id.ruzakj.lumalauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.net.Uri;
import android.provider.Settings;
import android.view.MenuItem;
import android.widget.PopupMenu;
import android.widget.ImageView;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private final int bg = Color.rgb(16, 20, 17);
    private final int panel = Color.rgb(31, 38, 32);
    private final int accent = Color.rgb(190, 244, 111);
    private final int muted = Color.rgb(157, 169, 157);
    private LinearLayout appGrid;
    private EditText search;
    private List<AppEntry> apps = new ArrayList<>();
    private int columns = 4;
    private boolean tvMode = false;

    private static class AppEntry {
        String label, pkg;
        android.graphics.drawable.Drawable icon;
        AppEntry(String l, String p, android.graphics.drawable.Drawable i) { label=l; pkg=p; icon=i; }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(0);
        tvMode = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_TYPE_MASK) == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION;
        columns = getResources().getConfiguration().smallestScreenWidthDp >= 600 ? 6 : 4;
        buildHome();
        loadApps();
        renderApps("");
    }

    private int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + 0.5f); }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private TextView text(String value, float size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }
    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l;
    }
    private void buildHome() {
        LinearLayout root = column();
        root.setPadding(dp(22), dp(18), dp(22), dp(12));
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(28,39,29), bg, Color.rgb(12,16,14)}));
        setContentView(root);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = text("luma", 22, Color.WHITE, true);
        brand.setLetterSpacing(0.03f);
        top.addView(brand, new LinearLayout.LayoutParams(0, dp(42), 1));
        TextView date = text(new SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(new Date()).toUpperCase(Locale.getDefault()), 11, muted, true);
        top.addView(date);
        TextView menu = text("  ⋮  ", 25, accent, true);
        menu.setGravity(Gravity.CENTER);
        menu.setContentDescription("Launcher settings");
        top.addView(menu, new LinearLayout.LayoutParams(dp(44), dp(44)));
        menu.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showLauncherMenu(v); } });
        root.addView(top);

        LinearLayout hero = column();
        hero.setPadding(0, dp(34), 0, dp(22));
        TextView greeting = text("MAKE SPACE.", 11, accent, true);
        greeting.setLetterSpacing(0.16f);
        hero.addView(greeting);
        TextView clock = text(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()), 58, Color.WHITE, false);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        hero.addView(clock);
        TextView subtitle = text("Less noise. More focus.", 14, muted, false);
        hero.addView(subtitle);
        root.addView(hero);

        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextSize(14);
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(muted);
        search.setHint(tvMode ? "Find an app (use remote)" : "Search your apps");
        search.setPadding(dp(16), 0, dp(16), 0);
        search.setCompoundDrawablePadding(dp(10));
        search.setBackground(shape(panel, 18));
        search.setHeight(dp(52));
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        root.addView(search, new LinearLayout.LayoutParams(-1, dp(52)));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { renderApps(s.toString()); }
            public void afterTextChanged(Editable e) {}
        });

        LinearLayout section = new LinearLayout(this);
        section.setGravity(Gravity.CENTER_VERTICAL);
        section.setPadding(0, dp(22), 0, dp(10));
        TextView title = text("YOUR APPS", 11, muted, true);
        title.setLetterSpacing(0.13f);
        section.addView(title, new LinearLayout.LayoutParams(0, dp(28), 1));
        TextView count = text("TAP TO OPEN", 9, muted, false);
        count.setLetterSpacing(0.08f);
        section.addView(count);
        root.addView(section);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        appGrid = column();
        scroll.addView(appGrid);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView footer = text("BUILT FOR EVERYDAY  ·  KEEP IT SIMPLE", 9, Color.rgb(105,120,107), true);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(12), 0, dp(5));
        footer.setLetterSpacing(0.06f);
        root.addView(footer);
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN, null);
        query.addCategory(Intent.CATEGORY_LAUNCHER);
        List<android.content.pm.ResolveInfo> found = pm.queryIntentActivities(query, 0);
        for (android.content.pm.ResolveInfo info : found) {
            String pkg = info.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            apps.add(new AppEntry(info.loadLabel(pm).toString(), pkg, info.loadIcon(pm)));
        }
        Collections.sort(apps, new Comparator<AppEntry>() {
            public int compare(AppEntry a, AppEntry b) { return a.label.compareToIgnoreCase(b.label); }
        });
    }

    private void renderApps(String filter) {
        if (appGrid == null) return;
        appGrid.removeAllViews();
        String q = filter == null ? "" : filter.trim().toLowerCase(Locale.getDefault());
        int matches = 0;
        LinearLayout row = null;
        for (AppEntry app : apps) {
            if (!app.label.toLowerCase(Locale.getDefault()).contains(q)) continue;
            if (matches % columns == 0) {
                row = new LinearLayout(this);
                row.setGravity(Gravity.TOP);
                row.setPadding(0, dp(3), 0, dp(3));
                appGrid.addView(row, new LinearLayout.LayoutParams(-1, dp(tvMode ? 132 : 108)));
            }
            row.addView(appCell(app), new LinearLayout.LayoutParams(0, dp(tvMode ? 124 : 104), 1));
            matches++;
        }
        if (matches == 0) {
            TextView empty = text(q.isEmpty() ? "No apps found on this device." : "No apps match “" + filter + "”.", 13, muted, false);
            empty.setGravity(Gravity.CENTER);
            appGrid.addView(empty, new LinearLayout.LayoutParams(-1, dp(90)));
        }
    }

    private View appCell(final AppEntry app) {
        LinearLayout cell = column();
        cell.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        cell.setPadding(dp(2), dp(5), dp(2), 0);
        cell.setFocusable(true);
        cell.setFocusableInTouchMode(false);
        cell.setBackground(shape(Color.TRANSPARENT, 14));
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(app.icon);
        icon.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        int iconSize = tvMode ? 56 : 42;
        cell.addView(icon, new LinearLayout.LayoutParams(dp(iconSize), dp(iconSize)));
        TextView label = text(app.label, tvMode ? 12 : 10, Color.rgb(231,236,229), false);
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(2);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(32));
        lp.topMargin = dp(5);
        cell.addView(label, lp);
        cell.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) { showAppMenu(v, app); return true; }
        });
        cell.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean focused) { v.setBackground(shape(focused ? Color.rgb(57,72,53) : Color.TRANSPARENT, 14)); }
        });
        cell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                launchApp(app, false);
            }
        });
        return cell;
    }

    private void showAppMenu(View anchor, final AppEntry app) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, "Open");
        popup.getMenu().add(0, 2, 1, "App info");
        popup.getMenu().add(0, 3, 2, "Uninstall");
        popup.getMenu().add(0, 4, 3, "Open in split screen");
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == 1) launchApp(app, false);
                else if (item.getItemId() == 2) startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.pkg)));
                else if (item.getItemId() == 3) {
                    new AlertDialog.Builder(MainActivity.this).setTitle("Uninstall " + app.label + "?")
                        .setMessage("Android will ask you to confirm the uninstall.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Continue", (d, w) -> {
                            try { startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.pkg))); }
                            catch (Exception e) { Toast.makeText(MainActivity.this, "Cannot open uninstall screen", Toast.LENGTH_SHORT).show(); }
                        }).show();
                } else if (item.getItemId() == 4) launchApp(app, true);
                return true;
            }
        });
        popup.show();
    }

    private void launchApp(AppEntry app, boolean split) {
        Intent intent = getPackageManager().getLaunchIntentForPackage(app.pkg);
        if (intent == null) { Toast.makeText(this, "App cannot be opened", Toast.LENGTH_SHORT).show(); return; }
        if (split) {
            if (android.os.Build.VERSION.SDK_INT >= 24) {
                intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            } else {
                Toast.makeText(this, "Split screen requires Android 7.0 or newer", Toast.LENGTH_LONG).show();
                return;
            }
        }
        try { startActivity(intent); }
        catch (Exception e) { Toast.makeText(this, split ? "Split screen is not supported by this device or app" : "Unable to open app", Toast.LENGTH_LONG).show(); }
    }

    private void showLauncherMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, "Choose default launcher");
        popup.getMenu().add(0, 2, 1, "Refresh app list");
        popup.getMenu().add(0, 3, 2, "Display settings");
        popup.getMenu().add(0, 4, 3, "Launcher info");
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                try {
                    if (item.getItemId() == 1) startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
                    else if (item.getItemId() == 2) {
                        apps.clear(); loadApps(); renderApps(search == null ? "" : search.getText().toString());
                        Toast.makeText(MainActivity.this, "App list refreshed", Toast.LENGTH_SHORT).show();
                    } else if (item.getItemId() == 3) startActivity(new Intent(Settings.ACTION_DISPLAY_SETTINGS));
                    else if (item.getItemId() == 4) new AlertDialog.Builder(MainActivity.this).setTitle("Luma Launcher")
                        .setMessage("Minimal, lightweight home screen. Long-press an app for app info, uninstall, or split-screen launch. Floating windows depend on device firmware.")
                        .setPositiveButton("OK", null).show();
                } catch (Exception e) { Toast.makeText(MainActivity.this, "Open Settings and choose Luma Launcher as Home app", Toast.LENGTH_LONG).show(); }
                return true;
            }
        });
        popup.show();
    }

    @Override public void onResume() {
        super.onResume();
        if (appGrid != null) { apps.clear(); loadApps(); renderApps(search == null ? "" : search.getText().toString()); }
    }

    @Override public void onBackPressed() {
        if (search != null && search.length() > 0) search.setText("");
        else super.onBackPressed();
    }
}
