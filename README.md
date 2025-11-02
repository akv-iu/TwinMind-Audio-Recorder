# TwinMind Audio Recorder



## Core Classes

### `MainActivity.kt`
Main activity containing the Compose UI for recording and playback controls.
- `onCreate()` - Requests permissions and sets up UI
- `AudioRecorderApp()` - Main UI composable with recording controls and list
- `RecordingCard()` - Individual recording item with play/pause functionality
- `formatDuration()` - Converts seconds to human-readable duration format

### `RecordingViewModel.kt`
Core business logic for audio recording with pause/resume and call detection.
- `toggleRecord()` - Starts/stops recording based on current state
- `togglePause()` - Pauses/resumes active recording 
- `play()` - Plays selected recording with visual highlighting
- `stopPlayback()` - Stops audio playback and clears playing state
- `startRecording()` - Begins new recording session
- `pauseRecording()` - Pauses recording and saves current segment
- `resumeRecording()` - Resumes recording with new segment
- `stopRecording()` - Ends recording and saves final segment
- `setupPhoneStateListener()` - Configures call detection for auto-pause
- `handleCallStateChange()` - Manages recording pause/resume during phone calls

### `AndroidAudioRecorder.kt`
MediaRecorder wrapper for audio recording functionality.
- `start()` - Begins recording to specified file
- `stop()` - Stops recording and releases resources

### `AndroidAudioPlayer.kt`
MediaPlayer wrapper for audio playback functionality.
- `playFile()` - Plays audio file with automatic resource management
- `stop()` - Stops playback and releases resources
- `setOnCompletionListener()` - Sets callback for when playback finishes

### `NotificationHelper.kt`
Manages notifications for recording pause events.
- `showRecordingPausedNotification()` - Displays call interruption notification
- `hideRecordingPausedNotification()` - Removes pause notification
- `createNotificationChannel()` - Sets up notification channel for Android 8+

### `RecordingForegroundService.kt` *(Legacy - Not actively used)*
Background service for continuous audio recording with phone call detection.
- `startForegroundAndRecord()` - Starts background recording with notification
- `stopRecordingAndStopSelf()` - Stops service and cleans up resources

## Key Features

- **Smart Pause/Resume**: Manual and automatic pause with seamless continuation
- **Call Detection**: Auto-pauses during phone calls with notification feedback  
- **Segment Management**: Preserves audio before/after interruptions as separate parts
- **Visual Feedback**: Highlights currently playing recordings
- **Permission Handling**: Requests microphone, phone state, and notification permissions

## Recording States

- **Stopped**: No active recording
- **Recording**: Actively capturing audio
- **Paused**: Recording paused (manual or call interruption)

## File Organization

Recordings saved to app external files directory as:
- `meeting_YYYYMMDD_HHMMSS.m4a` - Initial recording
- `meeting_YYYYMMDD_HHMMSS_partN.m4a` - Continuation segments