package com.example.vxgplugin;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.ByteBuffer;
import java.util.Objects;

import veg.mediaplayer.sdk.MediaPlayer;
import veg.mediaplayer.sdk.MediaPlayerConfig;

public class MainActivity extends AppCompatActivity implements MediaPlayer.MediaPlayerCallback {

    private static final String TAG = MainActivity.class.getName();
    private static final int REQUEST_TREE = 10;

    private Handler mHandler = new Handler();
    private Handler statusHandler;

    private ProgressBar pbShowLoading;

    MediaPlayer vxgMediaPlayer = null;
    private int mOldMsg =0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Player
        vxgMediaPlayer = (MediaPlayer) findViewById(R.id.playerView);

        //Layouts
        final ConstraintLayout clMediaController = (ConstraintLayout) findViewById(R.id.cl_media_controller);
        final ImageView ivPausePlayBtn = (ImageView) findViewById(R.id.iv_pause);
        final ImageView ivRecordStartStopBtn = (ImageView) findViewById(R.id.iv_record_start);
        final ImageView ivMuteControlBtn = (ImageView) findViewById(R.id.iv_mute);
        //Set mute to false on start
        ivMuteControlBtn.setTag(false);

        //ProgressBar
        pbShowLoading = (ProgressBar) findViewById(R.id.pb_video_loading);

        statusHandler = new StatusHandler();

        MediaPlayerConfig vxgPlayerConfig = new MediaPlayerConfig();
        new Handler(Looper.getMainLooper()).post(() -> {
            pbShowLoading.setVisibility(View.VISIBLE);
        });
        //Player config
        vxgPlayerConfig.setConnectionUrl("rtsp://wowzaec2demo.streamlock.net/vod/mp4:BigBuckBunny_115k.mov");
        vxgPlayerConfig.setDecodingType(1);
        vxgPlayerConfig.setSynchroEnable(1);
        vxgPlayerConfig.setEnableAspectRatio(1);
        vxgPlayerConfig.setConnectionBufferingTime(2500);

//        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
//        startActivityForResult(i, REQUEST_TREE);

        //Recorder config
        vxgPlayerConfig.setRecordPath(getRecordPath());
        vxgPlayerConfig.getRecordSplitTime();
        vxgPlayerConfig.setRecordPrefix("vxg_rec");

        //Player setup
        vxgMediaPlayer.Open(vxgPlayerConfig,this);

        //mute - true – audio is off , false – audio is on.
        boolean isMuted = false;

        vxgMediaPlayer.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.performClick();
                Log.d(TAG, "ACTION_DOWN");
                if (clMediaController != null) {
                    clMediaController.setVisibility(View.VISIBLE);

                    if (vxgMediaPlayer != null &&
                            MediaPlayer.PlayerState.forType(vxgMediaPlayer.getState()) > MediaPlayer.PlayerState.forType(MediaPlayer.PlayerState.Opening) &&
                            MediaPlayer.PlayerState.forType(vxgMediaPlayer.getState()) < MediaPlayer.PlayerState.forType(MediaPlayer.PlayerState.Closing)) {

                        ivPausePlayBtn.setOnClickListener(view -> {
                            if (vxgMediaPlayer.getState() == MediaPlayer.PlayerState.Started) {
                                vxgMediaPlayer.Pause();
                                ivPausePlayBtn.setImageResource(R.drawable.ic_play_button);
                            } else if (vxgMediaPlayer.getState() == MediaPlayer.PlayerState.Paused) {
                                vxgMediaPlayer.Play();
                                ivPausePlayBtn.setImageResource(R.drawable.ic_pause_button);
                            }
                        });

                        ivRecordStartStopBtn.setOnClickListener(view -> {
                            if (vxgMediaPlayer.getState() == MediaPlayer.PlayerState.Started) {
                                //State of recording: 0: stopped, 1: paused, 2: run
                                if (vxgMediaPlayer.RecordGetStat(9) == 0 || vxgMediaPlayer.RecordGetStat(9) == 1) {
                                    vxgMediaPlayer.RecordStart();
                                    ivRecordStartStopBtn.setImageResource(R.drawable.ic_recording_stop);
                                } else if (vxgMediaPlayer.RecordGetStat(9) == 2) {
                                    vxgMediaPlayer.RecordStop();
                                    ivRecordStartStopBtn.setImageResource(R.drawable.ic_record_start);
                                }
                            }
                        });

                        ivMuteControlBtn.setOnClickListener(view -> {
                            if (vxgMediaPlayer != null &&
                                    MediaPlayer.PlayerState.forType(vxgMediaPlayer.getState()) > MediaPlayer.PlayerState.forType(MediaPlayer.PlayerState.Opening) &&
                                    MediaPlayer.PlayerState.forType(vxgMediaPlayer.getState()) < MediaPlayer.PlayerState.forType(MediaPlayer.PlayerState.Closing)) {
                                if (ivMuteControlBtn.getTag().equals(true)) {
                                    vxgMediaPlayer.toggleMute(false);
                                    ivMuteControlBtn.setTag(false);
                                    ivMuteControlBtn.setImageResource(R.drawable.ic_mute);
                                } else if (ivMuteControlBtn.getTag().equals(false)) {
                                    vxgMediaPlayer.toggleMute(true);
                                    ivMuteControlBtn.setTag(true);
                                    ivMuteControlBtn.setImageResource(R.drawable.ic_unmute);
                                }
                            }
                        });
                    }
                    mHandler.postDelayed(() -> clMediaController.setVisibility(View.INVISIBLE), 2000);
                }
            }
            return true;
        });
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

    public String getRecordPath() {
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
                        if (pbShowLoading != null) {
                            if (pbShowLoading.isShown()) pbShowLoading.setVisibility(View.GONE);
                        }
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
                        if (pbShowLoading != null) {
                            if (pbShowLoading.isShown()) pbShowLoading.setVisibility(View.GONE);
                        }
                        break;

                    case VRP_LASTFRAME:
                        showToast(context,"Last frame",Toast.LENGTH_SHORT);
                        break;

                        //Recorder notifications
                    case CP_RECORD_STARTED:
                        Log.d(TAG,"Started recording");
                        showToast(context,"Recording started",Toast.LENGTH_SHORT);
                        //showToast(getBaseContext(),"Recorded saved in "+vxgMediaPlayer.RecordGetFileName(1),Toast.LENGTH_LONG);
                        break;

                    case CP_RECORD_STOPPED:
                        Log.d(TAG,"Stopped recording");
                        showToast(getBaseContext(),"Recorder Stopped",Toast.LENGTH_SHORT);
//                      if (vxgMediaPlayer != null) {
//                            if (vxgMediaPlayer.RecordGetStat(4) == 0) showToast(context,"Storage permission is denied",Toast.LENGTH_LONG);
//                        } else
                        //showToast(getBaseContext(),"Recorded video successfully saved in "+vxgMediaPlayer.RecordGetStat(4),Toast.LENGTH_LONG);
                        break;

                    case CP_RECORD_CLOSED:
                        Log.d(TAG,"Closed recording");
                        showToast(getBaseContext(),"Recorder closed",Toast.LENGTH_SHORT);
                        if (vxgMediaPlayer != null)
                            showToast(getBaseContext(),"Recorded video successfully saved in "+vxgMediaPlayer.RecordGetFileName(0),Toast.LENGTH_LONG);
                        break;
                }

            }
    }

    private void dismissProgressBar() {
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
    protected void onDestroy() {
        if (vxgMediaPlayer != null) vxgMediaPlayer.Close();
        vxgMediaPlayer.onDestroy();

        super.onDestroy();
    }

    public void showToast(Context context,String message,int duration) {
        this.runOnUiThread(() -> Toast.makeText(context,message,duration).show());
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
        return 0;
    }


}