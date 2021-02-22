package com.applozic.audiovideo.core;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.CountDownTimer;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.applozic.audiovideo.listener.PostRoomEventsListener;
import com.applozic.audiovideo.listener.PostRoomParticipantEventsListener;
import com.applozic.audiovideo.model.OneToOneCall;
import com.applozic.audiovideo.service.CallService;
import com.applozic.mobicomkit.api.MobiComKitConstants;
import com.applozic.mobicomkit.api.notification.VideoCallNotificationHelper;
import com.applozic.mobicomkit.contact.AppContactService;
import com.applozic.mobicommons.commons.core.utils.Utils;
import com.applozic.mobicommons.people.contact.Contact;
import com.twilio.video.CameraCapturer;
import com.twilio.video.ConnectOptions;
import com.twilio.video.LocalAudioTrack;
import com.twilio.video.LocalParticipant;
import com.twilio.video.LocalVideoTrack;
import com.twilio.video.RemoteAudioTrack;
import com.twilio.video.RemoteAudioTrackPublication;
import com.twilio.video.RemoteDataTrack;
import com.twilio.video.RemoteDataTrackPublication;
import com.twilio.video.RemoteParticipant;
import com.twilio.video.RemoteVideoTrack;
import com.twilio.video.RemoteVideoTrackPublication;
import com.twilio.video.Room;
import com.twilio.video.TwilioException;
import com.twilio.video.Video;
import com.twilio.video.VideoTrack;

import java.util.Collections;

import applozic.com.audiovideo.R;

import static com.twilio.video.Room.State.CONNECTED;
import static com.twilio.video.Room.State.DISCONNECTED;

/**
 * For the management of a IP call (or a Twilio room [hence the name])
 *
 * <p>This class is responsible for connecting to, disconnecting from, managing etc. twilio rooms.
 * Interaction with the UI elements is done using {@link PostRoomEventsListener} and {@link PostRoomParticipantEventsListener}.
 * Data about a ongoing 1-to-1 call is stored using {@link OneToOneCall}.</p>
 */
public class RoomApplozicManager {
    public static final String TAG = "RoomManager";
    public static final long INCOMING_CALL_TIMEOUT = 30 * 1000L;
    private static final String LOCAL_VIDEO_TRACK_NAME = "camera";
    static final String CONNECTIVITY_CHANGE = "android.net.conn.CONNECTIVITY_CHANGE";

    Context context;
    protected String accessToken;
    private int previousAudioMode;
    private boolean previousMicrophoneMute;
    private int callDurationTickInSeconds;
    private PostRoomEventsListener postRoomEventsListener;
    private PostRoomParticipantEventsListener postRoomParticipantEventsListener;
    private final CallService.StopServiceCallback stopServiceCallback;
    private CallService.CallDurationTickCallback callDurationTickCallback;

    protected Room room;
    protected LocalParticipant localParticipant;
    protected RemoteParticipant remoteParticipant;
    protected LocalAudioTrack localAudioTrack;
    protected LocalVideoTrack localVideoTrack;
    protected VideoTrack remoteVideoTrack;
    protected AudioTrack remoteAudioTrack;

    protected AudioManager audioManager;
    protected CameraCapturer cameraCapturer;
    protected CountDownTimer callTimer; //TODO: make call timer its separate class, with support for single start (in case group call)

    protected VideoCallNotificationHelper videoCallNotificationHelper;
    protected OneToOneCall oneToOneCall;
    protected BroadcastReceiver applozicBroadCastReceiver;
    protected AppContactService contactService;

    private void initializeCallTimer() {
        callTimer = new CountDownTimer(Long.MAX_VALUE, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                callDurationTickInSeconds++;
                if(callDurationTickCallback != null) {
                    callDurationTickCallback.onTick(callDurationTickInSeconds);
                }
            }

            @Override
            public void onFinish() { }
        };
    }

    public RoomApplozicManager(Context context, String callId, String contactId, boolean videoCall, boolean received, CallService.StopServiceCallback stopServiceCallback) {
        this.context = context;
        contactService = new AppContactService(context);
        oneToOneCall = new OneToOneCall(callId, videoCall, contactService.getContactById(contactId), received);
        videoCallNotificationHelper = new VideoCallNotificationHelper(context, !videoCall);
        this.stopServiceCallback = stopServiceCallback;
        localAudioTrack = createAndReturnLocalAudioTrack();
        localVideoTrack = getLocalVideoTrack();
        callDurationTickInSeconds = 0;

        cameraCapturer = getCameraCapturer();
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        initializeCallTimer();
        initializeApplozicNotificationBroadcast();
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public boolean isRoomStateConnected() {
        return room.getState().equals(Room.State.CONNECTED);
    }

    public Room.State getRoomState() {
        return room.getState();
    }

    public OneToOneCall getOneToOneCall() {
        return oneToOneCall;
    }

    public void setVideoCall(boolean videoCall) {
        oneToOneCall.setVideoCall(videoCall);
    }

    public boolean isVideoCall() {
        return oneToOneCall.isVideoCall();
    }

    public String getCallId() {
        return oneToOneCall.getCallId();
    }

    public void setCallId(String callId) {
        oneToOneCall.setCallId(callId);
    }

    public Contact getContactCalled() {
        return oneToOneCall.getContactCalled();
    }

    public LocalVideoTrack getLocalVideoTrack() {
        if(localVideoTrack == null) {
            localVideoTrack = LocalVideoTrack.create(context, true, getCameraCapturer(), LOCAL_VIDEO_TRACK_NAME);
        }
        return localVideoTrack;
    }

    public LocalAudioTrack getLocalAudioTrack() {
        if(localAudioTrack == null) {
            return createAndReturnLocalAudioTrack();
        }
        return localAudioTrack;
    }

    public LocalParticipant getLocalParticipant() {
        return localParticipant;
    }

    public AudioTrack getRemoteAudioTrack() {
        return remoteAudioTrack;
    }

    public VideoTrack getRemoteVideoTrack() {
        return remoteVideoTrack;
    }

    public RemoteParticipant getRemoteParticipant() {
        return remoteParticipant;
    }

    public CallService.StopServiceCallback getStopServiceCallback() {
        return stopServiceCallback;
    }

    public CameraCapturer getCameraCapturer() {
        if(cameraCapturer == null) {
            try {
                cameraCapturer = new CameraCapturer(context, CameraCapturer.CameraSource.FRONT_CAMERA);
            } catch (IllegalStateException e) {
                Utils.printLog(context, TAG, "Front camera not found on device, using back camera..");
                cameraCapturer = new CameraCapturer(context, CameraCapturer.CameraSource.BACK_CAMERA);
            }
        }
        return cameraCapturer;
    }

    public Room getRoom() {
        return room;
    }

    public int getCallDurationTickInSeconds() {
        return callDurationTickInSeconds;
    }

    public void setCallDurationTickCallback(CallService.CallDurationTickCallback callDurationTickCallback) {
        this.callDurationTickCallback = callDurationTickCallback;
    }

    public boolean isCallReceived() {
        if(getOneToOneCall() != null) {
            return getOneToOneCall().isReceived();
        } else {
            return false;
        }
    }

    public void setPostRoomEventsListener(PostRoomEventsListener postRoomEventsListener) {
        this.postRoomEventsListener = postRoomEventsListener;
    }

    public void setPostRoomParticipantEventsListener(PostRoomParticipantEventsListener postRoomParticipantEventsListener) {
        this.postRoomParticipantEventsListener = postRoomParticipantEventsListener;
    }

    public void publishLocalVideoTrack() {
        localVideoTrack = getLocalVideoTrack();
        if (localParticipant == null) {
           if (room != null) {
              localParticipant = room.getLocalParticipant();
           }
        }
        if (localParticipant != null && localVideoTrack != null) {
            localParticipant.publishTrack(localVideoTrack);
        } else {
            Log.d(TAG, "Local participant or local video track is null. Failed to publish local video.");
        }
    }

    public void unPublishLocalVideoTrack() {
        /*
         * Release the local video track before going in the background. This ensures that the
         * camera can be used by other applications while this app is in the background.
         */
        if (localVideoTrack != null) {
            /*
             * If this local video track is being shared in a Room, remove from local
             * participant before releasing the video track. Participants will be notified that
             * the track has been removed.
             */
            if (localParticipant != null) {
                localParticipant.unpublishTrack(localVideoTrack);
            }

            localVideoTrack.release();
            localVideoTrack = null;
        }
    }

    public void disconnectRoom() {
        if (room != null && room.getState() != DISCONNECTED) {
            room.disconnect();
        }
    }

    public boolean isScheduleStopRequire() {
        return (oneToOneCall.isInviteSent() && (oneToOneCall.getParticipantId() == null || !oneToOneCall.getParticipantId().equals(getContactCalled().getUserId())));
    }

    public boolean isCallRinging() {
        return oneToOneCall.isInviteSent() && oneToOneCall.getParticipantId() == null;
    }

    public long getScheduledStopTimeDuration() {
        return oneToOneCall.isReceived() ? INCOMING_CALL_TIMEOUT : VideoCallNotificationHelper.MAX_NOTIFICATION_RING_DURATION + 10 * 1000;
    }

    public void sendApplozicMissedCallNotification() {
        videoCallNotificationHelper.sendCallMissed(getContactCalled(), getCallId());
        videoCallNotificationHelper.sendVideoCallMissedMessage(getContactCalled(), getCallId());
    }

    public void releaseAudioVideoTracks() {
        /*
         * Release the local audio and video tracks ensuring any memory allocated to audio
         * or video is freed.
         */
        if (localAudioTrack != null) {
            localAudioTrack.release();
            localAudioTrack = null;
        }
        if (localVideoTrack != null) {
            localVideoTrack.release();
            localVideoTrack = null;
        }
    }

    private void sendApplozicCallRequestAndConnectToRoom() {
        if (isVideoCall()) {
            setCallId(videoCallNotificationHelper.sendVideoCallRequest(getContactCalled()));
        } else {
            setCallId(videoCallNotificationHelper.sendAudioCallRequest(getContactCalled()));
        }
        connectToRoom(getCallId());
    }

    public void initiateRoomCall() {
        if (oneToOneCall.isReceived() && !TextUtils.isEmpty(getCallId())) {
            connectToRoom(getCallId());
        } else {
            sendApplozicCallRequestAndConnectToRoom();
            oneToOneCall.setInviteSent(true);
        }
    }

    static IntentFilter BrodCastIntentFilters() {
        IntentFilter intentFilter = new IntentFilter();

        intentFilter.addAction(MobiComKitConstants.APPLOZIC_VIDEO_CALL_REJECTED);
        intentFilter.addAction(VideoCallNotificationHelper.CALL_CANCELED);
        intentFilter.addAction(VideoCallNotificationHelper.CALL_END);
        intentFilter.addAction(MobiComKitConstants.APPLOZIC_VIDEO_DIALED);
        intentFilter.addAction(VideoCallNotificationHelper.CALL_MISSED);
        intentFilter.addAction(CONNECTIVITY_CHANGE);

        return intentFilter;
    }

    public void registerApplozicBroadcastReceiver() {
        LocalBroadcastManager.getInstance(context).registerReceiver(applozicBroadCastReceiver,
                BrodCastIntentFilters());
    }

    public void unregisterApplozicBroadcastReceiver() {
        LocalBroadcastManager.getInstance(context).unregisterReceiver(applozicBroadCastReceiver);
    }

    public void initializeApplozicNotificationBroadcast() {
        applozicBroadCastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                String incomingCallId = intent.getStringExtra(VideoCallNotificationHelper.CALL_ID);
                boolean isNotificationForSameId = false;

                Log.i(TAG, "incomingCallId: " + incomingCallId + ", intent.getAction(): " + intent.getAction());

                if (CONNECTIVITY_CHANGE.equals(intent.getAction())) {
                    if (!Utils.isInternetAvailable(context)) {
                        Toast.makeText(context, R.string.no_network_connectivity, Toast.LENGTH_LONG);
                        if (room != null && room.getState().equals(CONNECTED)) {
                            room.disconnect();
                        }
                    }
                    return;
                }

                if (!TextUtils.isEmpty(getCallId())) {
                    isNotificationForSameId = (getCallId().equals(incomingCallId));
                }
                if ((MobiComKitConstants.APPLOZIC_VIDEO_CALL_REJECTED.equals(intent.getAction()) ||
                        VideoCallNotificationHelper.CALL_CANCELED.equals(intent.getAction()) ||
                        VideoCallNotificationHelper.CALL_MISSED.equals(intent.getAction()) ||
                        VideoCallNotificationHelper.CALL_END.equals(intent.getAction()))
                        && isNotificationForSameId) {

                    Toast.makeText(context, R.string.participant_busy, Toast.LENGTH_LONG).show();
                    if (room != null) {
                        oneToOneCall.setInviteSent(false);
                        room.disconnect();
                    }
                } else if (MobiComKitConstants.APPLOZIC_VIDEO_DIALED.equals(intent.getAction())) {

                    String contactId = intent.getStringExtra("CONTACT_ID");

                    if (!contactId.equals(getContactCalled().getUserId()) || (room != null && room.getState().equals(CONNECTED))) {
                        Contact contact = contactService.getContactById(contactId);
                        videoCallNotificationHelper.sendVideoCallReject(contact, incomingCallId);
                        return;
                    }
                    setCallId(incomingCallId);
                    connectToRoom(getCallId());
                }
            }
        };
    }

    public LocalAudioTrack createAndReturnLocalAudioTrack() {
        if(localAudioTrack != null) {
            return localAudioTrack;
        }
        localAudioTrack = LocalAudioTrack.create(context, true);
        return localAudioTrack;
    }

    @SuppressLint("SetTextI18n")
    private void addRemoteParticipant(RemoteParticipant remoteParticipant) {
        videoCallNotificationHelper.sendVideoCallAnswer(getContactCalled(), getCallId());
        oneToOneCall.setParticipantId(remoteParticipant.getIdentity());

        if (remoteParticipant.getRemoteVideoTracks().size() > 0) {
            RemoteVideoTrackPublication remoteVideoTrackPublication =
                    remoteParticipant.getRemoteVideoTracks().get(0);

            remoteParticipant.setListener(remoteParticipantListener());

            if (remoteVideoTrackPublication.isTrackSubscribed()) {
                if(postRoomParticipantEventsListener != null) {
                    postRoomParticipantEventsListener.afterParticipantConnectedToCall(remoteVideoTrackPublication);
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private void removeRemoteParticipant(RemoteParticipant remoteParticipant) {
        if (!remoteParticipant.getIdentity().equals(oneToOneCall.getParticipantId())) {
            return;
        }

        if (!remoteParticipant.getRemoteVideoTracks().isEmpty()) {
            RemoteVideoTrackPublication remoteVideoTrackPublication =
                    remoteParticipant.getRemoteVideoTracks().get(0);

            if (remoteVideoTrackPublication.isTrackSubscribed()) {
                if(postRoomParticipantEventsListener != null) {
                    postRoomParticipantEventsListener.afterParticipantDisconnectedFromCall(remoteVideoTrackPublication);
                }
            }
        }
    }

    private void requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            AudioFocusRequest focusRequest =
                    new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                            .setAudioAttributes(playbackAttributes)
                            .setAcceptsDelayedFocusGain(true)
                            .setOnAudioFocusChangeListener(
                                    new AudioManager.OnAudioFocusChangeListener() {
                                        @Override
                                        public void onAudioFocusChange(int i) {
                                        }
                                    })
                            .build();
            audioManager.requestAudioFocus(focusRequest);
        } else {
            audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        }
    }

    private void connectToRoom(String roomName) {
        try {
            configureAudio(true);
            ConnectOptions.Builder connectOptionsBuilder = new ConnectOptions.Builder(accessToken)
                    .roomName(roomName);

            if (localAudioTrack != null) {
                connectOptionsBuilder
                        .audioTracks(Collections.singletonList(localAudioTrack));
            }

            if (localVideoTrack != null) {
                connectOptionsBuilder.videoTracks(Collections.singletonList(getLocalVideoTrack()));
            }
            room = Video.connect(context, connectOptionsBuilder.build(), roomListener());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void configureAudio(boolean enable) {
        if (enable) {
            previousAudioMode = audioManager.getMode();
            // Request audio focus before making any device switch.

            requestAudioFocus();
            /*
             * Use MODE_IN_COMMUNICATION as the default audio mode. It is required
             * to be in this mode when playout and/or recording starts for the best
             * possible VoIP performance. Some devices have difficulties with
             * speaker mode if this is not set.
             */
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            /*
             * Always disable microphone mute during a WebRTC call.
             */
            previousMicrophoneMute = audioManager.isMicrophoneMute();
            audioManager.setMicrophoneMute(false);
        } else {
            audioManager.setMode(previousAudioMode);
            audioManager.abandonAudioFocus(null);
            audioManager.setMicrophoneMute(previousMicrophoneMute);
        }
    }

    private Room.Listener roomListener() {
        return new Room.Listener() {
            @Override
            public void onConnected(@androidx.annotation.NonNull Room room) {
                Log.d(TAG, "Connected to room: " + room.getName());
                RoomApplozicManager.this.room = room;
                localParticipant = room.getLocalParticipant();
                for (RemoteParticipant participant : room.getRemoteParticipants()) {
                    addRemoteParticipant(participant);
                    if(postRoomEventsListener != null) {
                        postRoomEventsListener.afterRoomConnected(room);
                    }
                    break;
                }
                if (oneToOneCall != null && oneToOneCall.isReceived() && callTimer != null) {
                    callTimer.start();
                }
            }

            @Override
            public void onConnectFailure(@androidx.annotation.NonNull Room room, @androidx.annotation.NonNull TwilioException e) {
                Log.d(TAG, "Failed to connect to room.");
                oneToOneCall.setInviteSent(false);
                configureAudio(false);
                if(postRoomEventsListener != null) {
                    postRoomEventsListener.afterRoomConnectionFailure();
                }
            }

            @Override
            public void onReconnecting(@androidx.annotation.NonNull Room room, @androidx.annotation.NonNull TwilioException twilioException) {
                if(postRoomEventsListener != null) {
                    postRoomEventsListener.afterReconnecting();
                }
            }

            @Override
            public void onReconnected(@androidx.annotation.NonNull Room room) {
                if(postRoomEventsListener != null) {
                    postRoomEventsListener.afterConnectionReestablished(room);
                }
            }

            @Override
            public void onDisconnected(@NonNull Room room, TwilioException e) {
                try {
                    Log.d(TAG, "Disconnected from room: " + room.getName());
                    localParticipant = null;
                    RoomApplozicManager.this.room = null;
                    configureAudio(false);
                    if (!oneToOneCall.isReceived() && oneToOneCall.getCallStartTime() > 0) {
                        long diff = (System.currentTimeMillis() - oneToOneCall.getCallStartTime());
                        videoCallNotificationHelper.sendVideoCallEnd(getContactCalled(), getCallId(), String.valueOf(diff));
                    }
                } catch (Exception exp) {
                    exp.printStackTrace();
                } finally {
                    if(postRoomEventsListener != null) {
                        postRoomEventsListener.afterRoomDisconnected(room);
                    }
                    if(stopServiceCallback != null) {
                        stopServiceCallback.stopService();
                    }
                    if (callTimer != null) {
                        callTimer.cancel();
                        callDurationTickInSeconds = 0;
                    }
                }
            }

            @Override
            public void onParticipantConnected(@androidx.annotation.NonNull Room room, @androidx.annotation.NonNull RemoteParticipant remoteParticipant) {
                Log.d(TAG, "Participant connected.");
                addRemoteParticipant(remoteParticipant);
                if (!oneToOneCall.isReceived()) {
                    oneToOneCall.setCallStartTime(System.currentTimeMillis());
                }
                if(postRoomEventsListener != null) {
                    postRoomEventsListener.afterParticipantConnected(remoteParticipant);
                }
                if (callTimer != null) {
                    callTimer.start();
                }
            }

            @Override
            public void onParticipantDisconnected(@androidx.annotation.NonNull Room room, @androidx.annotation.NonNull RemoteParticipant remoteParticipant) {
                Log.d(TAG, "Participant has disconnected.");
                removeRemoteParticipant(remoteParticipant);
                if(postRoomEventsListener != null) {
                    postRoomEventsListener.afterParticipantDisconnected(remoteParticipant);
                }
            }

            @Override
            public void onRecordingStarted(@androidx.annotation.NonNull Room room) {
                /*
                 * Indicates when media shared to a Room is being recorded. Note that
                 * recording is only available in our Group Rooms developer preview.
                 */
                Log.d(TAG, "onRecordingStarted");
            }

            @Override
            public void onRecordingStopped(@androidx.annotation.NonNull Room room) {
                /*
                 * Indicates when media shared to a Room is no longer being recorded. Note that
                 * recording is only available in our Group Rooms developer preview.
                 */
                Log.d(TAG, "onRecordingStopped");
            }
        };
    }

    private RemoteParticipant.Listener remoteParticipantListener() {
        return new RemoteParticipant.Listener() {
            @Override
            public void onAudioTrackPublished(@androidx.annotation.NonNull RemoteParticipant remoteParticipant, @androidx.annotation.NonNull RemoteAudioTrackPublication remoteAudioTrackPublication) { }

            @Override
            public void onAudioTrackUnpublished(@androidx.annotation.NonNull RemoteParticipant remoteParticipant, @androidx.annotation.NonNull RemoteAudioTrackPublication remoteAudioTrackPublication) { }

            @Override
            public void onAudioTrackSubscribed(@androidx.annotation.NonNull RemoteParticipant remoteParticipant, @androidx.annotation.NonNull RemoteAudioTrackPublication remoteAudioTrackPublication, @androidx.annotation.NonNull RemoteAudioTrack remoteAudioTrack) { }

            @Override
            public void onAudioTrackSubscriptionFailed(@androidx.annotation.NonNull RemoteParticipant remoteParticipant, @androidx.annotation.NonNull RemoteAudioTrackPublication remoteAudioTrackPublication, @androidx.annotation.NonNull TwilioException twilioException) { }

            @Override
            public void onAudioTrackUnsubscribed(@androidx.annotation.NonNull RemoteParticipant remoteParticipant, @androidx.annotation.NonNull RemoteAudioTrackPublication remoteAudioTrackPublication, @androidx.annotation.NonNull RemoteAudioTrack remoteAudioTrack) {

            }

            @Override
            public void onVideoTrackPublished(@androidx.annotation.NonNull RemoteParticipant remoteParticipant, @androidx.annotation.NonNull RemoteVideoTrackPublication remoteVideoTrackPublication) { }

            @Override
            public void onVideoTrackUnpublished(@androidx.annotation.NonNull RemoteParticipant remoteParticipant, @androidx.annotation.NonNull RemoteVideoTrackPublication remoteVideoTrackPublication) { }

            @Override
            public void onVideoTrackSubscribed(@androidx.annotation.NonNull RemoteParticipant remoteParticipant, @androidx.annotation.NonNull RemoteVideoTrackPublication remoteVideoTrackPublication, @androidx.annotation.NonNull RemoteVideoTrack remoteVideoTrack) {
                if(postRoomParticipantEventsListener != null) {
                    postRoomParticipantEventsListener.afterVideoTrackSubscribed(remoteVideoTrack);
                }
            }

            @Override
            public void onVideoTrackSubscriptionFailed(@androidx.annotation.NonNull RemoteParticipant remoteParticipant, @androidx.annotation.NonNull RemoteVideoTrackPublication remoteVideoTrackPublication, @androidx.annotation.NonNull TwilioException twilioException) {

            }

            @Override
            public void onVideoTrackUnsubscribed(@androidx.annotation.NonNull RemoteParticipant remoteParticipant, @androidx.annotation.NonNull RemoteVideoTrackPublication remoteVideoTrackPublication, @androidx.annotation.NonNull RemoteVideoTrack remoteVideoTrack) {
                if(postRoomParticipantEventsListener != null) {
                    postRoomParticipantEventsListener.afterVideoTrackUnsubscribed(remoteVideoTrack);
                }
            }

            @Override
            public void onDataTrackPublished(@androidx.annotation.NonNull RemoteParticipant remoteParticipant, @androidx.annotation.NonNull RemoteDataTrackPublication remoteDataTrackPublication) {

            }

            @Override
            public void onDataTrackUnpublished(@androidx.annotation.NonNull RemoteParticipant remoteParticipant, @androidx.annotation.NonNull RemoteDataTrackPublication remoteDataTrackPublication) {

            }

            @Override
            public void onDataTrackSubscribed(@androidx.annotation.NonNull RemoteParticipant remoteParticipant, @androidx.annotation.NonNull RemoteDataTrackPublication remoteDataTrackPublication, @androidx.annotation.NonNull RemoteDataTrack remoteDataTrack) {

            }

            @Override
            public void onDataTrackSubscriptionFailed(@androidx.annotation.NonNull RemoteParticipant remoteParticipant, @androidx.annotation.NonNull RemoteDataTrackPublication remoteDataTrackPublication, @androidx.annotation.NonNull TwilioException twilioException) {

            }

            @Override
            public void onDataTrackUnsubscribed(@androidx.annotation.NonNull RemoteParticipant remoteParticipant, @androidx.annotation.NonNull RemoteDataTrackPublication remoteDataTrackPublication, @androidx.annotation.NonNull RemoteDataTrack remoteDataTrack) {

            }

            @Override
            public void onAudioTrackEnabled(@androidx.annotation.NonNull RemoteParticipant remoteParticipant, @androidx.annotation.NonNull RemoteAudioTrackPublication remoteAudioTrackPublication) {
                if(postRoomParticipantEventsListener != null) {
                    postRoomParticipantEventsListener.afterAudioTrackEnabled(remoteParticipant);
                }
            }

            @Override
            public void onAudioTrackDisabled(@androidx.annotation.NonNull RemoteParticipant remoteParticipant, @androidx.annotation.NonNull RemoteAudioTrackPublication remoteAudioTrackPublication) {
                if(postRoomParticipantEventsListener != null) {
                    postRoomParticipantEventsListener.afterAudioTrackDisabled(remoteParticipant, room);
                }
            }

            @Override
            public void onVideoTrackEnabled(@androidx.annotation.NonNull RemoteParticipant remoteParticipant, @androidx.annotation.NonNull RemoteVideoTrackPublication remoteVideoTrackPublication) {
                if(postRoomParticipantEventsListener != null) {
                    postRoomParticipantEventsListener.afterVideoTrackEnabled(remoteParticipant);
                }
            }

            @Override
            public void onVideoTrackDisabled(@androidx.annotation.NonNull RemoteParticipant remoteParticipant, @androidx.annotation.NonNull RemoteVideoTrackPublication remoteVideoTrackPublication) {
                if(postRoomParticipantEventsListener != null) {
                    postRoomParticipantEventsListener.afterVideoTrackDisabled(remoteParticipant, room);
                }
            }
        };
    }
}
