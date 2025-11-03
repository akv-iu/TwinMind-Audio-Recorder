feat: Add live recording status display on lock screen with interactive controls

## Major Features Added

### Lock Screen Recording Display
- Implemented foreground service (RecordingService.kt) for persistent recording notifications
- Live timer updates every second showing elapsed recording time on lock screen
- Interactive pause/resume and stop buttons work directly from notification bar
- Stopwatch-style notification design for consistency with Android's built-in timers
- High-priority notification channel configuration for optimal lock screen visibility

### Enhanced Service-ViewModel Communication
- Added service binding architecture for real-time communication between UI and service
- Implemented RecordingServiceCallback interface for bidirectional control
- Service status synchronization ensures notification reflects actual recording state
- Notification buttons trigger actual recording actions in ViewModel (pause/resume/stop)

### Permission Management Improvements  
- Added NotificationPermissionHelper.kt for managing notification permissions
- Automatic permission dialog prompts when recording starts without notification access
- Support for Android 14+ foreground service microphone permissions
- Guided user experience for enabling lock screen notifications

### Notification System Enhancements
- Updated notification channels with proper importance levels for lock screen display
- MediaStyle notification support for better action button layout
- Real-time timer synchronization between ViewModel and service notification
- Proper notification lifecycle management (create/update/dismiss)

## Technical Improvements

### Service Architecture
- Added RecordingService as bound service with foreground notification capabilities
- Service binding lifecycle management in ViewModel (bind on init, unbind on destroy)
- Proper service startup sequence ensuring binding before callback registration
- Enhanced error handling and debugging throughout service communication

### State Management
- Fixed notification status lag by removing service-side status changes
- ViewModel now controls all recording state with service reflecting changes
- Improved state synchronization after pause/resume/stop actions
- Added comprehensive logging for debugging service-ViewModel communication

### Android Compatibility
- Full support for Android 8+ notification channels with proper configuration
- Android 13+ notification permission handling
- Android 14+ foreground service microphone permission support
- Backwards compatibility maintained for older Android versions

## Files Modified

### New Files
- `app/src/main/java/.../service/RecordingService.kt` - Foreground service implementation
- `app/src/main/java/.../permissions/NotificationPermissionHelper.kt` - Permission utilities

### Enhanced Files  
- `RecordingViewModel.kt` - Added service binding, synchronization, and enhanced state management
- `MainActivity.kt` - Added notification permission dialog and service binding support
- `AndroidManifest.xml` - Added foreground service permissions and service declaration
- `build.gradle.kts` - Added androidx.media dependency for MediaStyle notifications
- `README.md` - Updated documentation with lock screen features and usage instructions

## User Experience Improvements

- Users can now control recordings without unlocking their device
- Live timer display keeps users informed of recording duration on lock screen
- Notification buttons provide quick access to pause/resume/stop functionality  
- Automatic permission guidance helps users enable optimal notification settings
- Seamless integration with existing recording workflow (no breaking changes)

## Testing Status

- ✅ Notification buttons respond to user taps
- ✅ Pause/resume actions sync correctly between notification and app
- ✅ Stop button properly terminates recording and updates UI
- ✅ Timer updates in real-time on lock screen during recording
- ✅ Service binding establishes reliable communication with ViewModel
- ✅ Status synchronization maintains consistent state between service and UI
- ✅ Permission dialogs guide users to enable notification access
- ✅ Backwards compatibility maintained across Android versions

This update transforms the app from a basic recording tool into a professional-grade solution with comprehensive lock screen integration, making it ideal for meeting recordings, interviews, and other scenarios where users need hands-free recording control.