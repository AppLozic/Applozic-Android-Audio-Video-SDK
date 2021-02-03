package com.applozic.audiovideo.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.applozic.audiovideo.activity.AudioCallActivityV2;
import com.applozic.audiovideo.activity.VideoActivity;
import com.applozic.audiovideo.core.CallConstants;
import com.applozic.mobicomkit.Applozic;
import com.applozic.mobicomkit.api.notification.NotificationChannels;
import com.applozic.mobicommons.commons.core.utils.Utils;

import applozic.com.audiovideo.R;

public class CallNotificationService {
    public static final int CALL_ONGOING_NOTIFICATION_ID = 12345678; //arbitrary number for id

    private NotificationChannels notificationChannels;
    private Context context;

    public CallNotificationService(Context context) {
        notificationChannels = new NotificationChannels(context, Applozic.getInstance(context).getCustomNotificationSound());
        this.context = context;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationChannels.prepareNotificationChannels();
        }
    }

    public Notification getOngoingCallNotification(boolean videoCall, String contactId, String callId, boolean incomingCall) {
        Intent notificationIntent;
        if(videoCall) {
            notificationIntent = new Intent(context, VideoActivity.class);
        } else {
            notificationIntent = new Intent(context, AudioCallActivityV2.class);
        }

        notificationIntent.putExtra(CallConstants.CALL_ACTIVITY_STARTED_FROM_NOTIFICATION, true);
        notificationIntent.putExtra("CONTACT_ID", contactId);
        notificationIntent.putExtra("INCOMING_CALL", incomingCall);
        notificationIntent.putExtra("CALL_ID", callId);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent pendingIntent =
                PendingIntent.getActivity(context, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(context, notificationChannels.getDefaultChannelId(true))
                .setContentTitle(context.getResources().getString(R.string.ongoing_call_notification_text))
                .setContentText(videoCall ? context.getResources().getString(R.string.ongoing_call_notification_video_call_text) : context.getResources().getString(R.string.ongoing_call_notification_audio_call_text))
                .setContentIntent(pendingIntent)
                .setSmallIcon(Utils.getLauncherIcon(context.getApplicationContext()))
                .setOngoing(true)
                .build();
    }
}
