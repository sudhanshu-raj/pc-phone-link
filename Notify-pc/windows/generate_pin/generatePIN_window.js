const { BrowserWindow} = require("electron/main")
const path = require("path")


function  createPINWindow(){
    const win = new BrowserWindow({
        width : 284,
        height : 544,
        resizable: false,
        webPreferences: {
                    preload: path.join(__dirname, '../../preload.js'),
                    contextIsolation: true,
                    nodeIntegration: false,
                }
    })

    win.loadFile(path.join(__dirname, "index.html"))
    // win.webContents.openDevTools()
    return win;
}

module.exports = {
    createPINWindow
}