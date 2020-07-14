package jp.rabee

import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.otaliastudios.transcoder.Transcoder
import com.otaliastudios.transcoder.TranscoderListener
import com.otaliastudios.transcoder.engine.TrackType
import com.otaliastudios.transcoder.sink.DefaultDataSink
import com.otaliastudios.transcoder.strategy.DefaultAudioStrategy
import io.agora.rtc.Constants
import io.agora.rtc.IRtcEngineEventHandler
import io.agora.rtc.RtcEngine
import nl.bravobit.ffmpeg.ExecuteBinaryResponseHandler
import nl.bravobit.ffmpeg.FFmpeg
import org.apache.cordova.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList


class CDVRoomRecording : CordovaPlugin(), ActivityCompat.OnRequestPermissionsResultCallback {
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
    private var recordingDir: File? = null
    private var recordedFile: File? = null

    private var SAMPLE_RATE = 44100
    private var BUFFER_SIZE = 4096

    //  callback
    private var joinRoomCallback: CallbackContext? = null
    private var leaveRoomCallback: CallbackContext? = null
    private var pushVolumeCallback: CallbackContext? = null
    private var pushSpeakersVolumeCallback : CallbackContext? = null
    private var compressProgressCallbackContext: CallbackContext? = null
    private var speakerOfflineCallbackContext: CallbackContext? = null


    private var isJoinRoom = false

    // event handler
    private val rtcEventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            super.onJoinChannelSuccess(channel, uid, elapsed)
            isJoinRoom = true
            joinRoomCallback.let { it
                val uuid = uid.toUInt()
                val data = JSONObject()
                data.put("roomId", channel)
                data.put("uid", uuid)

                data.put("elapsed", elapsed)
                val p = PluginResult(PluginResult.Status.OK, data)
                it?.sendPluginResult(p)
            }
        }
        override fun onLeaveChannel(stats: RtcStats?) {
            super.onLeaveChannel(stats)
            isJoinRoom = false
            leaveRoomCallback.let {
                val p = PluginResult(PluginResult.Status.OK, true)
                it?.sendPluginResult(p)
            }
        }

        // 音量の取得
        override fun onAudioVolumeIndication(speakers: Array<out AudioVolumeInfo>?, totalVolume: Int) {
            // レコーディング中はこっちが呼ばれる
            pushVolumeCallback?.let {
                if (isRecording) {
                    val data = buildSpeakerData(speakers, totalVolume)
                    val result = PluginResult(PluginResult.Status.OK, data)
                    result.keepCallback = true
                    it.sendPluginResult(result)
                }
            }
            // レコーディングしてなくても呼ばれる
            pushSpeakersVolumeCallback?.let {
                val data = buildSpeakerData(speakers, totalVolume)
                val result = PluginResult(PluginResult.Status.OK, data)
                result.keepCallback = true
                it.sendPluginResult(result)
            }
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            print("speaker did offline")
            speakerOfflineCallbackContext?.let {
                val uuid = uid.toUInt()
                val data = JSONObject()
                data.put("uid", uuid)
                val result = PluginResult(PluginResult.Status.OK, data)
                result.keepCallback = true
                it.sendPluginResult(result)
            }
        }
        override fun onUserMuteAudio(uid: Int, muted: Boolean) {}
    }

    private fun buildSpeakerData(speakers: Array<out IRtcEngineEventHandler.AudioVolumeInfo>?, totalVolume: Int): JSONObject {
        val data = JSONObject()
        val speakersData = JSONArray()
        speakers?.forEach {
            val speaker = JSONObject()
            speaker.put("room_id", it.channelId)
            speaker.put("uid", it.uid.toUInt())
            speaker.put("volume", it.volume)
            speaker.put("vad", it.vad)
            speakersData.put(speaker)
        }


        data.put("total_volume", totalVolume)
        data.put("speakers", speakersData)
        return data
    }

    // アプリ起動時に呼ばれる
    override public fun initialize(cordova: CordovaInterface,  webView: CordovaWebView) {


        val agoraAppId = preferences.getString("agora-app-id", null);
        agoraAppId.let { it
            try {
                agoraRtcEngine = RtcEngine.create(cordova.context, it, this.rtcEventHandler)
                print(agoraRtcEngine)
                agoraRtcEngine?.let {it.enableAudioVolumeIndication(50, 3, false)}
            } catch (e: Exception) {
                Log.e(TAG, Log.getStackTraceString(e))

                throw RuntimeException("NEED TO check rtc sdk init fatal error\n" + Log.getStackTraceString(e))
            }
        }

        RECORDING_DIR = cordova.context.filesDir.absolutePath + "/colloboRecording";
        val dir = File(RECORDING_DIR)
        // おそらく初回にはフォルダがないのでフォルダ作成する
        if (!dir.exists()) {
            dir.mkdir()
        }
        recordingDir = File(RECORDING_DIR)
        recordedFile = File(RECORDING_DIR + "/recorded.wav")
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
                val second = data.getString(0).toFloat()
                result = this.split(second, context)
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
            "removeRecordedFile" -> {
                result = this.removeRecordedFile(context)
            }
            "setMicEnable" -> {
                val isEnable = data.getBoolean(0);
                result = this.setMicEnable(isEnable, context)
            }
            "toggleMicEnable" -> {
                result = this.toggleMicEnable(context)
            }
            "getMicPermission" -> {
                result = this.getMicPermission(context)
            }
            "setSpeakerEnable" -> {
                val data = data.getJSONObject(0)
                val isEnable = data.getBoolean("isEnable")
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
            "setOnPushBufferCallback" -> {
                result = this.setOnPushBufferCallback(context)
            }
            "setOnSpeakerCallback" -> {
                result = this.setOnSpeakerCallback(context)
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
        if (!isJoinRoom) {
            val p = PluginResult(PluginResult.Status.OK, true)
            callbackContext.sendPluginResult(p)
            return true
        }
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
        val recordedDir = File(RECORDING_DIR + "/recorded.wav")
        if (recordedDir.exists()) {
            recordedDir.delete()
        }
        agoraRtcEngine?.startAudioRecording(RECORDING_DIR + "/temp.wav",
                SAMPLE_RATE, Constants.AUDIO_RECORDING_QUALITY_MEDIUM)
        val data = JSONObject()
        data.put("sampleRate", SAMPLE_RATE)
        data.put("bufferSize", BUFFER_SIZE)
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
    private fun split(second: Float, callbackContext: CallbackContext): Boolean {
        // guard する
        val inputFile = recordedFile?.also { print(it.absolutePath) } ?: run {
            // file が無い
            val p = PluginResult(PluginResult.Status.ERROR, "have not been recorded yet")
            callbackContext.sendPluginResult(p)
            return false
        }
        val outputDir = recordingDir?.also { print(it.absolutePath) } ?: run {
            // folder が無い
            return false
        }

        val outputFile = File(RECORDING_DIR + "/tempSplitAudio.wav")

        val commands = ArrayList<String>()
        commands.add("-ss");
        commands.add("0");
        commands.add("-i");
        commands.add(inputFile.absolutePath);
        // ms
        val ms = second * 1000
        val bd = ms.toBigDecimal().setScale(0, RoundingMode.DOWN)
        val plainTime = bd.toInt()
        // timeformat convert
        val formatter = SimpleDateFormat("HH:mm:ss.SSS")
        formatter.timeZone = TimeZone.getTimeZone("GMT")
        val timeFormatted = formatter.format(plainTime)
        commands.add("-t")
        commands.add(timeFormatted)
        commands.add(outputFile.absolutePath)
        // ffmpeg
        val ffmpeg = FFmpeg.getInstance(cordova.context)
        val command = commands.toTypedArray()
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
                    // file の移管
                    if (inputFile.exists()) {
                        inputFile.delete()
                    }
                    val newRecordedFile = File(inputFile.absolutePath)
                    outputFile.renameTo(newRecordedFile)

                    val data = JSONObject()
                    data.put("absolute_path", "file://" + newRecordedFile.absolutePath)


                    val r = PluginResult(PluginResult.Status.OK, data)
                    callbackContext.sendPluginResult(r)
                }
            })
        }
                return true
    }
    // recorded file へのパスを返す
    private fun export(callbackContext: CallbackContext): Boolean {
        recordedFile?.let {
            if (it.exists()) {
                val data = JSONObject()
                data.put("absolute_path", "file://" + it.absolutePath);
                val p = PluginResult(PluginResult.Status.OK, data)
                callbackContext.sendPluginResult(p)
            }
            else {
                //TODO: not found file error handling
            }
        }
        return true
    }
    // 圧縮して返す (.aac)
    private fun exportWithCompression(callbackContext: CallbackContext): Boolean {
        // guard する
        val inputFile = recordedFile?.also { print(it.absolutePath) } ?: run {
            // file が無い
            val p = PluginResult(PluginResult.Status.ERROR, "have not been recorded yet")
            callbackContext.sendPluginResult(p)
            return false
        }
        val outputDir = recordingDir?.also { print(it.absolutePath) } ?: run {
            // folder が無い
            return false
        }

        if (inputFile.exists()) {
            // 時間のかかる処理なので thread を分ける
            cordova.threadPool.execute {
                    // compressed.acc ファイルを作成して、そいつを返してやる
                    val outputFile = File.createTempFile("compressed", ".aac", outputDir)
                    val sink = DefaultDataSink(outputFile.absolutePath)
                    val strategy = DefaultAudioStrategy.builder().channels(1).sampleRate(SAMPLE_RATE).build()
                    Transcoder.into(sink)
                            .addDataSource(TrackType.AUDIO, inputFile.path)
                            .setAudioTrackStrategy(strategy)
                            .setListener(object: TranscoderListener {
                                // 進行中
                                override fun onTranscodeProgress(progress: Double) {
                                    compressProgressCallbackContext?.let {
                                        val result = PluginResult(PluginResult.Status.OK, (BigDecimal.valueOf(progress).setScale(3, RoundingMode.HALF_UP)).toPlainString())
                                        result.keepCallback = true
                                        it.sendPluginResult(result)

                                    }
                                }
                                // 完了
                                override fun onTranscodeCompleted(successCode: Int) {
                                    val data = JSONObject()
                                    data.put("absolute_path", "file://" + outputFile.absoluteFile)
                                    val result = PluginResult(PluginResult.Status.OK, data)
                                    callbackContext.sendPluginResult(result)
                                }
                                // 失敗
                                override fun onTranscodeFailed(exception: Throwable) {
                                    callbackContext.error(exception.localizedMessage)
                                }
                                // キャンセル
                                override fun onTranscodeCanceled() {
                                    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
                                }
                            })

            }

        }
        else {
            // error
            val p = PluginResult(PluginResult.Status.OK, "not found recorded file")
            callbackContext.sendPluginResult(p)
        }

        return true
    }
    // 波形を取得する関数
    private fun getWaveForm(callbackContext: CallbackContext): Boolean {
        val tempWaveFormFile =  File(RECORDING_DIR + "/temppcmbuffer")
        if (!tempWaveFormFile.exists()) {
            tempWaveFormFile.createNewFile()
        }
        val inputFile = recordedFile?.also {} ?: run {
            return false
        }

        cordova.threadPool.execute {
            val input = FileInputStream(inputFile)
            input.skip(36)
            val chunkID = ByteArray(4)
            input.read(chunkID)
            if (String(chunkID, Charset.forName("UTF-8")) == "LIST") {

                val chunkSize = ByteArray(4)
                input.read(chunkSize)
                val chunkSizeInt: Int = chunkSize[0].toInt() + (chunkSize[1].toInt() shl 8) + (chunkSize[2].toInt() shl 16) + (chunkSize[3].toInt() shl 24)
                input.skip(chunkSizeInt + 4 + 4.toLong())
            }
            else {
                input.skip(4)
            }

            val data = ByteArray(input.available())
            input.read(data)
            input.close()

            try {
                val output = FileOutputStream(tempWaveFormFile)
                output.write(data)
                output.close()
                callbackContext.success("file://" + tempWaveFormFile.absolutePath)
            }
            catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }

        return true
    }
    // recordig file があるのかどうか？
    private fun hasRecordedFile(callbackContext: CallbackContext): Boolean {
        recordedFile?.let {
            val p = PluginResult(PluginResult.Status.OK,  it.exists())
            callbackContext.sendPluginResult(p)
        }
        return true
    }
    // recording したファイルへのパスを返す
    private fun getRecordedFile(callbackContext: CallbackContext): Boolean {
        recordedFile?.let {
            if (it.exists()) {
                val data = JSONObject()
                data.put("absolute_path", "file://" + it.absolutePath);
                val p = PluginResult(PluginResult.Status.OK, data)
                callbackContext.sendPluginResult(p)
            }
            else {
                //TODO: not found file error handling
            }
        }
        return true
    }

    private fun removeRecordedFile(callbackContext: CallbackContext): Boolean {
        recordedFile?.let {
            if (it.exists()) {
                 it.delete()
            }
            callbackContext.success()
        }
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

    // レコーディング中の push volume の callback
    private fun setOnPushVolumeCallback(callbackContext: CallbackContext): Boolean {
        pushVolumeCallback = callbackContext
        return true
    }

    // レコーディング関係なくくる
    private fun setOnSpeakerCallback(callbackContext: CallbackContext): Boolean {
        pushSpeakersVolumeCallback = callbackContext
        return true
    }

    private fun setOnPushBufferCallback(callbackContext: CallbackContext): Boolean {
        return true
    }
    private fun setOnChangeSpeakersStatus(callbackContext: CallbackContext): Boolean {
        return true
    }
    private fun setOnSpeakerOfflineCallback(callbackContext: CallbackContext): Boolean {
        speakerOfflineCallbackContext = callbackContext
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

    private fun getMicPermission(callbackContext: CallbackContext): Boolean {
        // mic permission を確認
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO, PERMISSION_REQ_ID_RECORD_AUDIO)) {
            val p = PluginResult(PluginResult.Status.OK, true)
            callbackContext.sendPluginResult(p)
        } else {
            val p = PluginResult(PluginResult.Status.ERROR, false);
            callbackContext.sendPluginResult(p)
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {

        print("hogehgoe")
    }
}