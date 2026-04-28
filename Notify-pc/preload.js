const {ipcRenderer, contextBridge} = require("electron")


contextBridge.exposeInMainWorld("appAPI",{
    phoneMssg: (callback) => ipcRenderer.on('phone-data', (_event, message) => callback(message)),
    sendMssg : (value) => ipcRenderer.send("send-mssg",value)
})

contextBridge.exposeInMainWorld("windowAPI",{
    openSetNameWindow: () => ipcRenderer.send("enable-set-name-win"),
    openInstructionWindow: () => ipcRenderer.send("enable-instruction-win"),
    openDeviceScanWindow : () => ipcRenderer.send('enable-device-scan-win'),
    setDeviceName : (value) => ipcRenderer.send('set-device-name',value),
    onPhoneFound : (callback) => ipcRenderer.on('phone-found', (_event, value) => callback(value)),
    selectDeviceFound : (value) => ipcRenderer.send('select-device-found',value),
    onPINFound : (callback) => ipcRenderer.on('pin-found', (_event, value) => callback(value))
})