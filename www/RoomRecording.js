'use strict';

// 本体 -> これを利用したもう一つ大きなクラスを作って 体裁を整える
class RoomRecording {
    constructor(params) {
        this.exec = require('cordova/exec');
        this._listener = {};
        this.registerEvents('pushVolume', 'setOnPushVolumeCallback', params);
        this.registerEvents('pushBuffer', 'setOnPushBufferCallback', params);
        this.registerEvents('pushSpeakersVolume', 'setOnSpeakerCallback', params);
        this.registerEvents('changeSpeakersStatus', 'setOnChangeSpeakersStatus', params);
        this.registerEvents('offline', 'setOnSpeakerOfflineCallback', params);
        this.registerEvents('compressionProgress', 'setOnCompressionProgressCallback', params);
    };

    initialize(params) {
        return this.createAction('initialize', params); 
    }

    // cordova の実行ファイルを登録する
    registerCordovaExecuter(action, onSuccess, onFail, param) {
        return this.exec(onSuccess, onFail, 'RoomRecording', action, [param]);
    };

    // promise で返す。 cordova の excuter の wrapper
    createAction(action, params) {
        return new Promise((resolve, reject) => {
            // actionが定義されているかを判定したい
            if (true) {
                // cordova 実行ファイルを登録
                this.registerCordovaExecuter(action, resolve, reject, params);
            }
            else {
                // TODO: error handling
            }
        });
    };

    // イベントをバインド
    registerEvents(onSuccess, action, params) {
        this.exec(
            (data) => {
                this.trigger(onSuccess, data);
            }, 
            (error) => {
                console.log(error, 'error');
            }, 'RoomRecording', action, [params]
        );
    };
    
    trigger(event, value) {
        if (this._listener[event]) {
            this._listener[event].forEach(callback => {
                if (typeof callback === 'function') {
                    callback(value);
                }
            });
        }
    };

    getMicPermission(params) {
        return this.createAction('getMicPermission', params)
    };

    // for room
    createRoom(params) {
        return this.createAction('joinRoom', params)
    };

    joinRoom(params) {
        return this.createAction('joinRoom', params)
    };
    leaveRoom(params) {
        return this.createAction('leaveRoom', params)
    };
    // for recording
    startRecording(params) {
        return this.createAction('startRecording', params)
    };
    pauseRecording(params) {
        return this.createAction('pauseRecording', params)
    };
    resumeRecording(params) {
        return this.createAction('resumeRecording', params)
    };
    stopRecording(params) {
        return this.createAction('stopRecording', params)
    };
    split(params) {
        return this.createAction('split', params)
    };
    export(params) {
        return this.createAction('export', params)
    };
    exportWithCompression(params) {
        return this.createAction('exportWithCompression', params)
    };

    getWaveForm(params) {
        return this.createAction('getWaveForm', params)
    };

    hasRecordedFile(params) {
        return this.createAction('hasRecordedFile', params)
    };

    getRecordedFile(params) {
        return this.createAction('getRecordedFile', params)
    };

    removeRecordedFile(params) {
        return this.createAction('removeRecordedFile', params)
    };


    // mic 操作
    setMicEnable(params) {
        return this.createAction('setMicEnable', params)
    };
    toggleMic(params) {
        return this.createAction('toggleMicEnable', params)
    };
    // speaker 操作
    setSpeakerEnable(params) {
        return this.createAction('setSpeakerEnable', params)
    };
    toggleSpeakerEnable(params) {
        return this.createAction('toggleSpeakerEnable', params)
    };
    // 登録関係
    on(event, callback) {
        this._listener[event] = this._listener[event] || [];
        this._listener[event].push(callback);
    };
    off(event, callback) {
        this._listener[event] = this._listener[event] || [];

        if (event && typeof callback === 'function') {
            var i = this._listener[event].indexOf(callback);
            if (i !== -1) {
                this._listener[event].splice(i, 1);
            }
        }
    };
    clearEventListner(event) {
        this._listener[event] = [];
    };
}


module.exports = new RoomRecording();