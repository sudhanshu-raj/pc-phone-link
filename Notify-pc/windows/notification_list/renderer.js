document.addEventListener("DOMContentLoaded", async () => {
	const deviceName = document.querySelector(".device-head h1");
	const isConnected = document.querySelector(".status-pill")

	if (!deviceName || !isConnected) {
		return;
	}

	const deviceList = await window.appAPI.getDeviceList();

	let selectedDevice ;
	for(const key of Object.keys(deviceList)){
		const data = deviceList[key];
		if(data.isSelected){
			selectedDevice = data;
		}
	}

	if (selectedDevice) {
		deviceName.textContent = selectedDevice["deviceName"]?selectedDevice["deviceName"].slice(2):"Unknown";
		isConnected.textContent = selectedDevice["isConnected"] ? "Connected" : "Not Connected"
	}
});