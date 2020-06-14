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
    initialize: (params) => createAction('initialize', params),
    getMicPermission: (parms) => createAction('getMicPermission', params),
    createRoom: (params) => createAction('joinRoom', params),
    joinRoom: (params) => createAction('joinRoom', params),
    leaveRoom: (params) => createAction('leaveRoom', params),
    startRecording: (params) => createAction('startRecording', params),
    stopRecording: (params) => createAction('stopRecording', params),
    split: (params) => createAction('split', params),
    export: (params) => createAction('export', params),
    exportWithCompression: (params) => createAction('exportWithCompression', params),
    // 登録関係
    on: (type, callback, id) => {
      // type === progress | complete | failed;
      let actionType = '';
      switch(type) {
          case 'changeSpeakersStatus': 
              actionType = 'setOnChangeSpeakersStatus';
              break;
          case 'pushVolume':
              actionType = 'setOnPushVolumeCallback'
              break;
          default: 
              break;
      }
      if (!actionType) return console.warn('please set action type');
      exec(
          (data) => {
              // 成功
              if (typeof callback === 'function') {
                  callback(data)
              }
          },
          (error) => {
              // 失敗
              // TODO: error handling
              console.log(error, 'error')
          },'RoomRecording', actionType, [{id}]
      );
  },
}


module.exports = RoomRecording;
