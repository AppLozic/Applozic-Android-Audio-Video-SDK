package com.applozic.audiovideo.listener;

import com.twilio.video.RemoteParticipant;
import com.twilio.video.Room;

public interface PostRoomEventsListener {
    void afterRoomConnected(Room room);
    void afterRoomDisconnected(Room room);
    void afterRoomConnectionFailure();
    void afterReconnecting();
    void afterConnectionReestablished();
    void afterParticipantConnected(RemoteParticipant remoteParticipant);
    void afterParticipantDisconnected(RemoteParticipant remoteParticipant);
}
