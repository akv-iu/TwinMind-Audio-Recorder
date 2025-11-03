# TwinMind Audio Recorder

A simple Android audio recording app built with Jetpack Compose that supports pause/resume functionality, call detection, and visual playback feedback.

## Core Features

- **True Pause/Resume**: Puses and resume recording to the same file (no segments)
- **Lock Screen Recording Status**: Live recording timer and controls visible on lock screen
- **Interactive Notification Controls**: Pause/resume/stop recording directly from notification bar
- **Call Detection**: Automatic pause notifications during phone calls  
- **Silent Audio Detection**: Warns when no audio input is detected for 10+ seconds
- **Audio Device Switching**: Automatic switching between Bluetooth, wired, and device microphones
- **Storage Management**: Real-time storage monitoring with automatic recording stop when storage is low
- **Visual Feedback**: Highlights currently playing recordings
- **Single File Output**: All pauses/resumes save to one continuous file
- **Permission Management**: Handles microphone, phone state, Bluetooth, and notification permissions

## Core Classes

### `MainActivity.kt`
Main activity containing the Compose UI for recording and playback controls.
- `onCreate()` - Requests permissions and sets up UI
- `AudioRecorderApp()` - Main UI composable with recording controls and list
- `RecordingCard()` - Individual recording item with play/pause functionality
- `formatDuration()` - Converts seconds to human-readable duration format

### `RecordingViewModel.kt`
Core business logic for audio recording with true pause/resume and call detection.
- `toggleRecord()` - Starts/stops recording based on current state
- `togglePause()` - Pauses/resumes active recording in same file
- `play()` - Plays selected recording with visual highlighting
- `stopPlayback()` - Stops audio playback and clears playing state
- `startRecording()` - Begins new recording session with single file
- `pauseRecording()` - Pauses recording using MediaRecorder.pause() (no file save)
- `resumeRecording()` - Resumes recording to same file using MediaRecorder.resume()
- `stopRecording()` - Ends recording and saves single complete file
- `setupPhoneStateListener()` - Configures call detection for notifications
- `handleCallStateChange()` - Shows/hides notifications during phone calls

### `AndroidAudioRecorder.kt`
MediaRecorder wrapper for audio recording functionality with pause/resume support and silence detection.
- `start()` - Begins recording to specified file with silence monitoring
- `stop()` - Stops recording and releases resources
- `pause()` - Pauses recording (Android N+) without stopping file
- `resume()` - Resumes recording (Android N+) to same file
- `switchAudioSource()` - Changes audio input source during recording
- `setSilenceDetectionCallback()` - Sets callback for silence detection events

### `AndroidAudioPlayer.kt`
MediaPlayer wrapper for audio playback functionality.
- `playFile()` - Plays audio file with automatic resource management
- `stop()` - Stops playback and releases resources
- `setOnCompletionListener()` - Sets callback for when playback finishes

### `RecordingService.kt`
Foreground service that displays live recording status on lock screen with interactive controls.
- `startForeground()` - Creates persistent notification with timer and pause/stop buttons
- `updateNotification()` - Updates notification with current recording status and elapsed time
- `onStartCommand()` - Handles notification button presses (pause/resume/stop actions)
- `syncWithViewModelState()` - Synchronizes service status with ViewModel recording state
- `createNotification()` - Builds stopwatch-style notification for lock screen visibility
- Uses `CATEGORY_STOPWATCH` and `IMPORTANCE_HIGH` for optimal lock screen display

### `NotificationHelper.kt`
Manages notifications for recording events including call detection, audio source changes, storage warnings, and silence detection.
- `showRecordingPausedNotification()` - Displays call interruption notification
- `showAudioSourceChangeNotification()` - Shows when audio input device changes
- `showLowStorageNotification()` - Warns about insufficient storage during recording
- `showSilenceWarningNotification()` - Alerts when no audio is detected for 10+ seconds
- `hideRecordingPausedNotification()` - Removes pause notification
- `createNotificationChannel()` - Sets up notification channels for Android 8+

### `SilenceDetector.kt`
Monitors audio levels during recording to detect periods of silence.
- `startMonitoring()` - Begins audio level monitoring using AudioRecord
- `stopMonitoring()` - Stops silence detection and releases resources
- `calculateRMS()` - Calculates audio level from raw audio samples
- `getCurrentSilenceDuration()` - Returns current consecutive silence duration
- `resetSilenceCounter()` - Manually resets silence detection counter

### `AudioDeviceManager.kt`
Manages audio input device detection and automatic switching between microphone sources.
- `startMonitoring()` - Monitors for audio device connection/disconnection events
- `getCurrentDevice()` - Returns currently active audio input device
- `forceDeviceDetection()` - Manually triggers device detection scan
- Audio device types: Bluetooth headset, wired headset, USB headset, device microphone

### `StorageManager.kt`
Monitors device storage and manages recording based on available space.
- `hasEnoughStorageToStart()` - Checks if sufficient storage exists before recording
- `startStorageMonitoring()` - Monitors storage levels during recording
- `getStorageInfo()` - Returns detailed storage statistics
- `getEstimatedRecordingTimeMinutes()` - Calculates estimated recording time based on available storage
- Storage thresholds: 50MB minimum to start, 100MB warning level, 25MB critical stop level

### `NotificationPermissionHelper.kt`
Utility class for managing notification permissions and guiding users for optimal lock screen experience.
- `areNotificationsEnabled()` - Checks if app notifications are enabled
- `canShowOnLockScreen()` - Determines if notifications can appear on lock screen
- `getNotificationSettingsIntent()` - Creates intent to open notification settings for the app
- `getLockScreenSettingsIntent()` - Creates intent to open device lock screen settings
- `getPermissionMessage()` - Provides user-friendly permission status messages

## How It Works

### Recording Flow
1. **Start Recording** - Creates a new M4A file and begins audio capture
2. **Pause** (Optional) - Uses MediaRecorder.pause() to temporarily stop recording
3. **Resume** (Optional) - Uses MediaRecorder.resume() to continue to same file
4. **Stop Recording** - Finalizes and saves the complete audio file

### Call Detection
- App detects incoming/active phone calls using TelephonyManager
- Shows notification when recording is paused due to call
- User manually controls pause/resume using app buttons
- Notification automatically disappears when call ends

### Silent Audio Detection
- Continuously monitors audio input levels during recording using AudioRecord
- Detects when audio falls below silence threshold (500 amplitude) for 10+ seconds
- Shows "No audio detected - Check microphone" notification warning
- Helps identify microphone issues, muted devices, or positioning problems
- Automatically resets when audio input is detected again

### Audio Device Management
- Automatically detects Bluetooth headset connections/disconnections
- Supports wired headsets, USB headsets, and device microphone
- Shows notifications when audio source changes during recording
- Attempts to switch MediaRecorder audio source when device changes
- Optimizes audio source selection based on connected device type

### Storage Management
- Checks available storage before starting recording (requires 50MB minimum)
- Monitors storage levels continuously during recording
- Shows warning notification when storage drops below 100MB
- Automatically stops recording when storage reaches critical level (25MB)
- Estimates remaining recording time based on available storage and audio bitrate

### Recording States
- **Stopped**: No active recording session
- **Recording**: Actively capturing audio to file
- **Paused**: Recording temporarily stopped (file remains open)

## File Organization

Recordings are saved to app external files directory as:
- `meeting_YYYYMMDD_HHMMSS.m4a` - Complete recording file (including paused periods)

## Requirements

- **Android N (API 24+)** - For true pause/resume functionality
- **Android O (API 26+)** - Recommended for optimal notification channels and lock screen display
- **Permissions**: 
  - `RECORD_AUDIO` - Required for audio recording and silence detection
  - `FOREGROUND_SERVICE` - Required for persistent recording notifications
  - `FOREGROUND_SERVICE_MICROPHONE` - Required for microphone access in foreground service (Android 14+)
  - `POST_NOTIFICATIONS` - Required for lock screen notifications (Android 13+)
  - `READ_PHONE_STATE` - Optional for call detection notifications
  - `BLUETOOTH_CONNECT` - Optional for Bluetooth headset detection (Android 12+)
  - `MODIFY_AUDIO_SETTINGS` - Optional for audio device management

## Usage

1. **Grant Permissions** - Allow microphone, phone, Bluetooth, and notification access when prompted
2. **Enable Lock Screen Notifications** - For full lock screen functionality, ensure notifications are enabled in Settings
3. **Check Storage** - Ensure device has at least 50MB free storage for recording
4. **Start Recording** - Tap the record button to begin (live status appears on lock screen)
5. **Lock Screen Control** - When recording, you can:
   - View live timer updates on lock screen
   - Pause/resume recording using notification buttons
   - Stop recording directly from notification
6. **Monitor Notifications** - App will notify you of:
   - Phone call interruptions (auto-pause)
   - Audio device changes (Bluetooth/wired headset connect/disconnect)
   - Storage warnings (when space runs low)
   - Silent audio detection (no input for 10+ seconds)
7. **In-App Control** - Use main app for full control and monitoring
8. **Playback** - Tap any saved recording to play with visual highlighting

## Notification Features

### Lock Screen Recording Status
- **Live Timer Display**: Shows real-time recording duration on lock screen (updates every second)
- **Interactive Controls**: Pause/Resume and Stop buttons work directly from notification
- **Status Indicators**: Clear display of recording state ("Recording", "Paused", "Paused - Call")
- **Stopwatch-Style Design**: Uses Android's stopwatch notification pattern for consistency
- **High Priority Display**: Notification configured for optimal lock screen visibility

### Event Notifications
- **Call Detection**: Shows when recording is paused due to phone calls
- **Audio Source Changes**: Alerts when switching between microphones/headsets
- **Storage Warnings**: Warns when storage is running low during recording
- **Silent Audio Detection**: "No audio detected - Check microphone" after 10s silence
- **Permission Guidance**: Prompts user to enable notifications for lock screen display
- **Smart Intent Handling**: Notifications bring existing app to foreground (no duplicate instances)