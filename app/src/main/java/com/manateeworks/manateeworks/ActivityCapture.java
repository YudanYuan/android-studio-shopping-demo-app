/* Version 2.0
 * Copyright (C) 2012  Manatee Works, Inc.
 *
 */
package com.manateeworks.manateeworks;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Scanner;

import com.manateeworks.BarcodeScanner;
import com.manateeworks.BarcodeScanner.MWResult;
import com.manateeworks.CameraManager;
import com.manateeworks.MWOverlay;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnKeyListener;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

/**
 * The barcode reader activity itself. This is loosely based on the
 * CameraPreview example included in the Android SDK.
 */
public final class ActivityCapture extends Activity implements
        SurfaceHolder.Callback,
        ActivityCompat.OnRequestPermissionsResultCallback {

    public static final boolean USE_MWANALYTICS = false;
    public static final boolean PDF_OPTIMIZED = false;
    public static final boolean USE_MWPARSER = false;

	/* Parser */
    /*
	 * MWPARSER_MASK - Set the desired parser type Available options:
	 * MWParser.MWP_PARSER_MASK_ISBT MWParser.MWP_PARSER_MASK_AAMVA
	 * MWParser.MWP_PARSER_MASK_IUID MWParser.MWP_PARSER_MASK_HIBC
	 * MWParser.MWP_PARSER_MASK_SCM
	 * MWParser.MWP_PARSER_MASK_NONE
	 */
    // public static final int MWPARSER_MASK = MWParser.MWP_PARSER_MASK_ISBT;

    public static final int USE_RESULT_TYPE = BarcodeScanner.MWB_RESULT_TYPE_MW;

    public static final OverlayMode OVERLAY_MODE = OverlayMode.OM_MWOVERLAY;

    // !!! Rects are in format: x, y, width, height !!!
    public static final Rect RECT_LANDSCAPE_1D = new Rect(3, 20, 94, 60);
    public static final Rect RECT_LANDSCAPE_2D = new Rect(20, 5, 60, 90);
    public static final Rect RECT_PORTRAIT_1D = new Rect(20, 3, 60, 94);
    public static final Rect RECT_PORTRAIT_2D = new Rect(20, 5, 60, 90);
    public static final Rect RECT_FULL_1D = new Rect(3, 3, 94, 94);
    public static final Rect RECT_FULL_2D = new Rect(20, 5, 60, 90);
    public static final Rect RECT_DOTCODE = new Rect(30, 20, 40, 60);

    private static final String MSG_CAMERA_FRAMEWORK_BUG = "Sorry, the Android camera encountered a problem: ";

    public static final int ID_AUTO_FOCUS = 0x01;
    public static final int ID_DECODE = 0x02;
    public static final int ID_RESTART_PREVIEW = 0x04;
    public static final int ID_DECODE_SUCCEED = 0x08;
    public static final int ID_DECODE_FAILED = 0x10;

    private Handler decodeHandler;
    private boolean hasSurface;
    private String package_name;

    private int activeThreads = 0;
    public static int MAX_THREADS = Runtime.getRuntime().availableProcessors();

    private ImageButton zoomButton;
    private ImageButton buttonFlash;

    boolean flashOn = false;

    private int zoomLevel = 0;
    private int firstZoom = 150;
    private int secondZoom = 300;

    private SurfaceHolder surfaceHolder;
    private boolean surfaceChanged = false;

    private enum State {
        STOPPED, PREVIEW, DECODING
    }

    private enum OverlayMode {
        OM_IMAGE, OM_MWOVERLAY, OM_NONE
    }

    State state = State.STOPPED;

    public Handler getHandler() {
        return decodeHandler;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        surfaceChanged = false;
        package_name = getPackageName();
        // to avoid false detection
        BarcodeScanner.MWBsetFlags(0, BarcodeScanner.MWB_CFG_GLOBAL_CALCULATE_1D_LOCATION | BarcodeScanner.MWB_CFG_GLOBAL_VERIFY_1D_LOCATION);


        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(getResources().getIdentifier("capture", "layout",
                package_name));

        // register your copy of library with given key
        int registerResult = BarcodeScanner.MWBregisterSDK("SDK Key", this);

        switch (registerResult) {
            case BarcodeScanner.MWB_RTREG_OK:
                Log.i("MWBregisterSDK", "Registration OK");
                break;
            case BarcodeScanner.MWB_RTREG_INVALID_KEY:
                Log.e("MWBregisterSDK", "Registration Invalid Key");
                break;
            case BarcodeScanner.MWB_RTREG_INVALID_CHECKSUM:
                Log.e("MWBregisterSDK", "Registration Invalid Checksum");
                break;
            case BarcodeScanner.MWB_RTREG_INVALID_APPLICATION:
                Log.e("MWBregisterSDK", "Registration Invalid Application");
                break;
            case BarcodeScanner.MWB_RTREG_INVALID_SDK_VERSION:
                Log.e("MWBregisterSDK", "Registration Invalid SDK Version");
                break;
            case BarcodeScanner.MWB_RTREG_INVALID_KEY_VERSION:
                Log.e("MWBregisterSDK", "Registration Invalid Key Version");
                break;
            case BarcodeScanner.MWB_RTREG_INVALID_PLATFORM:
                Log.e("MWBregisterSDK", "Registration Invalid Platform");
                break;
            case BarcodeScanner.MWB_RTREG_KEY_EXPIRED:
                Log.e("MWBregisterSDK", "Registration Key Expired");
                break;

            default:
                Log.e("MWBregisterSDK", "Registration Unknown Error");
                break;
        }

        // choose code type or types you want to search for

        if (PDF_OPTIMIZED) {
            BarcodeScanner
                    .MWBsetDirection(BarcodeScanner.MWB_SCANDIRECTION_HORIZONTAL);
            BarcodeScanner.MWBsetActiveCodes(BarcodeScanner.MWB_CODE_MASK_PDF);
            BarcodeScanner.MWBsetScanningRect(BarcodeScanner.MWB_CODE_MASK_PDF,
                    RECT_PORTRAIT_1D);
        } else {
            // Our sample app is configured by default to search both
            // directions...
            BarcodeScanner
                    .MWBsetDirection(BarcodeScanner.MWB_SCANDIRECTION_HORIZONTAL
                            | BarcodeScanner.MWB_SCANDIRECTION_VERTICAL);
            // Our sample app is configured by default to search all supported
            // barcodes...
            BarcodeScanner.MWBsetActiveCodes(BarcodeScanner.MWB_CODE_MASK_25
                    | BarcodeScanner.MWB_CODE_MASK_39
                    | BarcodeScanner.MWB_CODE_MASK_93
                    | BarcodeScanner.MWB_CODE_MASK_128
                    | BarcodeScanner.MWB_CODE_MASK_AZTEC
                    | BarcodeScanner.MWB_CODE_MASK_DM
                    | BarcodeScanner.MWB_CODE_MASK_EANUPC
                    | BarcodeScanner.MWB_CODE_MASK_PDF
                    | BarcodeScanner.MWB_CODE_MASK_QR
                    | BarcodeScanner.MWB_CODE_MASK_CODABAR
                    | BarcodeScanner.MWB_CODE_MASK_11
                    | BarcodeScanner.MWB_CODE_MASK_MSI
                    | BarcodeScanner.MWB_CODE_MASK_RSS);

            // set the scanning rectangle based on scan direction(format in pct:
            // x, y, width, height)
            BarcodeScanner.MWBsetScanningRect(BarcodeScanner.MWB_CODE_MASK_25,
                    RECT_FULL_1D);
            BarcodeScanner.MWBsetScanningRect(BarcodeScanner.MWB_CODE_MASK_39,
                    RECT_FULL_1D);
            BarcodeScanner.MWBsetScanningRect(BarcodeScanner.MWB_CODE_MASK_93,
                    RECT_FULL_1D);
            BarcodeScanner.MWBsetScanningRect(BarcodeScanner.MWB_CODE_MASK_128,
                    RECT_FULL_1D);
            BarcodeScanner.MWBsetScanningRect(
                    BarcodeScanner.MWB_CODE_MASK_AZTEC, RECT_FULL_2D);
            BarcodeScanner.MWBsetScanningRect(BarcodeScanner.MWB_CODE_MASK_DM,
                    RECT_FULL_2D);
            BarcodeScanner.MWBsetScanningRect(
                    BarcodeScanner.MWB_CODE_MASK_EANUPC, RECT_FULL_1D);
            BarcodeScanner.MWBsetScanningRect(BarcodeScanner.MWB_CODE_MASK_PDF,
                    RECT_FULL_1D);
            BarcodeScanner.MWBsetScanningRect(BarcodeScanner.MWB_CODE_MASK_QR,
                    RECT_FULL_2D);
            BarcodeScanner.MWBsetScanningRect(BarcodeScanner.MWB_CODE_MASK_RSS,
                    RECT_FULL_1D);
            BarcodeScanner.MWBsetScanningRect(
                    BarcodeScanner.MWB_CODE_MASK_CODABAR, RECT_FULL_1D);
            BarcodeScanner.MWBsetScanningRect(
                    BarcodeScanner.MWB_CODE_MASK_DOTCODE, RECT_DOTCODE);
            BarcodeScanner.MWBsetScanningRect(BarcodeScanner.MWB_CODE_MASK_11,
                    RECT_FULL_1D);
            BarcodeScanner.MWBsetScanningRect(BarcodeScanner.MWB_CODE_MASK_MSI,
                    RECT_FULL_1D);

        }

        if (OVERLAY_MODE == OverlayMode.OM_IMAGE) {
            ImageView imageOverlay = (ImageView) findViewById(R.id.imageOverlay);
            imageOverlay.setVisibility(View.VISIBLE);
        }

		/* Analytics */
		/*
		 * Register with given apiUser and apiKey
		 */
		/*
		 * if (USE_MWANALYTICS) { MWBAnalytics.init(getApplicationContext(),
		 * "apiUser", "apiKey"); }
		 */

        // But for better performance, only activate the symbologies your
        // application requires...
        // BarcodeScanner.MWBsetActiveCodes( BarcodeScanner.MWB_CODE_MASK_25 );
        // BarcodeScanner.MWBsetActiveCodes( BarcodeScanner.MWB_CODE_MASK_39 );
        // BarcodeScanner.MWBsetActiveCodes( BarcodeScanner.MWB_CODE_MASK_93 );
        // BarcodeScanner.MWBsetActiveCodes( BarcodeScanner.MWB_CODE_MASK_128 );
        // BarcodeScanner.MWBsetActiveCodes( BarcodeScanner.MWB_CODE_MASK_AZTEC
        // );
        // BarcodeScanner.MWBsetActiveCodes( BarcodeScanner.MWB_CODE_MASK_EANUPC
        // );
        // BarcodeScanner.MWBsetActiveCodes( BarcodeScanner.MWB_CODE_MASK_PDF );
        // BarcodeScanner.MWBsetActiveCodes( BarcodeScanner.MWB_CODE_MASK_QR );
        // BarcodeScanner.MWBsetActiveCodes( BarcodeScanner.MWB_CODE_MASK_RSS );
        // BarcodeScanner.MWBsetActiveCodes(
        // BarcodeScanner.MWB_CODE_MASK_CODABAR );
        // BarcodeScanner.MWBsetActiveCodes(
        // BarcodeScanner.MWB_CODE_MASK_DOTCODE );
        // BarcodeScanner.MWBsetActiveCodes( BarcodeScanner.MWB_CODE_MASK_11 );
        // BarcodeScanner.MWBsetActiveCodes( BarcodeScanner.MWB_CODE_MASK_MSI );

        // But for better performance, set like this for PORTRAIT scanning...
        // BarcodeScanner.MWBsetDirection(BarcodeScanner.MWB_SCANDIRECTION_VERTICAL);
        // set the scanning rectangle based on scan direction(format in pct: x,
        // y, width, height)
        // BarcodeScanner.MWBsetScanningRect(BarcodeScanner.MWB_CODE_MASK_25,
        // RECT_PORTRAIT_1D);
        // BarcodeScanner.MWBsetScanningRect(BarcodeScanner.MWB_CODE_MASK_39,
        // RECT_PORTRAIT_1D);
        // BarcodeScanner.MWBsetScanningRect(BarcodeScanner.MWB_CODE_MASK_93,
        // RECT_PORTRAIT_1D);
        // BarcodeScanner.MWBsetScanningRect(BarcodeScanner.MWB_CODE_MASK_128,
        // RECT_PORTRAIT_1D);
        // BarcodeScanner.MWBsetScanningRect(BarcodeScanner.MWB_CODE_MASK_AZTEC,
        // RECT_PORTRAIT_2D);
        // BarcodeScanner.MWBsetScanningRect(BarcodeScanner.MWB_CODE_MASK_DM,
        // RECT_PORTRAIT_2D);
        // BarcodeScanner.MWBsetScanningRect(BarcodeScanner.MWB_CODE_MASK_EANUPC,
        // RECT_PORTRAIT_1D);
        // BarcodeScanner.MWBsetScanningRect(BarcodeScanner.MWB_CODE_MASK_PDF,
        // RECT_PORTRAIT_1D);
        // BarcodeScanner.MWBsetScanningRect(BarcodeScanner.MWB_CODE_MASK_QR,
        // RECT_PORTRAIT_2D);
        // BarcodeScanner.MWBsetScanningRect(BarcodeScanner.MWB_CODE_MASK_RSS,
        // RECT_PORTRAIT_1D);
        // BarcodeScanner.MWBsetScanningRect(BarcodeScanner.MWB_CODE_MASK_CODABAR,RECT_PORTRAIT_1D);
        // BarcodeScanner.MWBsetScanningRect(BarcodeScanner.MWB_CODE_MASK_DOTCODE,RECT_DOTCODE);
        // BarcodeScanner.MWBsetScanningRect(BarcodeScanner.MWB_CODE_MASK_11,
        // RECT_PORTRAIT_1D);
        // BarcodeScanner.MWBsetScanningRect(BarcodeScanner.MWB_CODE_MASK_MSI,
        // RECT_PORTRAIT_1D);

        // or like this for LANDSCAPE scanning - Preferred for dense or wide
        // codes...
        // BarcodeScanner.MWBsetDirection(BarcodeScanner.MWB_SCANDIRECTION_HORIZONTAL);
        // set the scanning rectangle based on scan direction(format in pct: x,
        // y, width, height)
        // BarcodeScanner.MWBsetScanningRect(BarcodeScanner.MWB_CODE_MASK_25,
        // RECT_LANDSCAPE_1D);
        // BarcodeScanner.MWBsetScanningRect(BarcodeScanner.MWB_CODE_MASK_39,
        // RECT_LANDSCAPE_1D);
        // BarcodeScanner.MWBsetScanningRect(BarcodeScanner.MWB_CODE_MASK_93,
        // RECT_LANDSCAPE_1D);
        // BarcodeScanner.MWBsetScanningRect(BarcodeScanner.MWB_CODE_MASK_128,
        // RECT_LANDSCAPE_1D);
        // BarcodeScanner.MWBsetScanningRect(BarcodeScanner.MWB_CODE_MASK_AZTEC,
        // RECT_LANDSCAPE_2D);
        // BarcodeScanner.MWBsetScanningRect(BarcodeScanner.MWB_CODE_MASK_DM,
        // RECT_LANDSCAPE_2D);
        // BarcodeScanner.MWBsetScanningRect(BarcodeScanner.MWB_CODE_MASK_EANUPC,
        // RECT_LANDSCAPE_1D);
        // BarcodeScanner.MWBsetScanningRect(BarcodeScanner.MWB_CODE_MASK_PDF,
        // RECT_LANDSCAPE_1D);
        // BarcodeScanner.MWBsetScanningRect(BarcodeScanner.MWB_CODE_MASK_QR,
        // RECT_LANDSCAPE_2D);
        // BarcodeScanner.MWBsetScanningRect(BarcodeScanner.MWB_CODE_MASK_RSS,
        // RECT_LANDSCAPE_1D);
        // BarcodeScanner.MWBsetScanningRect(BarcodeScanner.MWB_CODE_MASK_CODABAR,RECT_LANDSCAPE_1D);
        // BarcodeScanner.MWBsetScanningRect(BarcodeScanner.MWB_CODE_MASK_DOTCODE,RECT_DOTCODE);
        // BarcodeScanner.MWBsetScanningRect(BarcodeScanner.MWB_CODE_MASK_11,
        // RECT_LANDSCAPE_1D);
        // BarcodeScanner.MWBsetScanningRect(BarcodeScanner.MWB_CODE_MASK_MSI,
        // RECT_LANDSCAPE_1D);

        // set decoder effort level (1 - 5)
        // for live scanning scenarios, a setting between 1 to 3 will suffice
        // levels 4 and 5 are typically reserved for batch scanning
        BarcodeScanner.MWBsetLevel(2);
        BarcodeScanner.MWBsetResultType(USE_RESULT_TYPE);

        // Set minimum result length for low-protected barcode types
        BarcodeScanner.MWBsetMinLength(BarcodeScanner.MWB_CODE_MASK_25, 5);
        BarcodeScanner.MWBsetMinLength(BarcodeScanner.MWB_CODE_MASK_MSI, 5);
        BarcodeScanner.MWBsetMinLength(BarcodeScanner.MWB_CODE_MASK_39, 5);
        BarcodeScanner.MWBsetMinLength(BarcodeScanner.MWB_CODE_MASK_CODABAR, 5);
        BarcodeScanner.MWBsetMinLength(BarcodeScanner.MWB_CODE_MASK_11, 5);

        // Set adittional options for GS1 and ECI
        // BarcodeScanner.MWBsetParam(BarcodeScanner.MWB_CODE_MASK_DM,
        // BarcodeScanner.MWB_PAR_ID_ECI_MODE,
        // BarcodeScanner.MWB_PAR_VALUE_ECI_ENABLED);
        // BarcodeScanner.MWBsetParam(BarcodeScanner.MWB_CODE_MASK_DM,
        // BarcodeScanner.MWB_PAR_ID_RESULT_PREFIX,
        // BarcodeScanner.MWB_PAR_VALUE_RESULT_PREFIX_ALWAYS);
        // BarcodeScanner.MWBsetParam(BarcodeScanner.MWB_CODE_MASK_128,
        // BarcodeScanner.MWB_PAR_ID_RESULT_PREFIX,
        // BarcodeScanner.MWB_PAR_VALUE_RESULT_PREFIX_ALWAYS);
        // BarcodeScanner.MWBsetParam(BarcodeScanner.MWB_CODE_MASK_QR,
        // BarcodeScanner.MWB_PAR_ID_RESULT_PREFIX,
        // BarcodeScanner.MWB_PAR_VALUE_RESULT_PREFIX_ALWAYS);

        // BarcodeScanner.MWBsetFlags(BarcodeScanner.MWB_CODE_MASK_EANUPC,
        // BarcodeScanner.MWB_CFG_EANUPC_DISABLE_ADDON);

        CameraManager.init(getApplication());

        hasSurface = false;
        state = State.STOPPED;
        decodeHandler = new Handler(new Handler.Callback() {

            @Override
            public boolean handleMessage(Message msg) {
                // TODO Auto-generated method stub

                switch (msg.what) {
                    case ID_DECODE:
                        decode((byte[]) msg.obj, msg.arg1, msg.arg2);
                        break;

                    case ID_AUTO_FOCUS:
                        // When one auto focus pass finishes, start another. This is
                        // the
                        // closest thing to
                        // continuous AF. It does seem to hunt a bit, but I'm not
                        // sure what
                        // else to do.
                        if (state == State.PREVIEW || state == State.DECODING) {
                            CameraManager.get().requestAutoFocus(decodeHandler,
                                    ID_AUTO_FOCUS);
                        }
                        break;
                    case ID_RESTART_PREVIEW:
                        restartPreviewAndDecode();
                        break;
                    case ID_DECODE_SUCCEED:

                        // Bundle bundle = message.getData();
                        // Bitmap barcode = bundle == null ? null : (Bitmap)
                        // bundle.getParcelable(DecodeThread.BARCODE_BITMAP);
                        state = State.STOPPED;
                        handleDecode((MWResult) msg.obj);

                        break;
                    case ID_DECODE_FAILED:
                        // We're decoding as fast as possible, so when one decode
                        // fails,
                        // start another.
                        // state = State.PREVIEW;
                        // CameraManager.get().requestPreviewFrame(decodeThread.getHandler(),
                        // R.id.decode);
                        break;

                }

                return false;
            }
        });

        zoomButton = (ImageButton) findViewById(R.id.zoomButton);

        zoomButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {

                zoomLevel++;
                if (zoomLevel > 2) {
                    zoomLevel = 0;
                }

                switch (zoomLevel) {
                    case 0:
                        CameraManager.get().setZoom(100);
                        break;
                    case 1:
                        CameraManager.get().setZoom(firstZoom);
                        break;
                    case 2:
                        CameraManager.get().setZoom(secondZoom);
                        break;

                    default:
                        break;
                }

            }
        });
        buttonFlash = (ImageButton) findViewById(R.id.flashButton);
        buttonFlash.setOnClickListener(new OnClickListener() {
            // @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
            @Override
            public void onClick(View v) {
                toggleFlash();
            }
        });

    }

    @Override
    protected void onResume() {
        super.onResume();

        SurfaceView surfaceView = (SurfaceView) findViewById(getResources()
                .getIdentifier("preview_view", "id", package_name));
        surfaceHolder = surfaceView.getHolder();

        if (OVERLAY_MODE == OverlayMode.OM_MWOVERLAY) {
            MWOverlay.addOverlay(this, surfaceView);
        }

        if (hasSurface) {
            // The activity was paused but not stopped, so the surface still
            // exists. Therefore
            // surfaceCreated() won't be called, so init the camera here.
            // Log.i("preview", "init camera - on resume and has surface");

            // if (!surfaceChanged){
            Log.i("Init Camera", "On resume");
            initCamera();
            // }
        } else {
            // Install the callback and wait for surfaceCreated() to init the
            // camera.
            surfaceHolder.addCallback(this);
            surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }

        int ver = BarcodeScanner.MWBgetLibVersion();
        int v1 = (ver >> 16);
        int v2 = (ver >> 8) & 0xff;
        int v3 = (ver & 0xff);

        String libVersion = "Lib version: " + String.valueOf(v1) + "."
                + String.valueOf(v2) + "." + String.valueOf(v3);


    }

    @SuppressLint("Override")
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        if (requestCode == 12322) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission Granted
                initCamera();
            } else {
                onBackPressed();
            }
        }

    }

    @Override
    protected void onPause() {
        super.onPause();
        if (OVERLAY_MODE == OverlayMode.OM_MWOVERLAY) {
            MWOverlay.removeOverlay();
        }

        CameraManager.get().stopPreview();
        CameraManager.get().closeDriver();
        state = State.STOPPED;

        flashOn = false;

        updateFlash();

    }

    @Override
    public void onConfigurationChanged(Configuration config) {

        Display display = ((WindowManager) getSystemService(WINDOW_SERVICE))
                .getDefaultDisplay();
        int rotation = display.getRotation();

        CameraManager.get().updateCameraOrientation(rotation);

        super.onConfigurationChanged(config);
    }

    private void toggleFlash() {
        flashOn = !flashOn;
        updateFlash();
    }

    private void updateFlash() {

        if (!CameraManager.get().isTorchAvailable()) {
            buttonFlash.setVisibility(View.GONE);
            return;

        } else {
            buttonFlash.setVisibility(View.VISIBLE);
        }

        if (flashOn) {
            buttonFlash.setImageResource(R.drawable.flashbuttonon);
        } else {
            buttonFlash.setImageResource(R.drawable.flashbuttonoff);
        }

        CameraManager.get().setTorch(flashOn);

        buttonFlash.postInvalidate();

    }

    public void surfaceCreated(SurfaceHolder holder) {
        if (!hasSurface) {
            hasSurface = true;
            // Log.i("Init Camera", "On Surface created");
            // initCamera(holder);
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        hasSurface = false;
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {
        Log.i("Init Camera", "On Surface changed");
        initCamera();
        surfaceChanged = true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_FOCUS
                || keyCode == KeyEvent.KEYCODE_CAMERA) {
            // Handle these events so they don't launch the Camera app
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void decode(final byte[] data, final int width, final int height) {

        if (activeThreads >= MAX_THREADS || state == State.STOPPED) {
            return;
        }

        new Thread(new Runnable() {
            public void run() {
                activeThreads++;
                // Log.i("Active threads", String.valueOf(activeThreads));
                long start = System.currentTimeMillis();

                // byte[] source =
                // CameraManager.get().buildLuminanceSource(data, width,
                // height);

                byte[] rawResult = null;
				/*
				 * if (Global.mode_39) rawResult =
				 * BarcodeScanner.decode39(source, w, h); else rawResult =
				 * BarcodeScanner.decodeDM(source, w, h);
				 */

                rawResult = BarcodeScanner.MWBscanGrayscaleImage(data, width,
                        height);

                if (state == State.STOPPED) {
                    activeThreads--;
                    return;
                }

                MWResult mwResult = null;

                if (rawResult != null
                        && BarcodeScanner.MWBgetResultType() == BarcodeScanner.MWB_RESULT_TYPE_MW) {

                    BarcodeScanner.MWResults results = new BarcodeScanner.MWResults(
                            rawResult);

                    if (results.count > 0) {
                        mwResult = results.getResult(0);
                        rawResult = mwResult.bytes;
                    }

                } else if (rawResult != null
                        && BarcodeScanner.MWBgetResultType() == BarcodeScanner.MWB_RESULT_TYPE_RAW) {
                    mwResult = new MWResult();
                    mwResult.bytes = rawResult;
                    mwResult.text = rawResult.toString();
                    mwResult.type = BarcodeScanner.MWBgetLastType();
                    mwResult.bytesLength = rawResult.length;
                }

                if (mwResult != null) {

                    state = State.STOPPED;

                    Message message = Message.obtain(
                            ActivityCapture.this.getHandler(),
                            ID_DECODE_SUCCEED, mwResult);

                    message.arg1 = mwResult.type;

                    message.sendToTarget();

                } else {
                    Message message = Message.obtain(
                            ActivityCapture.this.getHandler(), ID_DECODE_FAILED);
                    message.sendToTarget();
                }

                activeThreads--;
            }
        }).start();
    }

    private void restartPreviewAndDecode() {
        if (state == State.STOPPED) {
            state = State.PREVIEW;
            Log.i("preview", "requestPreviewFrame.");
            CameraManager.get().requestPreviewFrame(getHandler(), ID_DECODE);
            CameraManager.get().requestAutoFocus(getHandler(), ID_AUTO_FOCUS);
        }
    }

    public void handleDecode(MWResult result) {

        byte[] rawResult = null;

        if (result != null && result.bytes != null) {
            rawResult = result.bytes;
        }

        String s = "";
		/* Parser */
		/*
		 * Parser result handler. Edit this code for custom handling of the
		 * parser result. Use MWParser.MWPgetJSON(MWPARSER_MASK,
		 * result.encryptedResult.getBytes()); to get JSON formatted result
		 */
		/*
		 * if (USE_MWPARSER && MWPARSER_MASK != MWParser.MWP_PARSER_MASK_NONE &&
		 * BarcodeScanner.MWBgetResultType() ==
		 * BarcodeScanner.MWB_RESULT_TYPE_MW) {
		 * 
		 * s = MWParser.MWPgetFormattedText(MWPARSER_MASK,
		 * result.encryptedResult.getBytes()); if (s == null) { String
		 * parserMask = "parser"; switch (MWPARSER_MASK) { case
		 * MWParser.MWP_PARSER_MASK_AAMVA: parserMask = "AAMVA"; break; case
		 * MWParser.MWP_PARSER_MASK_ISBT: parserMask = "ISBT"; break; case
		 * MWParser.MWP_PARSER_MASK_IUID: parserMask = "IUID"; break; case
		 * MWParser.MWP_PARSER_MASK_HIBC: parserMask = "HIBC"; break;
		 * 
		 * default: break; } s = result.text + "\n*Not a valid " + parserMask +
		 * " formatted barcode"; } } else {
		 */
        try {
            s = new String(rawResult, "UTF-8");
        } catch (UnsupportedEncodingException e) {

            s = "";
            for (int i = 0; i < rawResult.length; i++)
                s = s + (char) rawResult[i];
            e.printStackTrace();
        }
		/* Parser */
		/*
		 * }
		 */

        int bcType = result.type;
        String typeName = "";
        switch (bcType) {
            case BarcodeScanner.FOUND_25_INTERLEAVED:
                typeName = "Code 25 Interleaved";
                break;
            case BarcodeScanner.FOUND_25_STANDARD:
                typeName = "Code 25 Standard";
                break;
            case BarcodeScanner.FOUND_128:
                typeName = "Code 128";
                break;
            case BarcodeScanner.FOUND_39:
                typeName = "Code 39";
                break;
            case BarcodeScanner.FOUND_93:
                typeName = "Code 93";
                break;
            case BarcodeScanner.FOUND_AZTEC:
                typeName = "AZTEC";
                break;
            case BarcodeScanner.FOUND_DM:
                typeName = "Datamatrix";
                break;
            case BarcodeScanner.FOUND_EAN_13:
                typeName = "EAN 13";
                break;
            case BarcodeScanner.FOUND_EAN_8:
                typeName = "EAN 8";
                break;
            case BarcodeScanner.FOUND_NONE:
                typeName = "None";
                break;
            case BarcodeScanner.FOUND_RSS_14:
                typeName = "Databar 14";
                break;
            case BarcodeScanner.FOUND_RSS_14_STACK:
                typeName = "Databar 14 Stacked";
                break;
            case BarcodeScanner.FOUND_RSS_EXP:
                typeName = "Databar Expanded";
                break;
            case BarcodeScanner.FOUND_RSS_LIM:
                typeName = "Databar Limited";
                break;
            case BarcodeScanner.FOUND_UPC_A:
                typeName = "UPC A";
                break;
            case BarcodeScanner.FOUND_UPC_E:
                typeName = "UPC E";
                break;
            case BarcodeScanner.FOUND_PDF:
                typeName = "PDF417";
                break;
            case BarcodeScanner.FOUND_QR:
                typeName = "QR";
                break;
            case BarcodeScanner.FOUND_CODABAR:
                typeName = "Codabar";
                break;
            case BarcodeScanner.FOUND_128_GS1:
                typeName = "Code 128 GS1";
                break;
            case BarcodeScanner.FOUND_ITF14:
                typeName = "ITF 14";
                break;
            case BarcodeScanner.FOUND_11:
                typeName = "Code 11";
                break;
            case BarcodeScanner.FOUND_MSI:
                typeName = "MSI Plessey";
                break;
            case BarcodeScanner.FOUND_25_IATA:
                typeName = "IATA Code 25";
                break;
        }

        if (result.locationPoints != null
                && CameraManager.get().getCurrentResolution() != null
                && OVERLAY_MODE == OverlayMode.OM_MWOVERLAY) {
            MWOverlay.showLocation(result.locationPoints.points,
                    result.imageWidth, result.imageHeight);
        }

        if (result.isGS1) {
            typeName += " (GS1)";
        }


        if (bcType >= 0) {
            if (getIntent().hasExtra("position")) {
                Intent returnIntent = new Intent();

                returnIntent.putExtra("type", typeName);
                returnIntent.putExtra("code", s);
                returnIntent.putExtra("position", getIntent().getExtras().getInt("position"));
                setResult(Activity.RESULT_OK, returnIntent);
                finish();
            } else {

                Intent returnIntent = new Intent();

                returnIntent.putExtra("type", typeName);
                returnIntent.putExtra("code", s);
                setResult(Activity.RESULT_OK, returnIntent);
                finish();


            }
//			new AlertDialog.Builder(this)
//					.setTitle(typeName)
//					.setMessage(s)
//					.setOnKeyListener(new OnKeyListener() {
//						@Override
//						public boolean onKey(DialogInterface dialog,
//								int keyCode, KeyEvent event) {
//							if (keyCode == KeyEvent.KEYCODE_BACK) {
//								decodeHandler
//										.sendEmptyMessage(ID_RESTART_PREVIEW);
//								return false;
//							}
//							return false;
//						}
//
//					})
//					.setNegativeButton("Close",
//							new DialogInterface.OnClickListener() {
//								public void onClick(DialogInterface dialog,
//										int which) {
//									if (decodeHandler != null) {
//										decodeHandler.sendEmptyMessage(ID_RESTART_PREVIEW);
//										Intent intent = new Intent(ActivityCapture.this, ListActivity.class);
//										intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
//
//
//
//										startActivity(intent);
//
//										finish();
//									}
//
//								}
//							})
//
//					.show();
		/* Analytics */
		/*
		 * Replace "TestTag" in order to send custom tag.
		 */
		/*
		 * if (USE_MWANALYTICS) {
		 * MWBAnalytics.MWB_sendReport(result.encryptedResult, typeName,
		 * "TestTag"); }
		 */
        }
    }

    private void initCamera() {
        if (!isFinishing()) {

            if (ContextCompat.checkSelfPermission(ActivityCapture.this,
                    Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {

                if (ActivityCompat.shouldShowRequestPermissionRationale(
                        ActivityCapture.this, Manifest.permission.CAMERA)) {

                    new AlertDialog.Builder(ActivityCapture.this)
                            .setMessage(
                                    "You need to allow access to the Camera")
                            .setPositiveButton("OK",
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(
                                                DialogInterface dialogInterface,
                                                int i) {
                                            ActivityCompat
                                                    .requestPermissions(
                                                            ActivityCapture.this,
                                                            new String[]{Manifest.permission.CAMERA},
                                                            12322);
                                        }
                                    })
                            .setNegativeButton("Cancel",
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(
                                                DialogInterface dialogInterface,
                                                int i) {
                                            onBackPressed();
                                        }
                                    }).create().show();
                } else {
                    ActivityCompat.requestPermissions(ActivityCapture.this,
                            new String[]{Manifest.permission.CAMERA}, 12322);
                }
            } else {
                try {
                    // Select desired camera resoloution. Not all devices
                    // supports all
                    // resolutions, closest available will be chosen
                    // If not selected, closest match to screen resolution will
                    // be
                    // chosen
                    // High resolutions will slow down scanning proccess on
                    // slower
                    // devices

                    if (MAX_THREADS > 2 || PDF_OPTIMIZED) {
                        CameraManager.setDesiredPreviewSize(1280, 720);
                    } else {
                        CameraManager.setDesiredPreviewSize(800, 480);
                    }

                    CameraManager
                            .get()
                            .openDriver(
                                    surfaceHolder,
                                    (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT));

                    int maxZoom = CameraManager.get().getMaxZoom();
                    if (maxZoom < 100) {
                        zoomButton.setVisibility(View.GONE);
                    } else {
                        zoomButton.setVisibility(View.VISIBLE);
                        if (maxZoom < 300) {
                            secondZoom = maxZoom;
                            firstZoom = (maxZoom - 100) / 2 + 100;

                        }

                    }
                } catch (IOException ioe) {
                    displayFrameworkBugMessageAndExit(ioe.getMessage());
                    return;
                } catch (RuntimeException e) {
                    // Barcode Scanner has seen crashes in the wild of this
                    // variety:
                    // java.?lang.?RuntimeException: Fail to connect to camera
                    // service
                    displayFrameworkBugMessageAndExit(e.getMessage());
                    return;
                }
                Log.i("preview", "start preview.");

                flashOn = false;

                new Handler().postDelayed(new Runnable() {

                    @Override
                    public void run() {
                        switch (zoomLevel) {
                            case 0:
                                CameraManager.get().setZoom(100);
                                break;
                            case 1:
                                CameraManager.get().setZoom(firstZoom);
                                break;
                            case 2:
                                CameraManager.get().setZoom(secondZoom);
                                break;

                            default:
                                break;
                        }

                    }
                }, 300);
                CameraManager.get().startPreview();
                restartPreviewAndDecode();
                updateFlash();
            }

        }

    }

    private void displayFrameworkBugMessageAndExit(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getResources().getIdentifier("app_name", "string",
                package_name));
        builder.setMessage(MSG_CAMERA_FRAMEWORK_BUG + message);
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int i) {
                finish();
            }
        });
        builder.show();
    }


}
