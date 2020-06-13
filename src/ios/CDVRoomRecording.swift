

@objc(CDVRoomRecording) class CDVRoomRecording : CDVPlugin {
  override func pluginInitialize() {};

  @objc func initialize(_ command: CDVInvokedUrlCommand) {
    let result = CDVPluginResult(status: CDVCommandStatus_OK)
    commandDelegate.send(result, callbackId: command.callbackId)
  }

  // @objc func stop(_ command: CDVInvokedUrlCommand) {
  //   let result = CDVPluginResult(status: CDVCommandStatus_OK)
  //   commandDelegate.send(result, callbackId: command.callbackId)
  // }
}
