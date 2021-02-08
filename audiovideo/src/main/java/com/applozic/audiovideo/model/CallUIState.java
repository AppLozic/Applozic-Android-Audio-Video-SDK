package com.applozic.audiovideo.model;

/**
 * Model to store the call UI state.
 *
 * <p>This class is used to setup the UI in case of rebinding of service with UI.
 * If a situation like such arises, the states of the UI buttons/widgets etc. should be in memory.</p>
 */
public class CallUIState {
    public boolean loudspeakerOn;
    public boolean localAudioEnabled;
    public boolean localVideoEnabled;

    public boolean isLoudspeakerOn() {
        return loudspeakerOn;
    }

    public void setLoudspeakerOn(boolean loudspeakerOn) {
        this.loudspeakerOn = loudspeakerOn;
    }

    public boolean isLocalAudioEnabled() {
        return localAudioEnabled;
    }

    public void setLocalAudioEnabled(boolean localAudioEnabled) {
        this.localAudioEnabled = localAudioEnabled;
    }

    public boolean isLocalVideoEnabled() {
        return localVideoEnabled;
    }

    public void setLocalVideoEnabled(boolean localVideoEnabled) {
        this.localVideoEnabled = localVideoEnabled;
    }
}
