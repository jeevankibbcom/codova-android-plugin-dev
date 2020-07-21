package com.example.vxgplugin;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.example.vxgplugin.utils.Position;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.single.PermissionListener;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import veg.mediaplayer.sdk.MediaPlayer;
import veg.mediaplayer.sdk.MediaPlayerConfig;

public class MainActivity extends AppCompatActivity implements MediaPlayer.MediaPlayerCallback {

    private static final String TAG = MainActivity.class.getName();
    private static final int REQUEST_TREE = 10;
    private static final int RECORDING_REQUEST_CODE = 2;

    private Handler mHandler = new Handler();
    private Handler statusHandler;

    //Layouts
    private ConstraintLayout clMediaController;
    private FrameLayout flFlash;
    private ImageView ivPausePlayBtn;
    private ImageView ivRecordStartStopBtn;
    private ImageView ivMuteControlBtn;
    private ImageView ivRecordingIndicator;
    private ImageView ivVoiceRecordingStartStopBtn;
    private ImageView ivTakeScreenShot;
    //ProgressBar
    private ProgressBar pbShowLoading;
    //Video Player
    MediaPlayer vxgMediaPlayer = null;
    //Media Audio recorder
    MediaRecorder audioRecorder = null;

    private Position liveStreamPos;

    private int mOldMsg =0;
    private static String recordingPermission = Manifest.permission.RECORD_AUDIO;
    private static String storagePermissions = Manifest.permission.WRITE_EXTERNAL_STORAGE;

    //region Lifecycle
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Layouts
        clMediaController = (ConstraintLayout) findViewById(R.id.cl_media_controller);
        flFlash = (FrameLayout) findViewById(R.id.fl_flash_layout);
        ivPausePlayBtn = (ImageView) findViewById(R.id.iv_pause);
        ivRecordStartStopBtn = (ImageView) findViewById(R.id.iv_record_start);
        ivMuteControlBtn = (ImageView) findViewById(R.id.iv_mute);
        ivRecordingIndicator = (ImageView) findViewById(R.id.iv_recording_indicate);
        ivVoiceRecordingStartStopBtn = (ImageView) findViewById(R.id.iv_voice_record_start_stop);
        ivTakeScreenShot = (ImageView) findViewById(R.id.iv_take_video_shot);
        //ProgressBar
        pbShowLoading = (ProgressBar) findViewById(R.id.pb_video_loading);
        //Player
        vxgMediaPlayer = (MediaPlayer) findViewById(R.id.playerView);

        //Set mute to false on start
        ivMuteControlBtn.setTag(false);
        //Set voice recording to false on Start
        ivVoiceRecordingStartStopBtn.setTag(false);

        vxgMediaPlayer.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.performClick();
                Log.d(TAG, "ACTION_DOWN");
                showMediaControls();
            }
            return true;
        });
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (vxgMediaPlayer != null &&
                MediaPlayer.PlayerState.forType(vxgMediaPlayer.getState()) > MediaPlayer.PlayerState.forType(MediaPlayer.PlayerState.Opening) &&
                MediaPlayer.PlayerState.forType(vxgMediaPlayer.getState()) < MediaPlayer.PlayerState.forType(MediaPlayer.PlayerState.Closed)) {
            vxgMediaPlayer.onResume();
            vxgMediaPlayer.Play();
        } else initVxgMediaPlayer();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (vxgMediaPlayer != null &&
                MediaPlayer.PlayerState.forType(vxgMediaPlayer.getState()) > MediaPlayer.PlayerState.forType(MediaPlayer.PlayerState.Opening) &&
                MediaPlayer.PlayerState.forType(vxgMediaPlayer.getState()) < MediaPlayer.PlayerState.forType(MediaPlayer.PlayerState.Closing)) {
            vxgMediaPlayer.Pause();
            vxgMediaPlayer.onPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (vxgMediaPlayer != null &&
                MediaPlayer.PlayerState.forType(vxgMediaPlayer.getState()) > MediaPlayer.PlayerState.forType(MediaPlayer.PlayerState.Opening) &&
                MediaPlayer.PlayerState.forType(vxgMediaPlayer.getState()) < MediaPlayer.PlayerState.forType(MediaPlayer.PlayerState.Closing)) {
            vxgMediaPlayer.onResume();
            vxgMediaPlayer.Play();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (vxgMediaPlayer != null) vxgMediaPlayer.Pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (vxgMediaPlayer != null) vxgMediaPlayer.Close();
        vxgMediaPlayer.onDestroy();
    }
    //endregion

    private void initVxgMediaPlayer() {
        statusHandler = new StatusHandler();

        MediaPlayerConfig vxgPlayerConfig = new MediaPlayerConfig();
        showLoadingPlayerLoader(true);
        //Player config
        vxgPlayerConfig.setConnectionUrl("rtsp://wowzaec2demo.streamlock.net/vod/mp4:BigBuckBunny_115k.mov");
        vxgPlayerConfig.setDecodingType(1);
        vxgPlayerConfig.setSynchroEnable(1);
        vxgPlayerConfig.setEnableAspectRatio(1);
        vxgPlayerConfig.setConnectionBufferingTime(2500);

//        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
//        startActivityForResult(i, REQUEST_TREE);

        //Recorder config
        vxgPlayerConfig.setRecordPath(getRecordPathVideo());
        vxgPlayerConfig.getRecordSplitTime();
        vxgPlayerConfig.setRecordPrefix("vxg_rec");

        //Player startup
        vxgMediaPlayer.Open(vxgPlayerConfig,this);
    }

    private void showLoadingPlayerLoader(boolean showLoader) {
        if (pbShowLoading != null) {
            if (showLoader) pbShowLoading.setVisibility(View.VISIBLE);
            else if (pbShowLoading.isShown()) pbShowLoading.setVisibility(View.GONE);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void showMediaControls() {
        if (clMediaController != null) {
            clMediaController.setVisibility(View.VISIBLE);
            if (vxgMediaPlayer != null &&
                    MediaPlayer.PlayerState.forType(vxgMediaPlayer.getState()) > MediaPlayer.PlayerState.forType(MediaPlayer.PlayerState.Opening) &&
                    MediaPlayer.PlayerState.forType(vxgMediaPlayer.getState()) < MediaPlayer.PlayerState.forType(MediaPlayer.PlayerState.Closing)) {

                ivTakeScreenShot.setOnClickListener(view -> {
                    takeScreenShot();
                });
            }

            if (vxgMediaPlayer != null &&
                    MediaPlayer.PlayerState.forType(vxgMediaPlayer.getState()) > MediaPlayer.PlayerState.forType(MediaPlayer.PlayerState.Opening) &&
                    MediaPlayer.PlayerState.forType(vxgMediaPlayer.getState()) < MediaPlayer.PlayerState.forType(MediaPlayer.PlayerState.Closing)) {

                ivRecordStartStopBtn.setOnClickListener(view -> {
                    if (vxgMediaPlayer.getState() == MediaPlayer.PlayerState.Started) {
                        //State of recording: 0: stopped, 1: paused, 2: run
                        if (vxgMediaPlayer.RecordGetStat(9) == 0 || vxgMediaPlayer.RecordGetStat(9) == 1) {
                            vxgMediaPlayer.RecordStart();
                            ivRecordStartStopBtn.setImageDrawable(getResources().getDrawable(R.drawable.ic_recording_stop,null));
                        } else if (vxgMediaPlayer.RecordGetStat(9) == 2) {
                            vxgMediaPlayer.RecordStop();
                            ivRecordStartStopBtn.setImageDrawable(getResources().getDrawable(R.drawable.ic_record_start,null));
                        }
                    }
                });

                ivPausePlayBtn.setOnClickListener(view -> {
                    if (vxgMediaPlayer.getState() == MediaPlayer.PlayerState.Started) {
                        vxgMediaPlayer.Pause();
                        ivPausePlayBtn.setImageDrawable(getResources().getDrawable(R.drawable.ic_play_button,null));
                    } else if (vxgMediaPlayer.getState() == MediaPlayer.PlayerState.Paused) {
                        vxgMediaPlayer.Play();
                        ivPausePlayBtn.setImageDrawable(getResources().getDrawable(R.drawable.ic_pause_button,null));
                    }
                });

                ivMuteControlBtn.setOnClickListener(view -> {
                    //mute - true – audio is off , false – audio is on.
                    if (vxgMediaPlayer != null &&
                            MediaPlayer.PlayerState.forType(vxgMediaPlayer.getState()) > MediaPlayer.PlayerState.forType(MediaPlayer.PlayerState.Opening) &&
                            MediaPlayer.PlayerState.forType(vxgMediaPlayer.getState()) < MediaPlayer.PlayerState.forType(MediaPlayer.PlayerState.Closing)) {
                        if (ivMuteControlBtn.getTag().equals(true)) {
                            vxgMediaPlayer.toggleMute(false);
                            ivMuteControlBtn.setTag(false);
                            ivMuteControlBtn.setImageDrawable(getResources().getDrawable(R.drawable.ic_mute,null));
                        } else if (ivMuteControlBtn.getTag().equals(false)) {
                            vxgMediaPlayer.toggleMute(true);
                            ivMuteControlBtn.setTag(true);
                            ivMuteControlBtn.setImageDrawable(getResources().getDrawable(R.drawable.ic_unmute,null));
                        }
                    }
                });

                ivVoiceRecordingStartStopBtn.setOnClickListener(view -> {
                    if (ivVoiceRecordingStartStopBtn.getTag().equals(true)) {
                        stopVoiceRecording();
                        ivVoiceRecordingStartStopBtn.setImageDrawable(getResources().getDrawable(R.drawable.ic_voice_not_recording));
                        ivVoiceRecordingStartStopBtn.setTag(false);
                    } else if (ivVoiceRecordingStartStopBtn.getTag().equals(false)) {
                        if (checkVoiceRecordingPermissions() && checkStoragePermissions()) {
                            startVoiceRecording();
                            ivVoiceRecordingStartStopBtn.setImageDrawable(getResources().getDrawable(R.drawable.ic_voice_recording));
                            ivVoiceRecordingStartStopBtn.setTag(true);
                        }
                    }
                });
            }
            //Remove previous touch handler callback
            mHandler.removeCallbacks(mRunnableHideMediaControls);
            //Add new touch handler callback
            mHandler.postDelayed(mRunnableHideMediaControls, 3000);
        }
    }
    //Hide media controls
    //Ref ---->  https://stackoverflow.com/a/60523789/10459992
    Runnable mRunnableHideMediaControls = () -> {
        if (clMediaController != null) clMediaController.setVisibility(View.INVISIBLE);
    };

    private void takeScreenShot() {

//        String videoShotPath = getVideoShotPath();
//        if (videoShotPath.isEmpty()) {
//            showToast(this.getBaseContext(), "Storage permissions denied..", Toast.LENGTH_LONG);
//        }
        String videoShootPrefixName = "wifido_IMG_";

        //Get current time to append to filename
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd_hh_mm_ss", Locale.ENGLISH); //Format of date required
        Date dateNow = new Date();
        String recordFileName = videoShootPrefixName+dateFormat.format(dateNow);

        ContentResolver resolver = getApplication().getContentResolver();
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.Images.Media.TITLE, recordFileName);
        contentValues.put(MediaStore.Images.Media.DISPLAY_NAME, recordFileName);
        contentValues.put(MediaStore.Images.Media.DESCRIPTION, recordFileName);
        contentValues.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        // Add the date meta data to ensure the image is added at the front of the gallery
        contentValues.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis());
        contentValues.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());

        int width = 640;
        int height = 480;

        MediaPlayer.VideoShot frame = vxgMediaPlayer.getVideoShot(width, height);
        if (frame != null) {
            Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bmp.copyPixelsFromBuffer(frame.getData());
            try {
                Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
                assert uri != null;
                try (OutputStream imageOutputStream = resolver.openOutputStream(uri)) {
                    bmp.compress(Bitmap.CompressFormat.JPEG, 50, imageOutputStream);
                    showScreenShotAnimation();
                    //showToast(getBaseContext(),"Screenshot taken..",Toast.LENGTH_SHORT);
                }
            } catch (Exception e) {
                showToast(getBaseContext(),"Error capturing video shot",Toast.LENGTH_LONG);
            }
        }
    }

    private void showScreenShotAnimation() {
        flFlash.setVisibility(View.VISIBLE);
        AlphaAnimation fade = new AlphaAnimation(1, 0);
        fade.setDuration(50);
        flFlash.setLayoutAnimationListener(new Animation.AnimationListener() {

            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                if (flFlash.getVisibility() == View.VISIBLE) flFlash.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });
        flFlash.startAnimation(fade);
    }


    private void startVoiceRecording() {
        String audioPath = getRecordPathAudio();
        if (audioPath.isEmpty()) {
            showToast(this.getBaseContext(), "Storage permissions denied..", Toast.LENGTH_LONG);
        }
        String audioPrefixName = "wifido_audio_";

        //Get current time to append to filename
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd_hh_mm_ss", Locale.ENGLISH); //Format of date required
        Date dateNow = new Date();
        String recordFileName = audioPrefixName+dateFormat.format(dateNow)+".mp4";

        audioRecorder = new MediaRecorder();
        audioRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        audioRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        audioRecorder.setOutputFile(audioPath+"/"+recordFileName);
        audioRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

        try {
            audioRecorder.prepare();
        } catch (IOException e) {
            Log.d(TAG,"Audio recording start exception ---> \n"+e);
        }
        try {
            audioRecorder.start();
        } catch (Exception e) {
            Log.d(TAG,"Audio recording start exception---> Permission denied---> \n"+e);
        }

    }

    private void stopVoiceRecording() {
        if (audioRecorder != null ) {
            try {
                audioRecorder.stop();
                audioRecorder.release();
            } catch (Exception e) {
                Log.d(TAG,"Audio recording start exception---> Permission denied---> \n"+e);
            }
            audioRecorder = null;
        }
    }

    private void recordingAnimation(boolean startAnimation) {
        ivRecordingIndicator.setVisibility(View.VISIBLE);
        Animation anim = new AlphaAnimation(0.0f, 1.0f);
        anim.setDuration(800); //You can manage the blinking time with this parameter
        anim.setStartOffset(20);
        anim.setRepeatMode(Animation.REVERSE);
        anim.setRepeatCount(Animation.INFINITE);
        ivRecordingIndicator.setAnimation(anim);
        if (startAnimation) {
            anim.start();
        } else {
            anim.cancel();
            anim.reset();
            if (ivRecordingIndicator.getVisibility() == View.VISIBLE) ivRecordingIndicator.setVisibility(View.GONE);
        }
    }

    private boolean checkStoragePermissions() {
        //If permissions are already granted return true
        boolean[] isPermissionGranted = {false};
        Dexter.withContext(this).withPermission(storagePermissions).withListener(
                new PermissionListener() {
                    @Override
                    public void onPermissionGranted(PermissionGrantedResponse permissionGrantedResponse) {
                        //Permission granted
                        isPermissionGranted[0] = true;
                    }

                    @Override
                    public void onPermissionDenied(PermissionDeniedResponse permissionDeniedResponse) {
                        //Permissions are permanently denied TODO: Implement GOTO app settings
                        isPermissionGranted[0] = false;
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(PermissionRequest permissionRequest, PermissionToken permissionToken) {
                        //Permission denied request again
                        permissionToken.continuePermissionRequest();
                    }
                }
        ).check();
        return isPermissionGranted[0];
    }

    private boolean checkVoiceRecordingPermissions() {
        //If permissions are already granted return true
        boolean[] isPermissionGranted = {false};
        Dexter.withContext(this).withPermission(recordingPermission).withListener(
                new PermissionListener() {
                    @Override
                    public void onPermissionGranted(PermissionGrantedResponse permissionGrantedResponse) {
                        //Permission granted
                        isPermissionGranted[0] = true;
                    }

                    @Override
                    public void onPermissionDenied(PermissionDeniedResponse permissionDeniedResponse) {
                        //Permissions are permanently denied TODO: Implement GOTO app settings
                        isPermissionGranted[0] = false;
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(PermissionRequest permissionRequest, PermissionToken permissionToken) {
                        //Permission denied request again
                        permissionToken.continuePermissionRequest();
                    }
                }
        ).check();
        return isPermissionGranted[0];
    }

    //    region Recorder path MediaStoreAPI for android 10  --> not working
    public String getRecorderPathQ() {
        String videoPath = "/Wifido/User/Wifido_Videos";
        ContentResolver resolver = getApplication().getContentResolver();
        File mediaStorageDir = null;
        ContentValues contentValues = new ContentValues();
//        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, "vxg_recordings");
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
        contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM);
        contentValues.put(MediaStore.Video.Media.IS_PENDING,1);
        Uri root = resolver.insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL), contentValues);
        try {
            resolver.openOutputStream(root);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            contentValues.clear();
            contentValues.put(MediaStore.Video.Media.IS_PENDING,0);
            resolver.update(root,contentValues,null,null);
        }
        //String root = getExternalFilesDir(Environment.DIRECTORY_DCIM).toString();
        mediaStorageDir = new File(root.getPath());
        mediaStorageDir.mkdirs();

        if (mediaStorageDir == null || !mediaStorageDir.exists()) {
            if (!(mediaStorageDir.mkdirs() || mediaStorageDir.isDirectory())) {
                Log.e(TAG, "<=getRecordPath() failed to create directory path=" + mediaStorageDir.getPath());
                return "";
            }
        }
        return mediaStorageDir.getPath();
    }
    //endregion

    public String getVideoShotPath() {
        String videoPath = "/Wifido/User/Wifido_VideoShots";
        String root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString();
        File mediaStorageDir = new File(root + videoPath);
        mediaStorageDir.mkdirs();

        if (! mediaStorageDir.exists()){
            if (!(mediaStorageDir.mkdirs() || mediaStorageDir.isDirectory())){
                Log.e(TAG, "<=getRecordPath() failed to create directory path="+mediaStorageDir.getPath());
                return "";
            }
        }
        return mediaStorageDir.getPath();
    }

    public String getRecordPathVideo() {
        String videoPath = "/Wifido/User/Wifido_Videos";
        String root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString();
        File mediaStorageDir = new File(root + videoPath);
        mediaStorageDir.mkdirs();

        if (! mediaStorageDir.exists()){
            if (!(mediaStorageDir.mkdirs() || mediaStorageDir.isDirectory())){
                Log.e(TAG, "<=getRecordPath() failed to create directory path="+mediaStorageDir.getPath());
                return "";
            }
        }
        return mediaStorageDir.getPath();
    }

    public String getRecordPathAudio() {
        String audioPath = "/Wifido/User/Wifido_Audios";
        String root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString();
        File mediaStorageDir = new File(root + audioPath);
        mediaStorageDir.mkdirs();

        if (!mediaStorageDir.exists()) {
            if (!(mediaStorageDir.mkdirs() || mediaStorageDir.isDirectory())) {
                Log.e(TAG, "<=getRecordPath() failed to create directory path=" + mediaStorageDir.getPath());
                return "";
            }
        }
        return mediaStorageDir.getPath();
    }


    public void showToast(Context context,String message,int duration) {
        this.runOnUiThread(() -> Toast.makeText(context,message,duration).show());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && requestCode == REQUEST_TREE) {
            if (data.getData() != null) Log.d(TAG,"On data received="+data.getData().getPath());
        }
    }

    private class StatusHandler extends Handler {
            @Override
            public void handleMessage(@NonNull Message msg) {
                MediaPlayer.PlayerNotifyCodes status = (MediaPlayer.PlayerNotifyCodes) msg.obj;
                Context context = getBaseContext();

                switch (status) {
                    //Player notifications
                    case CP_INIT_FAILED:
                        showLoadingPlayerLoader(false);
                        showToast(context,"Check network",Toast.LENGTH_LONG);
                        break;

                    case CP_ERROR_NODATA_TIMEOUT:
                        showToast(context,"Network timeout",Toast.LENGTH_LONG);
                        if (vxgMediaPlayer != null &&
                                MediaPlayer.PlayerState.forType(vxgMediaPlayer.getState()) > MediaPlayer.PlayerState.forType(MediaPlayer.PlayerState.Opening) &&
                                MediaPlayer.PlayerState.forType(vxgMediaPlayer.getState()) < MediaPlayer.PlayerState.forType(MediaPlayer.PlayerState.Closing)) {
                            vxgMediaPlayer.Close();
                        }
                        break;

                    case PLP_BUILD_SUCCESSFUL:
                        showLoadingPlayerLoader(false);
                        break;

                    case VRP_LASTFRAME:
                        showToast(context,"Last frame",Toast.LENGTH_SHORT);
                        break;

                        //Recorder notifications
                    case CP_RECORD_STARTED:
                        Log.d(TAG,"Started recording");
                        recordingAnimation(true);
                        //showToast(getBaseContext(),"Recorded saved in "+vxgMediaPlayer.RecordGetFileName(1),Toast.LENGTH_LONG);
                        break;

                    case CP_RECORD_STOPPED:
                        Log.d(TAG,"Stopped recording");
                        recordingAnimation(false);
//                      if (vxgMediaPlayer != null) {
//                            if (vxgMediaPlayer.RecordGetStat(4) == 0) showToast(context,"Storage permission is denied",Toast.LENGTH_LONG);
//                        } else
                        //showToast(getBaseContext(),"Recorded video successfully saved in "+vxgMediaPlayer.RecordGetStat(4),Toast.LENGTH_LONG);
                        break;

                    case CP_RECORD_CLOSED:
                        Log.d(TAG,"Closed recording");
                        recordingAnimation(false);
                        if (vxgMediaPlayer != null)
                            showToast(getBaseContext(),"Recorded video successfully saved in "+vxgMediaPlayer.RecordGetFileName(0),Toast.LENGTH_LONG);
                        break;
                }

            }
    }

    @Override
    public int Status(int i) {
        MediaPlayer.PlayerNotifyCodes status = MediaPlayer.PlayerNotifyCodes.forValue(i);
        Log.d(TAG, "Status = " + status);
            Message msg = new Message();
            msg.obj = status;
            msg.what = 1;
            statusHandler.removeMessages(this.mOldMsg);
            this.mOldMsg = msg.what;
            statusHandler.sendMessage(msg);

        return 0;
    }

    @Override
    public int OnReceiveData(ByteBuffer byteBuffer, int i, long l) {
        Log.d(TAG,"OnReceiveData"+byteBuffer.toString());
        return 0;
    }


}