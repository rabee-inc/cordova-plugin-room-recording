document.addEventListener('deviceready', onDeviceReady, false);	
function onDeviceReady() {   	
    initialize();	


    // ルーム作成	
    const createRoomBtn = document.querySelector('.createRoomBtn') 	
    createRoomBtn.addEventListener('click', createRoom);	

    // ルーム参加	
    const joinRoomBtn = document.querySelector('.joinRoomBtn') 	
    joinRoomBtn.addEventListener('click', joinRoom);	

    // ルーム退出	
    const leaveRoomBtn = document.querySelector('.leaveRoomBtn') 	
    leaveRoomBtn.addEventListener('click', leaveRoom);	

    // レコーディング開始	
    const recordingStartBtn = document.querySelector('.recordingStartBtn') 	
    recordingStartBtn.addEventListener('click', startRecording);	

    // レコーディングストップ	
    const recordingStopBtn = document.querySelector('.recordingStopBtn') 	
    recordingStopBtn.addEventListener('click', stopRecording);	

    // export 	
    const exportAudioBtn = document.querySelector('.exportAudioBtn');	
    exportAudioBtn.addEventListener('click', exportAudio);	

    // export with compression	
    const exportWithCompressionBtn = document.querySelector('.exportWithCompressionBtn') 	
    exportWithCompressionBtn.addEventListener('click', exportWithCompression);	

    // split 	
    const splitBtn = document.querySelector('.splitBtn') 	
    splitBtn.addEventListener('click', split);	


    // offlinealert 	
    const offlineAlert = (data) => {
        window.alert('offline', data.uid);
    }
    const eventOffBtn = document.querySelector('.eventOffBtn') 	
    eventOffBtn.addEventListener('click', () => {
        RoomRecording.off('offline', offlineAlert);
        window.alert('func off')
    });	
    // getwaveform
    const getWaveFormBtn = document.querySelector('.getWaveFormBtn') 	
    getWaveFormBtn.addEventListener('click', getWaveForm);	

    // hasrecordedfile
    const hasRecordedFileBtn = document.querySelector('.hasRecordedFileBtn') 	
    hasRecordedFileBtn.addEventListener('click', hasRecordedFile);	

    // getrecordedfile
    const getRecordedFileBtn = document.querySelector('.getRecordedFileBtn') 	
    getRecordedFileBtn.addEventListener('click', getRecordedFile);	

    // 音が入ってきたら	
    RoomRecording.on('pushVolume', (data) => {	
        const {total_volume, speakers} = data;	
        console.log(total_volume, speakers.length, speakers);	
    });	
    // buffer
    RoomRecording.on('pushBuffer', (data) => {
        console.log('pushBuffer', data);
    });
    // 人が入ってきたら	
    RoomRecording.on('changeSpeakersStatus', (data) => {	
        console.log(data);	
    });	
    // オフラインの検出	
    RoomRecording.on('offline', offlineAlert);	
}	

// 初期化	
function initialize() {	
    RoomRecording.initialize().then(() => {	
        window.alert('initialized!');	
        RoomRecording.getMicPermission();	
    });	
}	
// ルームを作成する	
function createRoom () {	
    // const roomId = window.prompt("作成するルームidを入力してください");	
    const roomId = "aaa"	
    RoomRecording.joinRoom({room_id: roomId}).then(() => {	
        window.alert('ルームを作成しました');	
    });	
}	
// ルームに入室する	
function joinRoom () {	
    // const roomId = window.prompt("参加するルームidを入力してください");	
    const roomId = "aaa"	
    RoomRecording.joinRoom({room_id: roomId}).then(() => {	
        window.alert('ルームに入室しました');	
    });	
}	
// ルームから退出する	
function leaveRoom() {	
    RoomRecording.leaveRoom().then(() => {	
        window.alert('ルームから退出しました');	
    });	
}	

// レコーディングスタート	
function startRecording() {	
    RoomRecording.startRecording().then((v) => {	
        window.alert('レコーディングを開始しました');	
    });	
}	
// レコーディングストップ中	
function stopRecording() {	
    RoomRecording.stopRecording().then((v) => {	
        window.alert('レコーディングを終了しましたしました');	
    });	
}	

function exportAudio() {	
    RoomRecording.export().then((v) => {	
        console.log(JSON.stringify(v))	
        window.alert('エクスポートしました');	
    });	
}	

function exportWithCompression() {	
    RoomRecording.exportWithCompression().then((v) => {	
        console.log(JSON.stringify(v));	
        window.alert('圧縮エクスポートしました');	
    });	
}	

function split() {	
    const seconds = 2.0;	
    RoomRecording.split(seconds).then((v) => {	
        console.log(JSON.stringify(v));	
        window.alert(seconds + "で分割しました");	
    });	
} 

function getWaveForm() {	
    RoomRecording.hasRecordedFile().then((v) => {
        if (JSON.stringify(v)) {
            RoomRecording.getWaveForm().then((data) => {	
                console.log(data);	
                window.alert("波形データを取得しました");	
            });	
        } else {
            window.alert('波形データ取得に失敗しました');
        }
    });
} 

function getRecordedFile() {	
    RoomRecording.hasRecordedFile().then((v) => {
        if (JSON.stringify(v)) {
            RoomRecording.getRecordedFile().then((fileName) => {	
                console.log(JSON.stringify(fileName));
                window.alert("ファイルを取得しました");	
            });	
        } else {
            window.alert('ファイル取得に失敗しました');
        }
    });
} 

function hasRecordedFile() {	
    RoomRecording.hasRecordedFile().then((v) => {	
        console.log(JSON.stringify(v));	
        window.alert("録音済ファイル有無をチェックしました");
    });	
} 


// async restore(id) {


//     try {
//       var recorder = (window.recorder) ? window.recorder : window.rec;
//       const audio_data = await recorder.getAudio(id);
//       const url = await recorder.getWaveForm(audio_data.full_audio.path);
//       const binary = await this.getAudioBinary(url);
  
//       let buffer;
//       if (uuaa.os.name === "Android") {
//         const a = new Int16Array(binary);
//         const bit15 = 1 << 15;
//         const f = [];
//         for (let i = 0; i < a.length; ++i) {
//           f.push(a[i] / bit15);
//         }
//         buffer = new Float32Array(f);
//       } else {
//         buffer = new Float32Array(binary);
//       }
  
//       this.dataLength = buffer.length;
//       var loopNum = Math.ceil(this.dataLength / this.bufferSize);
//       this.recorder = true;
//       for (var i = 0; i < loopNum; ++i) {
//         var to = Math.min((i + 1) * this.bufferSize);
//         this.trigger("pushBuffer", buffer.subarray(i * this.bufferSize, to));
//       }
//     }
//     catch (e) {
//       throw e;
//     }