/*
 * Copyright (C) 2016 Pedro Paulo de Amorim
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.folioreader.android.sample;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.hardware.Sensor;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.provider.Telephony;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.folioreader.Config;
import com.folioreader.FolioReader;
import com.folioreader.model.HighLight;
import com.folioreader.model.locators.ReadLocator;
import com.folioreader.ui.base.OnSaveHighlight;
import com.folioreader.util.AppUtil;
import com.folioreader.util.OnHighlightListener;
import com.folioreader.util.ReadLocatorListener;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HomeActivity extends AppCompatActivity
        implements OnHighlightListener, ReadLocatorListener, FolioReader.OnClosedListener {

    private static final String LOG_TAG = HomeActivity.class.getSimpleName();
    private  FolioReader folioReader;

    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    private static final int REQUEST_CODE_PERMISSIONS = 2;
    private static final int UPLOAD_FILE_REQUEST = 456;
    private String fileName = null;
    private static final File dirPath = Environment.getExternalStorageDirectory();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        if(!isEmulator()) {
//            Toast.makeText(this, "Emulator Detected", Toast.LENGTH_SHORT).show();
//            finish();
//        }
        setContentView(R.layout.activity_home);
        folioReader = FolioReader.get()
                .setOnHighlightListener(this)
                .setReadLocatorListener(this)
                .setOnClosedListener(this);

        getHighlightsAndSave();

        findViewById(R.id.btn_raw).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Config config = AppUtil.getSavedConfig(getApplicationContext());
                if (config == null)
                    config = new Config();
                config.setAllowedDirection(Config.AllowedDirection.VERTICAL_AND_HORIZONTAL);

                folioReader.setConfig(config, true)
                        .openBook(R.raw.accessible_epub_3);
            }
        });

        findViewById(R.id.btn_assest).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                ReadLocator readLocator = getLastReadLocator();

                Config config = AppUtil.getSavedConfig(getApplicationContext());
                if (config == null)
                    config = new Config();
                config.setAllowedDirection(Config.AllowedDirection.VERTICAL_AND_HORIZONTAL);

                folioReader.setReadLocator(readLocator);
                folioReader.setConfig(config, true)
                        .openBook("file:///android_asset/TheSilverChair.epub");
            }
        });
        findViewById(R.id.select_epub_file).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (ContextCompat.checkSelfPermission(HomeActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(HomeActivity.this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);

                    } else {
                        openFileUploder();
                    }
                } else {
                    openFileUploder();
                }
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openFileUploder();
            }
        }
    }

    private void openFileUploder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/epub+zip");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Select EPUB"), UPLOAD_FILE_REQUEST);
    }


    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == UPLOAD_FILE_REQUEST && resultCode == RESULT_OK) {
            if (data != null) {
                Uri uri = data.getData();
                File file = new File(dirPath,"folioreader");
                if(file.exists() && file.isDirectory()) createDir(dirPath.toString(),uri,true);
                else createDir(dirPath.toString(),uri, false);
            }
        }
    }

    private void uploadFileProcess(String path, Uri uri){
        File epubFile = new File(path,fileName);
        Log.d("TAG", "uploadFileProcess: "+epubFile.getName());
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream != null) {
                OutputStream outputStream = new FileOutputStream(epubFile);
                byte[] buffer = new byte[1024];
                int length;
                while ((length = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, length);
                }

                outputStream.close();
                inputStream.close();

                Toast.makeText(this, "File uploaded to FolioReader folder", Toast.LENGTH_SHORT).show();
                openSpecificFilePicker();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "File upload failed "+e, Toast.LENGTH_SHORT).show();
        }
    }

    private void openSpecificFilePicker() {
        Log.d("TAG", "openSpecificFilePicker: "+fileName);

        if(fileName==null) {
            Toast.makeText(this, "File name not defined", Toast.LENGTH_SHORT).show();
            return;
        }
        Config config = AppUtil.getSavedConfig(getApplicationContext());
        if (config == null)
            config = new Config();
        config.setAllowedDirection(Config.AllowedDirection.VERTICAL_AND_HORIZONTAL);
        folioReader.setConfig(config, true)
                .openBook("file:///android_asset/"+fileName);
    }

    private void createDir(String dirPath,Uri uri,boolean appFolderExists) {
        File parentDir = new File(dirPath, "folioreader");
        String folderName = getFileName(uri).split("\\.")[0];
        if(folderName.isEmpty()) return;
        if(!appFolderExists) {
            Log.d("TAG", "createDir: " + folderName);
            parentDir.mkdir();
            File subFile = new File(parentDir,folderName);
            subFile.mkdir();
            uploadFileProcess(subFile.getPath(),uri);
        }
        else{
            File subFile = new File(parentDir,folderName);
            if(!subFile.exists()){
                subFile.mkdir();
            }

            uploadFileProcess(subFile.getPath(),uri);
        }

    }

     String getFileName(Uri uri) {
        if (uri != null) {
            if (ContentResolver.SCHEME_CONTENT.equalsIgnoreCase(uri.getScheme())) {
                Cursor cursor = getContentResolver().query(uri, null, null, null, null);
                try {
                    if (cursor != null && cursor.moveToFirst()) {
                        fileName = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                    }
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
            } else if (ContentResolver.SCHEME_FILE.equalsIgnoreCase(uri.getScheme())) {
                fileName = new File(uri.getPath()).getName();
            }
        }
        return fileName;
    }

    private ReadLocator getLastReadLocator() {

        String jsonString = loadAssetTextAsString("Locators/LastReadLocators/last_read_locator_1.json");
        return ReadLocator.fromJson(jsonString);
    }

    @Override
    public void saveReadLocator(ReadLocator readLocator) {
        Log.i(LOG_TAG, "-> saveReadLocator -> " + readLocator.toJson());
    }

    /*
     * For testing purpose, we are getting dummy highlights from asset. But you can get highlights from your server
     * On success, you can save highlights to FolioReader DB.
     */
    private void getHighlightsAndSave() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                ArrayList<HighLight> highlightList = null;
                ObjectMapper objectMapper = new ObjectMapper();
                try {
                    highlightList = objectMapper.readValue(
                            loadAssetTextAsString("highlights/highlights_data.json"),
                            new TypeReference<List<HighlightData>>() {
                            });
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if (highlightList == null) {
                    folioReader.saveReceivedHighLights(highlightList, new OnSaveHighlight() {
                        @Override
                        public void onFinished() {
                            //You can do anything on successful saving highlight list
                        }
                    });
                }
            }
        }).start();
    }

    private String loadAssetTextAsString(String name) {
        BufferedReader in = null;
        try {
            StringBuilder buf = new StringBuilder();
            InputStream is = getAssets().open(name);
            in = new BufferedReader(new InputStreamReader(is));

            String str;
            boolean isFirst = true;
            while ((str = in.readLine()) != null) {
                if (isFirst)
                    isFirst = false;
                else
                    buf.append('\n');
                buf.append(str);
            }
            return buf.toString();
        } catch (IOException e) {
            Log.e("HomeActivity", "Error opening asset " + name);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    Log.e("HomeActivity", "Error closing asset " + name);
                }
            }
        }
        return null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        FolioReader.clear();
    }

    @Override
    public void onHighlight(HighLight highlight, HighLight.HighLightAction type) {
        Toast.makeText(this,
                "highlight id = " + highlight.getUUID() + " type = " + type,
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onFolioReaderClosed() {
        Log.v(LOG_TAG, "-> onFolioReaderClosed");
    }

//    private boolean isEmulator() {
//        List<String> knownEmulatorManufacturers = Arrays.asList("Genymotion", "BlueStacks", "Nox", "Xamarin", "Anbox", "MeMU");
//        List<String> knownEmulatorBoards = Arrays.asList("goldfish", "ranchu", "unknown", "QC_Reference_Phone");
//        List<String> knownEmulatorHosts = Arrays.asList("Build", "localhost", "emulator", "Genymotion", "BlueStacks", "Xamarin", "Nox", "Anbox", "MeMU");
//        List<String> knownEmulatorCpuAbis = Arrays.asList("armeabi", "armeabi-v7a", "arm64-v8a", "x86", "x86_64");
//        List<String> knownEmulatorModels = Arrays.asList("Android SDK built for x86", "Android SDK built for x86_64", "google_sdk", "Emulator", "Android Emulator", "Genymotion", "Nox", "BlueStacks", "Xamarin", "Anbox", "MeMU");
//        List<String> knownEmulatorProducts = Arrays.asList("google_sdk", "sdk_google", "generic", "generic_x86", "generic_x86_64", "vbox86p", "vbox86t", "nox", "Genymotion", "Emulator", "Android SDK built for x86", "Android SDK built for x86_64", "BlueStacks", "Xamarin", "Anbox", "MeMU");
//
//        return (Build.FINGERPRINT.startsWith("generic")
//                || Build.FINGERPRINT.startsWith("unknown")
//                || knownEmulatorModels.contains(Build.MODEL)
//                || knownEmulatorCpuAbis.contains(Build.SUPPORTED_ABIS[0])
//                || knownEmulatorBoards.contains(Build.BOARD)
//                || knownEmulatorManufacturers.contains(Build.MANUFACTURER)
//                || knownEmulatorHosts.contains(Build.HOST)
//                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
//                || knownEmulatorProducts.contains(Build.PRODUCT)) && hasNfc() && isIMEINumberValid(getApplicationContext());
//    }
//    private boolean isIMEINumberValid(Context context){
//        TelephonyManager telephoneManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
//        if(ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return false;
//        String imei = "";
//        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) imei = telephoneManager.getImei();
//        else telephoneManager.getDeviceId();
//        List<String> knownEmulatorIMEI = Arrays.asList("000000000000000", "012345678912345");
//        return knownEmulatorIMEI.contains(imei);
//    }
//    private boolean hasNfc(){
//        NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(this);
//        return nfcAdapter != null && nfcAdapter.isEnabled();
//    }

}