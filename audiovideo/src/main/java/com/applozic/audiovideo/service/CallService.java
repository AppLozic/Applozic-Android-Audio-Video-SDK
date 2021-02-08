package com.applozic.audiovideo.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.applozic.audiovideo.activity.AudioCallActivityV2;
import com.applozic.audiovideo.authentication.MakeAsyncRequest;
import com.applozic.audiovideo.authentication.Token;
import com.applozic.audiovideo.authentication.TokenGeneratorCallback;
import com.applozic.audiovideo.core.CallConstants;
import com.applozic.audiovideo.core.RoomApplozicManager;
import com.applozic.audiovideo.listener.AudioVideoUICallback;
import com.applozic.audiovideo.listener.PostRoomEventsListener;
import com.applozic.audiovideo.listener.PostRoomParticipantEventsListener;
import com.applozic.audiovideo.model.CallUIState;
import com.applozic.mobicomkit.api.account.user.MobiComUserPreference;
import com.applozic.mobicomkit.broadcast.BroadcastService;
import com.applozic.mobicommons.json.GsonUtils;

import applozic.com.audiovideo.R;

/**
 * The service that handles a 1-to-1 IP call
 *
 * <p>This service is started by {@link AudioCallActivityV2} and {@link com.applozic.audiovideo.activity.VideoActivity}
 * which also serve as views for this. The aim of creating this service class is to keep the call logic independent from
 * the visuals(view) of calling.</p>
 *
 * <p>The {@link RoomApplozicManager} class is where Twilio calls are initiated and managed.</p>
 */
public class CallService extends Service implements TokenGeneratorCallback {
    private static final String TAG = "CallService";

    private RoomApplozicManager roomApplozicManager;
    private AudioVideoUICallback audioVideoUICallback;
    private CallUIState callUIState;

    public CallUIState getCallUIState() {
        if (callUIState == null) {
            callUIState = new CallUIState();
        }
        return callUIState;
    }

    public void setCallUIState(boolean loudspeakerOn, boolean localAudioEnabled, boolean localVideoEnabled) {

    }

    private final IBinder binder = new AudioVideoCallBinder();

    public void setPostRoomEventsListener(PostRoomEventsListener postRoomEventsListener) {
        if (roomApplozicManager != null) {
            roomApplozicManager.setPostRoomEventsListener(postRoomEventsListener);
        }
    }

    public void setPostRoomParticipantEventsListener(PostRoomParticipantEventsListener postRoomParticipantEventsListener) {
        if (roomApplozicManager != null) {
            roomApplozicManager.setPostRoomParticipantEventsListener(postRoomParticipantEventsListener);
        }
    }

    public void setAudioVideoUICallback(AudioVideoUICallback audioVideoUICallback) {
        this.audioVideoUICallback = audioVideoUICallback;
    }

    public void setCallDurationTickCallback(CallDurationTickCallback callDurationTickCallback) {
        if(roomApplozicManager != null) {
            roomApplozicManager.setCallDurationTickCallback(callDurationTickCallback);
        }
    }

    public RoomApplozicManager getRoomApplozicManager() {
        return roomApplozicManager;
    }

    private void initiateCallSessionWithToken(Token token) {
        roomApplozicManager.setAccessToken(token.getToken());
        roomApplozicManager.initiateRoomCall();
        if(roomApplozicManager != null && roomApplozicManager.isCallReceived()) {
            if(audioVideoUICallback != null) {
                audioVideoUICallback.disconnectAction(roomApplozicManager.getLocalVideoTrack(), roomApplozicManager.getCameraCapturer());
            }
        }
    }

    private void runTaskToRetrieveAccessTokenToThenStartCall() {
        MakeAsyncRequest asyncTask = new MakeAsyncRequest(this, this);
        asyncTask.execute((Void) null); //look for onNetworkComplete()
    }

    public static void setCallServiceOngoing(boolean callServiceOngoing) {
        BroadcastService.videoCallAcitivityOpend = callServiceOngoing;
    }

    public void setupAndCall(RoomApplozicManager roomApplozicManager) {
        roomApplozicManager.createAndReturnLocalAudioTrack();
        roomApplozicManager.createAndReturnLocalVideoTrack();

        //for the call UI
        callUIState = getCallUIState();
        callUIState.setLocalAudioEnabled(true);
        callUIState.setLocalVideoEnabled(true);
        callUIState.setLoudspeakerOn(roomApplozicManager.isVideoCall());

        if(roomApplozicManager.isCallReceived()) {
            scheduleStopRinging();
        }

        if(audioVideoUICallback != null) {
            audioVideoUICallback.connectingCall(roomApplozicManager.getCallId(), roomApplozicManager.isCallReceived());
        }

        roomApplozicManager.registerApplozicBroadcastReceiver();
        runTaskToRetrieveAccessTokenToThenStartCall();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }


    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        setCallServiceOngoing(true);

        String callId = intent.getStringExtra(CallConstants.CALL_ID);
        boolean received = intent.getBooleanExtra(CallConstants.INCOMING_CALL, Boolean.FALSE);
        String userIdContactCalled = intent.getStringExtra(CallConstants.CONTACT_ID);
        boolean videoCall = intent.getBooleanExtra(CallConstants.VIDEO_CALL, Boolean.FALSE);
        roomApplozicManager = new RoomApplozicManager(this, callId, userIdContactCalled, videoCall, received, () -> {
            stopSelf();
            stopForeground(true);
        });

        startForeground(CallNotificationService.CALL_ONGOING_NOTIFICATION_ID, new CallNotificationService(this).getOngoingCallNotification(videoCall, userIdContactCalled, callId, received));

        if(roomApplozicManager != null) {
            setupAndCall(roomApplozicManager);
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        /*
         * Always disconnect from the room on destroy
         * ensure any memory allocated to the Room resource is freed.
         */
        roomApplozicManager.disconnectRoom();
        roomApplozicManager.releaseAudioVideoTracks();
        roomApplozicManager.unregisterApplozicBroadcastReceiver();

        setCallServiceOngoing(false);
    }

    @Override
    public void onNetworkComplete(String response) {
        Log.i(TAG, "Token response: " + response);
        if (TextUtils.isEmpty(response)) {
            Log.i(TAG, "Not able to get token.");
            return;
        }
        Token token = (Token) GsonUtils.getObjectFromJson(response, Token.class);
        MobiComUserPreference.getInstance(this).setVideoCallToken(token.getToken());
        scheduleStopRinging();
        initiateCallSessionWithToken(token);
    }

    public void scheduleStopRinging() {
        final Context context = this;
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                long timeDuration = roomApplozicManager.getScheduledStopTimeDuration();
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        //Check for incoming call if
                        if (roomApplozicManager.getOneToOneCall() != null && roomApplozicManager.getOneToOneCall().isReceived() && roomApplozicManager.getOneToOneCall().getParticipantId() == null) {
                            Toast.makeText(context, R.string.connection_error, Toast.LENGTH_LONG).show();
                            if(audioVideoUICallback != null) {
                                audioVideoUICallback.callConnectionFailure(roomApplozicManager);
                            }
                            return;
                        }

                        if (roomApplozicManager.isScheduleStopRequire()) {
                            roomApplozicManager.sendApplozicMissedCallNotification();
                            Toast.makeText(context, R.string.no_answer, Toast.LENGTH_LONG).show();
                            if(audioVideoUICallback != null) {
                                audioVideoUICallback.noAnswer(roomApplozicManager);
                            }
                        }
                    }
                }, timeDuration);
            }
        });
    }

    public class AudioVideoCallBinder extends Binder {
        public CallService getCallService() {
            return CallService.this;
        }
    }

    /**
     * Callback for stopping service
     *
     * <p>Required when interaction with a UI widget or external event needs to stop the service.</p>
     */
    public interface StopServiceCallback {
        void stopService();
    }

    /**
     * Callback that fires {@link CallDurationTickCallback#onTick(int)} for each tick(second)
     *
     * <p>Required for displaying call timer in UI</p>
     */
    public interface CallDurationTickCallback {
        void onTick(int timeInSeconds);
    }
}
