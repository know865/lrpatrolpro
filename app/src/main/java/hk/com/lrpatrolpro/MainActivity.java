package hk.com.lrpatrolpro;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Base64;
import android.view.KeyEvent;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private static final int PERM_REQ = 1001;
    private static final int FILE_CHOOSER_REQ = 2001;
    private static final int CAMERA_REQ = 2002;
    private static final int MANAGE_STORAGE_REQ = 3001;
    private ValueCallback<Uri[]> filePathCallback;
    private Uri cameraOutputUri;

    /* ✅ 匯出檔案存放的資料夾名稱（手機根目錄下） */
    private static final String APP_FOLDER = "LRPatrolPro";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);

        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        ws.setSupportZoom(false);
        ws.setBuiltInZoomControls(false);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        ws.setAllowFileAccessFromFileURLs(true);
        ws.setAllowUniversalAccessFromFileURLs(true);

        /* ✅ 註冊 JS 介面，讓 HTML 可以呼叫原生儲存 */
        webView.addJavascriptInterface(new AndroidSaver(), "AndroidSaver");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                }
                MainActivity.this.filePathCallback = filePathCallback;

                boolean capture = fileChooserParams.isCaptureEnabled();
                if (capture) {
                    return openCamera();
                } else {
                    try {
                        Intent intent = fileChooserParams.createIntent();
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        startActivityForResult(intent, FILE_CHOOSER_REQ);
                        return true;
                    } catch (Exception e) {
                        MainActivity.this.filePathCallback = null;
                        Toast.makeText(MainActivity.this, "無法開啟檔案選擇器", Toast.LENGTH_SHORT).show();
                        return false;
                    }
                }
            }
        });

        webView.loadUrl("file:///android_asset/index.html");

        requestAllPermissions();

        webView.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
                webView.goBack();
                return true;
            }
            return false;
        });
    }

    /* =========================================================
       ✅ JS 介面：從 HTML 呼叫原生儲存檔案
       目標路徑：/storage/emulated/0/LRPatrolPro/檔名
       ========================================================= */
    public class AndroidSaver {
        @JavascriptInterface
        public void saveFile(String filename, String base64Content, String mimeType) {
            try {
                /* 移除 Base64 可能的 data URL 前綴 */
                String pureBase64 = base64Content;
                if (pureBase64.contains(",")) {
                    pureBase64 = pureBase64.substring(pureBase64.indexOf(",") + 1);
                }
                byte[] data = Base64.decode(pureBase64, Base64.DEFAULT);

                /* 目標資料夾：手機根目錄/LRPatrolPro/ */
                File targetDir = new File(Environment.getExternalStorageDirectory(), APP_FOLDER);
                if (!targetDir.exists()) {
                    targetDir.mkdirs();
                }

                File targetFile = new File(targetDir, filename);
                FileOutputStream fos = new FileOutputStream(targetFile);
                fos.write(data);
                fos.flush();
                fos.close();

                final String path = targetFile.getAbsolutePath();
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "✅ 已儲存：\n" + path, Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                final String err = e.getMessage();
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "❌ 儲存失敗：" + err, Toast.LENGTH_LONG).show());
            }
        }
    }

    /* =========================================================
       權限
       ========================================================= */
    private void requestAllPermissions() {
        List<String> perms = new ArrayList<>();
        perms.add(Manifest.permission.CAMERA);
        perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
        perms.add(Manifest.permission.ACCESS_COARSE_LOCATION);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.READ_MEDIA_IMAGES);
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        List<String> need = new ArrayList<>();
        for (String p : perms) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                need.add(p);
            }
        }
        if (!need.isEmpty()) {
            ActivityCompat.requestPermissions(this, need.toArray(new String[0]), PERM_REQ);
        }

        /* ✅ Android 11+ 需要 MANAGE_EXTERNAL_STORAGE 才能寫入根目錄 */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, MANAGE_STORAGE_REQ);
                } catch (Exception e) {
                    try {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                        startActivityForResult(intent, MANAGE_STORAGE_REQ);
                    } catch (Exception e2) { /* 忽略 */ }
                }
            }
        }
    }

    private boolean openCamera() {
        try {
            File photoDir = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "LRPatrol");
            if (!photoDir.exists()) photoDir.mkdirs();

            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File photoFile = new File(photoDir, "IMG_" + timeStamp + ".jpg");
            cameraOutputUri = FileProvider.getUriForFile(this, "hk.com.lrpatrolpro.fileprovider", photoFile);

            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraOutputUri);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivityForResult(intent, CAMERA_REQ);
                return true;
            } else {
                if (filePathCallback != null) { filePathCallback.onReceiveValue(null); filePathCallback = null; }
                Toast.makeText(this, "此裝置無相機應用程式", Toast.LENGTH_SHORT).show();
                return false;
            }
        } catch (Exception e) {
            if (filePathCallback != null) { filePathCallback.onReceiveValue(null); filePathCallback = null; }
            Toast.makeText(this, "開啟相機失敗：" + e.getMessage(), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERM_REQ) {
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "部分權限未授予，部分功能可能受限", Toast.LENGTH_LONG).show();
                    break;
                }
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == MANAGE_STORAGE_REQ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!Environment.isExternalStorageManager()) {
                    Toast.makeText(this, "未授予檔案管理權限，匯出檔案可能無法儲存", Toast.LENGTH_LONG).show();
                }
            }
            return;
        }

        if (requestCode == CAMERA_REQ) {
            if (filePathCallback != null) {
                Uri[] results = null;
                if (resultCode == Activity.RESULT_OK && cameraOutputUri != null) {
                    results = new Uri[]{ cameraOutputUri };
                }
                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            }
            cameraOutputUri = null;
            return;
        }

        if (requestCode == FILE_CHOOSER_REQ) {
            if (filePathCallback == null) return;
            Uri[] results = null;
            if (resultCode == Activity.RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    results = new Uri[count];
                    for (int i = 0; i < count; i++) {
                        results[i] = data.getClipData().getItemAt(i).getUri();
                    }
                } else if (data.getData() != null) {
                    results = new Uri[]{ data.getData() };
                }
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) { webView.destroy(); webView = null; }
        super.onDestroy();
    }
}
