import AgoraRtcKit

@objc(CDVRoomRecording) class CDVRoomRecording : CDVPlugin {
    
    var agoraKit:AgoraRtcEngineKit!
    // callback
    var pushCallbackId: String?
    var speakerStatusChangeCallbackIds: [String] = []
    var stopRecordingCallbackId: String?
    var pauseRecordingCallbackId: String?
    var compressProgressCallBackId: String?
    var completeCompressionCallbackId: String?
    var completeSplitCallbackId: String?
    var speakerOfflineCallbackIds: [String] = []
    var pushBufferCallbackId: String?
    var pushSpeakersVolumeCallbackId: String?
    var sampleRate = 0
    var bufferSize = 0
    
    var micEnable = true
    var speakerEnable = true
    var isRecording = true
    var RECORDING_DIR = ""
    
    var agoraMediaDataPlugin:AgoraMediaDataPlugin?
    
    
    // エラーコード定義
    enum CDVRoomRecordingErrorCode: String {
        case permissionError = "permission_error"
        case argumentError = "argument_error"
        case folderManipulationError = "folder_manipulation_error"
        case jsonSerializeError = "json_serialize_error"
        // for plugin send
        func toDictionary(message: String) -> [String:Any] {
            return ["code": self.rawValue, "message": message]
        }
    }
    
    enum CDVRoomRecordingPlayerFrom {
        case start
        case stop
        case pause
        case resume
    }
    

    //
    override func pluginInitialize() {
        // シュミレーターのフォルダを特定しとく(debug用)
        let documentDirPath = NSSearchPathForDirectoriesInDomains(FileManager.SearchPathDirectory.documentDirectory, FileManager.SearchPathDomainMask.userDomainMask, true)
            print(documentDirPath)
        
        // コラボレコーディングのパス
        RECORDING_DIR = NSSearchPathForDirectoriesInDomains(.documentDirectory, .userDomainMask, true).first! + "/colloboRecording"
        sampleRate = 48000
        bufferSize = 4096

        
        
        // agorakit initialize
        guard let agoraAppId = self.commandDelegate.settings["agora-app-id"] as? String else {return}
        agoraKit = AgoraRtcEngineKit.sharedEngine(withAppId: agoraAppId, delegate: self)
        agoraMediaDataPlugin = AgoraMediaDataPlugin(agoraKit: agoraKit)
        agoraMediaDataPlugin?.registerAudioRawDataObserver([.mixedAudio])
        agoraMediaDataPlugin?.audioDelegate = self;
        
        // なければフォルダ生成する
        do {
            var isDir: ObjCBool = false
            if !FileManager.default.fileExists(atPath: RECORDING_DIR, isDirectory: &isDir) {
                try FileManager.default.createDirectory(atPath: RECORDING_DIR, withIntermediateDirectories: true, attributes: nil)
            }
        }
        catch let error {
            print(error)
        }
        speakerOfflineCallbackIds = []
        speakerStatusChangeCallbackIds = []
    };
    
    @objc func initialize(_ command: CDVInvokedUrlCommand) {
        let result = CDVPluginResult(status: CDVCommandStatus_OK)
        commandDelegate.send(result, callbackId: command.callbackId)
    }
    
    @objc func getMicPermission(_ command: CDVInvokedUrlCommand) {
        // 録音する許可がされているか？
        let audioSession = AVAudioSession.sharedInstance()
        audioSession.requestRecordPermission {[weak self] granted in
            guard let self = self else { return }
            if !granted {
                let message = CDVRoomRecordingErrorCode.permissionError.toDictionary(message: "deny permission")
                let result = CDVPluginResult(
                    status: CDVCommandStatus_ERROR,
                    messageAs:message
                    )
                self.commandDelegate.send(result, callbackId: command.callbackId)
            }
            else {
                self.commandDelegate.send(CDVPluginResult(status: CDVCommandStatus_OK, messageAs: true), callbackId:command.callbackId)
            }
        }
    }
    
    @objc func joinRoom(_ command: CDVInvokedUrlCommand) {
        guard
            let data = command.argument(at: 0) as? [String:Any],
            let roomId = data["room_id"] as? String,
            let uid = data["uid"] as? UInt else {return}
            
        
        // audio Profile
        agoraKit.setAudioProfile(.musicHighQualityStereo, scenario: .education)
        agoraKit.enableAudioVolumeIndication(50, smooth: 10, report_vad: true)
        // 音声のsampleRate と bufferSize 設定
        agoraKit.setMixedAudioFrameParametersWithSampleRate(sampleRate, samplesPerCall: bufferSize)
        // uid は agora の user id を示している
        // 0 の場合は、success callback に uid が発行されて帰ってくる
        agoraKit.joinChannel(byToken: nil, channelId: roomId, info: nil, uid: uid, joinSuccess: { [weak self] (id, uid, elapsed) in
            guard let self = self else {return}
            let data = [
                "roomId": id,
                "uid": uid,
                "elapsed": elapsed
                ] as [String: Any]
            print(data)
            let result = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: data)
            self.commandDelegate.send(result, callbackId: command.callbackId)
        })
    }
    
    @objc func leaveRoom(_ command: CDVInvokedUrlCommand) {
        agoraKit.leaveChannel(nil)
        let result = CDVPluginResult(status: CDVCommandStatus_OK)
        commandDelegate.send(result, callbackId: command.callbackId)
    }
    
    // 再開
    @objc func startRecording(_ command: CDVInvokedUrlCommand) {
        // 録音中ならスタートしない
        if (isRecording) {
            let r = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: "have already started")
            commandDelegate.send(r, callbackId: command.callbackId)
            return
        }
        // すでに録音されていれば、削除して、録音開始
        let recordedURL = URL(fileURLWithPath: RECORDING_DIR + "/recorded.wav")
        if FileManager.default.fileExists(atPath: RECORDING_DIR + "/recorded.wav") {
            do {
                try FileManager.default.removeItem(at: recordedURL)
            }
            catch let error {
                let e = CDVRoomRecordingErrorCode.folderManipulationError.toDictionary(message: error.localizedDescription)
                let r = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: e)
                commandDelegate.send(r, callbackId: command.callbackId)
                return;
            }
        }
        
        isRecording = true
        agoraKit.startAudioRecording(RECORDING_DIR + "/temp.wav", sampleRate: sampleRate, quality: .high)
                
        // 問題なければ result
        let resultData = [
            "sampleRate": sampleRate,
            "bufferSize": bufferSize
        ]
        let result = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: resultData as [AnyHashable : Any])
        self.commandDelegate.send(result, callbackId:command.callbackId)
    }
    //　一時停止
    @objc func pauseRecording(_ command: CDVInvokedUrlCommand) {
        pauseRecordingCallbackId = command.callbackId
        // 録音していなければエラー
        if (!isRecording) {
            let r = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: "not recording yet")
            commandDelegate.send(r, callbackId: command.callbackId)
            return
        }
        
        // stop するだけ
        isRecording = false
        agoraKit.stopAudioRecording();
        // 音声のマージ
        mergeRecording(from: CDVRoomRecordingPlayerFrom.pause)
    }
    
    // 再開
    @objc func resumeRecording(_ command: CDVInvokedUrlCommand) {
        
        // 録音中ならスタートしない
        if (isRecording) {
            let r = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: "have already started")
            commandDelegate.send(r, callbackId: command.callbackId)
            return
        }
        isRecording = true
        agoraKit.startAudioRecording(RECORDING_DIR + "/temp.wav", sampleRate: sampleRate, quality: .high)
        
        let result = CDVPluginResult(status: CDVCommandStatus_OK)
        commandDelegate.send(result, callbackId: command.callbackId)
    }
    
    // 終了
    @objc func stopRecording(_ command: CDVInvokedUrlCommand) {
        stopRecordingCallbackId = command.callbackId
        // 録音していなければエラー
        if (!isRecording) {
            let r = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: "not recording yet")
            commandDelegate.send(r, callbackId: command.callbackId)
            return
        }
        
        isRecording = false
        agoraKit.stopAudioRecording()
        // 音声をマージする
        mergeRecording(from: CDVRoomRecordingPlayerFrom.stop)
    }
    
    // 圧縮して出力
    @objc func exportWithCompression(_ command: CDVInvokedUrlCommand) {
        completeCompressionCallbackId = command.callbackId
        let inputPath = URL(fileURLWithPath: RECORDING_DIR + "/recorded.wav")
        let outputPath = URL(fileURLWithPath: RECORDING_DIR + "/recorded.m4a")
        
        // file があった場合は削除
        if FileManager.default.fileExists(atPath: outputPath.path) {
            do {
                try FileManager.default.removeItem(at: outputPath)
            } catch let err {
                return print(err)
            }
        }
        
        let asset = AVURLAsset(url: inputPath)
        guard let session = AVAssetExportSession(asset: asset, presetName: AVAssetExportPresetAppleM4A) else { return }
        session.outputURL = outputPath
        session.outputFileType = .m4a
        session.exportAsynchronously { [weak self] in
            guard let self = self else {return}
            switch (session.status) {
            case .completed:
                self.completeCompression()
                break
            case .failed:
                print("[failed -------------------------->]")
                break
            case .exporting:
                print("[exporting -------------------------->]")
                break
            case .waiting:
                print("[waiting -------------------------->]")
                break
            default:
                break
            }
        }
        
        // プログレスバーのコールバック(別スレッド)
        DispatchQueue.global().async { [weak self] in
            guard let self = self else {return}
            var p = 0;
            var r: CDVPluginResult?
            while(session.status != .completed && session.status != .failed) {
                if p != Int(round(session.progress * 100)) {
                    p = Int(round(session.progress * 100))
                    print(p)
                    r = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: String(p))
                    if self.compressProgressCallBackId != nil {
                        r?.keepCallback = true
                        self.commandDelegate.send(r, callbackId: self.compressProgressCallBackId)
                    }
                }
            }
            r?.keepCallback = false;
        }
    }
    
    // 圧縮完了
    private func completeCompression() {
        print("---------------------------------------------------------> compression complete")
        if let callbackId = completeCompressionCallbackId {
            let path = URL(fileURLWithPath: RECORDING_DIR + "/recorded.m4a")
            let data = [
                "absolute_path": path.absoluteString
            ] as [String:Any]
            let r = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: data)
            commandDelegate.send(r, callbackId: callbackId)
        }
    }
    
    // 未圧縮ファイルを export する
    @objc func export(_ command: CDVInvokedUrlCommand) {
        let path = URL(fileURLWithPath: RECORDING_DIR + "/recorded.wav")
        let data = [
            "absolute_path": path.absoluteString
        ] as [String:Any]
        let r = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: data)
        commandDelegate.send(r, callbackId: command.callbackId)
    }
    
    // それまで録音されていた音声と、今回録音された音声をマージする
    private func mergeRecording(from: CDVRoomRecordingPlayerFrom) {
        
        let tempURL = URL(fileURLWithPath: RECORDING_DIR + "/temp.wav")
        if FileManager.default.fileExists(atPath: RECORDING_DIR + "/recorded.wav") {
            // 音源のマージ
            let composition = AVMutableComposition()
            guard let compositionAudioTrack = composition.addMutableTrack(withMediaType: AVMediaType.audio, preferredTrackID: kCMPersistentTrackID_Invalid)
                else {return}
            let recordedURL = URL(fileURLWithPath: RECORDING_DIR + "/recorded.wav")
            // すでにレコーディングがされているものに対して、新しい音源をマージする
            var nextStartTime = kCMTimeZero
            [recordedURL, tempURL].forEach({item in
                let asset = AVURLAsset(url: item)
                if let assetTrack = asset.tracks.first {
                    let timeRange = CMTimeRange(start: kCMTimeZero, duration: asset.duration)
                    do {
                        try compositionAudioTrack.insertTimeRange(timeRange, of: assetTrack, at: nextStartTime)
                        nextStartTime = CMTimeAdd(nextStartTime, timeRange.duration)
                    } catch {
                        print("concatenateError : \(error)")
                    }
                }
            });
            
            if let assetExport = AVAssetExportSession(asset: composition, presetName: AVAssetExportPresetPassthrough) {
                assetExport.outputFileType = AVFileType.wav
                assetExport.outputURL = URL(fileURLWithPath: RECORDING_DIR + "/concat.wav")
                assetExport.exportAsynchronously(completionHandler: { [weak self] in
                    guard let self = self else {return}
                    do {
                        try FileManager.default.removeItem(at: recordedURL)
                        try FileManager.default.moveItem(atPath: self.RECORDING_DIR + "/concat.wav", toPath: self.RECORDING_DIR + "/recorded.wav")
                        self.mergeComplete(from: from)
                    }
                    catch {
                        
                    }

                })
            }
        }
        // なければ移動するだけ
        else {
            do {
                try FileManager.default.moveItem(atPath: RECORDING_DIR + "/temp.wav", toPath: RECORDING_DIR + "/recorded.wav")
                mergeComplete(from: from)
            } catch let error {
                return print(error)
            }
        }
    }
    // マージが完了する
    private func mergeComplete(from: CDVRoomRecordingPlayerFrom) {
        print("merge complete")
        
        var callbackId: String?
        
        switch from {
        case .pause:
            if pauseRecordingCallbackId != nil {
                callbackId = pauseRecordingCallbackId
            }
            break
        case .stop:
            if stopRecordingCallbackId != nil {
                callbackId = stopRecordingCallbackId
            }
            break
        case .start:
            break
        case .resume:
            break
        }
        
        
        if callbackId != nil {
            let result = CDVPluginResult(status: CDVCommandStatus_OK)
            commandDelegate.send(result, callbackId: callbackId)
        }
    }

    
    // 分割完了
    private func completeSplit() {
        if let callbackId = completeCompressionCallbackId {
            let path = URL(fileURLWithPath: RECORDING_DIR + "/recorded.wav")
            let data = [
                "absolute_path": path.absoluteString
            ] as [String:Any]
            let r = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: data)
            commandDelegate.send(r, callbackId: callbackId)
        }
    }
    
    // 分割
    @objc func split(_ command: CDVInvokedUrlCommand) {
        
        completeCompressionCallbackId = command.callbackId
        guard let s = command.argument(at: 0) as? NSNumber else {return}
        // float
        let seconds = s.floatValue
        
        // Audio Asset 作成
        let audioURL = URL(fileURLWithPath: RECORDING_DIR + "/recorded.wav")
        let audioAsset = AVURLAsset(url: audioURL)
        // composition 作成
        let composition = AVMutableComposition()
        guard let audioAssetTrack = audioAsset.tracks(withMediaType: AVMediaType.audio).first,
            let audioCompositionTrack = composition.addMutableTrack(withMediaType: AVMediaType.audio, preferredTrackID: kCMPersistentTrackID_Invalid) else {return}

        let timescale = Int32(NSEC_PER_SEC)
        let start = CMTimeMakeWithSeconds(0, timescale)
        let end = CMTimeMakeWithSeconds(Float64(seconds), timescale)
        let range = CMTimeRangeMake(start, end)
        // カット
        do {
            try audioCompositionTrack.insertTimeRange(range, of: audioAssetTrack, at: kCMTimeZero)
        }
        catch let error {
            print(error)
        }
        
        //  export
        if let exportSession = AVAssetExportSession(asset: composition, presetName: AVAssetExportPresetPassthrough) {
            
            let tempURL = URL(fileURLWithPath: RECORDING_DIR + "/temp_split.wav")
            if FileManager.default.fileExists(atPath: tempURL.path) {
                do {
                    try FileManager.default.removeItem(at: tempURL)
                }
                catch let error {
                    print(error)
                    
                }
            }
            
            exportSession.outputFileType = AVFileType.wav
            exportSession.outputURL = tempURL
            
            exportSession.exportAsynchronously { [weak self] in
                guard let self = self else { return }
                switch exportSession.status {
                case .completed:
                    do {
                        // リネーム
                        try FileManager.default.removeItem(at: audioURL)
                        try FileManager.default.moveItem(at: tempURL, to: audioURL)
                        self.completeSplit()  // 完了通知
                    }
                    catch let err {
                        print(err)
                    }
                case .failed, .cancelled:
                    print("[join error: failed or cancelled]", exportSession.error.debugDescription)
                case .waiting:
                    print(exportSession.progress);
                default:
                    print("[join error: other error]", exportSession.error.debugDescription)
                }
            }
        }
    }
    
    // 1 チャンネルの最大音量を取得
    private func getMaxVolume(buffer: AVAudioPCMBuffer) -> Float {
        var maxVolume: Float = 0
        var n = 0
        let data = buffer.floatChannelData![0]
        let length = Int(buffer.frameLength)
        while n < length {
            let volume = abs(data[n])
            if (volume > maxVolume) {
                maxVolume = volume
            }
            n += 1
        }
        return maxVolume
    }
    
    // 波形を取得する
    @objc func getWaveForm(_ command: CDVInvokedUrlCommand) {
        guard let path = URL(string: RECORDING_DIR + "/recorded.wav") else {
            let result = CDVPluginResult(
                status: CDVCommandStatus_ERROR,
                messageAs: CDVRoomRecordingErrorCode.permissionError.toDictionary(message: "[recorder: getAudio] First argument required. Please specify folder id")
                )
            self.commandDelegate.send(result, callbackId: command.callbackId)
            return
        }
        do {
            
            let audioFile = try AVAudioFile(forReading: path)
            let nframe = Int(audioFile.length)
            
            var output: [Float] = []
            
            // 1ループごとに読み込むフレーム数
            let frameCapacity = AVAudioFrameCount(bufferSize)
            // 最後までループする
            while audioFile.framePosition < nframe {
                let PCMBuffer = AVAudioPCMBuffer(pcmFormat: audioFile.processingFormat, frameCapacity: frameCapacity)!
                // read すると framePosition が進む
                try audioFile.read(into: PCMBuffer, frameCount: PCMBuffer.frameCapacity)
                // 最大音量を配列に追加する
                output.append(getMaxVolume(buffer: PCMBuffer))
            }
            
            // ファイル書き込み
            let bufferData = Data(buffer: UnsafeRawBufferPointer.init(start: output, count: output.count * 4).bindMemory(to: Float.self))
            let pcmBufferPath = URL(fileURLWithPath: RECORDING_DIR + "/temppcmbuffer")
            try bufferData.write(to: pcmBufferPath)
            let result = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: pcmBufferPath.absoluteString)
            self.commandDelegate.send(result, callbackId: command.callbackId)
            
        } catch let err {
            let result = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: "get wave form error: \(err)")
            commandDelegate.send(result, callbackId: command.callbackId)
        }
    }
    
    @objc func hasRecordedFile(_ command: CDVInvokedUrlCommand){
        let exists = FileManager.default.fileExists(atPath: RECORDING_DIR + "/recorded.wav")

        let result = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: exists)
        self.commandDelegate.send(result, callbackId: command.callbackId)
    
    }
    
    @objc func removeRecordedFile(_ command: CDVInvokedUrlCommand){
        let recordedURL = URL(fileURLWithPath: RECORDING_DIR + "/recorded.wav")
        if FileManager.default.fileExists(atPath: RECORDING_DIR + "/recorded.wav") {
            do {
                try FileManager.default.removeItem(at: recordedURL)
            }
            catch let error {
                let e = CDVRoomRecordingErrorCode.folderManipulationError.toDictionary(message: error.localizedDescription)
                let r = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: e)
                commandDelegate.send(r, callbackId: command.callbackId)
                return;
            }
            let result = CDVPluginResult(status: CDVCommandStatus_OK)
            commandDelegate.send(result, callbackId: command.callbackId)
        }

    }


    
    @objc func getRecordedFile(_ command: CDVInvokedUrlCommand){
        let path = URL(fileURLWithPath: RECORDING_DIR + "/recorded.wav")
        let data = [
            "absolute_path": path.absoluteString
        ] as [String:Any]
        let result = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: data)
        self.commandDelegate.send(result, callbackId: command.callbackId)
    }
    
    
    
    
    // スピーカー on / off
    @objc func setSpeakerEnable(_ command: CDVInvokedUrlCommand) {
        guard let isEnable = command.argument(at: 0) as? Bool else {return}
        speakerEnable = isEnable
        agoraKit.setEnableSpeakerphone(speakerEnable)
        
        let result = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: ["speakerEnable": speakerEnable])
        commandDelegate.send(result, callbackId: command.callbackId)
    }
    // スピーカー on / off のトグル
    @objc func toggleSpeakerEnable(_ command: CDVInvokedUrlCommand) {
        speakerEnable = !speakerEnable
        agoraKit.setEnableSpeakerphone(speakerEnable)
        
        let result = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: ["speakerEnable": speakerEnable])
        commandDelegate.send(result, callbackId: command.callbackId)
    }
    
    // マイク on / off
    @objc func setMicEnable(_ command: CDVInvokedUrlCommand) {
        guard let isEnable = command.argument(at: 0) as? Bool else {return}
        micEnable = isEnable
        agoraKit.muteLocalAudioStream(micEnable)
        let result = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: ["micEnable": micEnable])
        commandDelegate.send(result, callbackId: command.callbackId)
    }
    // マイク on / off のトグル
    @objc func toggleMicEnable(_ command: CDVInvokedUrlCommand) {
        micEnable = !micEnable
        agoraKit.muteLocalAudioStream(micEnable)
        let result = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: ["micEnable": micEnable])
        commandDelegate.send(result, callbackId: command.callbackId)
    }
    
    //
    @objc func setOnPushVolumeCallback(_ command: CDVInvokedUrlCommand) {
        pushCallbackId = command.callbackId
    }
    
    //
    @objc func setOnSpeakerCallback(_ command: CDVInvokedUrlCommand) {
        pushSpeakersVolumeCallbackId = command.callbackId
    }

    //
    @objc func setOnChangeSpeakersStatus(_ command: CDVInvokedUrlCommand) {
        // あれば追加する
        if !speakerStatusChangeCallbackIds.contains(command.callbackId) {
            speakerStatusChangeCallbackIds.append(command.callbackId)
        }
    }
    // seton
    @objc func setOnSpeakerOfflineCallback(_ command: CDVInvokedUrlCommand) {
        // あれば追加する
        if !speakerOfflineCallbackIds.contains(command.callbackId) {
            speakerOfflineCallbackIds.append(command.callbackId)
        }
    }
    // push buffer の登録
    @objc func setOnPushBufferCallback(_ command: CDVInvokedUrlCommand) {
        pushBufferCallbackId = command.callbackId
    }
    
    // progressの取得
    @objc func setOnCompressionProgressCallback(_ command: CDVInvokedUrlCommand){
        compressProgressCallBackId = command.callbackId
    }
}

// 随時波形取得用の拡張
extension CDVRoomRecording: AgoraAudioDataPluginDelegate {
    func mediaDataPlugin(_ mediaDataPlugin: AgoraMediaDataPlugin, didMixedAudioRawData audioRawData: AgoraAudioRawData) -> AgoraAudioRawData {
        // レコーディング中のみ buffer を送る
        if (!isRecording) {return audioRawData}
        // callbackIdがあれば送る
        if let callbackId = pushBufferCallbackId {

            let bytesLength = Int(audioRawData.samples * audioRawData.bytesPerSample * audioRawData.channels)
            let int16array = Array(UnsafeRawBufferPointer.init(start: audioRawData.buffer, count: bytesLength).bindMemory(to: Int16.self))
            
            var maxVolume = 0
            for int16 in int16array {
                let volume = abs(Int(int16))
                if (volume > maxVolume) {
                    maxVolume = volume
                }
            }

            // ネイティブで最大音量を処理したので一つだけの配列を生成
            let sendData: [Float] = [Float(maxVolume) / Float(-INT16_MIN)]
            let result = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: sendData)
            result?.keepCallback = true
            commandDelegate.send(result, callbackId: callbackId)
        }
        return audioRawData
    }
    
}

// agora delegate
extension CDVRoomRecording: AgoraRtcEngineDelegate {
    func rtcEngineConnectionDidInterrupted(_ engine: AgoraRtcEngineKit) {
        print("Connection Interrupted")
    }

    func rtcEngineConnectionDidLost(_ engine: AgoraRtcEngineKit) {
        print("Connection Lost")
    }

    func rtcEngine(_ engine: AgoraRtcEngineKit, didOccurError errorCode: AgoraErrorCode) {
        print("Occur error: \(errorCode.rawValue)")
    }

    func rtcEngine(_ engine: AgoraRtcEngineKit, didJoinChannel channel: String, withUid uid: UInt, elapsed: Int) {
        print("Did joined channel: \(channel), with uid: \(uid), elapsed: \(elapsed)")
    }
    func rtcEngine(_ engine: AgoraRtcEngineKit, didLeaveChannelWith stats: AgoraChannelStats) {
        print("Did leave: \(stats)")
    }
    func rtcEngine(_ engine: AgoraRtcEngineKit, didJoinedOfUid uid: UInt, elapsed: Int) {
        print("Did joined of uid: \(uid)")
        speakerStatusChangeCallbackIds.forEach({ callbackId in
            let data = [
                "uid": uid,
                "status": "joined"
            ] as [String: Any]
            
            let result = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: data)
            result?.keepCallback = true
            commandDelegate.send(result, callbackId: callbackId)
        })
    }

    func rtcEngine(_ engine: AgoraRtcEngineKit, didOfflineOfUid uid: UInt, reason: AgoraUserOfflineReason) {
        print("Did offline of uid: \(uid), reason: \(reason.rawValue)")
        speakerOfflineCallbackIds.forEach({ id in
            let data = [
                "uid": uid,
            ]
            let result = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: data)
            result?.keepCallback = true
            commandDelegate.send(result, callbackId: id)
        })
    }

    func rtcEngine(_ engine: AgoraRtcEngineKit, audioQualityOfUid uid: UInt, quality: AgoraNetworkQuality, delay: UInt, lost: UInt) {
        print("Audio Quality of uid: \(uid), quality: \(quality.rawValue), delay: \(delay), lost: \(lost)")
    
    }
    

    func rtcEngine(_ engine: AgoraRtcEngineKit, didApiCallExecute api: String, error: Int) {
        print("Did api call execute: \(api), error: \(error)")
    }
    
    // 音が入ってきたとき
    func rtcEngine(_ engine: AgoraRtcEngineKit, reportAudioVolumeIndicationOfSpeakers speakers: [AgoraRtcAudioVolumeInfo], totalVolume: Int) {
        // callbackid があって レコーディング中の時のみ
        if let callbackId = self.pushCallbackId  {
            if (isRecording) {
                getSpeakerData(callbackId: callbackId, speakers: speakers, totalVolume: totalVolume)
            };
            
        }
        // callbackid があって レコーディング中の時のみ
        if let callbackId = self.pushSpeakersVolumeCallbackId  {
            getSpeakerData(callbackId: callbackId, speakers: speakers, totalVolume: totalVolume)
        }

    }

    
    
    private func getSpeakerData(callbackId: String, speakers: [AgoraRtcAudioVolumeInfo], totalVolume: Int){
        let resultSpeakers = speakers.map({ speaker in
            return [
                "room_id": speaker.channelId,
                "uid": speaker.uid,
                "volume": speaker.volume,
                "vad": speaker.vad
            ]
        })
        let data = [
            "total_volume": totalVolume,
            "speakers": resultSpeakers
        ] as [String : Any]

        let result = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: data)
        result?.keepCallback = true
        commandDelegate.send(result, callbackId: callbackId)
    }
}
