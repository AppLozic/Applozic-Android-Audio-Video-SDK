package com.applozic.audiovideo.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import androidx.core.content.ContextCompat;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

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

    LinearLayout videoOptionlayout;

    public VideoActivity() {
        super(true);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        init();

        setContentView(R.layout.activity_conversation);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        contactName = (TextView) findViewById(R.id.contact_name);
        //profileImage = (ImageView) findViewById(R.id.applozic_audio_profile_image);
        textCount = (TextView) findViewById(R.id.applozic_audio_timer);

        if(contactCalled != null) {
            contactName.setText(contactCalled.getDisplayName());
        }
        //pauseVideo = true;

        primaryVideoView = (VideoView) findViewById(R.id.primary_video_view);
        thumbnailVideoView = (VideoView) findViewById(R.id.thumbnail_video_view);

        videoStatusTextView = (TextView) findViewById(R.id.video_status_textview);
        videoStatusTextView.setVisibility(View.GONE);

        connectActionFab = (FloatingActionButton) findViewById(R.id.call_action_fab);
        switchCameraActionFab = (FloatingActionButton) findViewById(R.id.switch_camera_action_fab);
        localVideoActionFab = (FloatingActionButton) findViewById(R.id.local_video_action_fab);
        muteActionFab = (FloatingActionButton) findViewById(R.id.mute_action_fab);
        speakerActionFab = (FloatingActionButton) findViewById(R.id.speaker_action_fab);
        videoOptionlayout = (LinearLayout) findViewById(R.id.video_call_option);
        FrameLayout frameLayout = (FrameLayout) findViewById(R.id.video_container);

        frameLayout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                hideShowWithAnimation();
                return false;
            }
        });

        /*
         * Enable changing the volume using the up/down keys during a conversation
         */
        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

        /*
         * Needed for setting/abandoning audio focus during call
         */
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        /*
         * Check camera and microphone permissions. Needed in Android M.
         */
        if (!checkPermissionForCameraAndMicrophone()) {
            requestPermissionForCameraAndMicrophone();
        } else {
            setupAndStartCallService();
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
    public void setupAndStartCallService() {
        super.setupAndStartCallService();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
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
                    //CameraCapturer cameraCapturer = roomApplozicManager.getCameraCapturer();
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

    //TODO: ServiceStuff pass local video track
    private View.OnClickListener localVideoClickListener(LocalVideoTrack localVideoTrack) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //LocalVideoTrack localVideoTrack = roomApplozicManager.getLocalVideoTrack();
                /*
                 * Enable/disable the local video track
                 */
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
