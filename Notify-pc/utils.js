const crypto = require('crypto');
const os = require('os')
const net = require('net');

class utils {

    static status = {
    ONLINE: "ONLINE",
    OFFLINE: "OFFLINE",
    PENDING: "PENDING"
    };

    static failureType = {
        INVALID_DATA : "INVALID DATA",
        PIN_TIMEOUT : "PIN TIMEOUT",
        INVALID_PIN : "INVALID PIN",
        INVALID_TOKEN : "INVALID TOKEN"
    }

    static PIN_EXPIRE_TIME = 10; // in min
    static WEBSOCKET_DEFAULT_PORT = 5060
    static HTTP_DEFAULT_PORT = 6060;
    static WEBSOCKET_RUNNING_PORT = null;

    static  generateKeys(length = 10) {
        const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
        const bytes = crypto.randomBytes(length);

        let result = '';
        for (let i = 0; i < length; i++) {
            result += chars[bytes[i] % chars.length];
        }

        return result;
    }

    static getPreferredLanIPv4() {
        const interfaces = os.networkInterfaces();

        // Prefer Wi-Fi/WLAN addresses first, then any routable IPv4.
        const preferredInterfaceNames = Object.keys(interfaces).sort((a, b) => {
            const aWifi = /wi-?fi|wlan|wireless/i.test(a) ? 0 : 1;
            const bWifi = /wi-?fi|wlan|wireless/i.test(b) ? 0 : 1;
            return aWifi - bWifi;
        });

        for (const name of preferredInterfaceNames) {
            const netEntries = interfaces[name] || [];
            for (const addr of netEntries) {
                if (addr.family === 'IPv4' && !addr.internal) {
                    return addr.address;
                }
            }
        }

        return null;
    }

    static isPortAvailable(port) {
        return new Promise((resolve) => {
            const server = net.createServer();

            server.once('error', (err) => {
            if (err.code === 'EADDRINUSE') {
                resolve(false); // port in use
            } else {
                resolve(false);
            }
            });

            server.once('listening', () => {
            server.close();
            resolve(true); // port available
            });

            server.listen(port);
        });
    }

    static async findAvailablePort(start) {
        let port = start;

        while (!(await this.isPortAvailable(port))) {
            port++;
        }

        return port;
    }

    static async getWebSocketPort() {
        if (this.WEBSOCKET_RUNNING_PORT) {
            return this.WEBSOCKET_RUNNING_PORT;
        }
        const port = await this.findAvailablePort(this.WEBSOCKET_DEFAULT_PORT);
        this.WEBSOCKET_RUNNING_PORT = port;
        return port;
    }

    static async getWebSocketURL(){
        const port = await this.getWebSocketPort();
        const ip = this.getPreferredLanIPv4();
        return "ws://"+ip+":"+port;
    }

    static async getHTTP_PORT(){
        return await this.findAvailablePort(this.HTTP_DEFAULT_PORT);
    }

    
}

module.exports = utils