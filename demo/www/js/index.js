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

    // getMicPermission
    const getMicPermissionBtn = document.querySelector('.getMicPermissionBtn') 	
    getMicPermissionBtn.addEventListener('click', getMicPermission);	

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


    const progress = (e) => {
        console.log(e) // 0 - 100 の値を返すようにする
        if (e === 100) {
            RoomRecording.off(progress);
        }
    }
    

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

    // compression progress check
    RoomRecording.on('compressionProgress', progress);
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

function getMicPermission() {	
    RoomRecording.getMicPermission().then((v) => {
        console.log(JSON.stringify(v));
        window.alert('マイク許可をリクエストしました');
    });
} 

function hasRecordedFile() {	
    RoomRecording.hasRecordedFile().then((v) => {	
        console.log(JSON.stringify(v));	
        window.alert("録音済ファイル有無をチェックしました");
    });	
} 
