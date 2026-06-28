# Toggle Grayscale for Android TV

This is a headless application that allows you to quickly toggle the grayscale accessibility mode on an Android TV device. It runs immediately and closes itself in the background without showing any graphical interface.

## Installation and Configuration Instructions (ADB)

For this application to silently modify accessibility settings, it requires special permissions that cannot be granted through the normal user interface, especially on devices like Chromecast with Google TV.

Follow these steps to connect via ADB to your TV and grant the application the necessary permission:

### Step 1: Enable Developer Options on the TV
1. Go to your Chromecast / Android TV **Settings** (gear icon).
2. Go to **System** > **About**.
3. Scroll down to **Android TV OS build** and press the center button on your remote 7 times in a row until you see the message "You are now a developer!".
4. Go back to the **System** menu, and you will now see **Developer options**.
5. Enter and enable the **USB debugging** (or "Network debugging" if available) option.

### Step 2: Connect PC to Chromecast via ADB
You will need ADB tools installed on your computer. 

On this machine, the correct and working ADB executable is located at:
`C:\Users\agust\AppData\Local\Android\Sdk\platform-tools\adb.exe`

*(Avoid using the scrcpy/winget ADB version as it may cause daemon conflicts).*

1. Open a terminal (or command prompt) on your PC.
2. Find your TV's IP address: on the TV, go to **Settings** > **Network & Internet**, select your Wi-Fi network and note down the IP Address (e.g., `192.168.1.50`).
3. In your PC's terminal, run:
   ```bash
   adb connect 192.168.1.50
   ```
   *(Change the IP to match yours)*
4. A prompt will appear on your TV screen asking if you allow debugging. Check "Always allow from this computer" and select **OK**.
5. Verify you are connected by running:
   ```bash
   adb devices
   ```
   Your TV's IP should appear with the text `device` next to it.

### Step 3: Install the app and grant permissions
1. Install the Toggle Grayscale APK on your TV. You can push it over the network using ADB:
   ```bash
   adb install path/to/file/app-debug.apk
   ```
2. Once installed, you must grant the special permission to modify secure settings by running the following command:
   ```bash
   adb shell pm grant com.example.togglegrayscale android.permission.WRITE_SECURE_SETTINGS
   ```

You're all set! Now when you open "Toggle Grayscale", the screen will immediately switch to grayscale (or return to color if it was already on), without showing any window.
