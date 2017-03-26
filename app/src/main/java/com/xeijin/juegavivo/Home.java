package com.xeijin.juegavivo;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.squareup.picasso.Picasso;

import org.openalpr.OpenALPR;
import org.openalpr.model.Results;
import org.openalpr.model.ResultsError;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class Home extends AppCompatActivity {

    String filePath = "";

    private static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".provider";

    private static final int STORAGE = 1;
    private static final int CAMERA = 2;
    private static File destination;

    public static final int PHOTO_CODE = 23;
    public static final int GALLERY_CODE = 45;
    private String ANDROID_DATA_DIR;
    File photoReportar;

    TextView text;
    ImageView imageView;
    Button btnTakePicture,
            btnOpenGallery;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.home);


        initInstances();


    }


    private void initInstances() {

        text = (TextView) findViewById(R.id.txt_response);
        imageView = (ImageView) findViewById(R.id.img_reporte);
        btnTakePicture = (Button) findViewById(R.id.btn_picture);
        btnOpenGallery = (Button) findViewById(R.id.btn_gallery);

        btnTakePicture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkPermission()) {
                    takePicture();
                }


            }
        });

        btnOpenGallery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkPermission()) {
                    openGallery();
                }
            }
        });

        ANDROID_DATA_DIR = this.getApplicationInfo().dataDir;
    }


    private void takePicture() {

        filePath = Environment.getExternalStorageDirectory()
                .getAbsolutePath() + "/" + System.currentTimeMillis() + (new Random().nextInt(100) + 0) + ".jpg";
        File newfile = new File(filePath);
        try {
            newfile.createNewFile();
        } catch (IOException e) {
        }

        Uri outputFileUri;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            outputFileUri = FileProvider.getUriForFile(this, AUTHORITY, newfile);
        else
            outputFileUri = Uri.fromFile(newfile);

        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, outputFileUri);

        startActivityForResult(cameraIntent, PHOTO_CODE);

    }


    private void openGallery() {

        Intent i = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(i, GALLERY_CODE);

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        photoReportar = null;

        switch (requestCode) {

            case PHOTO_CODE:

                if (resultCode == RESULT_OK && data != null) {
                    Log.d("CameraDemo", "Pic saved");
                    photoReportar = new File(filePath);
                } else {
                    photoReportar = null;
                }

                break;

            case GALLERY_CODE:

                if (resultCode == RESULT_OK && data != null && data.getData() != null) {

                    Uri selectedImage = data.getData();
                    String[] filePathColumn = {MediaStore.Images.Media.DATA};
                    Cursor cursor = getContentResolver().query(selectedImage,
                            filePathColumn, null, null, null);

                    if (cursor == null || cursor.getCount() < 1) {
                        photoReportar = null;
                    }

                    cursor.moveToFirst();
                    int columnIndex = cursor.getColumnIndex(filePathColumn[0]);

                    if (cursor == null || cursor.getCount() < 1) {
                        photoReportar = null;
                        break;
                    }


                    photoReportar = new File(cursor.getString(columnIndex));
                    cursor.close();
                } else {
                    photoReportar = null;
                }

                break;
        }
            if (photoReportar != null) {

                final ProgressDialog progress = ProgressDialog.show(this, "Loading", "Parsing result...", true);
                final String openAlprConfFile = ANDROID_DATA_DIR + File.separatorChar + "runtime_data" + File.separatorChar + "openalpr.conf";
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 10;


                Picasso.with(this).load(photoReportar).into(imageView);
                text.setText("Processing");


                AsyncTask.execute(new Runnable() {
                    @Override
                    public void run() {
                        String result = OpenALPR.Factory.create(Home.this, ANDROID_DATA_DIR).recognizeWithCountryRegionNConfig("us", "", photoReportar.getAbsolutePath(), openAlprConfFile, 10);

                        Log.d("OPEN ALPR", result);

                        try {
                            final Results results = new Gson().fromJson(result, Results.class);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (results == null || results.getResults() == null || results.getResults().size() == 0) {
                                        Toast.makeText(Home.this, "It was not possible to detect the licence plate.", Toast.LENGTH_LONG).show();
                                        text.setText("It was not possible to detect the licence plate.");
                                    } else {
                                        text.setText("Plate: " + results.getResults().get(0).getPlate()
                                                // Trim confidence to two decimal places
                                                + " Confidence: " + String.format("%.2f", results.getResults().get(0).getConfidence()) + "%"
                                                // Convert processing time to seconds and trim to two decimal places
                                                + " Processing time: " + String.format("%.2f", ((results.getProcessingTimeMs() / 1000.0) % 60)) + " seconds");
                                    }
                                }
                            });

                        } catch (JsonSyntaxException exception) {
                            final ResultsError resultsError = new Gson().fromJson(result, ResultsError.class);

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    text.setText(resultsError.getMsg());
                                }
                            });
                        }

                        progress.dismiss();
                    }
                });

                Log.d("CameraDemo", "Pic saved");




        }
    }


    private boolean checkPermission() {
        List<String> permissions = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (!permissions.isEmpty()) {
            Toast.makeText(this, "Necesita acceso a la galería.", Toast.LENGTH_LONG).show();
            String[] params = permissions.toArray(new String[permissions.size()]);
            ActivityCompat.requestPermissions(this, params, STORAGE);
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.CAMERA);
            }
            if (!permissions.isEmpty()) {
                Toast.makeText(this, "Necesita acceso a la camara.", Toast.LENGTH_LONG).show();
                String[] params = permissions.toArray(new String[permissions.size()]);
                ActivityCompat.requestPermissions(this, params, CAMERA);
            } else {
                return true;
            }
        }
        return false;
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case STORAGE:{
                Map<String, Integer> perms = new HashMap<>();

                perms.put(Manifest.permission.WRITE_EXTERNAL_STORAGE, PackageManager.PERMISSION_GRANTED);

                for (int i = 0; i < permissions.length; i++)
                    perms.put(permissions[i], grantResults[i]);

                Boolean storage = perms.get(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
                if (storage) {

                    Toast.makeText(this, "Permiso de galería habilitado.", Toast.LENGTH_LONG).show();
                } else {

                    Toast.makeText(this, "Se necesita permiso a la galería.", Toast.LENGTH_LONG).show();
                }
            }

            case CAMERA:{
                Map<String, Integer> perms = new HashMap<>();

                perms.put(Manifest.permission.CAMERA, PackageManager.PERMISSION_GRANTED);

                for (int i = 0; i < permissions.length; i++)
                    perms.put(permissions[i], grantResults[i]);

                Boolean camera = perms.get(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
                if (camera) {

                    Toast.makeText(this, "Permiso de la camara habilitado.", Toast.LENGTH_LONG).show();
                } else {

                    Toast.makeText(this, "Se necesita permiso a la camara.", Toast.LENGTH_LONG).show();
                }
            }

            default:
                break;
        }
    }

}
