package jp.rabee

import android.content.pm.PackageManager
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import io.agora.rtc.IRtcEngineEventHandler
import io.agora.rtc.RtcEngine
import org.apache.cordova.*
import org.json.JSONArray
import org.json.JSONObject

class CDVRoomRecording : CordovaPlugin() {
    companion object {
        protected val TAG = "CDVRoomRecording"
    }

    private val PERMISSION_REQ_ID_RECORD_AUDIO = 22

    lateinit var context: CallbackContext
    private var agoraRtcEngine: RtcEngine? = null

    //  callback
    private var joinRoomCallback: CallbackContext? = null
    private var leaveRoomCallback: CallbackContext? = null
    // event handler
    private val rtcEventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            super.onJoinChannelSuccess(channel, uid, elapsed)
            joinRoomCallback.let { it
                val data = JSONObject()
                data.put("roomId", channel)
                data.put("uid", uid)
                data.put("elapsed", elapsed)
                val p = PluginResult(PluginResult.Status.OK, data)
                it?.sendPluginResult(p)
            }
        }
        override fun onLeaveChannel(stats: RtcStats?) {
            super.onLeaveChannel(stats)
            leaveRoomCallback.let {
                val p = PluginResult(PluginResult.Status.OK, true)
                it?.sendPluginResult(p)
            }
        }
        override fun onUserOffline(uid: Int, reason: Int) {}
        override fun onUserMuteAudio(uid: Int, muted: Boolean) {}
    }

    // アプリ起動時に呼ばれる
    override public fun initialize(cordova: CordovaInterface,  webView: CordovaWebView) {
        // mic permissio を確認
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO, PERMISSION_REQ_ID_RECORD_AUDIO)) {
            val agoraAppId = preferences.getString("agora-app-id", null);
            agoraAppId.let { it
                try {
                    agoraRtcEngine = RtcEngine.create(cordova.context, it, this.rtcEventHandler)
                    print(agoraRtcEngine)
                } catch (e: Exception) {
                    Log.e(TAG, Log.getStackTraceString(e))

                    throw RuntimeException("NEED TO check rtc sdk init fatal error\n" + Log.getStackTraceString(e))
                }
            }
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
                val data = data.getJSONObject(0)
                val roomId = data.getString("room_id")
                // error
                result = if (roomId != null) {
                    this.joinRoom(roomId, context)
                }
                // room id がないよ
                else {
                    val p = PluginResult(PluginResult.Status.ERROR, "not found arguments");
                    callbackContext.sendPluginResult(p)
                    false
                }

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
    // 参加
    private fun joinRoom(roomId: String, callbackContext: CallbackContext): Boolean {
        joinRoomCallback = callbackContext
        agoraRtcEngine?.joinChannel(null, roomId, "", 0)
        return true
    }
    // 外出
    private fun leaveRoom(callbackContext: CallbackContext): Boolean {
        leaveRoomCallback = callbackContext
        agoraRtcEngine?.leaveChannel()
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

    // パーミッションあるかどうか確認
    private fun checkSelfPermission(permission: String, requestCode: Int): Boolean {
        Log.i(TAG, "checkSelfPermission $permission $requestCode")
        if (ContextCompat.checkSelfPermission(cordova.context,
                        permission) != PackageManager.PERMISSION_GRANTED) {


            ActivityCompat.requestPermissions(cordova.activity, arrayOf(permission), requestCode)

            return false
        }
        return true
    }

}