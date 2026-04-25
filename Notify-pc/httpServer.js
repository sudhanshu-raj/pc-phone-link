const express = require("express");
const keytar = require("keytar");
const Store = require("electron-store").default;
const utils = require("./utils");

const store = new Store();

let devicesInfo = {};

async function createHttpServer() {
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

  app.post("/generatePIN", (req, res) => {
    try {
      const { deviceName, phoneIP } = req.body;
      console.log("Got the pin generation request from device :", deviceName);

      const deviceID = utils.generateKeys();
      const pin = Math.floor(1000 + Math.random() * 9000);
      console.log("PIN for ", deviceName, " ,is:", pin); // WILL ERASE THIS

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
      devicesInfo[deviceID] = info;
      store.set(deviceID, info);

      return res.json({
        status: "success",
        deviceID: deviceID,
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

      if (!devicesInfo[deviceID] || !devicesInfo[deviceID]?.pin) {
        return res.json({
          status: "failed",
          failureType: utils.failureType.INVALID_DATA,
          message: "Invalid device",
        });
      }

      const originalPin = devicesInfo[deviceID]?.pin;
      const pinGeneratedOn = devicesInfo[deviceID]?.pinGeneratedOn;
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

      devicesInfo[deviceID].isAuthenticated = true;
      devicesInfo[deviceID].meta.lastSeen = Date.now();
      const token = utils.generateKeys(20);
      store.set(deviceID, {
        ...store.get(deviceID),
        token: token,
        isAuthenticated : true
      });
      return res.json({
        status: "success",
        token: token,
        webSocketURL: await utils.getWebSocketURL(),
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
      console.log("Got the token:", token," from device id:", deviceID);

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
};
