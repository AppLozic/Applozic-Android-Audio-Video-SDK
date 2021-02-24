## Applozic Audio Video Call SDK

[[Link to our website.]](https://www.applozic.com)

This is the SDK code and sample app for the Applozic Audio Video SDK. 
You can use this code as a reference to our released SDK. The app can be considered a quick-start app for testing out the waters before you decide to use our messaging and call sdk.
Feel free to clone and fork the repo and check it out. 

You can file issues in the issue tab. 

---

The AppLozic Audio-Video Call SDK provides high quality IP audio and video calls. With this SDK, your application's users can take part in secure 1-to-1 calls.

The audio and video call feature is currently in its beta version and can be used only when using our pre-built UI.
Video and audio calls can be enabled for specific users or all user as per your use-case.

### Setup

#### Pre-requisites

- Before setting up the audio-video call feature, make sure you have finished with the [AppLozic messaging integration](https://docs.applozic.com/docs/android-chat-sdk).
- You will also need to have [push notifications setup](https://docs.applozic.com/docs/android-push-notification).

Without push notifications set-up, calls won't work. Please make sure they are set-up properly before proceeding further.


#### Audio-video call Gradle dependency:

To add our Audio-Video Call SDK add the following dependency in your app-level build.gradle.

```groovy
implementation 'com.applozic.communication.uiwidget:audiovideo:3.1.0'
```

The Audio-Video Call SDK includes our messaging SDK. If you add a dependency to this SDK, you do not need to add the dependency for our Applozic Messaging SDK.


#### Enable audio/video feature:

Audio-video call functionality needs to be enabled for our users. Before you log-in users, add the audio and video call feature and set it for the user object as shown below:

```java
List<String> featureList =  new ArrayList<>();
featureList.add(User.Features.IP_AUDIO_CALL.getValue()); //To enable audio call
featureList.add(User.Features.IP_VIDEO_CALL.getValue()); //To enable video call
user.setFeatures(featureList);
```


#### Add settings for audio/video activity handler:

In the onSuccess function of UserLoginTask, add the following code:

```java
ApplozicClient.getInstance(context).setHandleDial(true).setIPCallEnabled(true);

Map<ApplozicSetting.RequestCode, String> activityCallbacks = new HashMap<ApplozicSetting.RequestCode, String>();
activityCallbacks.put(ApplozicSetting.RequestCode.AUDIO_CALL, AudioCallActivityV2.class.getName());
activityCallbacks.put(ApplozicSetting.RequestCode.VIDEO_CALL, VideoActivity.class.getName());

ApplozicSetting.getInstance(context).setActivityCallbacks(activityCallbacks);
```

The above code will be used to identify the call activities for our Applozic Messaging SDK.


#### Add the call activities in your AndroidManifest.xml:

```xml
<activity
       android:name="com.applozic.audiovideo.activity.AudioCallActivityV2"
       android:configChanges="keyboardHidden|orientation|screenSize"
       android:exported="true" 
       android:launchMode="singleTop"
       android:theme="@style/Applozic_FullScreen_Theme"/>

<activity
       android:name="com.applozic.audiovideo.activity.CallActivity"
       android:configChanges="orientation|keyboardHidden|screenSize"
       android:label="@string/app_name"
       android:launchMode="singleTop"
       android:theme="@style/Applozic_FullScreen_Theme"/>

<activity
       android:name="com.applozic.audiovideo.activity.VideoActivity"              
       android:launchMode="singleTop"
       android:configChanges="keyboardHidden|orientation|screenSize"              
       android:exported="true"
       android:theme="@style/Applozic_FullScreen_Theme"/>
```


#### ProGuard Setup

If you are using ProGuard, add the following to your ProGuard configuration file:

```
-keep class org.webrtc.** { *; }
-keep class com.twilio.video.** { *; }
-keep class com.twilio.common.** { *; }
```


#### Congratulations

The setup is complete.
If a user has audio video calls enabled for them, they will be able to access the call options from the toolbar in the conversation screen/activity for the contact they wish to call.

If you are facing any difficulties, you can contact us at support@applozic.com.
