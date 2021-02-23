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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.os.IBinder;
import android.text.TextUtils;
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
import com.applozic.mobicomkit.uiwidgets.conversation.activity.ConversationActivity;
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
 * 1. {@link AudioCallActivityV2#postRoomParticipantEventsListener}
 * 2. {@link AudioCallActivityV2#postRoomEventsListener}
 * 3. {@link AudioCallActivityV2#audioVideoUICallback}
 * 4. {@link AudioCallActivityV2#callDurationTickCallback}</p>
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

    protected TextView videoStatusTextView;
    protected TextView audioStatusTextView;
    protected ImageView audioMuteStatus;
    protected ImageView videoMuteStatus;

    protected AudioManager audioManager; //speaker control
    protected MediaPlayer mediaPlayer; //for ringtone
    protected ProgressDialog loading;
    protected TextView contactName;
    protected ImageView profileImage;
    protected ImageLoader imageLoader;
    protected TextView callTimerText;
    protected AlertDialog alertDialog;

    protected boolean videoCall = false;
    protected String userIdContactCalled;
    protected Contact contactCalled;
    protected String callId;
    protected boolean received;
    protected boolean activityStartedFromNotification;
    protected boolean disconnectedFromOnDestroy;

    protected AppContactService contactService;
    protected CallService callService;

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
        public void afterReconnecting() {
            AudioCallActivityV2.this.afterReconnecting();
        }

        @Override
        public void afterConnectionReestablished(Room room) {
            AudioCallActivityV2.this.afterConnectionReestablished(room);
        }

        @Override
        public void afterParticipantConnected(RemoteParticipant remoteParticipant) {
            AudioCallActivityV2.this.afterParticipantConnected(remoteParticipant);
        }

        @Override
        public void afterParticipantDisconnected(RemoteParticipant remoteParticipant) {
            AudioCallActivityV2.this.afterParticipantDisconnected(remoteParticipant);
        }
    };

    final private PostRoomParticipantEventsListener postRoomParticipantEventsListener = new PostRoomParticipantEventsListener() {
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

        @Override
        public void afterVideoTrackEnabled(RemoteParticipant remoteParticipant) {
            hideVideoPausedStatusText();
        }

        @Override
        public void afterVideoTrackDisabled(RemoteParticipant remoteParticipant, Room room) {
            showVideoPausedStatusTextIfValid(remoteParticipant, room);
        }

        @Override
        public void afterAudioTrackEnabled(RemoteParticipant remoteParticipant) {
            hideMuteStatus();
        }

        @Override
        public void afterAudioTrackDisabled(RemoteParticipant remoteParticipant, Room room) {
            showMuteStatus(videoCall);
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

    final private CallService.CallDurationTickCallback callDurationTickCallback = new CallService.CallDurationTickCallback() {
        @SuppressLint("DefaultLocale")
        @Override
        public void onTick(int timeInSeconds) {
            int seconds = (timeInSeconds);
            int hrs = seconds / (60 * 60);
            int minutes = seconds / 60;
            seconds = seconds % 60;
            if(callTimerText != null) {
                callTimerText.setText(String.format("%d:%02d:%02d", hrs, minutes, seconds));
            }
        }
    };

    boolean isVideoCallStatusTextVideoPaused() {
        return getString(R.string.status_text_paused).contentEquals(videoStatusTextView.getText());
    }

    void hideVideoCallStatusText() {
        if(videoStatusTextView != null) {
            videoStatusTextView.setVisibility(View.INVISIBLE);
        }
    }

    void setVideoCallStatusText(String callStatusText) {
        if(videoStatusTextView != null) {
            videoStatusTextView.setVisibility(View.VISIBLE);
            videoStatusTextView.setText(callStatusText);
        }
    }

    void hideAudioCallStatusText() {
        if(audioStatusTextView != null) {
            audioStatusTextView.setVisibility(View.INVISIBLE);
        }
    }

    void setAudioCallStatusText(String callStatusText) {
        if(audioStatusTextView != null) {
            audioStatusTextView.setVisibility(View.VISIBLE);
            audioStatusTextView.setText(callStatusText);
        }
    }

    void showMuteStatus(boolean videoCall) {
        if (videoCall && videoMuteStatus == null) {
            return;
        }
        if(!videoCall && audioMuteStatus == null) {
            return;
        }

        if (videoCall) {
            videoMuteStatus.setVisibility(View.VISIBLE);
        } else {
            audioMuteStatus.setVisibility(View.VISIBLE);
        }
    }

    void hideMuteStatus() {
        if(audioMuteStatus != null) {
            audioMuteStatus.setVisibility(View.INVISIBLE);
        }
        if(videoMuteStatus != null) {
            videoMuteStatus.setVisibility(View.INVISIBLE);
        }
    }

    public void finishCallActivityProperly() {
        if(isTaskRoot()) {
            Intent intent = new Intent(this, ConversationActivity.class);
            startActivity(intent);
        }
        AudioCallActivityV2.this.finish();
    }

    public void finishActivityIfInternetNotAvailable() {
        if (!Utils.isInternetAvailable(this)) {
            Toast toast = Toast.makeText(this, getString(R.string.internet_connection_not_available), Toast.LENGTH_SHORT);
            toast.setGravity(Gravity.CENTER, 0, 0);
            toast.show();
            finishCallActivityProperly();
        }
    }

    public boolean isCallActivityValid() {
        return !TextUtils.isEmpty(userIdContactCalled) || (callServiceIsRunningInForeground(this)); //contactCalled should be valid if call isn't already ongoing
    }

    public void finishActivityAndOpenConversationActivityIfNotValid() {
        if (!isCallActivityValid()) {
            Toast.makeText(this, getResources().getString(R.string.call_not_valid), Toast.LENGTH_LONG).show();
            finishCallActivityProperly();
        }
    }

    protected void initCallDataFrom(Intent intent) {
        if(intent == null) {
            Log.d(TAG, "Intent is null.");
            return;
        }

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

    protected void updateCallDataForActivityFrom(RoomApplozicManager roomApplozicManager) {
        if(roomApplozicManager == null) {
            Log.d(TAG, "RoomApplozicManager object is null.");
            return;
        }

        userIdContactCalled = roomApplozicManager.getContactCalled().getUserId();
        received = roomApplozicManager.isCallReceived();
        callId = roomApplozicManager.getCallId();
        contactCalled = roomApplozicManager.getContactCalled();
    }

    protected boolean isCallDataValid(RoomApplozicManager roomApplozicManager) {
        return roomApplozicManager != null && roomApplozicManager.getOneToOneCall() != null && roomApplozicManager.getContactCalled() != null;
    }

    protected void closeEntireCall() {
        Toast.makeText(AudioCallActivityV2.this, getResources().getString(R.string.call_not_valid), Toast.LENGTH_SHORT).show();

        finishCallActivityProperly();

        callService.stopSelf();
        callService.stopForeground(true);
    }

    protected void bindCallServiceWithActivity(boolean wasCallServiceAlreadyRunning) {
        Intent intent = new Intent(this, CallService.class);
        bindService(intent, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.d(TAG, "Call Service Connected.");

                callService = ((CallService.AudioVideoCallBinder) service).getCallService();

                if(callService != null) {
                    RoomApplozicManager roomApplozicManager = callService.getRoomApplozicManager();

                    if(isCallDataValid(roomApplozicManager)) {
                        Log.d(TAG, "Call Service attached to Audio Call Activity: " + callService.getRoomApplozicManager().getOneToOneCall().getCallId());
                    } else {
                        closeEntireCall();
                    }

                    if (wasCallServiceAlreadyRunning) { //call ongoing
                        if (videoCall != roomApplozicManager.isVideoCall()) { //ongoing CallService call type and the call type that was opened don't match
                            Toast.makeText(AudioCallActivityV2.this, "Error: Ongoing call is a : " + (roomApplozicManager.isVideoCall() ? "video" : "audio") + " call!", Toast.LENGTH_LONG).show();
                            finishCallActivityProperly();
                        } else if (!userIdContactCalled.equals(roomApplozicManager.getContactCalled().getUserId())) { //ongoing CallService contact and the contact being called don't match
                            Toast.makeText(AudioCallActivityV2.this, "There is an ongoing call. Opening that.", Toast.LENGTH_LONG).show();
                        }
                    }

                    updateCallDataForActivityFrom(roomApplozicManager);

                    callService.setAudioVideoUICallback(audioVideoUICallback);
                    callService.setPostRoomParticipantEventsListener(postRoomParticipantEventsListener);
                    callService.setPostRoomEventsListener(postRoomEventsListener);
                    callService.setCallDurationTickCallback(callDurationTickCallback);

                    if(checkPermissionForCameraAndMicrophone() && videoCall) {
                        roomApplozicManager.publishLocalVideoTrack();
                    }

                    initializeUI(roomApplozicManager);
                } else {
                    finishCallActivityProperly();
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                unBindWithService();
            }
        }, Context.BIND_AUTO_CREATE);
    }

    protected void unBindWithService() {
        if(callService != null) {
            callService.setAudioVideoUICallback(null);
            callService.setPostRoomParticipantEventsListener(null);
            callService.setPostRoomEventsListener(null);
            callService.setCallDurationTickCallback(null);
        }
        callService = null;
    }

    protected void startAndOrBindCallService(boolean callServiceAlreadyRunning) {
        if (!callServiceAlreadyRunning) {
            Intent intent = new Intent(this, CallService.class);
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

        bindCallServiceWithActivity(callServiceAlreadyRunning);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initCallDataFrom(getIntent());

        finishActivityIfInternetNotAvailable();
        finishActivityAndOpenConversationActivityIfNotValid();

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        /*
         * Needed for setting/abandoning audio focus during call
         */
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

        //ringtone for incoming/received call
        mediaPlayer = MediaPlayer.create(this, R.raw.hangouts_video_call);
        mediaPlayer.setLooping(true);

        if (videoCall) {
            Utils.printLog(this, TAG, "This is a video call. Returning.");
            return;
        }

        setContentView(R.layout.applozic_audio_call);

        contactName = (TextView) findViewById(R.id.contact_name);
        profileImage = (ImageView) findViewById(R.id.applozic_audio_profile_image);
        callTimerText = (TextView) findViewById(R.id.applozic_audio_timer);

        primaryVideoView = (VideoView) findViewById(R.id.primary_video_view);
        thumbnailVideoView = (VideoView) findViewById(R.id.thumbnail_video_view);

        connectActionFab = (FloatingActionButton) findViewById(R.id.call_action_fab);
        muteActionFab = (FloatingActionButton) findViewById(R.id.mute_action_fab);
        speakerActionFab = (FloatingActionButton) findViewById(R.id.speaker_action_fab);

        videoStatusTextView = (TextView) findViewById(R.id.video_status_textview);
        audioStatusTextView = (TextView) findViewById(R.id.applozic_audio_status);
        audioMuteStatus = (ImageView) findViewById(R.id.audio_mute_status);
        videoMuteStatus = (ImageView) findViewById(R.id.video_mute_status);

        if(!received && !callServiceIsRunning(this)) {
            setAudioCallStatusText(getString(R.string.status_text_calling));
        }

        /*
         * Check camera and microphone permissions. Needed in Android M.
         */
        if (!checkPermissionForCameraAndMicrophone()) {
            requestPermissionForCameraAndMicrophone();
        } else {
            startAndOrBindCallService(activityStartedFromNotification || callServiceIsRunningInForeground(this) || callServiceIsRunning(this));
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
                startAndOrBindCallService(activityStartedFromNotification || callServiceIsRunningInForeground(this) || callServiceIsRunning(this));
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
    }

    @Override
    protected void onPause() {
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

    protected void initializeUIForVideoCall(RoomApplozicManager roomApplozicManager) {
        if (roomApplozicManager == null) {
            return;
        }
        if (videoCall) {
            roomApplozicManager.getLocalVideoTrack().addRenderer(primaryVideoView);
            localVideoView = primaryVideoView;
            primaryVideoView.setMirror(true);

            Room room = roomApplozicManager.getRoom();
            if (room != null) {
                for (RemoteParticipant remoteParticipant : room.getRemoteParticipants()) {
                    if(!remoteParticipant.getRemoteVideoTracks().isEmpty()) {
                        RemoteVideoTrack remoteVideoTrack = remoteParticipant.getRemoteVideoTracks().get(0).getRemoteVideoTrack();
                        if (remoteVideoTrack == null) {
                            return;
                        }
                        addRemoteParticipantVideo(remoteVideoTrack);

                        //remote video paused status
                        if(remoteVideoTrack.isEnabled()) {
                            hideVideoPausedStatusText();
                        } else {
                            showVideoPausedStatusTextIfValid(remoteParticipant, room);
                        }
                    }
                    break; //only one participant UI allowed (1-to-1 call)
                }
            }
        }
    }

    protected void initializeUI(RoomApplozicManager roomApplozicManager) {
        if(contactCalled != null) {
            contactName.setText(contactCalled.getDisplayName());

            if(imageLoader != null) { //will be null for video call (VideoActivity)
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
        }

        if (roomApplozicManager == null) {
            Log.d(TAG, "RoomApplozicManager object is null.");
            return;
        }

        initializeUIForVideoCall(roomApplozicManager);

        connectActionFab.setImageDrawable(ContextCompat.getDrawable(this,
                R.drawable.ic_call_end_white_24px));
        connectActionFab.show();
        connectActionFab.setOnClickListener(disconnectClickListener());
        if (videoCall) {
            switchCameraActionFab.show();
            switchCameraActionFab.setOnClickListener(switchCameraClickListener(roomApplozicManager.getCameraCapturer()));
            localVideoActionFab.show();
            localVideoActionFab.setOnClickListener(localVideoPauseClickListener(roomApplozicManager.getLocalVideoTrack()));
        }
        muteActionFab.show();
        muteActionFab.setOnClickListener(muteClickListener(roomApplozicManager.getLocalAudioTrack()));
        speakerActionFab.setOnClickListener(speakerClickListener());

        if (callService != null) {
            toggleSpeakerOnOffUI(callService.getCallUIState().isLoudspeakerOn());
            toggleAudioMuteUI(callService.getCallUIState().isLocalAudioEnabled());
            if(videoCall) {
                toggleCameraPauseUI(callService.getCallUIState().isLocalVideoEnabled());
            }
        }

        if (roomApplozicManager.getRoom() == null) {
            return;
        }

        //for remote audio mute status
        for (RemoteParticipant remoteParticipant : roomApplozicManager.getRoom().getRemoteParticipants()) {
            if (!remoteParticipant.getRemoteAudioTracks().isEmpty()) {
                boolean remoteAudioMuted = !remoteParticipant.getRemoteAudioTracks().get(0).isTrackEnabled();
                if (remoteAudioMuted) {
                    showMuteStatus(videoCall);
                } else {
                    hideMuteStatus();
                }
            }
            break; //only one participant supported currently
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

    protected void showVideoPausedStatusTextIfValid(RemoteParticipant remoteParticipant, Room room) {
        if(callService == null) {
            return;
        }

        if (videoCall && remoteParticipant.getIdentity().equals(callService.getRoomApplozicManager().getContactCalled().getUserId()) && room.getState() == CONNECTED) {
            setVideoCallStatusText(getString(R.string.status_text_paused));
        }
    }

    protected void hideVideoPausedStatusText() {
        if(isVideoCallStatusTextVideoPaused()) {
            hideVideoCallStatusText();
        }
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
                if(roomApplozicManager.getStopServiceCallback() != null) {
                    roomApplozicManager.getStopServiceCallback().stopService();
                }
                disconnectAndExit(roomApplozicManager);
            }
        };
    }

    private void disconnectAndExit(RoomApplozicManager roomApplozicManager) {
        if (roomApplozicManager.getRoom() != null) {
            roomApplozicManager.disconnectRoom();
            finish();
        }
    }

    protected void toggleCameraPauseUI(boolean enable) {
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

    protected void toggleAudioMuteUI(boolean enable) {
        int icon = enable ?
                R.drawable.ic_mic_green_24px : R.drawable.ic_mic_off_red_24px;
        muteActionFab.setImageDrawable(ContextCompat.getDrawable(
                AudioCallActivityV2.this, icon));
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
                    toggleCameraPauseUI(enable);
                    if(callService != null) {
                        callService.getCallUIState().setLocalVideoEnabled(enable);
                    }
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
                    toggleAudioMuteUI(enable);
                    if(callService != null) {
                        callService.getCallUIState().setLocalAudioEnabled(enable);
                    }
                }
            }
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
                boolean onOrOff = !audioManager.isSpeakerphoneOn();
                setSpeakerphoneOn(onOrOff);
                if(callService != null) {
                    callService.getCallUIState().setLoudspeakerOn(onOrOff);
                }
            }
        };
    }

    protected void toggleSpeakerOnOffUI(boolean onOrOff) {
        Drawable drawable;
        if (onOrOff) {
            drawable = ContextCompat.getDrawable(this,
                    R.drawable.ic_volume_down_green_24px);
        } else {
            // route back to headset
            drawable = ContextCompat.getDrawable(this,
                    R.drawable.ic_volume_down_white_24px);
        }
        speakerActionFab.setImageDrawable(drawable);
    }

    protected void setSpeakerphoneOn(boolean onOrOff) {
        try {
            if (audioManager != null) {
                audioManager.setSpeakerphoneOn(onOrOff);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        toggleSpeakerOnOffUI(onOrOff);
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
        }
    }

    public void afterRoomDisconnected(Room room) {
        Toast.makeText(AudioCallActivityV2.this, AudioCallActivityV2.this.getString(R.string.call_end), Toast.LENGTH_SHORT).show();
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
        finish();
    }

    public void afterRoomConnectionFailure() {
        hideVideoCallStatusText();
        hideAudioCallStatusText();
        hideProgress();
        finish();
    }

    public void afterReconnecting() {
        if(videoCall) {
            setVideoCallStatusText(getString(R.string.status_text_reconnecting));
        } else {
            setAudioCallStatusText(getString(R.string.status_text_reconnecting));
        }
    }

    public void afterConnectionReestablished(Room room) {
        RemoteParticipant remoteParticipant = room.getRemoteParticipants().get(0);
        hideVideoCallStatusText();
        hideAudioCallStatusText();
        //if remote video is paused (logic is for 1-to-1 video call only)
        if(videoCall && remoteParticipant != null  && !remoteParticipant.getRemoteVideoTracks().isEmpty() && !remoteParticipant.getRemoteVideoTracks().get(0).isTrackEnabled()) {
            setVideoCallStatusText(getString(R.string.status_text_paused));
        }
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
        hideVideoCallStatusText();
        hideAudioCallStatusText();
        hideProgress();
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

    public boolean callServiceIsRunning(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(
                Context.ACTIVITY_SERVICE);

        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(
                Integer.MAX_VALUE)) {
            if (CallService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return CallService.isRunning(); //fallback method for double checking
    }
}

