const {app, BrowserWindow, ipcMain} = require("electron/main")
const WebSocket = require("ws")
const path = require('path');
const bonjour = require('bonjour')();
const { createHttpServer } = require('./httpServer');
const utils = require('./utils')

let mainWindow;
const WEBSOCKET_PORT = 8090;
const MDNS_SERVICE_TYPE = 'notifypcws';
const SERVICE_NAME = "notify-my-pc"
let wssServer;
let mdnsService;

function createWindow(){
    const win = new BrowserWindow({
        width : 600,
        height : 800,
        webPreferences : {
            preload : path.join(__dirname,'preload.js'),
            contextIsolation: true,
            nodeIntegration: false,
        }

    })

    win.loadFile('index.html');
    return win;
}

async function startAppServices() {
    createHttpServer().then(httpServer => {
        if (httpServer) {
            const httpPort = httpServer.address().port;
            startMDNS(httpPort);
        }
    });

    const webSocketPort = await utils.getWebSocketPort();
    startWebSocketServer(webSocketPort);

    ipcMain.on("send-mssg",(_event,value) =>{
        if(value && wssServer){
            for (const client of wssServer.clients) {
                if (client.readyState === WebSocket.OPEN) {
                    client.send(value);
                }
            }
        }
    })
}


function buildServiceName(lanIp) {
    if (!lanIp) {
        return SERVICE_NAME;
    }
    return `${SERVICE_NAME}-ip-${lanIp.replace(/\./g, '-')}`;
}

async function startMDNS(httpPort) {
    const lanIp = utils.getPreferredLanIPv4();
    if (lanIp) {
        console.log("mDNS TXT serverIP:", lanIp);
        console.log("mDNS service name:", buildServiceName(lanIp));
    } else {
        console.warn("No preferred LAN IPv4 found for serverIP TXT");
    }

    mdnsService = bonjour.publish({
        name: buildServiceName(lanIp),
        type: MDNS_SERVICE_TYPE,
        protocol : 'tcp',
        port: httpPort,
        txt : {
            a:'1'
        }

    });
    
    mdnsService.on('up', () => {
        console.log('mDNS service is up');
    });

    mdnsService.on('error', (err) => {
        console.error('mDNS publish error:', err);
    });
}

function startWebSocketServer(port){

    wssServer = new WebSocket.Server({port : port});

    wssServer.on('connection',(ws,req) =>{
        console.log("Phone connected :", req.socket.remoteAddress);

        ws.on('message',(data) =>{
            const message = data.toString();
            console.log("Received", message);

            if(mainWindow){
                console.log("sending mssg to")
                mainWindow.webContents.send('phone-data', message);
            }
            ws.send("Received on PC");
        });

        ws.send("Connected to Elctron PC");
    });
    console.log(`WebSocket running on ws://0.0.0.0:${port}`)
}


app.whenReady().then(() =>{

    mainWindow = createWindow();
    mainWindow.webContents.once('did-finish-load', async () => {
        await startAppServices();
    });

    app.on('activate', () =>{
        if(BrowserWindow.getAllWindows().length === 0){
            mainWindow = createWindow();
        }
    })


})


app.on("window-all-closed",() =>{
    if (mdnsService) {
        mdnsService.stop();
    }
    bonjour.unpublishAll(() => bonjour.destroy());

    if(process.platform !== 'darwin'){
        app.quit();
    }
})