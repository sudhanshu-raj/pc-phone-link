const express = require("express");
const keytar = require("keytar");
const Store = require("electron-store").default;
const utils = require("./utils");

const store = new Store();

let scannedClientDevicesInfo = {};

async function createHttpServer({ onPhoneFound, onNewPINGenerated, onAuthenticationSuccess } = {}) {
  const HTTP_PORT = await utils.getHTTP_PORT();
  const app = express();
  app.use(express.json());
  app.use(express.urlencoded({ extended: true }));

  const router = express.Router();

  router.get("/ping", (req, res) => {
    try {
      return res.json({
        message: "pong",
      });
    } catch (error) {
      console.error("Error in /ping route:", error);
      return res.status(500).json({
        status: "error",
        message: "Internal server error",
      });
    }
  });

  router.post("/phonesFound", (req, res) => {
    try {
      const { clientDeviceName, clientDeviceIP, clientDeviceID } = req.body;
      if (onPhoneFound) {
        onPhoneFound({ deviceName:clientDeviceName, ip: clientDeviceIP });
      }
      console.log("found device request:", clientDeviceName, "ip:", clientDeviceIP);
      scannedClientDevicesInfo[clientDeviceID] = {
        deviceName : "ID"+clientDeviceName,
        isConnected : false,
        deviceIP : clientDeviceIP,
        lastSeen : Date.now()
      }
      return res.json({
        status: "success",
        serverDeviceID : store.get("thisDeviceID")?store.get('thisDeviceID'):"UNKNOWN"
      });
    } catch (error) {
      console.log("Error in /phonesFound:", error);
    }
  });

  router.post("/generatePIN", (req, res) => {
    try {
      const { clientDeviceID } = req.body;

      const pin = Math.floor(1000 + Math.random() * 9000);
      const existingDeviceData = scannedClientDevicesInfo[clientDeviceID];
    
      // If device already found, so probably pin got genereated for it already
      if (existingDeviceData && existingDeviceData.pin) {
        const olderPinGeneratedTime =
          existingDeviceData.pinGeneratedOn + utils.PIN_EXPIRE_TIME * 60 * 1000;
        if (Date.now() <= olderPinGeneratedTime) {
          return res.json({
            status: "success",
            deviceID: store.get("thisDeviceID"),
          });
        }
      }

      const info = {
        pin: pin,
        pinGeneratedOn: Date.now(),
        lastSeen: Date.now(),
      };
      scannedClientDevicesInfo[clientDeviceID] = {
        ...(scannedClientDevicesInfo[clientDeviceID] || {}),
        ...info
      };

      //new pin got generated, now move to verify pin window
      if (onNewPINGenerated) {
        onNewPINGenerated(pin);
      }
      return res.json({
        status: "success",
        deviceID: store.get("thisDeviceID"),
      });
    } catch (error) {
      console.error("Error in /generatePIN route:", error);
      return res.status(500).json({
        status: "error",
        failureType: "SERVER_ERROR",
        message: "Failed to generate PIN",
      });
    }
  });

  router.post("/authenticateLAN", async (req, res) => {
    try {
      const { pin, clientDeviceID } = req.body;

      if (!scannedClientDevicesInfo[clientDeviceID] || !scannedClientDevicesInfo[clientDeviceID]?.pin) {
        return res.json({
          status: "failed",
          failureType: utils.failureType.INVALID_DATA,
          message: "Invalid device",
        });
      }

      const originalPin = scannedClientDevicesInfo[clientDeviceID]?.pin;
      const pinGeneratedOn = scannedClientDevicesInfo[clientDeviceID]?.pinGeneratedOn;
      if (!pinGeneratedOn) {
        return res.json({
          status: "failed",
          failureType: utils.failureType.INVALID_DATA,
          message: "Invalid device",
        });
      }
      const durationMs = pinGeneratedOn + utils.PIN_EXPIRE_TIME * 60 * 1000;
      if (Date.now() > durationMs) {
        return res.json({
          status: "failure",
          failureType: utils.failureType.PIN_TIMEOUT,
          message: "PIN Time Expired",
        });
      }

      if (originalPin !== parseInt(pin)) {
        return res.json({
          status: "failure",
          failureType: utils.failureType.INVALID_PIN,
          message: "Invalid PIN",
        });
      }

      scannedClientDevicesInfo[clientDeviceID].isConnected = true;
      scannedClientDevicesInfo[clientDeviceID].lastSeen = Date.now();
      const token = utils.generateKeys(20);
      store.set(("ID"+clientDeviceID), {
        ...scannedClientDevicesInfo[clientDeviceID],
        token: token,
        isSelected : true
      });
      if(onAuthenticationSuccess){
        onAuthenticationSuccess();
      }
      const wsport = await utils.getWebSocketPort();
      return res.json({
        status: "success",
        token: token,
        webSocketPort: wsport,
      });
    } catch (error) {
      console.error("Error in /authenticateLAN route:", error);
      return res.status(500).json({
        status: "error",
        failureType: "SERVER_ERROR",
        message: "Authentication failed",
      });
    }
  });

  router.post("/verifyToken", async (req, res) => {
    try {
      const { token, deviceID } = req.body;
      console.log("Got the token:", token, " from device id:", deviceID);

      if (!token) {
        return res.json({
          status: "failure",
          failureType: utils.failureType.INVALID_DATA,
          message: "Invalid Data",
        });
      }

      const originalToken = store.get("ID"+deviceID)?.token;

      if (originalToken && originalToken === token ) {
        return res.json({
          status: "success",
          webSocketURL: await utils.getWebSocketURL(),
          message : 'Token verified'
        });
      } else {
        return res.json({
          status: "failed",
          failureType: utils.failureType.INVALID_TOKEN,
          message: "Invalid Token",
        });
      }
    } catch (error) {
      console.error("Error in /verifyToken route:", error);
      return res.status(500).json({
        status: "error",
        failureType: "SERVER_ERROR",
        message: "Token verification failed",
      });
    }
  });

  app.use("/api/v1", router);

  const server = app.listen(HTTP_PORT, () => {
    console.log("Server running on port", HTTP_PORT);
  });

  return server;
}

module.exports = {
  createHttpServer,
  scannedDevicesInfo: scannedClientDevicesInfo,
};
