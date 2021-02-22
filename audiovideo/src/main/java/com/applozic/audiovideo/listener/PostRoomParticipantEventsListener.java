package com.applozic.audiovideo.listener;

import com.twilio.video.RemoteParticipant;
import com.twilio.video.RemoteVideoTrack;
import com.twilio.video.RemoteVideoTrackPublication;
import com.twilio.video.Room;

public interface PostRoomParticipantEventsListener {
    void afterVideoTrackSubscribed(RemoteVideoTrack remoteVideoTrack);
    void afterVideoTrackUnsubscribed(RemoteVideoTrack remoteVideoTrack);
    void afterParticipantConnectedToCall(RemoteVideoTrackPublication remoteVideoTrackPublication);
    void afterParticipantDisconnectedFromCall(RemoteVideoTrackPublication remoteVideoTrackPublication);
    void afterVideoTrackEnabled(RemoteParticipant remoteParticipant);
    void afterVideoTrackDisabled(RemoteParticipant remoteParticipant, Room room);
    void afterAudioTrackEnabled(RemoteParticipant remoteParticipant);
    void afterAudioTrackDisabled(RemoteParticipant remoteParticipant, Room room);
}
