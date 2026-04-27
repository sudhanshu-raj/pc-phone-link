const { BrowserWindow} = require("electron/main")
const path = require("path")


function  createPINWindow(){
    const win = new BrowserWindow({
        width : 284,
        height : 544,
        resizable: false,
    })

    win.loadFile(path.join(__dirname, "index.html"))
    return win;
}

module.exports = {
    createPINWindow
}