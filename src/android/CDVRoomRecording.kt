package jp.rabee

import android.content.pm.PackageManager
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import io.agora.rtc.Constants
import io.agora.rtc.Constants.AUDIO_RECORDING_QUALITY_MEDIUM
import io.agora.rtc.IRtcEngineEventHandler
import io.agora.rtc.RtcEngine
import nl.bravobit.ffmpeg.ExecuteBinaryResponseHandler
import nl.bravobit.ffmpeg.FFmpeg
import org.apache.cordova.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class CDVRoomRecording : CordovaPlugin() {
    companion object {
        protected val TAG = "CDVRoomRecording"
    }

    private val PERMISSION_REQ_ID_RECORD_AUDIO = 22
    private var isRecording = false
    private var isEnableSpeaker = false
    private var micEnable = false

    lateinit var context: CallbackContext
    private var agoraRtcEngine: RtcEngine? = null

    // root フォルダーのチェック
    private var RECORDING_DIR = ""
    private var SAMPLE_RATE = 44100

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

            RECORDING_DIR = cordova.context.filesDir.absolutePath + "/colloboRecording";
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
            "stopRecording" -> {
                result = this.stopRecording(context)
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
                val data = data.getJSONObject(0)
                val isEnable = data.getString("isEnable")
                result = this.setSpeakerEnable(isEnable, context)
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
        if (isRecording) {
            // 録音スタートしないい
            val p = PluginResult(PluginResult.Status.ERROR, "have already started")
            callbackContext.sendPluginResult(p)
            return true
        }

        isRecording = true
        // 録音前にすでに録音されているものがあれば削除する
        val recordedDir = File(RECORDING_DIR + "recorded.wav")
        if (recordedDir.exists()) {
            recordedDir.delete()
        }
        agoraRtcEngine?.startAudioRecording(RECORDING_DIR + "/temp.wav",
                SAMPLE_RATE, Constants.AUDIO_RECORDING_QUALITY_MEDIUM)
        val data = JSONObject()
        data.put("sampleRate", SAMPLE_RATE);
        val p = PluginResult(PluginResult.Status.OK, data)
        callbackContext.sendPluginResult(p)
        return true
    }

    private fun pauseRecording(callbackContext: CallbackContext): Boolean {
        if (!isRecording) {
            // スタートしていないのでエラーを返す
            val p = PluginResult(PluginResult.Status.ERROR, "not start yet")
            callbackContext.sendPluginResult(p)
            return true
        }
        isRecording = false
        agoraRtcEngine?.stopAudioRecording()
        mergeRecording(callbackContext)
        return true
    }

    // start と resume は基本同じ対応になる
    private fun resumeRecording(callbackContext: CallbackContext): Boolean {

        if (isRecording) {
            // 録音スタートしないい
            val p = PluginResult(PluginResult.Status.ERROR, "have already started")
            callbackContext.sendPluginResult(p)
            return true
        }

        isRecording = true
        agoraRtcEngine?.startAudioRecording(RECORDING_DIR + "/temp.wav",
                SAMPLE_RATE, Constants.AUDIO_RECORDING_QUALITY_MEDIUM)
        val data = JSONObject()
        data.put("sampleRate", SAMPLE_RATE);
        val p = PluginResult(PluginResult.Status.OK, data)
        callbackContext.sendPluginResult(p)
        return true
    }

    private fun stopRecording(callbackContext: CallbackContext): Boolean {
        if (!isRecording) {
            // スタートしてないのでエラーを返す
            val p = PluginResult(PluginResult.Status.ERROR, "not start yet")
            callbackContext.sendPluginResult(p)
            return true
        }
        isRecording = false
        agoraRtcEngine?.stopAudioRecording()
        mergeRecording(callbackContext)
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
    // mic の on/off
    private fun setMicEnable(isEnable: Boolean, callbackContext: CallbackContext): Boolean {
        agoraRtcEngine?.let {
            micEnable = isEnable
            it.muteLocalAudioStream(micEnable)
            val data = JSONObject()
            data.put("micEnable", micEnable)
            val p = PluginResult(PluginResult.Status.OK, data)
            callbackContext.sendPluginResult(p)
            return true
        }
        return true
    }
    // mic の on/off のtoggle
    private fun toggleMicEnable(callbackContext: CallbackContext): Boolean {
        agoraRtcEngine?.let {
            micEnable = !micEnable
            it.muteLocalAudioStream(micEnable)
            val data = JSONObject()
            data.put("micEnable", micEnable)
            val p = PluginResult(PluginResult.Status.OK, data)
            callbackContext.sendPluginResult(p)
            return true
        }
        return true
    }
    // スピーカーの on/off
    private fun setSpeakerEnable(isEnable: Boolean, callbackContext: CallbackContext): Boolean {
        agoraRtcEngine?.let { engine ->
            isEnableSpeaker = isEnable
            engine.setEnableSpeakerphone(isEnableSpeaker)
            val data = JSONObject()
            data.put("speakerEnable", isEnableSpeaker)
            val p = PluginResult(PluginResult.Status.OK, data)
            callbackContext.sendPluginResult(p)
            return true
        }
        return false
    }
    // スピーカーの on/off トグル
    private fun toggleSpeakerEnable(callbackContext: CallbackContext): Boolean {
        agoraRtcEngine?.let { engine ->
            isEnableSpeaker = !isEnableSpeaker
            engine.setEnableSpeakerphone(isEnableSpeaker)
            val data = JSONObject()
            data.put("speakerEnable", isEnableSpeaker)
            val p = PluginResult(PluginResult.Status.OK, data)
            callbackContext.sendPluginResult(p)
            return true
        }
        return false
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

    private fun mergeRecording(callbackContext: CallbackContext) {
        var commands = ArrayList<String>()
        // 一時的につなげいる
        val tempOutputFile = File(RECORDING_DIR + "/temp-output.wav")
        // temp.wav を recorded.wav にマージする
        val tempFile = File(RECORDING_DIR + "/temp.wav")
        val recordedFile = File(RECORDING_DIR + "/recorded.wav");
        var concatAudioCounter = 0

        // success と finish を発火させるのhに必要
        commands.add("-y")

        if (recordedFile.exists()) {
            commands.add("-i")
            commands.add(recordedFile.absolutePath)
            concatAudioCounter++
        }

        commands.add("-i");
        commands.add(tempFile.absolutePath);
        concatAudioCounter++

        if (concatAudioCounter > 0) {
            commands.add("-filter_complex");
            commands.add("concat=n=" + concatAudioCounter + ":v=0:a=1");
        }

        // 出力先の設定
        commands.add(tempOutputFile.absolutePath)
        // コマンドの連結
        val command = commands.toTypedArray()

        val ffmpeg = FFmpeg.getInstance(cordova.context)
        if (ffmpeg.isSupported) {
            ffmpeg.execute(command, object: ExecuteBinaryResponseHandler() {
                override fun onStart() {
                    super.onStart()
                    LOG.v(TAG, "start")
                }
                override fun onProgress(message: String?) {
                    super.onProgress(message)
                    LOG.v(TAG, message)
                }
                override fun onFailure(message: String?) {
                    super.onFailure(message)
                    LOG.v(TAG, message)
                }
                override fun onSuccess(message: String?) {
                    super.onSuccess(message)
                    LOG.v(TAG, message)
                }
                override fun onFinish() {
                    super.onFinish()
                    if (recordedFile.exists()) {
                        recordedFile.delete()
                    }
                    recordedFile.parentFile.mkdir()
                    val newRecordedFile = File(recordedFile.absolutePath)
                    tempOutputFile.renameTo(newRecordedFile)
                    // temp.wav の削除
                    tempFile.delete()

                    // 成功
                    val r = PluginResult(PluginResult.Status.OK, true)
                    callbackContext.sendPluginResult(r)
                }
            })
        }


    }
}