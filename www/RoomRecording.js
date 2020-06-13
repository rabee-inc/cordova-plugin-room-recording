'use strict';

var exec = require('cordova/exec');

// cordova の実行ファイルを登録する
const registerCordovaExecuter = (action, onSuccess, onFail, param) => {
    return exec(onSuccess, onFail, 'RoomRecording', action, [param]);
};

// promise で返す。 cordova の excuter の wrapper
const createAction = (action, params) => {
    return new Promise((resolve, reject) => {
        // actionが定義されているかを判定したい
        if (true) {
            // cordova 実行ファイルを登録
            registerCordovaExecuter(action, resolve, reject, params);
        }
        else {
            // TODO: error handling
        }
    });
};

// 本体 -> これを利用したもう一つ大きなクラスを作って 体裁を整える
const RoomRecording = {
    // セットアップ
    initialize: (params) => createAction('start', params),
    createRoom: (params) => createAction('createRoom', params),
    joinRoom: (params) => createAction('joinRoom', params),
    startRecording: (params) => createAction('startRecording', params),
    stopRecording: (params) => createAction('stopRecording', params),
    split: (params) => createAction('stopRecording', params),
}


module.exports = RoomRecording;
