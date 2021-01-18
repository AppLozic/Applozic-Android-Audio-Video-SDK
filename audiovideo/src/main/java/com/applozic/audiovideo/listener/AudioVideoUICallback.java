package com.applozic.audiovideo.listener;

import com.applozic.audiovideo.core.RoomApplozicManager;
import com.twilio.video.CameraCapturer;
import com.twilio.video.LocalVideoTrack;

public interface AudioVideoUICallback {
    void noAnswer(RoomApplozicManager roomApplozicManager); //for what to show when there is no answer
    void callConnectionFailure(RoomApplozicManager roomApplozicManager); //for what to show when there is a failure in connecting to call
    void disconnectAction(LocalVideoTrack localVideoTrack, CameraCapturer cameraCapturer); //to set the disconnect button click action
    void connectingCall(String callId, boolean isReceived); //for what to show when connecting to call
}
