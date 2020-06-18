package com.example.hmskitstest;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.huawei.cloud.base.auth.DriveCredential;
import com.huawei.cloud.base.http.FileContent;
import com.huawei.cloud.base.util.StringUtils;
import com.huawei.cloud.client.exception.DriveCode;
import com.huawei.cloud.services.drive.Drive;
import com.huawei.cloud.services.drive.DriveScopes;
import com.huawei.cloud.services.drive.model.File;
import com.huawei.hmf.tasks.Task;
import com.huawei.hms.common.ApiException;
import com.huawei.hms.ml.scan.HmsScan;
import com.huawei.hms.support.api.entity.auth.Scope;
import com.huawei.hms.support.hwid.HuaweiIdAuthAPIManager;
import com.huawei.hms.support.hwid.HuaweiIdAuthManager;
import com.huawei.hms.support.hwid.request.HuaweiIdAuthParams;
import com.huawei.hms.support.hwid.request.HuaweiIdAuthParamsHelper;
import com.huawei.hms.support.hwid.result.AuthHuaweiId;
import com.huawei.hms.support.hwid.service.HuaweiIdAuthService;

import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.huawei.hms.support.hwid.request.HuaweiIdAuthParams.DEFAULT_AUTH_REQUEST_PARAM;

public class MainActivity extends AppCompatActivity {

    private final static String TAG = "MainActivity";
    public static final int DEFINED_CODE = 222;
    private static final int REQUEST_CODE_SCAN = 0X01;
    private static int REQUEST_SIGN_IN_LOGIN = 1002;

    private DriveCredential mCredential;
    private String accessToken;
    private String unionId;
    private File directoryCreated;
    private File fileUploaded;


    //directory path
    private String dirPath= Environment.getExternalStorageDirectory().getPath()+"/Notes";

    private static final Map<String, String> MIME_TYPE_MAP = new HashMap<String, String>();

    static {
        MIME_TYPE_MAP.put(".doc", "application/msword");
        MIME_TYPE_MAP.put(".jpg", "image/jpeg");
        MIME_TYPE_MAP.put(".mp3", "audio/x-mpeg");
        MIME_TYPE_MAP.put(".mp4", "video/mp4");
        MIME_TYPE_MAP.put(".pdf", "application/pdf");
        MIME_TYPE_MAP.put(".png", "image/png");
        MIME_TYPE_MAP.put(".txt", "text/plain");
    }


    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onPause() {
        super.onPause();
        //Temporary files and directory folder are deleted.
        java.io.File path = new java.io.File(dirPath);
        if (deleteDir(path)) {
            Log.d("Follow", "File was deleted.");
        }
    }

    public void QrScanBtnClick(View view) {
        ActivityCompat.requestPermissions(this, PERMISSIONS_STORAGE,DEFINED_CODE);
    }

    public void HuaweiDriveBtnClick(View view) {
        driveLogin();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (permissions == null || grantResults == null || grantResults.length < 2 || grantResults[0] != PackageManager.PERMISSION_GRANTED || grantResults[1] != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (requestCode == DEFINED_CODE) {
            // Display the barcode scanning view.
            this.startActivityForResult(new Intent(this, ScanActivity.class), REQUEST_CODE_SCAN);
        }
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        //Receive result
        super.onActivityResult(requestCode, resultCode, data);
        if(resultCode != RESULT_OK)
            Log.d(TAG,"result_ok_false");

        if(data == null)
            Log.d(TAG,"null");

        if (resultCode == RESULT_OK && data != null && requestCode == REQUEST_CODE_SCAN ) {
            HmsScan hmsScan = data.getParcelableExtra(ScanActivity.SCAN_RESULT);
            if (hmsScan != null && !TextUtils.isEmpty(hmsScan.getOriginalValue())) {
                Toast.makeText(MainActivity.this, hmsScan.getOriginalValue(), Toast.LENGTH_SHORT).show();
                showResult(hmsScan.getOriginalValue());
            }
        }else if(data != null && requestCode == REQUEST_SIGN_IN_LOGIN ){
            // Exceptional process for obtaining account information. Obtain and save the related accessToken and unionID using this function.
            Task<AuthHuaweiId> authHuaweiIdTask = HuaweiIdAuthManager.parseAuthResultFromIntent(data);
            if (authHuaweiIdTask.isSuccessful()) {
                AuthHuaweiId huaweiAccount = authHuaweiIdTask.getResult();
                accessToken = huaweiAccount.getAccessToken();
                unionId = huaweiAccount.getUnionId();
                int returnCode = init(unionId, accessToken, refreshAT);
                if (DriveCode.SUCCESS == returnCode) {
                    showTips("login ok");
                } else if (DriveCode.SERVICE_URL_NOT_ENABLED == returnCode) {
                    Log.d(TAG, "onActivityResult, signIn failed: " + "drive is not enabled");
                    showTips("drive is not enabled");
                }else{
                    Log.d(TAG, "onActivityResult, signIn failed: " + "login error");
                    showTips("login error");
                }
            } else {
                Log.d(TAG, "onActivityResult, signIn failed: " + ((ApiException) authHuaweiIdTask.getException()).getStatusCode());
                Toast.makeText(getApplicationContext(), "onActivityResult, signIn failed.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void driveLogin() {

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        List<Scope> scopeList = new ArrayList<>();
        scopeList.add(new Scope(DriveScopes.SCOPE_DRIVE)); // All permissions,  except permissions for the app folder.
        scopeList.add(new Scope(DriveScopes.SCOPE_DRIVE_READONLY)); // Permissions to view file content and metadata.
        scopeList.add(new Scope(DriveScopes.SCOPE_DRIVE_FILE)); // Permissions to view and manage files.
        scopeList.add(new Scope(DriveScopes.SCOPE_DRIVE_METADATA)); // Permissions to view and manage file metadata, excluding file content.
        scopeList.add(new Scope(DriveScopes.SCOPE_DRIVE_METADATA_READONLY)); // Permissions to view file metadata, excluding file content.
        scopeList.add(new Scope(DriveScopes.SCOPE_DRIVE_APPDATA)); // Permissions to upload and store app data.
        scopeList.add(HuaweiIdAuthAPIManager.HUAWEIID_BASE_SCOPE); // Basic account permissions.

        HuaweiIdAuthParams authParams = new HuaweiIdAuthParamsHelper(DEFAULT_AUTH_REQUEST_PARAM)
                .setAccessToken()
                .setIdToken()
                .setScopeList(scopeList)
                .createParams();
        // Call the account API to get account information.
        HuaweiIdAuthService client = HuaweiIdAuthManager.getService(this, authParams);
        startActivityForResult(client.getSignInIntent(), REQUEST_SIGN_IN_LOGIN);
    }

    private DriveCredential.AccessMethod refreshAT = new DriveCredential.AccessMethod() {

        @Override
        public String refreshToken() {
            return accessToken;
        }
    };

    private Drive buildDrive() {
        Drive service = new Drive.Builder(mCredential, this).build();
        return service;
    }

    private String mimeType(java.io.File file) {
        if (file != null && file.exists() && file.getName().contains(".")) {
            String fileName = file.getName();
            String suffix = fileName.substring(fileName.lastIndexOf("."));
            if (MIME_TYPE_MAP.keySet().contains(suffix)) {
                return MIME_TYPE_MAP.get(suffix);
            }
        }
        return "*/*";
    }

    /**
     * Initialize Drive based on the context and HUAWEI ID information including unionId, countrycode, and accessToken.
     * When the current accessToken expires, register an AccessMethod and obtain a new accessToken.
     *
     * @param unionID   unionID from HwID
     * @param at        access token
     * @param refreshAT a callback to refresh AT
     */
    public int init(String unionID, String at, DriveCredential.AccessMethod refreshAT) {
        if (StringUtils.isNullOrEmpty(unionID) || StringUtils.isNullOrEmpty(at)) {
            return DriveCode.ERROR;
        }
        DriveCredential.Builder builder = new DriveCredential.Builder(unionID, refreshAT);
        mCredential = builder.build().setAccessToken(at);
        return DriveCode.SUCCESS;
    }

   /* private boolean uploadFiles(final String filename) {
        final boolean[] uploadControl = {false};
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (accessToken == null) {
                        showTips("Please Login'");
                        return;
                    }

                    java.io.File fileObject = new java.io.File(Environment.getExternalStorageDirectory().getPath()+"/Notes/" + filename+".txt");
                    Log.d("path",fileObject.getPath());
                    if (!fileObject.exists()) {
                        showTips("the input file does not exit.");
                        return;
                    }
                    Drive drive = buildDrive();
                    Map<String, String> appProperties = new HashMap<>();
                    appProperties.put("appProperties", "property");

                    // create test.txt on cloud
                    String mimeType = mimeType(fileObject);

                    File content = new File()
                            .setFileName(fileObject.getName())
                            .setMimeType(mimeType);

                    fileUploaded = drive.files()
                            .create(content, new FileContent(mimeType, fileObject))
                            .setFields("*")
                            .execute();


                    showTips("upload success");
                    Log.d(TAG, "upload success");
                    uploadControl[0] = true;
                } catch (Exception ex) {

                    Log.d(TAG, "upload", ex);
                    showTips("upload error " + ex.toString());
                    uploadControl[0] = false;
                }
            }
        }).start();


        return uploadControl[0];
    }*/

    private void showResult(String textContent) {

        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        LayoutInflater layoutInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        final View dialogView = layoutInflater.inflate(R.layout.scan_result, null);
        dialogBuilder.setView(dialogView);

        final TextView resultText = dialogView.findViewById(R.id.txt_scan_result);
        resultText.setText(textContent);

        final EditText fileName = dialogView.findViewById(R.id.text_filename);
        final Button saveButton = dialogView.findViewById(R.id.result_save);
        final Button cancelButton = dialogView.findViewById(R.id.result_cancel);
        final AlertDialog alertDialog = dialogBuilder.create();

        alertDialog.show();

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                String name = fileName.getText().toString();
                String content = resultText.getText().toString();

                if(name.isEmpty()){
                    fileName.setError("This field mustn't empty.");
                    return;
                }

                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault());
                String currentDateandTime = sdf.format(new Date());

                alertDialog.dismiss();

                if (accessToken == null) {
                     driveLogin();
                }
                String fileNameFull = name +"_"+currentDateandTime;

               boolean fileControl = generateTemporaryTextFile(fileNameFull,content);

                if(fileControl){

                    new TextFileUpload(fileNameFull).execute();

                    // boolean uploadControl = uploadFiles(fileNameFull);
                     /*if(uploadControl){
                         showTips("succes");
                         Log.d("Follow","File was created.:"+fileNameFull);
                     }*/

                }else{
                    showTips("error");
                }

            }
        });
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                alertDialog.dismiss();
            }
        });
    }
    private void showTips(final String toastText) {
        MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), toastText, Toast.LENGTH_LONG).show();
            }
        });
    }

    public boolean generateTemporaryTextFile(String sFileName, String sBody) {
          boolean control = false;

        try {
            java.io.File root = new java.io.File(Environment.getExternalStorageDirectory(), "Notes");

            if (!root.exists()) {
                if(root.mkdirs()){
                    Log.d("succes","succes");
                }else{
                    Log.d("error","error");
                }
            }

            java.io.File file_1 = new java.io.File(root, sFileName+".txt");
            FileWriter writer = new FileWriter(file_1);
            writer.append(sBody);
            writer.flush();
            writer.close();
            control = true;
        } catch (IOException e) {
            Log.d("file_create_err",e.getMessage());
            control = false;
            e.printStackTrace();
        }

        return control;
    }

    public  boolean deleteDir(java.io.File dir) {
        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (int i=0; i<children.length; i++) {
                boolean success = deleteDir(new java.io.File(dir, children[i]));
                if (!success) {
                    return false;
                }
            }
        }

        // The directory is now empty so delete it
        return dir.delete();
    }


    public class TextFileUpload extends AsyncTask<Void,Void,Void> {

        private ProgressDialog progress;
        private String filename;

        public TextFileUpload(String fileName) {
            super();
            this.filename = fileName;
        }


        @Override
        protected void onPreExecute() {
            progress = ProgressDialog.show(MainActivity.this, "The file is uploading.", "Please wait.");
        }

        @Override
        protected Void doInBackground(Void... voids) {



            try {
                if (accessToken == null) {
                    showTips("Please Login'");

                }

                java.io.File fileObject = new java.io.File(Environment.getExternalStorageDirectory().getPath() + "/Notes/" + filename + ".txt");
                Log.d("path", fileObject.getPath());
                if (!fileObject.exists()) {
                    showTips("the input file does not exit.");

                }
                Drive drive = buildDrive();
                Map<String,String> appProperties = new HashMap<>();
                appProperties.put("appProperties", "property");

                // create test.txt on cloud
                String mimeType = mimeType(fileObject);

                File content = new File()
                        .setFileName(fileObject.getName())
                        .setMimeType(mimeType);

                fileUploaded = drive.files()
                        .create(content, new FileContent(mimeType, fileObject))
                        .setFields("*")
                        .execute();


                showTips("upload success");
                Log.d(TAG, "upload success");

            } catch (Exception ex) {
                Log.d(TAG, "upload", ex);
                showTips("upload error " + ex.toString());
            }


            return null;

        }
        protected void onPostExecute(Void result) {

            if (progress.isShowing()) {
                progress.dismiss();
            }

        }
    }
}
