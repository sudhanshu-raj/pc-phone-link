const {ipcRenderer, contextBridge} = require("electron")


contextBridge.exposeInMainWorld("phoneAPI",{
    phoneMssg: (callback) => ipcRenderer.on('phone-data', (_event, message) => callback(message)),
    sendMssg : (value) => ipcRenderer.send("send-mssg",value)
})