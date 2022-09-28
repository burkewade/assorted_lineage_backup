/*
 * Copyright (C) 2017-2019 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lineageos.jelly;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.DownloadManager;
import android.app.assist.AssistContent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import androidx.core.graphics.drawable.IconCompat;
import android.graphics.drawable.TransitionDrawable;
import android.net.Uri;
import android.net.http.HttpResponseCache;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.os.ResultReceiver;
import android.preference.PreferenceManager;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.ValueCallback;
import android.widget.AutoCompleteTextView;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.view.menu.MenuPopupHelper;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.snackbar.Snackbar;

import org.lineageos.jelly.favorite.FavoriteActivity;
import org.lineageos.jelly.favorite.FavoriteProvider;
import org.lineageos.jelly.history.HistoryActivity;
import org.lineageos.jelly.suggestions.SuggestionsAdapter;
import org.lineageos.jelly.ui.SearchBarController;
import org.lineageos.jelly.ui.UrlBarController;
import org.lineageos.jelly.utils.AdBlocker;
import org.lineageos.jelly.utils.IntentUtils;
import org.lineageos.jelly.utils.PrefsUtils;
import org.lineageos.jelly.utils.TabUtils;
import org.lineageos.jelly.utils.UiUtils;
import org.lineageos.jelly.webview.WebViewCompat;
import org.lineageos.jelly.webview.WebViewExt;
import org.lineageos.jelly.webview.WebViewExtActivity;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.List;

public class MainActivity extends WebViewExtActivity implements
        SearchBarController.OnCancelListener {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String PROVIDER = "com.oF2pks.browser4.fileprovider";
    private static final String STATE_KEY_THEME_COLOR = "theme_color";
    private static final int STORAGE_PERM_REQ = 423;
    private static final int LOCATION_PERM_REQ = 424;
    private static final int ALWAYS_DEFAULT_TO_INCOGNITO = 1;
    private static final int EXTERNAL_DEFAULT_TO_INCOGNITO = 2;
    private static final int MHT_ONACTIVITYRESULT = 7890;
    private static final int INPUT_FILE_REQUEST_CODE = 1;
    private ValueCallback<Uri[]> mFilePathCallback;

    private CoordinatorLayout mCoordinator;
    private AppBarLayout mAppBar;
    private FrameLayout mWebViewContainer;
    private WebViewExt mWebView;
    private final BroadcastReceiver mUrlResolvedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Intent resolvedIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (TextUtils.equals(getPackageName(), resolvedIntent.getPackage())) {
                String url = intent.getStringExtra(IntentUtils.EXTRA_URL);
                mWebView.loadUrl(url);
            } else {
                startActivity(resolvedIntent);
            }
            ResultReceiver receiver = intent.getParcelableExtra(Intent.EXTRA_RESULT_RECEIVER);
            receiver.send(RESULT_CANCELED, new Bundle());
        }
    };
    private ProgressBar mLoadingProgress;
    private SearchBarController mSearchController;
    private RelativeLayout mToolbarSearchBar;
    private boolean mHasThemeColorSupport;
    private Drawable mLastActionBarDrawable;
    private int mThemeColor;
    private String mWaitingDownloadUrl;
    private Bitmap mUrlIcon;
    private final BroadcastReceiver mUiModeChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            setUiMode();
        }
    };
    private boolean mIncognito;

    private View mCustomView;
    private WebChromeClient.CustomViewCallback mFullScreenCallback;

    private boolean mSearchActive = false;

    @TargetApi(31)
    @Override
    public void onProvideAssistContent(AssistContent outContent) {
        super.onProvideAssistContent(outContent);

        outContent.setWebUri(Uri.parse(mWebView.getUrl()));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mCoordinator = findViewById(R.id.coordinator_layout);
        mAppBar = findViewById(R.id.app_bar_layout);
        mWebViewContainer = findViewById(R.id.web_view_container);
        mLoadingProgress = findViewById(R.id.load_progress);
        mToolbarSearchBar = findViewById(R.id.toolbar_search_bar);
        AutoCompleteTextView autoCompleteTextView = findViewById(R.id.url_bar);
        autoCompleteTextView.setAdapter(new SuggestionsAdapter(this));
        autoCompleteTextView.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                UiUtils.hideKeyboard(autoCompleteTextView);

                mWebView.loadUrl(autoCompleteTextView.getText().toString());
                autoCompleteTextView.clearFocus();
                return true;
            }
            return false;
        });
        autoCompleteTextView.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                UiUtils.hideKeyboard(autoCompleteTextView);

                mWebView.loadUrl(autoCompleteTextView.getText().toString());
                autoCompleteTextView.clearFocus();
                return true;
            }
            return false;
        });
        autoCompleteTextView.setOnItemClickListener((adapterView, view, pos, l) -> {
            CharSequence searchString = ((TextView) view.findViewById(R.id.title)).getText();
            String url = searchString.toString();

            UiUtils.hideKeyboard(autoCompleteTextView);

            autoCompleteTextView.clearFocus();
            mWebView.loadUrl(url);
        });

        // Make sure prefs are set before loading them
        PreferenceManager.setDefaultValues(this, R.xml.settings, false);

        Intent intent = getIntent();
        String url = intent.getDataString();
        boolean desktopMode = false;
        switch (PrefsUtils.getIncognitoPolicy(this)) {
            case ALWAYS_DEFAULT_TO_INCOGNITO:
                mIncognito = true;
                break;
            case EXTERNAL_DEFAULT_TO_INCOGNITO:
                mIncognito = !Intent.ACTION_MAIN.equals(intent.getAction());
                break;
            default:
                mIncognito = intent.getBooleanExtra(IntentUtils.EXTRA_INCOGNITO, false);
        }

        // Restore from previous instance
        if (savedInstanceState != null) {
            mIncognito = savedInstanceState.getBoolean(IntentUtils.EXTRA_INCOGNITO, mIncognito);
            if (url == null || url.isEmpty()) {
                url = savedInstanceState.getString(IntentUtils.EXTRA_URL, null);
            }
            desktopMode = savedInstanceState.getBoolean(IntentUtils.EXTRA_DESKTOP_MODE, false);
            mThemeColor = savedInstanceState.getInt(STATE_KEY_THEME_COLOR, 0);
        }

        if (mIncognito  && Build.VERSION.SDK_INT >= 26 ) {
            autoCompleteTextView.setImeOptions(autoCompleteTextView.getImeOptions() |
                    EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        }


        // Listen for local broadcasts
        registerLocalBroadcastListeners();

        setUiMode();

        ImageView incognitoIcon = findViewById(R.id.incognito);
        incognitoIcon.setVisibility(mIncognito ? View.VISIBLE : View.GONE);

        setupMenu();

        UrlBarController urlBarController = new UrlBarController(autoCompleteTextView,
                findViewById(R.id.secure));

        mWebView = findViewById(R.id.web_view);
        mWebView.init(this, urlBarController, mLoadingProgress, mIncognito);
        mWebView.setDesktopMode(desktopMode);
        mWebView.loadUrl(url == null ? PrefsUtils.getHomePage(this) : url);

        AdBlocker.init(this);

        mHasThemeColorSupport = WebViewCompat.isThemeColorSupported(mWebView);

        mSearchController = new SearchBarController(mWebView,
                findViewById(R.id.search_menu_edit),
                findViewById(R.id.search_status),
                findViewById(R.id.search_menu_prev),
                findViewById(R.id.search_menu_next),
                findViewById(R.id.search_menu_cancel),
                this);

        //applyThemeColor(mThemeColor);

        try {
            File httpCacheDir = new File(getCacheDir(), "suggestion_responses");
            long httpCacheSize = 1024 * 1024; // 1 MiB
            HttpResponseCache.install(httpCacheDir, httpCacheSize);
        } catch (IOException e) {
            Log.i(TAG, "HTTP response cache installation failed:" + e);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(mUrlResolvedReceiver, new IntentFilter(IntentUtils.EVENT_URL_RESOLVED));
    }

    @Override
    protected void onStop() {
        CookieManager.getInstance().flush();
        unregisterReceiver(mUrlResolvedReceiver);
        HttpResponseCache cache = HttpResponseCache.getInstalled();
        if (cache != null) {
            cache.flush();
        }
        super.onStop();
    }

    @Override
    public void onPause() {
        mWebView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mWebView.onResume();
        CookieManager.getInstance()
                .setAcceptCookie(!mWebView.isIncognito() && PrefsUtils.getCookie(this));
        if (PrefsUtils.getLookLock(this)) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    @Override
    protected void onDestroy() {
        // Unregister the local broadcast receiver because the activity is being trashed
        unregisterLocalBroadcastsListeners();

        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (mSearchActive) {
            mSearchController.onCancel();
        } else if (mCustomView != null) {
            onHideCustomView();
        } else if (mWebView.canGoBack()) {
            mWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        switch (requestCode) {
            case LOCATION_PERM_REQ:
                if (hasLocationPermission()) {
                    mWebView.reload();
                }
                break;
            case STORAGE_PERM_REQ:
                if (hasStoragePermission() && mWaitingDownloadUrl != null) {
                    downloadFileAsk(mWaitingDownloadUrl, null, null);
                } else {
                    if (shouldShowRequestPermissionRationale(
                            Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                        new AlertDialog.Builder(this)
                                .setTitle(R.string.permission_error_title)
                                .setMessage(R.string.permission_error_storage)
                                .setCancelable(false)
                                .setPositiveButton(getString(R.string.permission_error_ask_again),
                                        ((dialog, which) -> requestStoragePermission()))
                                .setNegativeButton(getString(R.string.dismiss),
                                        (((dialog, which) -> dialog.dismiss())))
                                .show();
                    } else {
                        Snackbar.make(mCoordinator, getString(R.string.permission_error_forever),
                                Snackbar.LENGTH_LONG).show();
                    }
                }
                break;
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        // Preserve webView status
        outState.putString(IntentUtils.EXTRA_URL, mWebView.getUrl());
        outState.putBoolean(IntentUtils.EXTRA_INCOGNITO, mWebView.isIncognito());
        outState.putBoolean(IntentUtils.EXTRA_DESKTOP_MODE, mWebView.isDesktopMode());
        outState.putInt(STATE_KEY_THEME_COLOR, mThemeColor);
    }

    private void tabsManage(ImageButton menu) {
        ContextThemeWrapper wrapper = new ContextThemeWrapper(this,
                R.style.AppTheme_PopupMenuOverlapAnchor);
        PopupMenu popupMenu = new PopupMenu(wrapper, menu, Gravity.NO_GRAVITY,
                R.attr.actionOverflowMenuStyle, 0);
        popupMenu.inflate(R.menu.menu_kill);
        popupMenu.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.menu_new) {
                TabUtils.openInNewTab(this, null, false);
            } else if (itemId == R.id.menu_incognito) {
                TabUtils.openInNewTab(this, null, true);
            } else if (itemId == R.id.menu_reload) {
                mWebView.reload();
            } else if (itemId == R.id.menu_favorite) {
                startActivity(new Intent(this, FavoriteActivity.class));
            } else if (itemId == R.id.menu_history) {
                startActivity(new Intent(this, HistoryActivity.class));
            } else {
                ActivityManager am = (ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);
                if(am != null) {
                    List<ActivityManager.AppTask> tasks = am.getAppTasks();
                    if (tasks != null && tasks.size() > 0) {
                        if (itemId != R.id.kill_this) {
                            for (int i = 1; i < tasks.size(); i++){
                                tasks.get(i).setExcludeFromRecents(true);
                                tasks.get(i).finishAndRemoveTask();
                            }
                        }
                        if (itemId != R.id.kill_others) {
                            tasks.get(0).setExcludeFromRecents(true);
                            tasks.get(0).finishAndRemoveTask();
                        }
                    }
                }
                if (itemId == R.id.kill_all) {
                    android.os.Process.killProcess(android.os.Process.myPid());
                }
            }
            return true;
        });

        MenuPopupHelper helper = new MenuPopupHelper(wrapper,
                (MenuBuilder) popupMenu.getMenu(), menu);
        //noinspection RestrictedApi
        helper.setForceShowIcon(true);
        //noinspection RestrictedApi
        helper.show();
    }

    private void setupMenu() {
        ImageButton menu = findViewById(R.id.search_menu);
        menu.setOnLongClickListener(v -> {
            tabsManage(menu);
            return true;
        });
        menu.setOnClickListener(v -> {
            boolean isDesktop = mWebView.isDesktopMode();
            ContextThemeWrapper wrapper = new ContextThemeWrapper(this,
                    R.style.AppTheme_PopupMenuOverlapAnchor);

            PopupMenu popupMenu = new PopupMenu(wrapper, menu, Gravity.NO_GRAVITY,
                    R.attr.actionOverflowMenuStyle, 0);
            popupMenu.inflate(R.menu.menu_main);
            if (mWebView.getLastLoadedUrl().endsWith(".xml")
                    && mWebView.getLastLoadedUrl().startsWith("file:///")){
                popupMenu.getMenu().findItem((R.id.save_mht)).setEnabled(false);
            } else popupMenu.getMenu().findItem((R.id.save_mht)).setEnabled(true);

            MenuItem desktopMode = popupMenu.getMenu().findItem(R.id.desktop_mode);
            desktopMode.setTitle(getString(isDesktop ?
                    R.string.menu_mobile_mode : R.string.menu_desktop_mode));
            desktopMode.setIcon(ContextCompat.getDrawable(this, isDesktop ?
                    R.drawable.ic_mobile : R.drawable.ic_desktop));

            popupMenu.setOnMenuItemClickListener(item -> {
                int itemId = item.getItemId();

                if (itemId == R.id.menu_reload) {
                    mWebView.reload();
                } else if (itemId == R.id.menu_add_favorite) {
                    setAsFavorite(mWebView.getTitle(), mWebView.getUrl());
                } else if (itemId == R.id.menu_share) {// Delay a bit to allow popup menu hide animation to play
                    new Handler().postDelayed(() -> shareUrl(mWebView.getUrl()), 300);
                } else if (itemId == R.id.menu_search) {// Run the search setup
                    showSearch();
                } else if (itemId == R.id.menu_shortcut) {
                    addShortcut(mWebView.getTitle(), mWebView.getUrl());
                } else if (itemId == R.id.menu_print) {
                        try {
                            finish();
                            // clearing app data
                            //Runtime runtime = Runtime.getRuntime();
                            //runtime.exec("pm clear com.oF2pks.browser4");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                } else if (itemId == R.id.save_mht) {
                    if (Build.VERSION.SDK_INT < 29) {
                        mWebView.saveWebArchive(pathSaveWebArchive() + nameSaveWebArchive());
                    } else {
                        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("text/mht");
                        intent.putExtra(Intent.EXTRA_TITLE, nameSaveWebArchive());
                        startActivityForResult(intent, MHT_ONACTIVITYRESULT);

                    }
                    addShortcut("\u2707" + mWebView.getTitle(), "file:///" + pathSaveWebArchive() + nameSaveWebArchive());
                    setAsFavorite("\u2707" + mWebView.getTitle(), pathSaveWebArchive() + nameSaveWebArchive());
                    Toast.makeText(this, "\u2707" + getExternalFilesDir(null), Toast.LENGTH_LONG).show();
                } else if (itemId == R.id.desktop_mode) {
                    mWebView.setDesktopMode(!isDesktop);
                    desktopMode.setTitle(getString(isDesktop ?
                            R.string.menu_desktop_mode : R.string.menu_mobile_mode));
                    desktopMode.setIcon(ContextCompat.getDrawable(this, isDesktop ?
                            R.drawable.ic_desktop : R.drawable.ic_mobile));
                } else if (itemId == R.id.menu_history) {
                    startActivity(new Intent(this, HistoryActivity.class));
                } else if (itemId == R.id.menu_settings) {
                    startActivity(new Intent(this, SettingsActivity.class));
                }
                return true;
            });

            //noinspection RestrictedApi
            MenuPopupHelper helper = new MenuPopupHelper(wrapper,
                    (MenuBuilder) popupMenu.getMenu(), menu);
            //noinspection RestrictedApi
            helper.setForceShowIcon(true);
            //noinspection RestrictedApi
            helper.show();
        });
    }

    private void showSearch() {
        mToolbarSearchBar.setVisibility(View.GONE);
        findViewById(R.id.toolbar_search_page).setVisibility(View.VISIBLE);
        mSearchController.onShow();
        mSearchActive = true;
    }

    @Override
    public void onCancelSearch() {
        findViewById(R.id.toolbar_search_page).setVisibility(View.GONE);
        mToolbarSearchBar.setVisibility(View.VISIBLE);
        mSearchActive = false;
    }

    private void shareUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_TEXT, url);

        if (PrefsUtils.getAdvancedShare(this) && url.equals(mWebView.getUrl())) {
            File file = new File(getCacheDir(), System.currentTimeMillis() + ".png");
            try (FileOutputStream out = new FileOutputStream(file)) {
                Bitmap bm = mWebView.getSnap();
                if (bm == null) {
                    return;
                }
                bm.compress(Bitmap.CompressFormat.PNG, 70, out);
                out.flush();
                out.close();
                intent.putExtra(Intent.EXTRA_STREAM,
                        FileProvider.getUriForFile(this, PROVIDER, file));
                intent.setType("image/png");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
            }
        } else {
            intent.setType("text/plain");
        }

        startActivity(Intent.createChooser(intent, getString(R.string.share_title)));
    }

    private void setAsFavorite(String title, String url) {
        boolean hasValidIcon = mUrlIcon != null && !mUrlIcon.isRecycled();
        int color = hasValidIcon ? UiUtils.getColor(mUrlIcon, false) : Color.TRANSPARENT;
        if (color == Color.TRANSPARENT) {
            color = ContextCompat.getColor(this, R.color.colorAccent);
        }
        new SetAsFavoriteTask(getContentResolver(), title, url, color, mCoordinator).execute();
    }

    public void downloadFileAsk(String url, String contentDisposition, String mimeType) {
        String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);

        if (!hasStoragePermission()) {
            mWaitingDownloadUrl = url;
            requestStoragePermission();
            return;
        }
        mWaitingDownloadUrl = null;

        new AlertDialog.Builder(this)
                .setTitle(R.string.download_title)
                .setMessage(getString(R.string.download_message, fileName))
                .setPositiveButton(getString(R.string.download_positive),
                        (dialog, which) -> fetchFile(url, fileName))
                .setNegativeButton(getString(R.string.dismiss),
                        ((dialog, which) -> dialog.dismiss()))
                .show();
    }

    private void fetchFile(String url, String fileName) {
        DownloadManager.Request request;

        try {
            request = new DownloadManager.Request(Uri.parse(url));
        } catch (IllegalArgumentException e) {
            Snackbar.make(mCoordinator, "inter~Active(?) download miss ?",
                    Snackbar.LENGTH_LONG).show();
            Log.e(TAG, "Cannot download non http or https scheme");
            return;
        }

        // Let this downloaded file be scanned by MediaScanner - so that it can
        // show up in Gallery app, for example.
        request.allowScanningByMediaScanner();
        request.setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
        request.setMimeType(MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                MimeTypeMap.getFileExtensionFromUrl(url)));
        getSystemService(DownloadManager.class).enqueue(request);
    }

    public void showSheetMenu(String url, boolean shouldAllowDownload) {
        final BottomSheetDialog sheet = new BottomSheetDialog(this);

        View view = getLayoutInflater().inflate(R.layout.sheet_actions, new LinearLayout(this));
        View tabLayout = view.findViewById(R.id.sheet_new_tab);
        View shareLayout = view.findViewById(R.id.sheet_share);
        View favouriteLayout = view.findViewById(R.id.sheet_favourite);
        View downloadLayout = view.findViewById(R.id.sheet_download);

        tabLayout.setOnClickListener(v -> {
            TabUtils.openInNewTab(this, url, mIncognito);
            sheet.dismiss();
        });
        shareLayout.setOnClickListener(v -> {
            shareUrl(url);
            sheet.dismiss();
        });
        favouriteLayout.setOnClickListener(v -> {
            setAsFavorite(url, url);
            sheet.dismiss();
        });
        if (shouldAllowDownload) {
            downloadLayout.setOnClickListener(v -> {
                downloadFileAsk(url, null, null);
                sheet.dismiss();
            });
            downloadLayout.setVisibility(View.VISIBLE);
        }
        sheet.setContentView(view);
        sheet.show();
    }

    private void requestStoragePermission() {
        String[] permissionArray = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE};
        requestPermissions(permissionArray, STORAGE_PERM_REQ);
    }

    private boolean hasStoragePermission() {
        int result = checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    public void requestLocationPermission() {
        String[] permissionArray = new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
        requestPermissions(permissionArray, LOCATION_PERM_REQ);
    }

    public boolean hasLocationPermission() {
        int result = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    public void onThemeColorSet(int color) {
        if (mHasThemeColorSupport) {
            //applyThemeColor(color);
        }
    }

    private void applyThemeColor(int color) {
        boolean hasValidColor = color != Color.TRANSPARENT;
        mThemeColor = color;
        color = getThemeColorWithFallback();

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            ColorDrawable newDrawable = new ColorDrawable(color);
            if (mLastActionBarDrawable != null) {
                final Drawable[] layers = new Drawable[]{mLastActionBarDrawable, newDrawable};
                final TransitionDrawable transition = new TransitionDrawable(layers);
                transition.setCrossFadeEnabled(true);
                transition.startTransition(200);
                actionBar.setBackgroundDrawable(transition);
            } else {
                actionBar.setBackgroundDrawable(newDrawable);
            }
            mLastActionBarDrawable = newDrawable;
        }

        if (Build.VERSION.SDK_INT < 26) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            //getWindow().setNavigationBarColor(color);
            //getWindow().setStatusBarColor(color);
        }

        int progressColor = hasValidColor
                ? UiUtils.isColorXwhite(color) ? Color.BLACK : Color.WHITE
                : ContextCompat.getColor(this, R.color.colorAccent);
        mLoadingProgress.setProgressTintList(ColorStateList.valueOf(progressColor));
        mLoadingProgress.postInvalidate();

            getWindow().setStatusBarColor(Color.BLACK);
            getWindow().setNavigationBarColor(Color.BLACK);

        setTaskDescription(new ActivityManager.TaskDescription(mWebView.getTitle(),
                mUrlIcon, color));
    }

    private void resetSystemUIColor() {
        int flags = getWindow().getDecorView().getSystemUiVisibility();
        flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26) {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);

        getWindow().setStatusBarColor(Color.BLACK);//getResources().getColor(R.color.colorAccent)
        getWindow().setNavigationBarColor(Color.BLACK);
    }

    private int getThemeColorWithFallback() {
        if (mThemeColor != Color.TRANSPARENT) {
            return mThemeColor;
        }
        return ContextCompat.getColor(this,
                mWebView.isIncognito() ? R.color.colorIncognito : R.color.colorPrimary);
    }

    @Override
    public void onShowCustomView(View view, WebChromeClient.CustomViewCallback callback) {
        if (mCustomView != null) {
            callback.onCustomViewHidden();
            return;
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mCustomView = view;
        mFullScreenCallback = callback;
        setImmersiveMode(true);
        mCustomView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.black));
        addContentView(mCustomView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mAppBar.setVisibility(View.GONE);
        mWebViewContainer.setVisibility(View.GONE);
    }

    @Override
    public void onHideCustomView() {
        if (mCustomView == null) {
            return;
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setImmersiveMode(false);
        mAppBar.setVisibility(View.VISIBLE);
        mWebViewContainer.setVisibility(View.VISIBLE);
        ViewGroup viewGroup = (ViewGroup) mCustomView.getParent();
        viewGroup.removeView(mCustomView);
        mFullScreenCallback.onCustomViewHidden();
        mFullScreenCallback = null;
        mCustomView = null;
    }

    private void addShortcut(String sTitle , String sUrl) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setData(Uri.parse(sUrl));
        intent.setAction(Intent.ACTION_MAIN);

        IconCompat launcherIcon;

        if (mUrlIcon != null) {
            launcherIcon = IconCompat.createWithBitmap(
                    UiUtils.getShortcutIcon(mUrlIcon, getThemeColorWithFallback()));
        } else {
            launcherIcon = IconCompat.createWithResource(this, R.mipmap.ic_launcher);
        }

        //String title = mWebView.getTitle();
        ShortcutInfoCompat shortcutInfo = new ShortcutInfoCompat.Builder(this, sTitle)
                .setShortLabel(sTitle)
                .setIcon(launcherIcon)
                .setIntent(intent)
                .build();

        ShortcutManagerCompat.requestPinShortcut(this, shortcutInfo, null);
    }

    private void setImmersiveMode(boolean enable) {
        int flags = getWindow().getDecorView().getSystemUiVisibility();
        int immersiveModeFlags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        if (enable) {
            flags |= immersiveModeFlags;
        } else {
            flags &= ~immersiveModeFlags;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        setImmersiveMode(hasFocus && mCustomView != null);
    }

    private void registerLocalBroadcastListeners() {
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);

        if (!UiUtils.isTablet(this)) {
            manager.registerReceiver(mUiModeChangeReceiver, new IntentFilter(IntentUtils.EVENT_CHANGE_UI_MODE));
        }
    }

    private void unregisterLocalBroadcastsListeners() {
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);

        if (!UiUtils.isTablet(this)) {
            manager.unregisterReceiver(mUiModeChangeReceiver);
        }
    }

    private void setUiMode() {
        // Now you don't see it
        mCoordinator.setAlpha(0f);
        // Magic happens
        changeUiMode(UiUtils.isReachModeEnabled(this));
        // Now you see it
        mCoordinator.setAlpha(1f);
    }

    private void changeUiMode(boolean isReachMode) {
        CoordinatorLayout.LayoutParams appBarParams =
                (CoordinatorLayout.LayoutParams) mAppBar.getLayoutParams();
        CoordinatorLayout.LayoutParams containerParams =
                (CoordinatorLayout.LayoutParams) mWebViewContainer.getLayoutParams();
        RelativeLayout.LayoutParams progressParams =
                (RelativeLayout.LayoutParams) mLoadingProgress.getLayoutParams();
        RelativeLayout.LayoutParams searchBarParams =
                (RelativeLayout.LayoutParams) mToolbarSearchBar.getLayoutParams();

        int margin = (int) UiUtils.getDimenAttr(this, R.style.AppTheme,
                android.R.attr.actionBarSize);

        if (isReachMode) {
            appBarParams.gravity = Gravity.BOTTOM;
            containerParams.setMargins(0, 0, 0, margin);
            progressParams.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            progressParams.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
            searchBarParams.removeRule(RelativeLayout.ABOVE);
            searchBarParams.addRule(RelativeLayout.BELOW, R.id.load_progress);
        } else {
            appBarParams.gravity = Gravity.TOP;
            containerParams.setMargins(0, margin, 0, 0);
            progressParams.removeRule(RelativeLayout.ALIGN_PARENT_TOP);
            progressParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
            searchBarParams.removeRule(RelativeLayout.BELOW);
            searchBarParams.addRule(RelativeLayout.ABOVE, R.id.load_progress);
        }

        mAppBar.setLayoutParams(appBarParams);
        mAppBar.invalidate();
        mWebViewContainer.setLayoutParams(containerParams);
        mWebViewContainer.invalidate();
        mLoadingProgress.setLayoutParams(progressParams);
        mLoadingProgress.invalidate();
        mToolbarSearchBar.setLayoutParams(searchBarParams);
        mToolbarSearchBar.invalidate();

        resetSystemUIColor();

        if (mThemeColor != 0) {
            //applyThemeColor(mThemeColor);
        }
    }

    private static class SetAsFavoriteTask extends AsyncTask<Void, Void, Boolean> {
        private final String title;
        private final String url;
        private final int color;
        private final WeakReference<View> parentView;
        private ContentResolver contentResolver;

        SetAsFavoriteTask(ContentResolver contentResolver, String title, String url,
                          int color, View parentView) {
            this.contentResolver = contentResolver;
            this.title = title;
            this.url = url;
            this.color = color;
            this.parentView = new WeakReference<>(parentView);
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            FavoriteProvider.addOrUpdateItem(contentResolver, title, url, color);
            return true;
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            View view = parentView.get();
            if (view != null) {
                Snackbar.make(view, view.getContext().getString(R.string.favorite_added),
                        Snackbar.LENGTH_LONG).show();
            }
        }
    }

    // Intents used for QuickTiles and other shortcuts
    public static boolean handleShortcuts(Context c, String shortcut) {
        switch (shortcut){
            case "incognito":
                Intent intent = new Intent(c, MainActivity.class);
                intent.putExtra(IntentUtils.EXTRA_INCOGNITO, true);
                c.startActivity(intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                break;
            case "newtab":
                c.startActivity((new Intent(c, MainActivity.class)).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                break;
            case "favorites":
                c.startActivity((new Intent(c, FavoriteActivity.class)).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                break;
            case "history":
                c.startActivity((new Intent(c, HistoryActivity.class)).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                break;
            case "killall":
                TabUtils.killAll(c);
                break;
        }
        return true;
    }


    private String pathSaveWebArchive() {
        return getExternalFilesDir(null).toString()
                + File.separator;
    }
    private String nameSaveWebArchive() {
        return mWebView.getTitle().replaceAll("[^a-zA-Z0-9\\-]", "_")
                + ".mht";
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);
        if (requestCode == MHT_ONACTIVITYRESULT) {
            if (resultCode == RESULT_OK) {
                if (resultData != null) {
                    final Uri uri = resultData.getData();
                    mWebView.saveWebArchive( pathSaveWebArchive() + nameSaveWebArchive()
                            , false, s -> {
                                if (s != null && uri != null) {
                                    try {
                                        InputStream input = new BufferedInputStream((InputStream)(new FileInputStream(new File(s).getAbsoluteFile())));
                                        ParcelFileDescriptor pfd = getBaseContext().getContentResolver().
                                                openFileDescriptor(uri, "w");
                                        FileOutputStream fos = new FileOutputStream(pfd.getFileDescriptor());
                                        byte[] buffer = new byte[1024 * 4];
                                        int n = 0;
                                        while (-1 != (n = input.read(buffer))) {
                                            fos.write(buffer, 0, n);
                                        }

                                        // Let the document provider know you're done by closing the stream.
                                        fos.close();
                                        pfd.close();
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                            });
                    // Perform operations on the document using its URI.
                } else mWebView.saveWebArchive(pathSaveWebArchive() + nameSaveWebArchive());
            } else mWebView.saveWebArchive(pathSaveWebArchive() + nameSaveWebArchive());
        }
        if(requestCode == INPUT_FILE_REQUEST_CODE && mFilePathCallback != null) {
            Uri[] results = null;
            if(resultData != null) {
                // If there is not data, then we may have taken a photo
                String dataString = resultData.getDataString();
                if (dataString != null) {
                    results = new Uri[]{Uri.parse(dataString)};
                }
            }
            mFilePathCallback.onReceiveValue(results);
            mFilePathCallback = null;
        }
    }

    @Override
    public void showFileChooser(ValueCallback<Uri[]> filePathCallback) {
        if(mFilePathCallback != null) {
            mFilePathCallback.onReceiveValue(null);
        }
        mFilePathCallback = filePathCallback;
        Intent contentSelectionIntent = new Intent(Intent.ACTION_GET_CONTENT);
        contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE);
        contentSelectionIntent.setType("*/*");
        Intent chooserIntent = new Intent(Intent.ACTION_CHOOSER);
        chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent);
        startActivityForResult(chooserIntent, INPUT_FILE_REQUEST_CODE);
    }

}
