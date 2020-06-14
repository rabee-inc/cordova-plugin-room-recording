import AgoraRtcKit

@objc(CDVRoomRecording) class CDVRoomRecording : CDVPlugin {
    
    var agoraKit:AgoraRtcEngineKit!
    // callback
    var pushCallbackId: String?
    var speakerStatusChangeCallbackIds: [String] = []
    var stopRecordingCallbackId: String?
    var commpressProgressCallBackId: String?
    var completeCompressionCallbackId: String?
    var completeSplitCallbackId: String?
    
    var speakerEnable = true
    var isRecording = true
    var RECORDING_DIR = ""
    
    // エラーコード定義
    enum CDVErrorCode: String {
        case permissionError = "permission_error"
        case argumentError = "argument_error"
        case folderManipulationError = "folder_manipulation_error"
        case jsonSerializeError = "json_serialize_error"
        // for plugin send
        func toDictionary(message: String) -> [String:Any] {
            return ["code": self.rawValue, "message": message]
        }
    }

    //
    override func pluginInitialize() {
        // シュミレーターのフォルダを特定しとく(debug用)
        let documentDirPath = NSSearchPathForDirectoriesInDomains(FileManager.SearchPathDirectory.documentDirectory, FileManager.SearchPathDomainMask.userDomainMask, true)
            print(documentDirPath)
        
        // コラボレコーディングのパス
        RECORDING_DIR = NSSearchPathForDirectoriesInDomains(.documentDirectory, .userDomainMask, true).first! + "/colloboRecording"
        speakerStatusChangeCallbackIds = []
        
        let audioSession = AVAudioSession.sharedInstance()
        audioSession.requestRecordPermission {[weak self] granted in }
        
        // agorakit initialize
        guard let agoraAppId = self.commandDelegate.settings["agora-app-id"] as? String else {return}
        agoraKit = AgoraRtcEngineKit.sharedEngine(withAppId: agoraAppId, delegate: self)
        
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
                let message = CDVErrorCode.permissionError.toDictionary(message: "deny permission")
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
            let roomId = data["room_id"] as? String else {return}
        
        // audio Profile
        agoraKit.setAudioProfile(.musicHighQualityStereo, scenario: .education)
        agoraKit.enableAudioVolumeIndication(50, smooth: 10, report_vad: true)
        
        // uid は agora の user id を示している
        // 0 の場合は、success callback に uid が発行されて帰ってくる
        agoraKit.joinChannel(byToken: nil, channelId: roomId, info: nil, uid: 0, joinSuccess: { [weak self] (id, uid, elapsed) in
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
    
    @objc func startRecording(_ command: CDVInvokedUrlCommand) {
        let result = CDVPluginResult(status: CDVCommandStatus_OK)
        commandDelegate.send(result, callbackId: command.callbackId)
        isRecording = true
        agoraKit.startAudioRecording(RECORDING_DIR + "/temp.wav", sampleRate: 44100, quality: .high)
        
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
                    if self.commpressProgressCallBackId != nil {
                        r?.keepCallback = true
                        self.commandDelegate.send(r, callbackId: self.commpressProgressCallBackId)
                    }
                }
            }
            r?.keepCallback = false;
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
    private func mergeRecording() {
        
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
                        self.mergeComplete()
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
                mergeComplete()
            } catch let error {
                return print(error)
            }
        }
    }
    // マージが完了する
    private func mergeComplete() {
        print("merge complete")
        if (stopRecordingCallbackId != nil) {
            let result = CDVPluginResult(status: CDVCommandStatus_OK)
            commandDelegate.send(result, callbackId: stopRecordingCallbackId)
        }
    }
    // h
    @objc func stopRecording(_ command: CDVInvokedUrlCommand) {
        agoraKit.stopAudioRecording()
        isRecording = false
        stopRecordingCallbackId = command.callbackId
        mergeRecording()
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
    //
    @objc func setOnPushVolumeCallback(_ command: CDVInvokedUrlCommand) {
        pushCallbackId = command.callbackId
        
    }
    //
    @objc func setOnChangeSpeakersStatus(_ command: CDVInvokedUrlCommand) {
        // あれば追加する
        if !speakerStatusChangeCallbackIds.contains(command.callbackId) {
            speakerStatusChangeCallbackIds.append(command.callbackId)
        }
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
            if (!isRecording) {return};
                
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
}


extension AVMutableCompositionTrack {
    func append(url: URL) {
        let newAsset = AVURLAsset(url: url)
        let range = CMTimeRangeMake(kCMTimeZero, newAsset.duration)
        let end = timeRange.end
        print(end)
        if let track = newAsset.tracks(withMediaType: AVMediaType.audio).first {
            try! insertTimeRange(range, of: track, at: end)
        }
        
    }
}
