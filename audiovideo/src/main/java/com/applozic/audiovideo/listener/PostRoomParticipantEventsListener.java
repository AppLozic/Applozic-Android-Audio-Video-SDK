package com.applozic.audiovideo.listener;

import com.twilio.video.RemoteVideoTrack;
import com.twilio.video.RemoteVideoTrackPublication;

public interface PostRoomParticipantEventsListener {
    void afterVideoTrackSubscribed(RemoteVideoTrack remoteVideoTrack);
    void afterVideoTrackUnsubscribed(RemoteVideoTrack remoteVideoTrack);
    void afterParticipantConnectedToCall(RemoteVideoTrackPublication remoteVideoTrackPublication);
    void afterParticipantDisconnectedFromCall(RemoteVideoTrackPublication remoteVideoTrackPublication);
}
