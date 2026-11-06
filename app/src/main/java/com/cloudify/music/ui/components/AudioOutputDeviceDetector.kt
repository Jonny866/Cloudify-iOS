package com.cloudify.music.ui.components

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

data class ConnectedAudioDevice(
    val name: String,
    val type: DeviceType,
    val isBluetooth: Boolean
) {
    enum class DeviceType {
        HEADPHONES,
        SPEAKER,
        PHONE_SPEAKER,
        OTHER
    }

    val displayText: String
        get() = when (type) {
            DeviceType.PHONE_SPEAKER -> "Динамик телефона"
            else -> name.ifBlank { "Динамик телефона" }
        }
}

fun getActiveAudioDevice(audioManager: AudioManager): ConnectedAudioDevice? {
    val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

    var btDevice: AudioDeviceInfo? = null
    var wiredDevice: AudioDeviceInfo? = null
    var phoneSpeaker: AudioDeviceInfo? = null

    for (device in devices) {
        when (device.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER -> {
                btDevice = device
                break
            }
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET -> {
                if (wiredDevice == null) wiredDevice = device
            }
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> {
                phoneSpeaker = device
            }
        }
    }

    val chosen = btDevice ?: wiredDevice ?: phoneSpeaker ?: return null
    val rawName = chosen.productName?.toString()?.trim().orEmpty()
    val isBt = chosen.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
               chosen.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
               chosen.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
               chosen.type == AudioDeviceInfo.TYPE_BLE_SPEAKER

    val cleanName = when {
        rawName.isNotBlank() -> rawName
        isBt -> "Bluetooth аудио"
        chosen.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
        chosen.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Проводные наушники"
        else -> "Динамик телефона"
    }

    val lowerName = cleanName.lowercase()
    val type = when {
        chosen.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ->
            ConnectedAudioDevice.DeviceType.PHONE_SPEAKER
        chosen.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
        lowerName.contains("partybox") ||
        lowerName.contains("speaker") ||
        lowerName.contains("flip") ||
        lowerName.contains("charge") ||
        lowerName.contains("boombox") ||
        lowerName.contains("soundbar") ||
        lowerName.contains("колонк") ||
        lowerName.contains("box") ->
            ConnectedAudioDevice.DeviceType.SPEAKER
        else ->
            ConnectedAudioDevice.DeviceType.HEADPHONES
    }

    return ConnectedAudioDevice(
        name = cleanName,
        type = type,
        isBluetooth = isBt
    )
}

@Composable
fun rememberConnectedAudioDevice(): ConnectedAudioDevice? {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    var currentDevice by remember { mutableStateOf<ConnectedAudioDevice?>(null) }

    DisposableEffect(audioManager) {
        if (audioManager == null) return@DisposableEffect onDispose {}

        val update = {
            currentDevice = getActiveAudioDevice(audioManager)
        }

        update()

        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                update()
            }
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                update()
            }
        }

        audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))

        onDispose {
            audioManager.unregisterAudioDeviceCallback(callback)
        }
    }

    return currentDevice
}
