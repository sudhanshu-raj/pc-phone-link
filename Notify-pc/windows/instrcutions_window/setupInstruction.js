const { BrowserWindow } = require("electron/main")

function createWindow(){

    const win = new BrowserWindow({
        width : 190,
        height : 375,
    })

    win.loadFile("index.html")
    return win;
}

module.exports = {
    createWindow
}