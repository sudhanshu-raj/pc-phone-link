const { BrowserWindow} = require("electron/main")
const path = require("path")


function  createNotificationWindow(){
    const win = new BrowserWindow({
        width : 284,
        height : 544,
        resizable: false,
        webPreferences: {
                    // devTools: true,
                    preload: path.join(__dirname, '../../preload.js'),
                    contextIsolation: true,
                    nodeIntegration: false,
                }
    })

    win.loadFile(path.join(__dirname, "index.html"))
    // win.webContents.once("did-finish-load", () => {
    //     win.webContents.openDevTools({ mode: "detach" });
    // });
    return win;

    
}

module.exports = {
    createNotificationWindow
}