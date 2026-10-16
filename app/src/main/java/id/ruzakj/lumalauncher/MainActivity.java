package id.ruzakj.lumalauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String PREFS = "luma_preferences";
    private static final String KEY_SETUP = "setup_complete";
    private static final String KEY_ACCENT = "accent_color";
    private static final String KEY_ICON_SIZE = "icon_size";
    private static final String KEY_FAVORITES = "favorite_packages";
    private static final String KEY_AUTO_START = "auto_start_luma";
    private static final String KEY_HIDDEN_APPS = "hidden_packages";
    private static final String KEY_COLUMNS = "app_columns";
    private static final String KEY_HERO_URI = "hero_image_uri";
    private static final String KEY_HERO_FADE = "hero_image_fade";
    private ImageView heroPhoto;
    private boolean favoritesOnly = false;

    private final int bg = Color.rgb(13, 17, 15);
    private final int panel = Color.rgb(27, 34, 29);
    private final int panelRaised = Color.rgb(37, 46, 39);
    private final int muted = Color.rgb(159, 172, 161);
    private int accent;
    private int iconSizeDp;
    private LinearLayout appGrid;
    private EditText search;
    private List<AppEntry> apps = new ArrayList<>();
    private int columns = 4;
    private boolean tvMode;
    private boolean pendingPackageRefresh;
    private boolean packageReceiverRegistered;
    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            pendingPackageRefresh = true;
            refreshAppsAfterPackageChange();
        }
    };
    private TextView homeClock;
    private TextView homeDate;
    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private final Runnable clockUpdater = new Runnable() {
        @Override public void run() {
            if (homeClock != null) {
                homeClock.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
            }
            if (homeDate != null) {
                homeDate.setText(new SimpleDateFormat("EEE, d MMM", Locale.getDefault())
                        .format(new Date()).toUpperCase(Locale.getDefault()));
            }
            clockHandler.postDelayed(this, 15_000L);
        }
    };

    private static class AppEntry {
        String label, pkg;
        Drawable icon;
        AppEntry(String label, String pkg, Drawable icon) {
            this.label = label; this.pkg = pkg; this.icon = icon;
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        accent = prefs.getInt(KEY_ACCENT, Color.rgb(190, 244, 111));
        iconSizeDp = prefs.getInt(KEY_ICON_SIZE, 44);
        tvMode = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_TYPE_MASK)
                == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION;
        columns = prefs.getInt(KEY_COLUMNS, tvMode ? 6 : (getResources().getConfiguration().smallestScreenWidthDp >= 600 ? 6 : 4));
        styleSystemBars();
        if (!prefs.getBoolean(KEY_SETUP, false)) showSetupScreen();
        else showHome();
    }

    private void styleSystemBars() {
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(0);
    }

    private int dp(float n) {
        return (int) (n * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout card() {
        LinearLayout c = column();
        c.setPadding(dp(18), dp(16), dp(18), dp(16));
        c.setBackground(shape(panel, 22));
        return c;
    }

    private TextView actionButton(String label, boolean primary) {
        TextView b = text(label, 14, primary ? bg : Color.WHITE, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), dp(14), dp(16), dp(14));
        b.setBackground(shape(primary ? accent : panelRaised, 16));
        b.setFocusable(true);
        b.setFocusableInTouchMode(true);
        if (tvMode) {
            b.setOnFocusChangeListener((v, focused) -> {
                v.setBackground(shape(focused ? Color.rgb(218, 255, 169) : (primary ? accent : panelRaised), 16));
                b.setTextColor(focused ? Color.rgb(13, 17, 15) : (primary ? bg : Color.WHITE));
            });
        }
        return b;
    }

    private void showSetupScreen() {
        LinearLayout root = column();
        root.setPadding(dp(24), dp(26), dp(24), dp(22));
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(29, 42, 31), bg, Color.rgb(12, 15, 13)}));
        setContentView(root);

        TextView logo = text("luma.", 30, Color.WHITE, true);
        logo.setLetterSpacing(-0.03f);
        root.addView(logo);

        LinearLayout hero = column();
        hero.setPadding(0, dp(38), 0, dp(28));
        TextView eyebrow = text("YOUR SPACE, REIMAGINED", 10, accent, true);
        eyebrow.setLetterSpacing(0.15f);
        hero.addView(eyebrow);
        TextView heading = text("A calmer home.\nA cleaner start.", 31, Color.WHITE, false);
        heading.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        heading.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, dp(100));
        hp.topMargin = dp(12);
        hero.addView(heading, hp);
        TextView desc = text("Atur tampilan dulu, lalu jadikan Luma sebagai layar utama pilihanmu.", 14, muted, false);
        desc.setGravity(Gravity.TOP);
        hero.addView(desc);
        root.addView(hero);

        LinearLayout options = card();
        TextView appearance = text("APPEARANCE", 10, muted, true);
        appearance.setLetterSpacing(0.13f);
        options.addView(appearance);

        LinearLayout themeRow = new LinearLayout(this);
        themeRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView themeLabel = text("Accent color", 14, Color.WHITE, false);
        themeRow.addView(themeLabel, new LinearLayout.LayoutParams(0, dp(48), 1));
        TextView lime = optionChip("Lime", Color.rgb(190, 244, 111));
        TextView blue = optionChip("Blue", Color.rgb(130, 190, 255));
        themeRow.addView(lime);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, dp(38));
        bp.leftMargin = dp(8);
        themeRow.addView(blue, bp);
        options.addView(themeRow);
        lime.setOnClickListener(v -> setAccent(Color.rgb(190, 244, 111)));
        blue.setOnClickListener(v -> setAccent(Color.rgb(130, 190, 255)));

        LinearLayout sizeRow = new LinearLayout(this);
        sizeRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView sizeLabel = text("App icon size", 14, Color.WHITE, false);
        sizeRow.addView(sizeLabel, new LinearLayout.LayoutParams(0, dp(48), 1));
        TextView compact = optionChip("Compact", accent);
        TextView comfy = optionChip("Comfortable", accent);
        sizeRow.addView(compact);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, dp(38));
        cp.leftMargin = dp(8);
        sizeRow.addView(comfy, cp);
        options.addView(sizeRow);
        compact.setOnClickListener(v -> setIconSize(36));
        comfy.setOnClickListener(v -> setIconSize(44));
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(-1, -2);
        root.addView(options, op);

        View spacer = new View(this);
        root.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));

        TextView defaultButton = actionButton("Set as default launcher  ↗", true);
        LinearLayout.LayoutParams dbp = new LinearLayout.LayoutParams(-1, dp(52));
        dbp.topMargin = dp(18);
        root.addView(defaultButton, dbp);
        defaultButton.setOnClickListener(v -> openDefaultLauncherSettings());
        if (tvMode) root.post(() -> {
            defaultButton.setFocusableInTouchMode(true);
            defaultButton.requestFocus();
        });

        TextView startButton = actionButton("Continue to Luma", false);
        LinearLayout.LayoutParams sbp = new LinearLayout.LayoutParams(-1, dp(50));
        sbp.topMargin = dp(10);
        root.addView(startButton, sbp);
        startButton.setOnClickListener(v -> {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_SETUP, true).apply();
            showHome();
        });

        TextView note = text("You can change these choices later in Settings.", 11, muted, false);
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, dp(12), 0, 0);
        root.addView(note);
    }

    private TextView optionChip(String label, int color) {
        TextView chip = text(label, 11, Color.WHITE, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(11), 0, dp(11), 0);
        chip.setBackground(shape(panelRaised, 12));
        chip.setFocusable(true);
        chip.setFocusableInTouchMode(true);
        if (tvMode) chip.setOnFocusChangeListener((v, focused) ->
                v.setBackground(shape(focused ? Color.rgb(57, 72, 53) : panelRaised, 12)));
        return chip;
    }

    private void setAccent(int color) {
        accent = color;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_ACCENT, color).apply();
        showSetupScreen();
    }

    private void setIconSize(int size) {
        iconSizeDp = size;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_ICON_SIZE, size).apply();
        showSetupScreen();
    }

    private void showHome() {
        favoritesOnly = false;
        styleSystemBars();
        buildHome();
        apps.clear();
        loadApps();
        renderApps("");
        if (tvMode) requestInitialTvFocus();
    }

    private void requestInitialTvFocus() {
        if (appGrid == null) return;
        appGrid.post(() -> {
            if (!tvMode || appGrid == null) return;
            for (int i = 0; i < appGrid.getChildCount(); i++) {
                View child = appGrid.getChildAt(i);
                if (child instanceof ViewGroup) {
                    ViewGroup row = (ViewGroup) child;
                    for (int j = 0; j < row.getChildCount(); j++) {
                        View cell = row.getChildAt(j);
                        if (cell.isFocusable() && cell.requestFocus()) return;
                    }
                }
            }
            if (search != null) search.requestFocus();
        });
    }

    private void buildHome() {
        LinearLayout root = column();
        root.setPadding(dp(22), dp(18), dp(22), dp(12));
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(26, 37, 28), bg, Color.rgb(10, 13, 11)}));
        if (tvMode) root.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        setContentView(root);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = text("luma.", 23, Color.WHITE, true);
        brand.setLetterSpacing(-0.02f);
        top.addView(brand, new LinearLayout.LayoutParams(0, dp(44), 1));
        homeDate = text(new SimpleDateFormat("EEE, d MMM", Locale.getDefault())
                .format(new Date()).toUpperCase(Locale.getDefault()), 10, muted, true);
        top.addView(homeDate);
        TextView drawerButton = actionButton("Apps", false);
        LinearLayout.LayoutParams drawerButtonParams = new LinearLayout.LayoutParams(-2, dp(38));
        drawerButtonParams.rightMargin = dp(8);
        top.addView(drawerButton, drawerButtonParams);
        drawerButton.setOnClickListener(v -> showAppDrawer());
        TextView menu = text("•••", 18, accent, true);
        menu.setGravity(Gravity.CENTER);
        menu.setContentDescription("Launcher settings");
        menu.setFocusable(true);
        top.addView(menu, new LinearLayout.LayoutParams(dp(44), dp(44)));
        menu.setOnClickListener(v -> showLauncherSettings());

        LinearLayout hero = column();
        hero.setPadding(0, dp(27), 0, dp(22));
        TextView eyebrow = text("MAKE ROOM FOR WHAT MATTERS", 10, accent, true);
        eyebrow.setLetterSpacing(0.12f);
        hero.addView(eyebrow);
        homeClock = text(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()),
                58, Color.WHITE, false);
        homeClock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, dp(70));
        clp.topMargin = dp(3);
        hero.addView(homeClock, clp);
        TextView subtitle = text("Less noise. More focus.", 14, muted, false);
        hero.addView(subtitle);
        root.addView(hero);

        LinearLayout photoFrame = new LinearLayout(this);
        photoFrame.setGravity(Gravity.CENTER);
        photoFrame.setBackground(shape(panel, 20));
        heroPhoto = new ImageView(this);
        heroPhoto.setScaleType(ImageView.ScaleType.FIT_CENTER);
        photoFrame.addView(heroPhoto, new LinearLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams photoParams = new LinearLayout.LayoutParams(-1, dp(138));
        photoParams.bottomMargin = dp(14);
        root.addView(photoFrame, photoParams);
        loadHeroPhoto();
        photoFrame.setOnClickListener(v -> chooseHeroPhoto());
        photoFrame.setOnLongClickListener(v -> { showHeroPhotoOptions(); return true; });
        photoFrame.setFocusable(true);
        photoFrame.setContentDescription("Personal banner photo; click to choose, long press for options");

        search = new EditText(this);
        search.setSingleLine(true);
        search.setFocusable(true);
        search.setFocusableInTouchMode(!tvMode);
        search.setTextSize(14);
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(muted);
        search.setHint(tvMode ? "Find an app using your remote" : "Search apps");
        search.setPadding(dp(16), 0, dp(16), 0);
        search.setBackground(shape(panel, 17));
        search.setHeight(dp(52));
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setCompoundDrawablePadding(dp(10));
        root.addView(search, new LinearLayout.LayoutParams(-1, dp(52)));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderApps(s.toString());
            }
            public void afterTextChanged(Editable e) {}
        });

        LinearLayout section = new LinearLayout(this);
        section.setGravity(Gravity.CENTER_VERTICAL);
        section.setPadding(0, dp(20), 0, dp(8));
        TextView title = text("APPS", 10, muted, true);
        title.setLetterSpacing(0.15f);
        section.addView(title, new LinearLayout.LayoutParams(0, dp(28), 1));
        TextView hint = text("HOLD FOR OPTIONS", 9, muted, false);
        hint.setLetterSpacing(0.05f);
        section.addView(hint);
        root.addView(section);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        appGrid = column();
        scroll.addView(appGrid);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        if (tvMode) {
            scroll.setFocusable(false);
            scroll.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        }

        TextView footer = text("SIMPLE BY DESIGN", 9, Color.rgb(105, 120, 107), true);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(12), 0, dp(5));
        footer.setLetterSpacing(0.12f);
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
            boolean alreadyAdded = false;
            for (AppEntry entry : apps) if (entry.pkg.equals(pkg)) { alreadyAdded = true; break; }
            if (!alreadyAdded) apps.add(new AppEntry(info.loadLabel(pm).toString(), pkg, info.loadIcon(pm)));
        }
        final Set<String> favorites = getFavorites();
        Collections.sort(apps, new Comparator<AppEntry>() {
            public int compare(AppEntry a, AppEntry b) {
                boolean af = favorites.contains(a.pkg);
                boolean bf = favorites.contains(b.pkg);
                if (af != bf) return af ? -1 : 1;
                return a.label.compareToIgnoreCase(b.label);
            }
        });
    }

    private void renderApps(String filter) {
        if (appGrid == null) return;
        appGrid.removeAllViews();
        String q = filter == null ? "" : filter.trim().toLowerCase(Locale.getDefault());
        int matches = 0;
        LinearLayout row = null;
        Set<String> hidden = getHiddenApps();
        for (AppEntry app : apps) {
            if (hidden.contains(app.pkg)) continue;
            if (favoritesOnly && !getFavorites().contains(app.pkg)) continue;
            if (!app.label.toLowerCase(Locale.getDefault()).contains(q)) continue;
            if (matches % columns == 0) {
                row = new LinearLayout(this);
                row.setGravity(Gravity.TOP | Gravity.LEFT);
                row.setPadding(0, dp(3), 0, dp(3));
                appGrid.addView(row, new LinearLayout.LayoutParams(-1, dp(tvMode ? 132 : 106)));
            }
            if (q.isEmpty()) {
                row.addView(appCell(app), new LinearLayout.LayoutParams(0, dp(tvMode ? 124 : 102), 1));
            } else {
                row.addView(appCell(app), new LinearLayout.LayoutParams(dp(tvMode ? 112 : 84), dp(tvMode ? 124 : 102)));
            }
            matches++;
        }
        if (matches == 0) {
            TextView empty = text(q.isEmpty() ? "No apps found on this device." :
                    "No apps match “" + filter + "”.", 13, muted, false);
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
        cell.setContentDescription(app.label);
        cell.setBackground(shape(Color.TRANSPARENT, 15));
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(app.icon);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        int size = tvMode ? 56 : iconSizeDp;
        GradientDrawable circularMask = new GradientDrawable();
        circularMask.setShape(GradientDrawable.OVAL);
        circularMask.setColor(Color.TRANSPARENT);
        icon.setBackground(circularMask);
        icon.setClipToOutline(true);
        cell.addView(icon, new LinearLayout.LayoutParams(dp(size), dp(size)));
        String displayLabel = getFavorites().contains(app.pkg) ? "★ " + app.label : app.label;
        TextView label = text(displayLabel, tvMode ? 12 : 10, Color.rgb(232, 237, 231), false);
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(2);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(32));
        lp.topMargin = dp(5);
        cell.addView(label, lp);
        cell.setOnLongClickListener(v -> { showAppMenu(app); return true; });
        cell.setOnFocusChangeListener((v, focused) -> {
            v.setBackground(shape(focused ? Color.rgb(57, 72, 53) : Color.TRANSPARENT, 15));
            if (focused && tvMode) v.setElevation(dp(4));
            else v.setElevation(0);
        });
        cell.setOnClickListener(v -> launchApp(app, false));
        return cell;
    }

    private Set<String> getFavorites() {
        Set<String> stored = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getStringSet(KEY_FAVORITES, null);
        return stored == null ? new HashSet<String>() : new HashSet<String>(stored);
    }

    private Set<String> getHiddenApps() {
        Set<String> stored = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getStringSet(KEY_HIDDEN_APPS, null);
        return stored == null ? new HashSet<String>() : new HashSet<String>(stored);
    }

    private void setAppHidden(String packageName, boolean hidden) {
        Set<String> hiddenApps = getHiddenApps();
        if (hidden) hiddenApps.add(packageName);
        else hiddenApps.remove(packageName);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putStringSet(KEY_HIDDEN_APPS, hiddenApps).apply();
        renderApps(search == null ? "" : search.getText().toString());
    }

    private void showHiddenAppsSettings() {
        final String[] labels = new String[apps.size()];
        final boolean[] checked = new boolean[apps.size()];
        final Set<String> hidden = getHiddenApps();
        for (int i = 0; i < apps.size(); i++) {
            labels[i] = apps.get(i).label;
            checked[i] = hidden.contains(apps.get(i).pkg);
        }
        new AlertDialog.Builder(this)
                .setTitle("Hide apps")
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> {
                    String packageName = apps.get(which).pkg;
                    Set<String> current = getHiddenApps();
                    if (isChecked) current.add(packageName);
                    else current.remove(packageName);
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                            .putStringSet(KEY_HIDDEN_APPS, current).apply();
                })
                .setPositiveButton("Done", (dialog, which) ->
                        renderApps(search == null ? "" : search.getText().toString()))
                .setNeutralButton("Show all", (dialog, which) -> {
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                            .putStringSet(KEY_HIDDEN_APPS, new HashSet<String>()).apply();
                    renderApps(search == null ? "" : search.getText().toString());
                })
                .show();
    }

    private void toggleFavorite(String packageName) {
        Set<String> favorites = getFavorites();
        boolean added;
        if (favorites.contains(packageName)) {
            favorites.remove(packageName);
            added = false;
        } else {
            favorites.add(packageName);
            added = true;
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putStringSet(KEY_FAVORITES, favorites).apply();
        renderApps(search == null ? "" : search.getText().toString());
        Toast.makeText(this, added ? "Added to favorites" : "Removed from favorites",
                Toast.LENGTH_SHORT).show();
    }

    private void showAppMenu(final AppEntry app) {
        final boolean isFavorite = getFavorites().contains(app.pkg);
        final boolean isHidden = getHiddenApps().contains(app.pkg);
        final String[] actions = {"Open app", "App info", "Uninstall app", "Open in split screen",
                isFavorite ? "Remove from favorites" : "Add to favorites",
                isHidden ? "Show app in launcher" : "Hide app from launcher"};
        new AlertDialog.Builder(this)
                .setTitle(app.label)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) launchApp(app, false);
                    else if (which == 1) openAppInfo(app.pkg);
                    else if (which == 2) confirmUninstall(app);
                    else if (which == 3) launchApp(app, true);
                    else if (which == 4) toggleFavorite(app.pkg);
                    else if (which == 5) {
                        setAppHidden(app.pkg, !getHiddenApps().contains(app.pkg));
                        Toast.makeText(this, getHiddenApps().contains(app.pkg)
                                ? app.label + " hidden from Luma"
                                : app.label + " visible in Luma", Toast.LENGTH_SHORT).show();
                    }
                }).show();
    }

    private void confirmUninstall(final AppEntry app) {
        new AlertDialog.Builder(this)
                .setTitle("Uninstall " + app.label + "?")
                .setMessage("Android will show its system confirmation. Some preinstalled apps cannot be removed.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue", (dialog, which) -> startUninstall(app.pkg))
                .show();
    }

    private void startUninstall(String packageName) {
        Uri packageUri = Uri.parse("package:" + packageName);
        pendingPackageRefresh = true;

        // ACTION_DELETE is more compatible with older Android TV firmware.
        Intent delete = new Intent(Intent.ACTION_DELETE, packageUri);
        try {
            startActivity(delete);
            return;
        } catch (Exception firstError) {
            // Try the newer uninstall action if this firmware has a handler.
        }

        Intent uninstall = new Intent(Intent.ACTION_UNINSTALL_PACKAGE);
        uninstall.setData(packageUri);
        uninstall.putExtra(Intent.EXTRA_RETURN_RESULT, true);
        try {
            startActivity(uninstall);
            return;
        } catch (Exception secondError) {
            pendingPackageRefresh = false;
            openAppInfo(packageName);
            Toast.makeText(this,
                    "Layar uninstall tidak tersedia. Di App info, pilih Uninstall.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void refreshAppsAfterPackageChange() {
        if (appGrid == null || !pendingPackageRefresh) return;
        appGrid.post(() -> {
            if (!pendingPackageRefresh || isFinishing()) return;
            pendingPackageRefresh = false;
            String filter = search == null ? "" : search.getText().toString();
            apps.clear();
            loadApps();
            renderApps(filter);
        });
    }

    private void registerPackageReceiver() {
        if (packageReceiverRegistered) return;
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(packageReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(packageReceiver, filter);
            }
            packageReceiverRegistered = true;
        } catch (Exception ignored) {}
    }

    private void unregisterPackageReceiver() {
        if (!packageReceiverRegistered) return;
        try { unregisterReceiver(packageReceiver); } catch (Exception ignored) {}
        packageReceiverRegistered = false;
    }

    private void openAppInfo(String packageName) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + packageName));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "App info tidak tersedia di perangkat ini.", Toast.LENGTH_LONG).show();
        }
    }

    private void launchApp(AppEntry app, boolean split) {
        Intent intent = getPackageManager().getLaunchIntentForPackage(app.pkg);
        if (intent == null) {
            Toast.makeText(this, "App cannot be opened", Toast.LENGTH_SHORT).show();
            return;
        }
        if (split) {
            if (Build.VERSION.SDK_INT >= 24) {
                intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
                        | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            } else {
                Toast.makeText(this, "Split screen requires Android 7.0 or newer", Toast.LENGTH_LONG).show();
                return;
            }
        }
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, split
                    ? "Split screen is not supported by this device or app"
                    : "Unable to open app", Toast.LENGTH_LONG).show();
        }
    }

    private void openDefaultLauncherSettings() {
        Intent[] candidates;
        if (Build.VERSION.SDK_INT >= 24) {
            candidates = new Intent[] {
                    new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
                    new Intent(Settings.ACTION_HOME_SETTINGS),
                    new Intent(Settings.ACTION_SETTINGS)
            };
        } else {
            candidates = new Intent[] {
                    new Intent(Settings.ACTION_HOME_SETTINGS),
                    new Intent(Settings.ACTION_SETTINGS)
            };
        }
        for (Intent intent : candidates) {
            try {
                if (intent.resolveActivity(getPackageManager()) != null) {
                    startActivity(intent);
                    return;
                }
            } catch (Exception ignored) {}
        }
        Toast.makeText(this,
                "Buka Settings perangkat lalu cari Default apps / Home app dan pilih Luma Launcher.",
                Toast.LENGTH_LONG).show();
    }

    private void showLauncherSettings() {
        final String[] options = {
                "Set as default launcher",
                "Appearance & icon size",
                "Refresh app list",
                "Display settings",
                "Wi-Fi / Internet",
                "Battery settings",
                "Manage installed apps",
                "About Luma",
                "Startup & Home button",
                "Hide / show apps"
        };
        new AlertDialog.Builder(this)
                .setTitle("Luma settings")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) openDefaultLauncherSettings();
                    else if (which == 1) showAppearanceSettings();
                    else if (which == 2) {
                        apps.clear();
                        loadApps();
                        renderApps(search == null ? "" : search.getText().toString());
                        Toast.makeText(this, "App list refreshed", Toast.LENGTH_SHORT).show();
                    } else if (which == 3) {
                        try { startActivity(new Intent(Settings.ACTION_DISPLAY_SETTINGS)); }
                        catch (Exception e) { Toast.makeText(this, "Display settings unavailable", Toast.LENGTH_SHORT).show(); }
                    } else if (which == 4) {
                        openSystemSettings(new Intent(Settings.ACTION_WIFI_SETTINGS),
                                "Wi-Fi settings unavailable");
                    } else if (which == 5) {
                        openSystemSettings(new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS),
                                "Battery settings unavailable");
                    } else if (which == 6) {
                        openSystemSettings(new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS),
                                "App management settings unavailable");
                    } else if (which == 7) {
                        new AlertDialog.Builder(this).setTitle("Luma Launcher")
                                .setMessage("Lightweight, minimal home screen. Long-press an app to add it to favorites, open App info, uninstall, or try split screen. System shortcuts open Android settings; floating windows depend on your device firmware.")
                                .setPositiveButton("OK", null).show();
                    } else if (which == 8) {
                        showStartupSettings();
                    } else if (which == 9) {
                        showHiddenAppsSettings();
                    }
                }).show();
    }

    private void showStartupSettings() {
        boolean autoStart = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(KEY_AUTO_START, true);
        final String autoLabel = autoStart
                ? "Turn off launch after TV startup"
                : "Turn on launch after TV startup";
        final String[] options = {
                autoLabel,
                "Configure Home button / default launcher",
                "How the Home button works"
        };
        new AlertDialog.Builder(this)
                .setTitle("Startup & Home button")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        boolean enabled = !getSharedPreferences(PREFS, MODE_PRIVATE)
                                .getBoolean(KEY_AUTO_START, true);
                        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                                .putBoolean(KEY_AUTO_START, enabled).apply();
                        Toast.makeText(this, enabled
                                ? "Auto-start after TV startup enabled"
                                : "Auto-start after TV startup disabled",
                                Toast.LENGTH_LONG).show();
                    } else if (which == 1) {
                        openDefaultLauncherSettings();
                    } else if (which == 2) {
                        new AlertDialog.Builder(this)
                                .setTitle("Home button")
                                .setMessage("The Home button is controlled by the TV system, not by ordinary apps. Select Luma Launcher as the default Home app in system settings; then pressing the house button should return to Luma. The startup option is a fallback for TV firmware that does not reopen the selected Home app after a restart.")
                                .setPositiveButton("OK", null).show();
                    }
                }).show();
    }

    private void openSystemSettings(Intent intent, String errorMessage) {
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception ignored) {}
        }
    }


    private void showAppDrawer() {
        favoritesOnly = false;
        LinearLayout root = column();
        root.setPadding(dp(22), dp(18), dp(22), dp(12));
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(26, 37, 28), bg, Color.rgb(10, 13, 11)}));
        if (tvMode) root.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        setContentView(root);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = actionButton("← Home", false);
        header.addView(back, new LinearLayout.LayoutParams(-2, dp(42)));
        back.setOnClickListener(v -> showHome());
        TextView heading = text("App drawer", 24, Color.WHITE, true);
        heading.setPadding(dp(16), 0, 0, 0);
        header.addView(heading, new LinearLayout.LayoutParams(0, dp(48), 1));
        root.addView(header);

        EditText drawerSearch = new EditText(this);
        drawerSearch.setSingleLine(true);
        drawerSearch.setFocusable(true);
        drawerSearch.setFocusableInTouchMode(!tvMode);
        drawerSearch.setTextSize(14);
        drawerSearch.setTextColor(Color.WHITE);
        drawerSearch.setHintTextColor(muted);
        drawerSearch.setHint(tvMode ? "Search apps with remote" : "Search installed apps");
        drawerSearch.setPadding(dp(16), 0, dp(16), 0);
        drawerSearch.setBackground(shape(panel, 17));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, dp(50));
        searchParams.topMargin = dp(12);
        root.addView(drawerSearch, searchParams);

        LinearLayout filters = new LinearLayout(this);
        filters.setGravity(Gravity.CENTER_VERTICAL);
        filters.setPadding(0, dp(10), 0, dp(8));
        TextView all = actionButton("All apps", true);
        TextView fav = actionButton("Favorites", false);
        filters.addView(all, new LinearLayout.LayoutParams(-2, dp(38)));
        LinearLayout.LayoutParams favParams = new LinearLayout.LayoutParams(-2, dp(38));
        favParams.leftMargin = dp(8);
        filters.addView(fav, favParams);
        root.addView(filters);
        all.setOnClickListener(v -> {
            favoritesOnly = false;
            all.setBackground(shape(accent, 16));
            fav.setBackground(shape(panelRaised, 16));
            renderApps(drawerSearch.getText().toString());
        });
        fav.setOnClickListener(v -> {
            favoritesOnly = true;
            fav.setBackground(shape(accent, 16));
            all.setBackground(shape(panelRaised, 16));
            renderApps(drawerSearch.getText().toString());
        });

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        appGrid = column();
        scroll.addView(appGrid);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        drawerSearch.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderApps(s.toString());
            }
            public void afterTextChanged(Editable e) {}
        });
        renderApps("");
        if (tvMode) drawerSearch.post(() -> {
            if (appGrid != null) requestInitialTvFocus();
        });
    }

    private void chooseHeroPhoto() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        try { startActivityForResult(intent, 731); }
        catch (Exception e) { Toast.makeText(this, "Photo picker unavailable", Toast.LENGTH_LONG).show(); }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != 731 || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
        catch (Exception ignored) {}
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_HERO_URI, uri.toString()).apply();
        loadHeroPhoto();
    }

    private void loadHeroPhoto() {
        if (heroPhoto == null) return;
        String value = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_HERO_URI, null);
        if (value == null || value.isEmpty()) {
            heroPhoto.setImageDrawable(null);
            heroPhoto.setBackground(shape(panel, 20));
            return;
        }
        try {
            heroPhoto.setImageURI(null);
            heroPhoto.setImageURI(Uri.parse(value));
            heroPhoto.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int fade = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_HERO_FADE, 45);
            heroPhoto.setImageAlpha(255);
            heroPhoto.setForeground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{Color.argb(5, 13, 17, 15), Color.argb(fade * 255 / 100, 13, 17, 15)}));
        } catch (Exception e) { heroPhoto.setImageDrawable(null); }
    }

    private void showHeroPhotoOptions() {
        final String[] options = {"Choose / change photo", "Fade: subtle (20%)", "Fade: smooth (45%)", "Fade: strong (70%)", "Remove photo"};
        new AlertDialog.Builder(this).setTitle("Home banner").setItems(options, (d, which) -> {
            if (which == 0) { chooseHeroPhoto(); return; }
            if (which == 4) getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(KEY_HERO_URI).apply();
            else {
                int fade = which == 1 ? 20 : which == 2 ? 45 : 70;
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_HERO_FADE, fade).apply();
            }
            loadHeroPhoto();
        }).show();
    }

    private void showAppearanceSettings() {
        final String[] options = {"Accent: Lime", "Accent: Blue", "Accent: Purple", "Accent: Orange", "Icons: Compact", "Icons: Comfortable", "Icons: Large", "Grid: 4 columns", "Grid: 5 columns", "Grid: 6 columns", "Grid: 7 columns", "Run setup again"};
        new AlertDialog.Builder(this)
                .setTitle("Appearance")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) saveAccent(Color.rgb(190, 244, 111));
                    else if (which == 1) saveAccent(Color.rgb(130, 190, 255));
                    else if (which == 2) saveAccent(Color.rgb(190, 140, 255));
                    else if (which == 3) saveAccent(Color.rgb(255, 171, 105));
                    else if (which == 4) saveIconSize(36);
                    else if (which == 5) saveIconSize(44);
                    else if (which == 6) saveIconSize(52);
                    else if (which >= 7 && which <= 10) {
                        columns = which - 3;
                        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_COLUMNS, columns).apply();
                    } else {
                        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_SETUP, false).apply();
                        showSetupScreen();
                        return;
                    }
                    showHome();
                }).show();
    }

    private void saveAccent(int color) {
        accent = color;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_ACCENT, color).apply();
    }

    private void saveIconSize(int size) {
        iconSizeDp = size;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_ICON_SIZE, size).apply();
    }

    @Override public void onResume() {
        super.onResume();
        registerPackageReceiver();
        if (homeClock != null) {
            clockHandler.removeCallbacks(clockUpdater);
            clockUpdater.run();
        }
        // Refresh only when a package change is pending, not on every resume.
        refreshAppsAfterPackageChange();
    }

    @Override public void onPause() {
        clockHandler.removeCallbacks(clockUpdater);
        unregisterPackageReceiver();
        super.onPause();
    }


    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (tvMode && event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
            int key = event.getKeyCode();
            View current = getCurrentFocus();
            // Let Android dispatch OK/Enter to the focused View. Calling performClick()
            // directly on ACTION_DOWN bypasses the framework's long-press detection,
            // causing a held remote OK button to launch apps immediately.
            if (key == android.view.KeyEvent.KEYCODE_DPAD_UP
                    || key == android.view.KeyEvent.KEYCODE_DPAD_DOWN
                    || key == android.view.KeyEvent.KEYCODE_DPAD_LEFT
                    || key == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (current == null || !current.isFocused()) {
                    View target = findViewById(android.R.id.content);
                    if (target instanceof ViewGroup) {
                        View focusable = findFirstFocusable((ViewGroup) target);
                        if (focusable != null) {
                            focusable.requestFocus();
                            return true;
                        }
                    }
                }
                if (current != null) {
                    int direction = key == android.view.KeyEvent.KEYCODE_DPAD_UP
                            ? View.FOCUS_UP : key == android.view.KeyEvent.KEYCODE_DPAD_DOWN
                            ? View.FOCUS_DOWN : key == android.view.KeyEvent.KEYCODE_DPAD_LEFT
                            ? View.FOCUS_LEFT : View.FOCUS_RIGHT;
                    View next = current.focusSearch(direction);
                    if (next != null && next.requestFocus()) return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private View findFirstFocusable(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.isFocusable() && child.getVisibility() == View.VISIBLE) return child;
            if (child instanceof ViewGroup) {
                View nested = findFirstFocusable((ViewGroup) child);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    @Override public void onBackPressed() {
        if (search != null && search.length() > 0) {
            search.setText("");
            ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(search.getWindowToken(), 0);
        } else {
            super.onBackPressed();
        }
    }
}
