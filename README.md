# GoEasyPro Android
Easily control multiple GoPros with your android device with bluetooth<br><br>
<b>Live preview, media browsing and much more is supported!</b><br><br>
[Download latest release](https://github.com/sepp89117/GoEasyPro_Android/releases/latest)
<br><br>
<img src="https://github.com/sepp89117/GoEasyPro_Android/blob/master/GoEasyPro%20Android_v1.5.jpg?raw=true" width="400px">

# Usage information
<ul>
  <li>Compile the project with Android Studio and launch the app from there or install a release APK directly on the Android device.</li>
  <li>Open the app and grant the permission requests</li>
  <li>To pair new cameras, put the GoPro in pairing mode to pair with 'GoPro App' or 'Quick App' and click the "Add cam" button</li>
  <ul>
    <li>If your camera is already showing as paired, it doesn't need to be paired again.</li>
    <li>If the camera does not appear after the scan is complete, click "Scan again"</li>
    <li>Just click on the cameras to be connected in the list. 
    <ul>
      <li>If necessary, it will be automatically paired.</li>
      <li>The GoPro will show you a successful pairing and the camera will show as 'connected' in the app. (after pairing, the connection will be established automatically)</li>
    </ul>
    <li>After making the connections, go back to the main screen.</li>
  </ul>
  <li>To connect a paired camera, click on one of the cameras in the list and choose 'Try to connect'.</li>
  <li>The "Cams" list shows information about the paired cameras. If you click on a camera in the list, you will see a menu for controlling the individual cameras.</li>
  <li>In the "Control" field you can control all cameras at the same time.</li>
  
  <li>The SD card icon shows the WiFi status by its color.</li>
    <ul>
      <li>Red means: Camera's WiFi AP is off.</li>
      <li>Yellow means: Camera's WiFi AP is on but not connected to the app.</li>
      <li>Green means we are connected to the camera's WiFi.</li>
    </ul>
    <li>The Bluetooth symbol shows the connection of the app to the camera in color. The value below is the signal strength in dBm.</li>
</ul>

# Hardware and software requirements
- Supported Android versions: 8.1 (O_MR1; API Level 27) to 13 (TIRAMISU; API Level 33) (currently not all tested)
- Android device with Bluetooth support
- Android device with WiFi support; only needed for preview stream and media browsing

# Credits and Acknowledgments
- Thanks to [KonradIT](https://github.com/KonradIT) for his support, testing the app with various camera models and also for the helpful information contained in his repositories like [goprowifihack](https://github.com/KonradIT/goprowifihack) and [gopro-py-api](https://github.com/KonradIT/gopro-py-api)!
- Thanks to the open source library creators listed below
- Thanks to GoPro for the information in [OpenGoPro](https://gopro.github.io/OpenGoPro)

# Libraries used in this app
- [CascadePopupMenu](https://github.com/saket/cascade) with [Apache-2.0 license](https://github.com/saket/cascade/blob/trunk/LICENSE.txt) by [saket](https://github.com/saket)
- [ffmpeg-kit-min](https://github.com/arthenica/ffmpeg-kit) with [LGPL-3.0 license](https://github.com/arthenica/ffmpeg-kit/blob/main/LICENSE) by [arthenica](https://github.com/arthenica)
- [OkHttp](https://github.com/square/okhttp) with [Apache-2.0 license](https://github.com/square/okhttp/blob/master/LICENSE.txt) by [square](https://github.com/square)
- [ExoPlayer](https://github.com/google/ExoPlayer) with [Apache-2.0 license](https://github.com/google/ExoPlayer/blob/release-v2/LICENSE) by [google](https://github.com/google)

# Debugging on
- Samsung Galaxy S10 (Android 12)
- Xiaomi Redmi Note 11s (Android 11)
- Samsung Tab S4 (Android 10)
- with Hero5 Black, Hero8 Black and Hero10 Black only!<br>

If you have trouble with other Android devices or other camera models, please open a detailed issue.

I hope you can help to get the app running safely on other smartphones too!
Feel free to create an issue!
