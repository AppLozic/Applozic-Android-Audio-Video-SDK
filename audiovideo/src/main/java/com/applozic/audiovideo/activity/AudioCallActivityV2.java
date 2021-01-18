package com.applozic.audiovideo.activity;

import android.Manifest;
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
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;

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
import com.applozic.audiovideo.core.RoomApplozicManager;
import com.applozic.audiovideo.listener.AudioVideoUICallback;
import com.applozic.audiovideo.listener.PostRoomEventsListener;
import com.applozic.audiovideo.listener.PostRoomParticipantEventsListener;
import com.applozic.audiovideo.service.CallService;
import com.applozic.mobicomkit.broadcast.BroadcastService;
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
 * This activity is the base activity for an ongoing audio and video call.
 *
 * Created by Adarsh on 12/15/16.
 * Updated by shubham@applozic.com
 */

public class AudioCallActivityV2 extends AppCompatActivity {
    private static final int CAMERA_MIC_PERMISSION_REQUEST_CODE = 1;
    private static final String TAG = "AudioCallActivityV2";

    protected VideoView primaryVideoView;
    protected VideoView thumbnailVideoView;
    protected TextView videoStatusTextView;
    protected VideoView localVideoView;

    protected FloatingActionButton connectActionFab;
    protected FloatingActionButton switchCameraActionFab;
    protected FloatingActionButton localVideoActionFab;
    protected FloatingActionButton muteActionFab;

    protected AudioManager audioManager;
    protected ProgressDialog progress;
    protected MediaPlayer mediaPlayer;
    protected AppContactService contactService;
    protected TextView contactName;
    protected ImageView profileImage;
    protected boolean disconnectedFromOnDestroy;
    protected FloatingActionButton speakerActionFab;
    ImageLoader mImageLoader;
    CountDownTimer timer;
    TextView textCount;
    private AlertDialog alertDialog;
    private int tickCount;

    protected boolean videoCall = false;
    protected String userIdContactCalled;
    protected Contact contactCalled;
    protected String callId;
    protected boolean received;

    protected CallService callService;

    public AudioCallActivityV2() {
        this.videoCall = false;
    }

    public AudioCallActivityV2(boolean videoCall) {
        this.videoCall = videoCall;
    }

    public static void setOpenStatus(boolean isInOpenStatus) {
        BroadcastService.videoCallAcitivityOpend = isInOpenStatus;
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
            videoStatusTextView.setText("onVideoTrackAdded");
            addRemoteParticipantVideo(videoTrack);
        }

        @Override
        public void afterVideoTrackUnsubscribed(RemoteVideoTrack videoTrack) {
            videoStatusTextView.setText("onVideoTrackRemoved");
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
        public void connectingCall(String callId, boolean isReceived) {
            timer = initializeTimer();
            if (isReceived) {
                progress = new ProgressDialog(AudioCallActivityV2.this);
                progress.setMessage(getString(R.string.connecting));
                progress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                progress.setIndeterminate(true);
                progress.setCancelable(false);
                progress.show();
            } else {
                mediaPlayer.start();
            }
        }
    };

    protected void init() {
        Intent intent = getIntent();
        userIdContactCalled = intent.getStringExtra("CONTACT_ID");
        received = intent.getBooleanExtra("INCOMING_CALL", Boolean.FALSE);
        callId = intent.getStringExtra("CALL_ID");
        contactService = new AppContactService(this);
        contactCalled = contactService.getContactById(userIdContactCalled);
        Log.i(TAG, "Init. isVideoCall(): " + videoCall);
        Log.i(TAG, "Contact Id: " + userIdContactCalled);
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
        mediaPlayer = MediaPlayer.create(this, R.raw.hangouts_video_call);
        mediaPlayer.setLooping(true);
        if (!Utils.isInternetAvailable(this)) {
            Toast toast = Toast.makeText(this, getString(R.string.internet_connection_not_available), Toast.LENGTH_SHORT);
            toast.setGravity(Gravity.CENTER, 0, 0);
            toast.show();
            finish();
            return;
        }

        Intent intent = new Intent(this, CallService.class);
        intent.putExtra("CONTACT_ID", userIdContactCalled);
        intent.putExtra("CALL_ID", callId);
        intent.putExtra("INCOMING_CALL", received);
        intent.putExtra("VIDEO_CALL", videoCall);

        startService(intent);
        bindService(intent, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                CallService.AudioVideoCallBinder audioVideoCallBinder = (CallService.AudioVideoCallBinder) service;
                callService = audioVideoCallBinder.getCallService();
                if(callService != null) {
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
        setOpenStatus(true);

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
        textCount = (TextView) findViewById(R.id.applozic_audio_timer);

        if(contactCalled != null) {
            contactName.setText(contactCalled.getDisplayName());

            mImageLoader = new ImageLoader(this, profileImage.getHeight()) {
                @Override
                protected Bitmap processBitmap(Object data) {
                    return contactService.downloadContactImage(AudioCallActivityV2.this, (Contact) data);
                }
            };
            mImageLoader.setLoadingImage(R.drawable.applozic_ic_contact_picture_holo_light);
            // Add a cache to the image loader
            mImageLoader.setImageFadeIn(false);
            mImageLoader.loadImage(contactCalled, profileImage);
        }

        primaryVideoView = (VideoView) findViewById(R.id.primary_video_view);
        thumbnailVideoView = (VideoView) findViewById(R.id.thumbnail_video_view);

        //Video Status Text view, for debug only
        videoStatusTextView = (TextView) findViewById(R.id.video_status_textview);
        videoStatusTextView.setVisibility(View.GONE);

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
        setOpenStatus(false);
        unBindWithService();
    }

    /*
     * The initial state when there is no active conversation.
     */
    protected void initializeUI(RoomApplozicManager roomApplozicManager) {
        connectActionFab.setImageDrawable(ContextCompat.getDrawable(this,
                R.drawable.ic_call_end_white_24px));
        connectActionFab.show();
        connectActionFab.setOnClickListener(disconnectClickListener());
        if (videoCall) {
            switchCameraActionFab.show();
            switchCameraActionFab.setOnClickListener(switchCameraClickListener(roomApplozicManager.getCameraCapturer()));
            localVideoActionFab.show();
            localVideoActionFab.setOnClickListener(localVideoClickListener(roomApplozicManager.getLocalVideoTrack()));

            roomApplozicManager.getLocalVideoTrack().addRenderer(primaryVideoView);
            localVideoView = primaryVideoView;
        }
        primaryVideoView.setMirror(true);
        muteActionFab.show();
        muteActionFab.setOnClickListener(muteClickListener(roomApplozicManager.getLocalAudioTrack()));
        if(roomApplozicManager.getRoom() != null) {
            speakerActionFab.setOnClickListener(speakerClickListener());
        }
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
                //LocalVideoTrack localVideoTrack = roomApplozicManager.getLocalVideoTrack();
                thumbnailVideoView.setVisibility(View.VISIBLE);
                if (localVideoTrack != null) {
                    localVideoTrack.removeRenderer(primaryVideoView);
                    localVideoTrack.addRenderer(thumbnailVideoView);
                    localVideoView = thumbnailVideoView;
                    //CameraCapturer cameraCapturer = roomApplozicManager.getCameraCapturer().getCameraSource()
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
                //invite sent but NOT yet connected
                if (roomApplozicManager.isCallRinging()) {
                    roomApplozicManager.getOneToOneCall().setInviteSent(false);
                    roomApplozicManager.sendApplozicMissedCallNotification();
                }
                disconnectAndExit(roomApplozicManager);
            }
        };
    }

    //TODO: ServiceStuff keep
    private void disconnectAndExit(RoomApplozicManager roomApplozicManager) {
        if (roomApplozicManager.getRoom() != null) {
            roomApplozicManager.disconnectRoom();
        } else {
            finish();
        }
    }

    private View.OnClickListener connectActionClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Send request to same userId...
            }
        };
    }

    /**
    private DialogInterface.OnClickListener cancelConnectDialogClickListener() {
        return new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                intializeUI();
                alertDialog.dismiss();
            }
        };
    }
     **/

    //TODO: ServiceStuff pass camera capturer
    private View.OnClickListener switchCameraClickListener(CameraCapturer cameraCapturer) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    //CameraCapturer cameraCapturer = roomApplozicManager.getCameraCapturer();
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

    //TODO: ServiceStuff pass local video track
    private View.OnClickListener localVideoClickListener(LocalVideoTrack localVideoTrack) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //LocalVideoTrack localVideoTrack = roomApplozicManager.getLocalVideoTrack();
                /*
                 * Enable/disable the local video track
                 */
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

            @Override
            public void onTick(long millisUntilFinished) {

                tickCount++;
                // long millis = cnt;
                int seconds = (tickCount);
                int hrs = seconds / (60 * 60);
                int minutes = seconds / 60;
                seconds = seconds % 60;
                textCount.setText(String.format("%d:%02d:%02d", hrs, minutes, seconds));
            }

            @Override
            public void onFinish() {

            }
        };
    }

    protected void hideProgress() {
        try {
            Log.i(TAG, "Hiding progress dialog.");
            if (alertDialog != null && alertDialog.isShowing()) {
                alertDialog.dismiss();
            }
            if (progress != null && progress.isShowing()) {
                progress.dismiss();
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
        //room is connected
        if (room != null && room.getState().equals(CONNECTED)) {
            alertDialog = Dialog.createCloseSessionDialog(new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Log.i(TAG, "onBackPressed cancel does nothing... ");
                }
            }, closeSessionListener(roomApplozicManager), this);
            alertDialog.show();

        } else if (room != null && !room.getState().equals(CONNECTED)) {
            // if room is not connected, do nothing
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
        videoStatusTextView.setText("Connected to: " + room.getName());
        setTitle(room.getName());
        setSpeakerphoneOn(videoCall);
        for(RemoteParticipant remoteParticipant : room.getRemoteParticipants()) {
            /*
             * This app only displays video for one additional participant per Room
             */
            if (thumbnailVideoView.getVisibility() == View.VISIBLE) {
                Snackbar.make(connectActionFab,
                        R.string.multiple_participants_not_available,
                        Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                return;
            }
            videoStatusTextView.setText("Participant " + remoteParticipant.getIdentity() + " joined.");
            hideProgress();
            if (!videoCall) {
                timer.start();
            }
        }
    }

    public void afterRoomDisconnected(Room room) {
        videoStatusTextView.setText("Disconnected from " + room.getName());
        if (!videoCall) {
            timer.cancel();
        }
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
        finish();
    }

    public void afterRoomConnectionFailure() {
        videoStatusTextView.setText("Failed to connect");
        hideProgress();
        finish();
    }

    public void afterReconnecting() {

    }

    public void afterConnectionReestablished() {

    }

    public void afterParticipantConnected(RemoteParticipant remoteParticipant) {
        /*
         * This app only displays video for one additional participant per Room
         */
        if (thumbnailVideoView.getVisibility() == View.VISIBLE) {
            Snackbar.make(connectActionFab,
                    R.string.multiple_participants_not_available,
                    Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            return;
        }
        videoStatusTextView.setText("Participant " + remoteParticipant.getIdentity() + " joined.");
        hideProgress();
        if (!videoCall) {
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
        videoStatusTextView.setText("Participant " + remoteParticipant.getIdentity() + " left.");
        if (roomApplozicManager.getRoom() != null) {
            roomApplozicManager.disconnectRoom();
        } else {
            finish();
        }
    }
}

