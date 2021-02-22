package com.applozic.audiovideo.core;

import com.applozic.audiovideo.service.CallService;

/**
 * This class is used to expose only the "non-harmful" {@link CallService} functions to the view that is used for the call UI
 *
 * <p>The CallServiceDelegate object is intended to be used with {@link CallService}
 * after a successful {@link android.content.ServiceConnection}.</p>
 */
public class CallServiceDelegate {
    private CallService callService;

    public CallServiceDelegate(CallService callService) {
        this.callService = callService;
    }
}
