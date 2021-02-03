package com.applozic.audiovideo.model;

import com.applozic.mobicommons.people.contact.Contact;

/**
 * Model class for a ongoing audio-video call
 */
public class OneToOneCall {
    private String callId;
    private boolean videoCall; //audio or video
    private boolean received; //the call was received instead of being dialled
    private boolean inviteSent; //invite sent to pick up call
    private String participantId; //remote participant in twilio terms
    private Contact contactCalled; //the contact being called
    private long callStartTime;

    public OneToOneCall(String callId, boolean videoCall, Contact contactCalled, boolean received) {
        this.callId = callId;
        this.videoCall = videoCall;
        this.contactCalled = contactCalled;
        this.participantId = contactCalled.getUserId();
        this.received = received;
    }

    public String getParticipantId() {
        return participantId;
    }

    public void setParticipantId(String participantId) {
        this.participantId = participantId;
    }

    public Contact getContactCalled() {
        return contactCalled;
    }

    public void setContactCalled(Contact contactCalled) {
        this.contactCalled = contactCalled;
    }

    public String getCallId() {
        return callId;
    }

    public void setCallId(String callId) {
        this.callId = callId;
    }

    public boolean isVideoCall() {
        return videoCall;
    }

    public void setVideoCall(boolean videoCall) {
        this.videoCall = videoCall;
    }

    public boolean isReceived() {
        return received;
    }

    public void setReceived(boolean received) {
        this.received = received;
    }

    public boolean isInviteSent() {
        return inviteSent;
    }

    public void setInviteSent(boolean inviteSent) {
        this.inviteSent = inviteSent;
    }

    public long getCallStartTime() {
        return callStartTime;
    }

    public void setCallStartTime(long callStartTime) {
        this.callStartTime = callStartTime;
    }
}
