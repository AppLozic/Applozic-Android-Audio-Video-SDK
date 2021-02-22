package com.applozic.audiovideo.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import androidx.core.content.ContextCompat;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.applozic.audiovideo.core.RoomApplozicManager;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.twilio.video.CameraCapturer;
import com.twilio.video.LocalVideoTrack;
import com.twilio.video.VideoView;

import applozic.com.audiovideo.R;

/**
 * This activity extends {@link AudioCallActivityV2} and adds additional functionality for video calls.
 */
public class VideoActivity extends AudioCallActivityV2 {
    private static final String TAG = VideoActivity.class.getName();

    LinearLayout videoOptionLayout;

    public VideoActivity() {
        super(true);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initCallDataFrom(getIntent());

        setContentView(R.layout.activity_conversation);

        contactName = (TextView) findViewById(R.id.contact_name);
        callTimerText = (TextView) findViewById(R.id.applozic_audio_timer);

        if(contactCalled != null) {
            contactName.setText(contactCalled.getDisplayName());
        }

        primaryVideoView = (VideoView) findViewById(R.id.primary_video_view);
        thumbnailVideoView = (VideoView) findViewById(R.id.thumbnail_video_view);

        connectActionFab = (FloatingActionButton) findViewById(R.id.call_action_fab);
        switchCameraActionFab = (FloatingActionButton) findViewById(R.id.switch_camera_action_fab);
        localVideoActionFab = (FloatingActionButton) findViewById(R.id.local_video_action_fab);
        muteActionFab = (FloatingActionButton) findViewById(R.id.mute_action_fab);
        speakerActionFab = (FloatingActionButton) findViewById(R.id.speaker_action_fab);
        videoOptionLayout = (LinearLayout) findViewById(R.id.video_call_option);
        FrameLayout frameLayout = (FrameLayout) findViewById(R.id.video_container);
        videoStatusTextView = (TextView) findViewById(R.id.video_status_textview);
        videoMuteStatus = (ImageView) findViewById(R.id.video_mute_status);
        if(!received) {
            setVideoCallStatusText(getString(R.string.status_text_calling));
        }

        frameLayout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                hideShowWithAnimation();
                return false;
            }
        });

        /*
         * Check camera and microphone permissions. Needed in Android M.
         */
        if (!checkPermissionForCameraAndMicrophone()) {
            requestPermissionForCameraAndMicrophone();
        } else {
            startAndOrBindCallService(activityStartedFromNotification || callServiceIsRunningInForeground(this));
        }

    }

    private void hideShowWithAnimation() {
        if (switchCameraActionFab.isShown()) {
            switchCameraActionFab.hide();
        } else {
            switchCameraActionFab.show();

        }

        if (muteActionFab.isShown()) {
            muteActionFab.hide();
        } else {
            muteActionFab.show();

        }

        if (localVideoActionFab.isShown()) {
            localVideoActionFab.hide();
        } else {
            localVideoActionFab.show();
        }

        if (speakerActionFab.isShown()) {
            speakerActionFab.hide();
        } else {
            speakerActionFab.show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        /*
         * If the local video track was released when the app was put in the background, recreate.
         */
        try {
            if (callService == null) {
                return;
            }
            RoomApplozicManager roomApplozicManager = callService.getRoomApplozicManager();
            LocalVideoTrack localVideoTrack = roomApplozicManager.getLocalVideoTrack();
            if (videoCall) {
                if (checkPermissionForCameraAndMicrophone()) {
                    if (localVideoTrack == null) {
                        localVideoTrack = roomApplozicManager.getLocalVideoTrack();
                    }
                    localVideoTrack.addRenderer(localVideoView);
                    roomApplozicManager.publishLocalVideoTrack();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if(callService == null || callService.getRoomApplozicManager() == null) {
            return;
        }
        callService.getRoomApplozicManager().unPublishLocalVideoTrack();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    /*
     * The initial state when there is no active conversation.
     */
    @Override
    protected void setDisconnectAction(LocalVideoTrack localVideoTrack, CameraCapturer cameraCapturer) {
        super.setDisconnectAction(localVideoTrack, cameraCapturer);
        if(cameraCapturer == null || localVideoTrack == null) {
            return;
        }
        if (isFrontCamAvailable(getBaseContext())) {
            switchCameraActionFab.show();
            switchCameraActionFab.setOnClickListener(switchCameraClickListener(cameraCapturer));
        } else {
            switchCameraActionFab.hide();
        }
        localVideoActionFab.show();
        localVideoActionFab.setOnClickListener(localVideoClickListener(localVideoTrack));
    }

    private View.OnClickListener switchCameraClickListener(CameraCapturer cameraCapturer) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if (cameraCapturer != null) {
                        CameraCapturer.CameraSource cameraSource = cameraCapturer.getCameraSource();
                        cameraCapturer.switchCamera();
                        if (thumbnailVideoView.getVisibility() == View.VISIBLE) {
                            thumbnailVideoView.setMirror(cameraSource == CameraCapturer.CameraSource.BACK_CAMERA);
                        } else {
                            primaryVideoView.setMirror(cameraSource == CameraCapturer.CameraSource.BACK_CAMERA);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
    }

    private View.OnClickListener localVideoClickListener(LocalVideoTrack localVideoTrack) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (localVideoTrack != null) {
                    boolean enable = !localVideoTrack.isEnabled();
                    localVideoTrack.enable(enable);
                    int icon;
                    if (enable) {
                        icon = R.drawable.ic_videocam_green_24px;
                        switchCameraActionFab.show();
                    } else {
                        icon = R.drawable.ic_videocam_off_red_24px;
                        switchCameraActionFab.hide();
                    }
                    localVideoActionFab.setImageDrawable(
                            ContextCompat.getDrawable(VideoActivity.this, icon));
                }
            }
        };
    }

    public boolean isFrontCamAvailable(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)) {
            return true;
        } else {
            return false;
        }
    }

}
