package jp.rabee

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.view.WindowManager
import io.agora.rtc.IRtcEngineEventHandler
import io.agora.rtc.RtcEngine
import org.apache.cordova.*
import org.json.JSONArray


class CDVRoomRecording : CordovaPlugin() {
    lateinit var context: CallbackContext

    // 別の callback context を用意する
    lateinit var onProgressCallbackContext: CallbackContext

    // AgoraEngine
    private var mRtcEngine: RtcEngine? = null
    private val PERMISSION_REQ_ID_RECORD_AUDIO = 22

    // アプリ起動時に呼ばれる
    override public fun initialize(cordova: CordovaInterface,  webView: CordovaWebView) {
        LOG.d(TAG, "hi! This is CDVKeepAwake. Now intitilaizing ...");

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO, PERMISSION_REQ_ID_RECORD_AUDIO)) {
            initializeAgoraEngine();
        }
    }

    // js 側で関数が実行されるとこの関数がまず発火する
    override fun execute(action: String, data: JSONArray, callbackContext: CallbackContext): Boolean {
        context = callbackContext
        var result = true
        when(action) {
            "start" -> {
                result = this.start(context)
            }
            "stop" -> {
                result = this.stop(context)
            }
            "joinRoom" -> {
                result = this.joinChannel()
            }
            "leaveRoom" -> {
                result = this.leaveChannel()
            }
            else -> {
                // TODO error
            }
        }

        return result
    }

    // 画面光らせるスタート
    private fun start(callbackContext: CallbackContext): Boolean {
        val activity = cordova.activity;
        // ui thread でしか実行できないので、UIスレッドで実行する
        activity.runOnUiThread() {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val result = PluginResult(PluginResult.Status.OK, true)
            callbackContext.sendPluginResult(result)

        }
        return true;
    }

    // 画面光らせないストップ
    private fun stop(callbackContext: CallbackContext): Boolean {
        val activity = cordova.activity;
        // ui thread でしか実行できないので、UIスレッドで実行する
        activity.runOnUiThread() {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            val result = PluginResult(PluginResult.Status.OK, true)
            callbackContext.sendPluginResult(result)

        }
        return true;
    }

    companion object {
        protected val TAG = "CDVKeepAwake"
    }

    // Call the create method to initialize RtcEngine.
    private fun initializeAgoraEngine() {
        // initialize AgoraEngine
        try {
            // TODO: context取得方法これで正しいか不明
            var activity: Activity = this.cordova.getActivity()
            var baseContext: Context = activity.getApplicationContext();
            mRtcEngine = RtcEngine.create(baseContext, activity.getString(R.string.agora_app_id), mRtcEventHandler)
        } catch (e: Exception) {
            Log.e(TAG, Log.getStackTraceString(e))
            throw RuntimeException("NEED TO check rtc sdk init fatal error\n" + Log.getStackTraceString(e))
        }
    }

    private val mRtcEventHandler = object : IRtcEngineEventHandler() {
        // Listen for the onUserOffline callback.
        // This callback occurs when the remote user leaves the channel or drops offline.
        override fun onUserOffline(uid: Int, reason: Int) {
            // TODO: ???
            // runOnUiThread { onRemoteUserLeft() }
        }

        // Listen for the onUserMuterAudio callback.
        // This callback occurs when a remote user stops sending the audio stream.
        override fun onUserMuteAudio(uid: Int, muted: Boolean) {
            // TODO: ???
            // runOnUiThread { onRemoteUserVoiceMuted(uid, muted)}
        }
    }

    /**
     * パーミッション保有チェック
     */
    private fun checkSelfPermission(permission: String, requestCode: Int): Boolean {
        Log.i(TAG, "checkSelfPermission $permission $requestCode")
        if (ContextCompat.checkSelfPermission(this,
                        permission) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermission(this
                    arrayOf(permission),
                    requestCode)
            return false
        }
        return true
    }

    private fun joinChannel(): Boolean {
        // TODO: トークン取得
        val token: String = ""
        mRtcEngine!!.joinChannel(token, "demoChannel1", "Extra Optional Data", 0)
        return true
    }

    private fun leaveChannel(): Boolean{
        mRtcEngine!!.leaveChannel()
        return true
    }
}