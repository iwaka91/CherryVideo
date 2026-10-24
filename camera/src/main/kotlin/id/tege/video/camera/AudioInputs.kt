package id.tege.video.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

object AudioInputs {
    fun bluetooth(device: AudioDeviceInfo)=device.type==AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
        (Build.VERSION.SDK_INT>=31 && device.type==AudioDeviceInfo.TYPE_BLE_HEADSET)
    fun external(device: AudioDeviceInfo)=device.isSource && (device.type in setOf(
        AudioDeviceInfo.TYPE_USB_DEVICE,AudioDeviceInfo.TYPE_USB_HEADSET,AudioDeviceInfo.TYPE_USB_ACCESSORY,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,AudioDeviceInfo.TYPE_LINE_ANALOG,AudioDeviceInfo.TYPE_LINE_DIGITAL) || bluetooth(device))
    fun devices(context: Context): List<AudioDeviceInfo> = runCatching {
        context.getSystemService(AudioManager::class.java).getDevices(AudioManager.GET_DEVICES_INPUTS).toList()
    }.getOrDefault(emptyList())
    fun name(device: AudioDeviceInfo): String = runCatching { "${device.productName} · ${when {
        bluetooth(device)->"Bluetooth";device.type==AudioDeviceInfo.TYPE_BUILTIN_MIC->"Internal";
        device.type==AudioDeviceInfo.TYPE_WIRED_HEADSET->"Kabel";else->"USB / line"
    }}" }.getOrDefault("Input ${device.id}")
    fun report(device: AudioDeviceInfo?)=if(device==null) JSONObject().put("status","Belum diverifikasi") else
        JSONObject().put("id",device.id).put("type",device.type).put("name",name(device))
    fun catalog(context: Context)=JSONArray(devices(context).map { report(it) })
}

/** One take owns communication routing and restores the previous route on release. */
@Suppress("DEPRECATION")
@SuppressLint("MissingPermission")
class AudioRoute(private val context: Context,private val external: Boolean,deviceId: Int) : AutoCloseable {
    private val manager=context.getSystemService(AudioManager::class.java)
    private val previousMode=manager.mode
    private var changed=false
    private val previousCommunication=if(Build.VERSION.SDK_INT>=31 && bluetoothAllowed()) manager.communicationDevice else null
    val selected: AudioDeviceInfo?=AudioInputs.devices(context).let { devices ->
        if(external) devices.firstOrNull { AudioInputs.external(it) && (deviceId==0 || it.id==deviceId) }
        else devices.firstOrNull { it.type==AudioDeviceInfo.TYPE_BUILTIN_MIC }
    }
    val source: Int get()=if(selected?.let { AudioInputs.bluetooth(it) }==true) MediaRecorder.AudioSource.VOICE_COMMUNICATION
        else if(external) MediaRecorder.AudioSource.MIC else MediaRecorder.AudioSource.CAMCORDER
    private fun bluetoothAllowed()=Build.VERSION.SDK_INT<31 || context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED
    @SuppressLint("MissingPermission")
    fun prepare() {
        check(!external || selected!=null) { "Mic eksternal tidak tersedia. Sambungkan atau pilih ulang perangkat." }
        if(selected?.let { AudioInputs.bluetooth(it) }==true) {
            check(bluetoothAllowed()) { "Berikan izin Bluetooth untuk mic ini." }
            changed=true;manager.mode=AudioManager.MODE_IN_COMMUNICATION
            if(Build.VERSION.SDK_INT>=31) {
                val communication=manager.availableCommunicationDevices.firstOrNull { it.id==selected.id }
                    ?: manager.availableCommunicationDevices.firstOrNull { AudioInputs.bluetooth(it) && it.type==selected.type }
                check(communication!=null && manager.setCommunicationDevice(communication)) { "Android menolak jalur mic Bluetooth ini." }
            } else { manager.startBluetoothSco();manager.isBluetoothScoOn=true }
        }
    }
    fun apply(record: AudioRecord) {
        selected?.let { check(record.setPreferredDevice(it)) { "Android menolak input ${AudioInputs.name(it)}." } }
    }
    fun verify(actual: AudioDeviceInfo?) {
        check(!external || (actual!=null && actual.id==selected?.id)) {
            "Mic eksternal belum menjadi input aktif. Periksa koneksi, lalu coba kembali."
        }
    }
    @SuppressLint("MissingPermission")
    override fun close() {
        if(!changed) return
        runCatching {
            if(Build.VERSION.SDK_INT>=31) {
                if(previousCommunication!=null) manager.setCommunicationDevice(previousCommunication) else manager.clearCommunicationDevice()
            } else { manager.stopBluetoothSco();manager.isBluetoothScoOn=false }
            manager.mode=previousMode
        }
        changed=false
    }
}
