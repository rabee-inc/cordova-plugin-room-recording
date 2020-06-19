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


    // split 	
    const offlineAlert = (data) => {
        window.alert('offline', data.uid);
    }
    const eventOffBtn = document.querySelector('.eventOffBtn') 	
    eventOffBtn.addEventListener('click', () => {
        RoomRecording.off('offline', offlineAlert);
        window.alert('func off')
    });	

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