# TwinMind Audio Recorder

A simple Android audio recording app built with Jetpack Compose that supports pause/resume functionality, call detection, and visual playback feedback.

## Core Features

- **True Pause/Resume**: Pause and resume recording to the same file (no segments)
- **Call Detection**: Automatic pause notifications during phone calls  
- **Visual Feedback**: Highlights currently playing recordings
- **Single File Output**: All pauses/resumes save to one continuous file
- **Permission Management**: Handles microphone, phone state, and notification permissions

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
MediaRecorder wrapper for audio recording functionality with pause/resume support.
- `start()` - Begins recording to specified file
- `stop()` - Stops recording and releases resources
- `pause()` - Pauses recording (Android N+) without stopping file
- `resume()` - Resumes recording (Android N+) to same file

### `AndroidAudioPlayer.kt`
MediaPlayer wrapper for audio playback functionality.
- `playFile()` - Plays audio file with automatic resource management
- `stop()` - Stops playback and releases resources
- `setOnCompletionListener()` - Sets callback for when playback finishes

### `NotificationHelper.kt`
Manages notifications for recording pause events during phone calls.
- `showRecordingPausedNotification()` - Displays call interruption notification
- `hideRecordingPausedNotification()` - Removes pause notification
- `createNotificationChannel()` - Sets up notification channel for Android 8+

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

### Recording States
- **Stopped**: No active recording session
- **Recording**: Actively capturing audio to file
- **Paused**: Recording temporarily stopped (file remains open)

## File Organization

Recordings are saved to app external files directory as:
- `meeting_YYYYMMDD_HHMMSS.m4a` - Complete recording file (including paused periods)

## Requirements

- **Android N (API 24+)** - For true pause/resume functionality
- **Permissions**: 
  - `RECORD_AUDIO` - Required for audio recording
  - `READ_PHONE_STATE` - Optional for call detection notifications
  - `POST_NOTIFICATIONS` - Optional for call pause notifications

## Usage

1. **Grant Permissions** - Allow microphone and phone access when prompted
2. **Start Recording** - Tap the record button to begin
3. **Pause/Resume** - Use pause button during calls or breaks (optional)
4. **Stop** - Tap stop to finalize and save the recording
5. **Playback** - Tap any saved recording to play with visual highlighting