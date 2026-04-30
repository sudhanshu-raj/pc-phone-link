const express = require("express");
const keytar = require("keytar");
const Store = require("electron-store").default;
const utils = require("./utils");

const store = new Store();

let scannedDevicesInfo = {};

async function createHttpServer({ onPhoneFound, onNewPINGenerated, onAuthenticationSuccess } = {}) {
  const HTTP_PORT = await utils.getHTTP_PORT();
  const app = express();
  app.use(express.json());
  app.use(express.urlencoded({ extended: true }));

  app.get("/ping", (req, res) => {
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

  app.post("/phonesFound", (req, res) => {
    try {
      const { deviceName, phoneIP } = req.body;
      if (onPhoneFound) {
        onPhoneFound({ deviceName, ip: phoneIP });
      }
      console.log("found the device:", deviceName, "ip:", phoneIP);
      return res.json({
        status: "success",
      });
    } catch (error) {
      console.log("Error in /phonesFound:", error);
    }
  });

  app.post("/generatePIN", (req, res) => {
    try {
      const { deviceName, phoneIP } = req.body;

      const deviceID = utils.generateKeys();
      const pin = Math.floor(1000 + Math.random() * 9000);

      const existingDevice = Object.entries(scannedDevicesInfo || {}).find(
        ([, d]) => d.deviceName === deviceName,
      );
      const existingDeviceId = existingDevice?.[0];
      const existingDeviceData = existingDevice?.[1];

      // If device already found, so probably pin got genereated for it already
      if (existingDeviceId && existingDeviceData) {
        const olderPinGeneratedTime =
          existingDeviceData.pinGeneratedOn + utils.PIN_EXPIRE_TIME * 60 * 1000;
        if (Date.now() <= olderPinGeneratedTime) {
          return res.json({
            status: "success",
            deviceID: existingDeviceId,
            serverDeviceName : store.get("thisDeviceName")?store.get("thisDeviceName"):"NULL"
          });
        }
      }

      const info = {
        deviceName: deviceName,
        pin: pin,
        pinGeneratedOn: Date.now(),
        status: utils.status.PENDING,
        isAuthenticated: false,
        meta: {
          ip: phoneIP,
          lastSeen: Date.now(),
        },
      };
      scannedDevicesInfo[deviceID] = info;

      //new pin got generated, now move to verify pin window
      if (onNewPINGenerated) {
        onNewPINGenerated(pin);
      }
      return res.json({
        status: "success",
        deviceID: deviceID,
        serverDeviceName : store.get("thisDeviceName")?store.get("thisDeviceName"):"NULL"

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

  app.post("/authenticateLAN", async (req, res) => {
    try {
      const { pin, deviceID } = req.body;
      console.log("Got the pin:", pin, ", from device:", deviceID); // ERASE THIS

      if (!scannedDevicesInfo[deviceID] || !scannedDevicesInfo[deviceID]?.pin) {
        return res.json({
          status: "failed",
          failureType: utils.failureType.INVALID_DATA,
          message: "Invalid device",
        });
      }

      const originalPin = scannedDevicesInfo[deviceID]?.pin;
      const pinGeneratedOn = scannedDevicesInfo[deviceID]?.pinGeneratedOn;
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

      scannedDevicesInfo[deviceID].isAuthenticated = true;
      scannedDevicesInfo[deviceID].meta.lastSeen = Date.now();
      const token = utils.generateKeys(20);
      store.set(deviceID, {
        ...scannedDevicesInfo[deviceID],
        token: token,
        isAuthenticated: true,
      });
      if(onAuthenticationSuccess){
        onAuthenticationSuccess();
      }
      const wsport = await utils.getWebSocketPort();
      console.log("websocket port sending:",wsport," & it has type :",typeof(wsport))
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

  app.post("/verifyLANToken", async (req, res) => {
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

      const originalToken = store.get(deviceID)?.token;
      const isAuthenticated = store.get(deviceID)?.isAuthenticated;

      if (originalToken === token && isAuthenticated) {
        return res.json({
          status: "success",
          webSocketURL: await utils.getWebSocketURL(),
        });
      } else {
        return res.json({
          status: "failed",
          failureType: utils.failureType.INVALID_TOKEN,
          message: "Invalid Token",
        });
      }
    } catch (error) {
      console.error("Error in /verifyLANToken route:", error);
      return res.status(500).json({
        status: "error",
        failureType: "SERVER_ERROR",
        message: "Token verification failed",
      });
    }
  });

  const server = app.listen(HTTP_PORT, () => {
    console.log("Server running on port", HTTP_PORT);
  });

  return server;
}

module.exports = {
  createHttpServer,
  scannedDevicesInfo,
};
