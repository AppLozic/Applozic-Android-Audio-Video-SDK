package com.applozic.audiovideo.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.applozic.audiovideo.authentication.Dialog;
import com.applozic.audiovideo.core.CallConstants;
import com.applozic.audiovideo.core.RoomApplozicManager;
import com.applozic.audiovideo.listener.AudioVideoUICallback;
import com.applozic.audiovideo.listener.PostRoomEventsListener;
import com.applozic.audiovideo.listener.PostRoomParticipantEventsListener;
import com.applozic.audiovideo.service.CallService;
import com.applozic.mobicomkit.contact.AppContactService;
import com.applozic.mobicommons.commons.core.utils.Utils;
import com.applozic.mobicommons.commons.image.ImageLoader;
import com.applozic.mobicommons.people.contact.Contact;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.twilio.video.CameraCapturer;
import com.twilio.video.LocalAudioTrack;
import com.twilio.video.LocalVideoTrack;
import com.twilio.video.RemoteParticipant;
import com.twilio.video.RemoteVideoTrack;
import com.twilio.video.RemoteVideoTrackPublication;
import com.twilio.video.Room;
import com.twilio.video.VideoView;

import applozic.com.audiovideo.R;

import static com.twilio.video.Room.State.CONNECTED;

/**
 * This activity is the base activity for an ongoing audio/video call.
 *
 * <p>Call logic is present in {@link CallService}. This class handles the base
 * audio call <i>view</i>. For the video call <i>view</i> refer to {@link VideoActivity}. Incoming calls
 * are handled by {@link CallActivity}.</p>
 *
 * <p>This activity and the sub-class {@link VideoActivity} communicates with the
 * {@link CallService} through three callbacks:
 * 1. {@link AudioCallActivityV2#postRomParticipantEventsListener}
 * 2. {@link AudioCallActivityV2#postRoomEventsListener}
 * 3. {@link AudioCallActivityV2#audioVideoUICallback}</p>
 *
 * Created by Adarsh on 12/15/16.
 * Updated by Shubham Tewari (shubham@applozic.com)
 */

public class AudioCallActivityV2 extends AppCompatActivity {
    private static final String TAG = "AudioCallActivityV2";
    private static final int CAMERA_MIC_PERMISSION_REQUEST_CODE = 1000;

    protected VideoView primaryVideoView;
    protected VideoView thumbnailVideoView;
    protected VideoView localVideoView;

    protected FloatingActionButton connectActionFab;
    protected FloatingActionButton switchCameraActionFab;
    protected FloatingActionButton localVideoActionFab;
    protected FloatingActionButton muteActionFab;
    protected FloatingActionButton speakerActionFab;

    protected AudioManager audioManager; //speaker control
    protected MediaPlayer mediaPlayer; //for ringtone
    protected ProgressDialog loading;
    protected TextView contactName;
    protected ImageView profileImage;
    protected ImageLoader imageLoader;
    protected TextView callTimerText;
    protected AlertDialog alertDialog;

    private int timerTickCount;
    protected boolean videoCall = false;
    protected String userIdContactCalled;
    protected Contact contactCalled;
    protected String callId;
    protected boolean received;
    protected boolean activityStartedFromNotification;
    protected boolean disconnectedFromOnDestroy;

    protected AppContactService contactService;
    protected CallService callService;

    protected CountDownTimer timer;

    public AudioCallActivityV2() {
        this.videoCall = false;
    }

    public AudioCallActivityV2(boolean videoCall) {
        this.videoCall = videoCall;
    }

    final private PostRoomEventsListener postRoomEventsListener = new PostRoomEventsListener() {
        @Override
        public void afterRoomConnected(Room room) {
            AudioCallActivityV2.this.afterRoomConnected(room);
        }

        @Override
        public void afterRoomDisconnected(Room room) {
            AudioCallActivityV2.this.afterRoomDisconnected(room);
        }

        @Override
        public void afterRoomConnectionFailure() {
            AudioCallActivityV2.this.afterRoomConnectionFailure();
        }

        @Override
        public void afterReconnecting() { }

        @Override
        public void afterConnectionReestablished() { }

        @Override
        public void afterParticipantConnected(RemoteParticipant remoteParticipant) {
            AudioCallActivityV2.this.afterParticipantConnected(remoteParticipant);
        }

        @Override
        public void afterParticipantDisconnected(RemoteParticipant remoteParticipant) {
            AudioCallActivityV2.this.afterParticipantDisconnected(remoteParticipant);
        }
    };

    final private PostRoomParticipantEventsListener postRomParticipantEventsListener = new PostRoomParticipantEventsListener() {
        @Override
        public void afterVideoTrackSubscribed(RemoteVideoTrack videoTrack) {
            addRemoteParticipantVideo(videoTrack);
        }

        @Override
        public void afterVideoTrackUnsubscribed(RemoteVideoTrack videoTrack) {
            removeParticipantVideo(videoTrack);
        }

        @Override
        public void afterParticipantConnectedToCall(RemoteVideoTrackPublication remoteVideoTrackPublication) {
            addRemoteParticipantVideo(remoteVideoTrackPublication.getRemoteVideoTrack());
        }

        @Override
        public void afterParticipantDisconnectedFromCall(RemoteVideoTrackPublication remoteVideoTrackPublication) {
            removeParticipantVideo(remoteVideoTrackPublication.getRemoteVideoTrack());
        }
    };

    final private AudioVideoUICallback audioVideoUICallback = new AudioVideoUICallback() {
        @Override
        public void noAnswer(RoomApplozicManager roomApplozicManager) {
            hideProgress();
            disconnectAndExit(roomApplozicManager);
        }

        @Override
        public void callConnectionFailure(RoomApplozicManager roomApplozicManager) {
            hideProgress();
            disconnectAndExit(roomApplozicManager);
        }

        @Override
        public void disconnectAction(LocalVideoTrack localVideoTrack, CameraCapturer cameraCapturer) {
            setDisconnectAction(localVideoTrack, cameraCapturer);
        }

        @Override
        public void connectingCall(String callId, boolean isReceived) { }
    };

    protected void init() {
        Intent intent = getIntent();

        userIdContactCalled = intent.getStringExtra(CallConstants.CONTACT_ID);
        received = intent.getBooleanExtra(CallConstants.INCOMING_CALL, Boolean.FALSE);
        callId = intent.getStringExtra(CallConstants.CALL_ID);
        activityStartedFromNotification = intent.getBooleanExtra(CallConstants.CALL_ACTIVITY_STARTED_FROM_NOTIFICATION, Boolean.FALSE);

        Log.d(TAG, "Call id from intent: " + callId);
        Log.d(TAG, "Started from notification: " + activityStartedFromNotification);
        Log.i(TAG, "isVideoCall? " + videoCall);
        Log.i(TAG, "Contact Id of use called: " + userIdContactCalled);

        contactService = new AppContactService(this);
        contactCalled = contactService.getContactById(userIdContactCalled);
    }

    protected void unBindWithService() {
        if(callService != null) {
            callService.setAudioVideoUICallback(null);
            callService.setPostRoomParticipantEventsListener(null);
            callService.setPostRoomEventsListener(null);
        }
        callService = null;
    }

    protected void setupAndStartCallService() {
        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

        if (!Utils.isInternetAvailable(this)) {
            Toast toast = Toast.makeText(this, getString(R.string.internet_connection_not_available), Toast.LENGTH_SHORT);
            toast.setGravity(Gravity.CENTER, 0, 0);
            toast.show();
            finish();
            return;
        }

        Intent intent = new Intent(this, CallService.class);

        mediaPlayer = MediaPlayer.create(this, R.raw.hangouts_video_call);
        mediaPlayer.setLooping(true);

        if (activityStartedFromNotification || callServiceIsRunningInForeground(this)) {
            timer = initializeTimer();
        } else {
            timer = initializeTimer();

            intent.putExtra(CallConstants.CONTACT_ID, userIdContactCalled);
            intent.putExtra(CallConstants.CALL_ID, callId);
            intent.putExtra(CallConstants.INCOMING_CALL, received);
            intent.putExtra(CallConstants.VIDEO_CALL, videoCall);

            if (received) {
                loading = new ProgressDialog(AudioCallActivityV2.this);
                loading.setMessage(getString(R.string.connecting));
                loading.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                loading.setIndeterminate(true);
                loading.setCancelable(false);
                loading.show();
            } else {
                mediaPlayer.start();
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        }

        bindService(intent, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.d(TAG, "Call Service Connected.");
                CallService.AudioVideoCallBinder audioVideoCallBinder = (CallService.AudioVideoCallBinder) service;
                callService = audioVideoCallBinder.getCallService();
                if(callService != null) {
                    Log.d(TAG, "Call Service attached to Audio Call Activity: " + callService.getRoomApplozicManager().getOneToOneCall().getCallId());
                    callService.setAudioVideoUICallback(audioVideoUICallback);
                    callService.setPostRoomParticipantEventsListener(postRomParticipantEventsListener);
                    callService.setPostRoomEventsListener(postRoomEventsListener);
                    initializeUI(callService.getRoomApplozicManager());
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                unBindWithService();
            }
        }, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        init();

        if (videoCall) {
            Utils.printLog(this, TAG, "This is a video call. Returning.");
            return;
        }

        setContentView(R.layout.applozic_audio_call);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        contactName = (TextView) findViewById(R.id.contact_name);
        profileImage = (ImageView) findViewById(R.id.applozic_audio_profile_image);
        callTimerText = (TextView) findViewById(R.id.applozic_audio_timer);

        if(contactCalled != null) {
            contactName.setText(contactCalled.getDisplayName());

            imageLoader = new ImageLoader(this, profileImage.getHeight()) {
                @Override
                protected Bitmap processBitmap(Object data) {
                    return contactService.downloadContactImage(AudioCallActivityV2.this, (Contact) data);
                }
            };
            imageLoader.setLoadingImage(R.drawable.applozic_ic_contact_picture_holo_light);
            // Add a cache to the image loader
            imageLoader.setImageFadeIn(false);
            imageLoader.loadImage(contactCalled, profileImage);
        }

        primaryVideoView = (VideoView) findViewById(R.id.primary_video_view);
        thumbnailVideoView = (VideoView) findViewById(R.id.thumbnail_video_view);

        connectActionFab = (FloatingActionButton) findViewById(R.id.call_action_fab);
        muteActionFab = (FloatingActionButton) findViewById(R.id.mute_action_fab);
        speakerActionFab = (FloatingActionButton) findViewById(R.id.speaker_action_fab);

        /*
         * Needed for setting/abandoning audio focus during call
         */
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        /*
         * Check camera and microphone permissions. Needed in Android M.
         */
        if (!checkPermissionForCameraAndMicrophone()) {
            requestPermissionForCameraAndMicrophone();
        } else {
            setupAndStartCallService();
        }
    }

    protected boolean checkPermissionForCameraAndMicrophone() {
        int resultCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        int resultMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        return resultCamera == PackageManager.PERMISSION_GRANTED &&
                resultMic == PackageManager.PERMISSION_GRANTED;
    }

    protected void requestPermissionForCameraAndMicrophone() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA) ||
                ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.RECORD_AUDIO)) {
            Toast.makeText(this,
                    R.string.permissions_needed,
                    Toast.LENGTH_LONG).show();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                    CAMERA_MIC_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == CAMERA_MIC_PERMISSION_REQUEST_CODE) {
            boolean cameraAndMicPermissionGranted = true;

            for (int grantResult : grantResults) {
                cameraAndMicPermissionGranted &= grantResult == PackageManager.PERMISSION_GRANTED;
            }

            if (cameraAndMicPermissionGranted) {
                setupAndStartCallService();
            } else {
                Toast.makeText(this,
                        R.string.permissions_needed,
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        /*
         * If the local video track was released when the app was put in the background, recreate.
         */
        try {
            if(callService == null) {
                return;
            }
            RoomApplozicManager roomApplozicManager = callService.getRoomApplozicManager();
            LocalVideoTrack localVideoTrack = roomApplozicManager.getLocalVideoTrack();
            if (videoCall) {
                if (localVideoTrack == null && checkPermissionForCameraAndMicrophone()) {
                    localVideoTrack = roomApplozicManager.createAndReturnLocalVideoTrack();
                    localVideoTrack.addRenderer(localVideoView);
                    roomApplozicManager.publishLocalVideoTrack();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        if(callService == null) {
            return;
        }
        callService.getRoomApplozicManager().unPublishLocalVideoTrack();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        disconnectedFromOnDestroy = true;
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
        }
        super.onDestroy();
        unBindWithService();
    }

    protected void initializeUI(RoomApplozicManager roomApplozicManager) {
        connectActionFab.setImageDrawable(ContextCompat.getDrawable(this,
                R.drawable.ic_call_end_white_24px));
        connectActionFab.show();
        connectActionFab.setOnClickListener(disconnectClickListener());
        if (videoCall) {
            switchCameraActionFab.show();
            switchCameraActionFab.setOnClickListener(switchCameraClickListener(roomApplozicManager.getCameraCapturer()));
            localVideoActionFab.show();
            localVideoActionFab.setOnClickListener(localVideoPauseClickListener(roomApplozicManager.getLocalVideoTrack()));

            roomApplozicManager.getLocalVideoTrack().addRenderer(primaryVideoView);
            localVideoView = primaryVideoView;
            primaryVideoView.setMirror(true);
        }
        muteActionFab.show();
        muteActionFab.setOnClickListener(muteClickListener(roomApplozicManager.getLocalAudioTrack()));
        speakerActionFab.setOnClickListener(speakerClickListener());
    }

    /*
     * The actions performed during disconnect.
     */
    protected void setDisconnectAction(LocalVideoTrack localVideoTrack, CameraCapturer cameraCapturer) {
        connectActionFab.setImageDrawable(ContextCompat.getDrawable(this,
                R.drawable.ic_call_end_white_24px));
        connectActionFab.show();
        connectActionFab.setOnClickListener(disconnectClickListener());
    }

    /*  Set primary view as renderer for participant video track
    */
    protected void addRemoteParticipantVideo(RemoteVideoTrack videoTrack) {
        if (videoCall) {
            if(callService == null) {
                return;
            }
            RoomApplozicManager roomApplozicManager = callService.getRoomApplozicManager();
            moveLocalVideoToThumbnailView(roomApplozicManager.getLocalVideoTrack(), roomApplozicManager.getCameraCapturer());
            primaryVideoView.setMirror(false);
            videoTrack.addRenderer(primaryVideoView);
        }
    }

    protected void moveLocalVideoToThumbnailView(LocalVideoTrack localVideoTrack, CameraCapturer cameraCapturer) {
        try {
            if (thumbnailVideoView.getVisibility() == View.GONE) {
                thumbnailVideoView.setVisibility(View.VISIBLE);
                if (localVideoTrack != null) {
                    localVideoTrack.removeRenderer(primaryVideoView);
                    localVideoTrack.addRenderer(thumbnailVideoView);
                    localVideoView = thumbnailVideoView;
                    thumbnailVideoView.setMirror(cameraCapturer.getCameraSource() ==
                            CameraCapturer.CameraSource.FRONT_CAMERA);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void removeParticipantVideo(RemoteVideoTrack remoteVideoTrack) {
        if (remoteVideoTrack != null) {
            remoteVideoTrack.removeRenderer(primaryVideoView);
        }
    }

    protected void moveLocalVideoToPrimaryView(LocalVideoTrack localVideoTrack, CameraCapturer cameraCapturer) {
        try {
            if (thumbnailVideoView.getVisibility() == View.VISIBLE) {
                if (localVideoTrack != null) {
                    localVideoTrack.removeRenderer(thumbnailVideoView);
                    thumbnailVideoView.setVisibility(View.GONE);
                    localVideoTrack.addRenderer(primaryVideoView);
                    localVideoView = primaryVideoView;
                    primaryVideoView.setMirror(cameraCapturer.getCameraSource() ==
                            CameraCapturer.CameraSource.FRONT_CAMERA);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private View.OnClickListener disconnectClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(callService == null) {
                    return;
                }
                RoomApplozicManager roomApplozicManager = callService.getRoomApplozicManager();
                if (roomApplozicManager.isCallRinging()) { //i.e invite is sent but call is not connected yet
                    roomApplozicManager.getOneToOneCall().setInviteSent(false);
                    roomApplozicManager.sendApplozicMissedCallNotification();
                }
                disconnectAndExit(roomApplozicManager);
            }
        };
    }

    private void disconnectAndExit(RoomApplozicManager roomApplozicManager) {
        if (roomApplozicManager.getRoom() != null) {
            roomApplozicManager.disconnectRoom();
        } else {
            finish();
        }
    }

    private View.OnClickListener switchCameraClickListener(CameraCapturer cameraCapturer) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if (cameraCapturer != null) {
                        CameraCapturer.CameraSource cameraSource = cameraCapturer.getCameraSource();
                        cameraCapturer.switchCamera();
                        if (thumbnailVideoView.getVisibility() == View.VISIBLE) {
                            thumbnailVideoView.setMirror(cameraSource == CameraCapturer.CameraSource.BACK_CAMERA);
                        } else {
                            primaryVideoView.setMirror(cameraSource == CameraCapturer.CameraSource.BACK_CAMERA);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
    }

    private View.OnClickListener localVideoPauseClickListener(LocalVideoTrack localVideoTrack) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (localVideoTrack != null) {
                    boolean enable = !localVideoTrack.isEnabled();
                    localVideoTrack.enable(enable);
                    int icon;
                    if (enable) {
                        icon = R.drawable.ic_videocam_green_24px;
                        switchCameraActionFab.show();
                    } else {
                        icon = R.drawable.ic_videocam_off_red_24px;
                        switchCameraActionFab.hide();
                    }
                    localVideoActionFab.setImageDrawable(
                            ContextCompat.getDrawable(AudioCallActivityV2.this, icon));
                }
            }
        };
    }

    private View.OnClickListener muteClickListener(LocalAudioTrack localAudioTrack) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                /*
                 * Enable/disable the local audio track
                 */
                if (localAudioTrack != null) {
                    boolean enable = !localAudioTrack.isEnabled();
                    localAudioTrack.enable(enable);
                    int icon = enable ?
                            R.drawable.ic_mic_green_24px : R.drawable.ic_mic_off_red_24px;
                    muteActionFab.setImageDrawable(ContextCompat.getDrawable(
                            AudioCallActivityV2.this, icon));
                }
            }
        };
    }

    @NonNull
    public CountDownTimer initializeTimer() {
        return new CountDownTimer(Long.MAX_VALUE, 1000) {
            @SuppressLint("DefaultLocale")
            @Override
            public void onTick(long millisUntilFinished) {
                timerTickCount++;
                int seconds = (timerTickCount);
                int hrs = seconds / (60 * 60);
                int minutes = seconds / 60;
                seconds = seconds % 60;
                callTimerText.setText(String.format("%d:%02d:%02d", hrs, minutes, seconds));
            }

            @Override
            public void onFinish() { }
        };
    }

    protected void hideProgress() {
        try {
            if (alertDialog != null && alertDialog.isShowing()) {
                alertDialog.dismiss();
            }
            if (loading != null && loading.isShowing()) {
                loading.dismiss();
            }
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Override
    public void onBackPressed() {
        if(callService == null) {
            return;
        }
        RoomApplozicManager roomApplozicManager = callService.getRoomApplozicManager();
        Room room = roomApplozicManager.getRoom();
        if (room != null && room.getState().equals(CONNECTED)) { //room is connected
            alertDialog = Dialog.createCloseSessionDialog((dialog, which) -> Log.i(TAG, "Alert dialog cancel pressed."), closeSessionListener(roomApplozicManager), this);
            alertDialog.show();

        } else if (room != null && !room.getState().equals(CONNECTED)) {
            Log.i(TAG, "Room is null or not connected.");
        } else {
            super.onBackPressed();
        }
    }

    private DialogInterface.OnClickListener closeSessionListener(RoomApplozicManager roomApplozicManager) {
        return new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                roomApplozicManager.disconnectRoom();
            }
        };
    }

    private View.OnClickListener speakerClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setSpeakerphoneOn(!audioManager.isSpeakerphoneOn());
            }
        };
    }

    protected void setSpeakerphoneOn(boolean onOrOff) {
        try {
            if (audioManager != null) {
                audioManager.setSpeakerphoneOn(onOrOff);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (onOrOff) {
            Drawable drawable = ContextCompat.getDrawable(this,
                    R.drawable.ic_volume_down_green_24px);
            speakerActionFab.setImageDrawable(drawable);
        } else {
            // route back to headset
            Drawable drawable = ContextCompat.getDrawable(this,
                    R.drawable.ic_volume_down_white_24px);
            speakerActionFab.setImageDrawable(drawable);
        }
    }

    public void afterRoomConnected(Room room) {
        if(room == null) {
            return;
        }
        setTitle(room.getName());
        setSpeakerphoneOn(videoCall);
        for(RemoteParticipant remoteParticipant : room.getRemoteParticipants()) {
            /*
             * This app only displays video for one participant
             */
            if (thumbnailVideoView.getVisibility() == View.VISIBLE) {
                Snackbar.make(connectActionFab,
                        R.string.multiple_participants_not_available,
                        Snackbar.LENGTH_LONG).show();
                return;
            }
            hideProgress();
            if (!videoCall && timer != null) {
                timer.start();
            }
        }
    }

    public void afterRoomDisconnected(Room room) {
        if (!videoCall && timer != null) {
            timer.cancel();
        }
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
        finish();
    }

    public void afterRoomConnectionFailure() {
        hideProgress();
        finish();
    }

    public void afterReconnecting() {

    }

    public void afterConnectionReestablished() {

    }

    public void afterParticipantConnected(RemoteParticipant remoteParticipant) {
        /*
         * This app only displays video for one participant
         */
        if (thumbnailVideoView.getVisibility() == View.VISIBLE) {
            Snackbar.make(connectActionFab,
                    R.string.multiple_participants_not_available,
                    Snackbar.LENGTH_LONG).show();
            return;
        }
        hideProgress();
        if (!videoCall && timer != null) {
            timer.start();
        }
    }

    public void afterParticipantDisconnected(RemoteParticipant remoteParticipant) {
        if(callService == null) {
            return;
        }
        RoomApplozicManager roomApplozicManager = callService.getRoomApplozicManager();
        if (videoCall) {
            moveLocalVideoToPrimaryView(roomApplozicManager.getLocalVideoTrack(), roomApplozicManager.getCameraCapturer());
        }
        if (roomApplozicManager.getRoom() != null) {
            roomApplozicManager.disconnectRoom();
        } else {
            finish();
        }
    }

    public boolean callServiceIsRunningInForeground(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(
                Context.ACTIVITY_SERVICE);

        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(
                Integer.MAX_VALUE)) {
            if (CallService.class.getName().equals(service.service.getClassName())) {
                if (service.foreground) {
                    return true;
                }
            }
        }
        return false;
    }
}

