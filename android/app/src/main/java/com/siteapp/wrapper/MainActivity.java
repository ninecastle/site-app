package com.siteapp.wrapper;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.core.content.FileProvider;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

/**
 * site.json 에 적힌 사이트를 주소창 없이 전체 화면으로 띄우는 웹뷰 앱.
 * - 로그인/쿠키 유지
 * - 뒤로가기 = 웹페이지 뒤로가기
 * - 동영상 전체화면 지원
 * - 파일 업로드 지원
 * - 오프라인일 때 다시 시도 화면
 */
public class MainActivity extends Activity {

    private static final int FILE_CHOOSER_REQUEST = 1001;

    private FrameLayout root;
    private WebView webView;

    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private ValueCallback<Uri[]> filePathCallback;
    private File cameraPhotoFile;
    private Uri cameraPhotoUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        root = new FrameLayout(this);
        setupInsets(root);

        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        configureWebView();

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
        } else {
            Uri data = getIntent() != null ? getIntent().getData() : null;
            webView.loadUrl(data != null ? data.toString() : BuildConfig.START_URL);
        }
    }

    /** 상태바·내비게이션바 영역만큼 여백을 줘서 화면이 가려지지 않게 함 (Android 15 edge-to-edge 대응). */
    private void setupInsets(View view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.setOnApplyWindowInsetsListener((v, insets) -> {
                if (customView == null) {
                    android.graphics.Insets bars = insets.getInsets(
                            WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                    v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                } else {
                    v.setPadding(0, 0, 0, 0);
                }
                return WindowInsets.CONSUMED;
            });
        } else {
            view.setFitsSystemWindows(true);
        }
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setAllowFileAccess(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(false); // 새 창 링크도 앱 안에서 열기

        // 구글 로그인은 "앱 속 웹뷰"로 보이면 막히므로(disallowed_useragent),
        // 일반 크롬 모바일과 같은 User-Agent 로 보이게 함.
        // ("; wv" 표시와 "Version/4.0" 토큰은 크롬에는 없는 웹뷰 전용 표시)
        String ua = s.getUserAgentString()
                .replace("; wv)", ")")
                .replaceAll("Version/\\d+(\\.\\d+)* ", "");
        s.setUserAgentString(ua);
        // 웹뷰는 요청마다 앱 패키지 이름을 X-Requested-With 헤더로 붙이는데, 이것도 끔
        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
            WebSettingsCompat.setRequestedWithHeaderOriginAllowList(s, Collections.emptySet());
        }

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if ("http".equals(scheme) || "https".equals(scheme)) {
                    return false; // 웹 주소는 앱 안에서
                }
                openExternal(uri.toString()); // intent://, mailto:, tel: 등은 다른 앱으로
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    showOfflinePage(request.getUrl().toString());
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            // 동영상 전체화면
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                root.addView(view, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                webView.setVisibility(View.GONE);
                setVideoFullscreen(true);
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            }

            @Override
            public void onHideCustomView() {
                exitFullscreen();
            }

            // 파일 업로드
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = callback;
                cameraPhotoUri = null;

                Intent picker = params.createIntent();
                if (params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                    picker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                }

                // 이미지를 받는 칸이면 "카메라로 찍기"도 선택지에 넣기
                Intent camera = acceptsImages(params) ? createCameraIntent() : null;
                Intent toLaunch;
                if (camera != null && params.isCaptureEnabled()) {
                    toLaunch = camera; // capture 속성이 있으면 바로 카메라
                } else if (camera != null) {
                    toLaunch = Intent.createChooser(picker, null);
                    toLaunch.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{camera});
                } else {
                    toLaunch = picker;
                }

                try {
                    startActivityForResult(toLaunch, FILE_CHOOSER_REQUEST);
                } catch (ActivityNotFoundException e) {
                    filePathCallback = null;
                    cameraPhotoUri = null;
                    return false;
                }
                return true;
            }
        });
    }

    private void exitFullscreen() {
        if (customView == null) return;
        root.removeView(customView);
        customView = null;
        webView.setVisibility(View.VISIBLE);
        setVideoFullscreen(false);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
            customViewCallback = null;
        }
    }

    @SuppressWarnings("deprecation")
    private void setVideoFullscreen(boolean on) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                if (on) {
                    c.hide(WindowInsets.Type.systemBars());
                    c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                } else {
                    c.show(WindowInsets.Type.systemBars());
                }
            }
            root.requestApplyInsets();
        } else {
            getWindow().getDecorView().setSystemUiVisibility(on
                    ? View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    : View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    private void openExternal(String url) {
        try {
            Intent intent = url.startsWith("intent:")
                    ? Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                    : new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            // intent:// 의 대체 주소가 있으면 앱 안에서 열기
            if (url.startsWith("intent:")) {
                try {
                    String fallback = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                            .getStringExtra("browser_fallback_url");
                    if (fallback != null) webView.loadUrl(fallback);
                } catch (Exception ignored) { }
            }
        }
    }

    private void showOfflinePage(String failedUrl) {
        String safeUrl = failedUrl.replace("'", "%27").replace("\"", "%22");
        String html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>body{margin:0;height:100vh;display:flex;flex-direction:column;align-items:center;"
                + "justify-content:center;font-family:sans-serif;background:#111;color:#eee;text-align:center}"
                + "button{margin-top:20px;padding:12px 28px;font-size:16px;border:0;border-radius:24px;"
                + "background:#3b82f6;color:#fff}</style></head><body>"
                + "<div style='font-size:20px;font-weight:600'>연결할 수 없어요</div>"
                + "<div style='margin-top:8px;color:#aaa'>인터넷 연결을 확인해 주세요.</div>"
                + "<button onclick=\"location.href='" + safeUrl + "'\">다시 시도</button>"
                + "</body></html>";
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", failedUrl);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST && filePathCallback != null) {
            filePathCallback.onReceiveValue(collectChosenFiles(resultCode, data));
            filePathCallback = null;
            cameraPhotoUri = null;
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /** 앨범에서 고른 파일(여러 장 포함) 또는 방금 카메라로 찍은 사진을 돌려줌. */
    private Uri[] collectChosenFiles(int resultCode, Intent data) {
        if (resultCode != RESULT_OK) return null;

        if (data != null && data.getClipData() != null) {
            ClipData clip = data.getClipData();
            Uri[] uris = new Uri[clip.getItemCount()];
            for (int i = 0; i < uris.length; i++) uris[i] = clip.getItemAt(i).getUri();
            return uris;
        }
        if (data != null && data.getData() != null) {
            return new Uri[]{data.getData()};
        }
        if (cameraPhotoFile != null && cameraPhotoFile.length() > 0 && cameraPhotoUri != null) {
            return new Uri[]{cameraPhotoUri};
        }
        return null;
    }

    private boolean acceptsImages(WebChromeClient.FileChooserParams params) {
        String[] types = params.getAcceptTypes();
        if (types == null || types.length == 0) return true;
        for (String t : types) {
            if (t == null || t.isEmpty() || t.startsWith("image") || t.equals("*/*")) return true;
        }
        return false;
    }

    /** 카메라 앱이 사진을 저장할 임시 파일을 만들고, 카메라 실행 Intent 를 돌려줌. */
    private Intent createCameraIntent() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) == null) return null;
        try {
            File dir = new File(getCacheDir(), "camera");
            if (!dir.exists() && !dir.mkdirs()) return null;
            cameraPhotoFile = File.createTempFile("photo_", ".jpg", dir);
            cameraPhotoUri = FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", cameraPhotoFile);
        } catch (IOException | IllegalArgumentException e) {
            cameraPhotoFile = null;
            cameraPhotoUri = null;
            return null;
        }
        intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraPhotoUri);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        // 일부 기기는 ClipData 로도 권한을 넘겨줘야 저장이 됨
        intent.setClipData(ClipData.newRawUri("", cameraPhotoUri));
        return intent;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.getData() != null) {
            webView.loadUrl(intent.getData().toString());
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (customView != null) {
            exitFullscreen();
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onPause() {
        super.onPause();
        CookieManager.getInstance().flush(); // 로그인 상태 저장
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            root.removeView(webView);
            webView.destroy();
        }
        super.onDestroy();
    }
}
