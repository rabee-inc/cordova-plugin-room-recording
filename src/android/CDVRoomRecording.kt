package jp.rabee

import org.apache.cordova.*
import org.json.JSONException
import android.util.Log
import android.view.WindowManager
import org.json.*
import io.agora.rtc.*

class CDVRoomRecording : CordovaPlugin() {
    companion object {
        protected val TAG = "CDVRoomRecording"
    }

    lateinit var context: CallbackContext
    lateinit var agoraRtcEngine: RtcEngine

    // アプリ起動時に呼ばれる
    override public fun initialize(cordova: CordovaInterface,  webView: CordovaWebView) {
        LOG.d(TAG, "hi! This is CDVKeepAwake. Now intitilaizing ...");
        val agoraAppId = preferences.getString("agora-app-id", null);
        agoraAppId.let { it
            agoraRtcEngine = RtcEngine.create(cordova.context, it, null)

        }
    }

    // js 側で関数が実行されるとこの関数がまず発火する
    override fun execute(action: String, data: JSONArray, callbackContext: CallbackContext): Boolean {
        context = callbackContext
        var result = true
        when(action) {
            "initialize" -> {
                result = this.init(context)
            }
            "joinRoom" -> {
                result = this.joinRoom(context)
            }
            "leaveRoom" -> {
                result = this.leaveRoom(context)
            }
            "startRecording" -> {
                result = this.startRecording(context)
            }
            "pauseRecording" -> {
                result = this.pauseRecording(context)
            }
            "resumeRecording" -> {
                result = this.resumeRecording(context)
            }
            "split" -> {
                result = this.split(context)
            }
            "export" -> {
                result = this.export(context)
            }
            "exportWithCompression" -> {
                result = this.exportWithCompression(context)
            }
            "getWaveForm" -> {
                result = this.getWaveForm(context)
            }
            "hasRecordedFile" -> {
                result = this.hasRecordedFile(context)
            }
            "getRecordedFile" -> {
                result = this.getRecordedFile(context)
            }
            "setMicEnable" -> {
                result = this.setMicEnable(context)
            }
            "toggleMicEnable" -> {
                result = this.toggleMicEnable(context)
            }
            "setSpeakerEnable" -> {
                result = this.setSpeakerEnable(context)
            }
            "toggleSpeakerEnable" -> {
                result = this.toggleSpeakerEnable(context)
            }
            // イベント登録系統
            "setOnPushVolumeCallback" -> {
                result = this.setOnPushVolumeCallback(context)
            }
            "setOnPushBufferCallback" -> {
                result = this.setOnPushBufferCallback(context)
            }
            "setOnChangeSpeakersStatus" -> {
                result = this.setOnChangeSpeakersStatus(context)
            }
            "setOnSpeakerOfflineCallback" -> {
                result = this.setOnSpeakerOfflineCallback(context)
            }
            else -> {
                // TODO error
            }
        }
        return result
    }

    private fun init(callbackContext: CallbackContext): Boolean {
        return true
    }
    private fun joinRoom(callbackContext: CallbackContext): Boolean {
        return true
    }
    private fun leaveRoom(callbackContext: CallbackContext): Boolean {
        return true
    }
    // 録音系
    private fun startRecording(callbackContext: CallbackContext): Boolean {
        return true
    }
    private fun pauseRecording(callbackContext: CallbackContext): Boolean {
        return true
    }
    private fun resumeRecording(callbackContext: CallbackContext): Boolean {
        return true
    }
    private fun stopRecording(callbackContext: CallbackContext): Boolean {
        return true
    }
    // 音声操作系
    private fun split(callbackContext: CallbackContext): Boolean {
        return true
    }
    private fun export(callbackContext: CallbackContext): Boolean {
        return true
    }
    private fun exportWithCompression(callbackContext: CallbackContext): Boolean {
        return true
    }
    private fun getWaveForm(callbackContext: CallbackContext): Boolean {
        return true
    }
    private fun hasRecordedFile(callbackContext: CallbackContext): Boolean {
        return true
    }
    private fun getRecordedFile(callbackContext: CallbackContext): Boolean {
        return true
    }
    // マイクスピーカー操作系
    private fun setMicEnable(callbackContext: CallbackContext): Boolean {
        return true
    }
    private fun toggleMicEnable(callbackContext: CallbackContext): Boolean {
        return true
    }
    private fun setSpeakerEnable(callbackContext: CallbackContext): Boolean {
        return true
    }
    private fun toggleSpeakerEnable(callbackContext: CallbackContext): Boolean {
        return true
    }
    // イベント登録系統
    private fun setOnPushVolumeCallback(callbackContext: CallbackContext): Boolean {
        return true
    }
    private fun setOnPushBufferCallback(callbackContext: CallbackContext): Boolean {
        return true
    }
    private fun setOnChangeSpeakersStatus(callbackContext: CallbackContext): Boolean {
        return true
    }
    private fun setOnSpeakerOfflineCallback(callbackContext: CallbackContext): Boolean {
        return true
    }
}