package com.example.myapplication.audio

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * Manages audio device detection and switching for recording
 * Handles Bluetooth headsets, wired headsets, and device microphone
 */
class AudioDeviceManager(
    private val context: Context,
    private val onDeviceChanged: (AudioDeviceType, String) -> Unit
) {
    
    companion object {
        private const val TAG = "AudioDeviceManager"
    }
    
    enum class AudioDeviceType {
        DEVICE_MIC,
        WIRED_HEADSET,
        BLUETOOTH_HEADSET,
        USB_HEADSET
    }
    
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothHeadset: BluetoothHeadset? = null
    private var isRegistered = false
    
    // Current audio device state
    private var currentDeviceType = AudioDeviceType.DEVICE_MIC
    private var currentDeviceName = "Device Microphone"
    
    // Audio device change listener for Android N+
    private val audioRecordingCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        @RequiresApi(Build.VERSION_CODES.N)
        object : AudioManager.AudioRecordingCallback() {
            override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>?) {
                super.onRecordingConfigChanged(configs)
                detectCurrentAudioDevice()
            }
        }
    } else null
    
    // Broadcast receiver for device connection/disconnection
    private val deviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                AudioManager.ACTION_HEADSET_PLUG -> {
                    handleWiredHeadsetChange(intent)
                }
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                    handleBluetoothHeadsetChange(intent)
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    handleBluetoothAdapterChange(intent)
                }
            }
        }
    }
    
    // Bluetooth profile listener
    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile == BluetoothProfile.HEADSET) {
                bluetoothHeadset = proxy as BluetoothHeadset
                detectCurrentAudioDevice()
            }
        }
        
        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HEADSET) {
                bluetoothHeadset = null
            }
        }
    }
    
    /**
     * Start monitoring audio device changes
     */
    fun startMonitoring() {
        if (isRegistered) return
        
        try {
            // Register broadcast receivers
            val filter = IntentFilter().apply {
                addAction(AudioManager.ACTION_HEADSET_PLUG)
                addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            }
            context.registerReceiver(deviceReceiver, filter)
            
            // Register audio recording callback (Android N+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                audioRecordingCallback?.let { callback ->
                    audioManager.registerAudioRecordingCallback(callback, null)
                }
            }
            
            // Setup Bluetooth profile listener
            bluetoothAdapter?.getProfileProxy(context, profileListener, BluetoothProfile.HEADSET)
            
            isRegistered = true
            
            // Initial device detection
            detectCurrentAudioDevice()
            
            Log.d(TAG, "Audio device monitoring started")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting audio device monitoring", e)
        }
    }
    
    /**
     * Stop monitoring audio device changes
     */
    fun stopMonitoring() {
        if (!isRegistered) return
        
        try {
            context.unregisterReceiver(deviceReceiver)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                audioRecordingCallback?.let { callback ->
                    audioManager.unregisterAudioRecordingCallback(callback)
                }
            }
            
            bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HEADSET, bluetoothHeadset)
            bluetoothHeadset = null
            
            isRegistered = false
            Log.d(TAG, "Audio device monitoring stopped")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio device monitoring", e)
        }
    }
    
    /**
     * Get current audio device information
     */
    fun getCurrentDevice(): Pair<AudioDeviceType, String> {
        return Pair(currentDeviceType, currentDeviceName)
    }
    
    /**
     * Manually trigger device detection for testing
     */
    fun forceDeviceDetection() {
        Log.d(TAG, "=== MANUAL DEVICE DETECTION TRIGGERED ===")
        detectCurrentAudioDevice()
    }
    
    /**
     * Detect and update current audio device
     */
    private fun detectCurrentAudioDevice() {
        val (newDeviceType, newDeviceName) = determineCurrentAudioDevice()
        
        if (newDeviceType != currentDeviceType || newDeviceName != currentDeviceName) {
            val oldDeviceType = currentDeviceType
            val oldDeviceName = currentDeviceName
            
            currentDeviceType = newDeviceType
            currentDeviceName = newDeviceName
            
            Log.d(TAG, "Audio device changed: $oldDeviceName -> $newDeviceName")
            onDeviceChanged(newDeviceType, newDeviceName)
        }
    }
    
    /**
     * Determine current audio device based on connected devices
     */
    private fun determineCurrentAudioDevice(): Pair<AudioDeviceType, String> {
        // Priority order: Bluetooth -> Wired -> Device mic
        
        Log.d(TAG, "=== Determining Current Audio Device ===")
        
        // Check Bluetooth headset
        val bluetoothConnected = isBluetoothHeadsetConnected()
        Log.d(TAG, "Bluetooth headset connected: $bluetoothConnected")
        if (bluetoothConnected) {
            val device = getConnectedBluetoothHeadset()
            val deviceName = device?.name ?: "Bluetooth Headset"
            Log.d(TAG, "Selected: BLUETOOTH_HEADSET - $deviceName")
            return Pair(AudioDeviceType.BLUETOOTH_HEADSET, deviceName)
        }
        
        // Check wired headset
        val wiredConnected = isWiredHeadsetConnected()
        Log.d(TAG, "Wired headset connected: $wiredConnected")
        if (wiredConnected) {
            Log.d(TAG, "Selected: WIRED_HEADSET")
            return Pair(AudioDeviceType.WIRED_HEADSET, "Wired Headset")
        }
        
        // Check USB headset (Android M+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val usbDevice = getConnectedUSBHeadset()
            Log.d(TAG, "USB headset: ${usbDevice?.productName}")
            if (usbDevice != null) {
                val deviceName = usbDevice.productName?.toString() ?: "USB Headset"
                Log.d(TAG, "Selected: USB_HEADSET - $deviceName")
                return Pair(AudioDeviceType.USB_HEADSET, deviceName)
            }
        }
        
        // Default to device microphone
        Log.d(TAG, "Selected: DEVICE_MIC")
        return Pair(AudioDeviceType.DEVICE_MIC, "Device Microphone")
    }
    
    private fun handleWiredHeadsetChange(intent: Intent) {
        val state = intent.getIntExtra("state", 0)
        val name = intent.getStringExtra("name") ?: "Wired Headset"
        val microphone = intent.getIntExtra("microphone", 0)
        
        Log.d(TAG, "=== Wired headset change ===")
        Log.d(TAG, "State: $state (0=disconnected, 1=connected)")
        Log.d(TAG, "Name: $name")
        Log.d(TAG, "Microphone: $microphone (0=no mic, 1=has mic)")
        
        detectCurrentAudioDevice()
    }
    
    private fun handleBluetoothHeadsetChange(intent: Intent) {
        val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
        
        Log.d(TAG, "Bluetooth headset change: state=$state, device=${device?.name}")
        detectCurrentAudioDevice()
    }
    
    private fun handleBluetoothAdapterChange(intent: Intent) {
        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
        Log.d(TAG, "Bluetooth adapter change: state=$state")
        
        if (state == BluetoothAdapter.STATE_OFF) {
            detectCurrentAudioDevice()
        }
    }
    
    private fun isBluetoothHeadsetConnected(): Boolean {
        return try {
            val connected = bluetoothHeadset?.connectedDevices?.isNotEmpty() == true
            val adapterEnabled = bluetoothAdapter?.isEnabled == true
            Log.d(TAG, "Bluetooth adapter enabled: $adapterEnabled, headset connected: $connected")
            connected && adapterEnabled
        } catch (e: SecurityException) {
            Log.w(TAG, "No Bluetooth permission for headset check: ${e.message}")
            false
        }
    }
    
    private fun getConnectedBluetoothHeadset(): BluetoothDevice? {
        return try {
            val device = bluetoothHeadset?.connectedDevices?.firstOrNull()
            Log.d(TAG, "Connected Bluetooth device: ${device?.name} (${device?.address})")
            device
        } catch (e: SecurityException) {
            Log.w(TAG, "No Bluetooth permission for headset access: ${e.message}")
            null
        }
    }
    
    private fun isWiredHeadsetConnected(): Boolean {
        // Use AudioDeviceInfo for more accurate detection (Android M+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            val wiredFound = devices.any { device ->
                Log.d(TAG, "Input device: type=${device.type}, productName=${device.productName}")
                device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
            }
            Log.d(TAG, "Wired headset found via AudioDeviceInfo: $wiredFound")
            return wiredFound
        } else {
            // Fallback to deprecated method for older devices
            @Suppress("DEPRECATION")
            val connected = audioManager.isWiredHeadsetOn
            Log.d(TAG, "Wired headset connected (isWiredHeadsetOn): $connected")
            return connected
        }
    }
    
    @RequiresApi(Build.VERSION_CODES.M)
    private fun getConnectedUSBHeadset(): AudioDeviceInfo? {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        return devices.find { device ->
            device.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            device.type == AudioDeviceInfo.TYPE_USB_DEVICE
        }
    }
}