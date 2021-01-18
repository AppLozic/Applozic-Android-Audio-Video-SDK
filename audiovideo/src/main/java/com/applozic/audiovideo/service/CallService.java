package com.applozic.audiovideo.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.PendingIntent;
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
import androidx.core.app.NotificationCompat;

import com.applozic.audiovideo.activity.AudioCallActivityV2;
import com.applozic.audiovideo.activity.VideoActivity;
import com.applozic.audiovideo.authentication.MakeAsyncRequest;
import com.applozic.audiovideo.authentication.Token;
import com.applozic.audiovideo.authentication.TokenGeneratorCallback;
import com.applozic.audiovideo.core.RoomApplozicManager;
import com.applozic.audiovideo.listener.AudioVideoUICallback;
import com.applozic.audiovideo.listener.PostRoomEventsListener;
import com.applozic.audiovideo.listener.PostRoomParticipantEventsListener;
import com.applozic.mobicomkit.api.account.user.MobiComUserPreference;
import com.applozic.mobicommons.json.GsonUtils;

import applozic.com.audiovideo.R;

public class CallService extends Service implements TokenGeneratorCallback {
    private static final String TAG = "CallService";

    private RoomApplozicManager roomApplozicManager;
    private AudioVideoUICallback audioVideoUICallback;

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

    public void setupAndCall(RoomApplozicManager roomApplozicManager) {
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
        String callId = intent.getStringExtra("CONTACT_ID");
        boolean received = intent.getBooleanExtra("INCOMING_CALL", Boolean.FALSE);
        String userIdContactCalled = intent.getStringExtra("CALL_ID");
        boolean videoCall = intent.getBooleanExtra("VIDEO_CALL", Boolean.FALSE);
        roomApplozicManager = new RoomApplozicManager(this, true, callId, userIdContactCalled, videoCall, received);
        roomApplozicManager.createAndReturnLocalAudioTrack();
        roomApplozicManager.createAndReturnLocalVideoTrack();
        if(roomApplozicManager != null) {
            setupAndCall(roomApplozicManager);
        }
        Intent notificationIntent;
        if(videoCall) {
            notificationIntent = new Intent(this, VideoActivity.class);
        } else {
            notificationIntent = new Intent(this, AudioCallActivityV2.class);
        }
        PendingIntent pendingIntent =
                PendingIntent.getActivity(this, 0, notificationIntent, 0);

        Notification notification = new NotificationCompat.Builder(this, NotificationChannel.DEFAULT_CHANNEL_ID)
                .setContentTitle("Ongoing Call")
                .setContentText(videoCall ? "Video Call" : "Audio Call")
                .setContentIntent(pendingIntent)
                .build();

        startForeground(5, notification);
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
}
