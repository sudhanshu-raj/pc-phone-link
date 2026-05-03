require("electron-reload")(__dirname);

const { app, BrowserWindow, ipcMain, Menu } = require("electron/main");
const WebSocket = require("ws");
const path = require("path");
const bonjour = require("bonjour")();
const { createHttpServer, scannedDevicesInfo } = require("./httpServer");
const utils = require("./utils");
const Store = require("electron-store").default;

const {
  createInstructionWindow,
} = require("./windows/instrcutions_window/setupInstruction");
const { createDeviceScanWindow } = require("./windows/device_scan/deviceScan");
const {
  createPINWindow,
} = require("./windows/generate_pin/generatePIN_window");
const {
  createNotificationWindow,
} = require("./windows/notification_list/notification_list");
const {
  createSetDeviceNameWin,
} = require("./windows/set_device_name/setDeviceName");

const store = new Store();

let mainWindow;
const WEBSOCKET_PORT = 8090;
const MDNS_SERVICE_TYPE = "notifypcws";
const SERVICE_NAME = "notify-my-pc";
let wssServer;
let mdnsService;
let deviceNameValue = "";
let isServerStarted = false;
let devicesFoundOnRadar = {};
let httpPort;
let webSocketPort;

function isDeviceScanWindowActive() {
  if (!mainWindow || mainWindow.isDestroyed()) {
    return false;
  }

  try {
    return mainWindow.webContents.getURL().includes("/device_scan/index.html");
  } catch (error) {
    return false;
  }
}

function switchWindow(createWindowFn, event) {
  const sourceWindow = BrowserWindow.fromWebContents(event.sender);
  const nextWindow = createWindowFn();
  mainWindow = nextWindow;

  if (sourceWindow && !sourceWindow.isDestroyed()) {
    sourceWindow.close();
  }

  return nextWindow;
}

function createWindow() {
  const win = new BrowserWindow({
    width: 284,
    height: 544,
    webPreferences: {
      preload: path.join(__dirname, "preload.js"),
      contextIsolation: true,
      nodeIntegration: false,
    },
  });

  win.loadFile("index.html");
  return win;
}

async function startAppServices() {
  try {
    createHttpServer({
      onPhoneFound: (foundDevice) => {
        if (isDeviceScanWindowActive()) {
          devicesFoundOnRadar[foundDevice.deviceName] = foundDevice.ip;
          mainWindow.webContents.send("phone-found", foundDevice);
        }
      },
      onNewPINGenerated: (pin) => {
        const sourceWindow = mainWindow;
        const pinWindow = createPINWindow();
        mainWindow = pinWindow;

        if (sourceWindow && !sourceWindow.isDestroyed()) {
          sourceWindow.close();
        }

        pinWindow.webContents.once("did-finish-load", () => {
          pinWindow.webContents.send("pin-generated", pin);
          console.log("send the pin to window", pin);
        });
      },
      onAuthenticationSuccess: () => {
        const sourceWindow = mainWindow;
        const pinWindow = createNotificationWindow();
        mainWindow = pinWindow;

        if (sourceWindow && !sourceWindow.isDestroyed()) {
          sourceWindow.close();
        }
      },
    }).then((httpServer) => {
      if (httpServer) {
        httpPort = httpServer.address().port;
        startMDNS(httpPort);
      }
    });

    webSocketPort = await utils.getWebSocketPort();
    startWebSocketServer(webSocketPort);
    isServerStarted = true;

    ipcMain.on("send-mssg", (_event, value) => {
      if (value && wssServer) {
        for (const client of wssServer.clients) {
          if (client.readyState === WebSocket.OPEN) {
            client.send(value);
          }
        }
      }
    });
  } catch (error) {
    console.log("Error on startAppServices", error);
  }
}

ipcMain.handle("store:get", (_event, key) => {
  return store.get(key);
});

ipcMain.handle("store:set", (_event, key, value) => {
  store.set(key, value);
  return true;
});

ipcMain.handle("store:list-ids", () => {
  return Object.entries(store.store)
    .filter(([k]) => k.startsWith("ID"))
    .reduce((obj, [k, v]) => ({ ...obj, [k]: v }), {});
});

function buildServiceName(lanIp) {
  if (!lanIp) {
    return SERVICE_NAME;
  }
  return `${SERVICE_NAME}-ip-${lanIp.replace(/\./g, "-")}`;
}

async function startMDNS(httpPort) {
  const lanIp = utils.getPreferredLanIPv4();
  if (lanIp) {
    console.log("mDNS TXT serverIP:", lanIp);
    // console.log("mDNS service name:", buildServiceName(lanIp));
  } else {
    console.warn("No preferred LAN IPv4 found for serverIP TXT");
  }

  mdnsService = bonjour.publish({
    name: deviceNameValue ? deviceNameValue : SERVICE_NAME,
    type: MDNS_SERVICE_TYPE,
    protocol: "tcp",
    port: httpPort,
    txt: {
      a: "1",
    },
  });

  mdnsService.on("up", () => {
    console.log("mDNS service is up");
  });

  mdnsService.on("error", (err) => {
    console.error("mDNS publish error:", err);
  });
}

function startWebSocketServer(port) {
  wssServer = new WebSocket.Server({ port: port });

  wssServer.on("connection", (ws, req) => {
    console.log("Phone connected :", req.socket.remoteAddress);

    ws.on("message", (data) => {
      const mssgString = data.toString();
      try {
        const mssgObj = JSON.parse(mssgString);
        if (mainWindow) {
          console.log("sending mssg to");
          mainWindow.webContents.send("notification-popup", mssgObj);
        }
        ws.send("Received on PC");
      } catch (error) {
        console.log("Message is plain string", mssgString);
      }
    });

    ws.send("Connected to Elctron PC");
  });
  console.log(`WebSocket running on ws://0.0.0.0:${port}`);
}

ipcMain.on("set-device-name", (_event, value) => {
  deviceNameValue = value;
  console.log("Device name set is :", deviceNameValue);
});

app.whenReady().then(() => {
  Menu.setApplicationMenu(null);
  store.clear();
  mainWindow = createInstructionWindow();

  app.on("activate", () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      mainWindow = createWindow();
    }
  });

  ipcMain.on("enable-set-name-win", (event) =>
    switchWindow(createSetDeviceNameWin, event),
  );
  ipcMain.on("enable-instruction-win", (event) =>
    switchWindow(createInstructionWindow, event),
  );
  ipcMain.on("send-mssg", (key, value) => {
    console.log("Message from send-mssg,", key, ":", value);
  });

  //important listener for startup device case
  ipcMain.on("enable-device-scan-win-from-setup", (event) => {
    const deviceScanWindow = switchWindow(createDeviceScanWindow, event);

    if (deviceScanWindow) {
      deviceScanWindow.webContents.once("did-finish-load", () => {
        deviceScanWindow.webContents.send("device-name", deviceNameValue);
        store.set("thisDeviceName", deviceNameValue);
        store.set("thisDeviceID", utils.generateKeys());
        if (!isServerStarted) {
          startAppServices();
        }
      });
    }
  });

  ipcMain.on("select-device-found", async (_event, value) => {
    const ip = devicesFoundOnRadar[value];

    if (!httpPort || !ip) {
      console.warn("Cannot generate PIN: missing httpPort or device IP", {
        httpPort,
        deviceName: value,
        ip,
      });
      return;
    }

    try {
      const response = await fetch(`http://localhost:${httpPort}/generatePIN`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          deviceName: value,
          phoneIP: ip,
        }),
      });

      const result = await response.json();
      console.log("generatePIN response:", result);
    } catch (error) {
      console.error("Error calling /generatePIN:", error);
    }
  });
});

app.on("window-all-closed", () => {
  if (mdnsService) {
    mdnsService.stop();
  }
  bonjour.unpublishAll(() => bonjour.destroy());

  if (process.platform !== "darwin") {
    app.quit();
  }
});
