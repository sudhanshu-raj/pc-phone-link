let notifications = {};
let currentDeviceID;

function ensureDeviceNotifications(deviceID) {
	if (!deviceID) return;
	if (!notifications[deviceID]) notifications[deviceID] = [];
}

function parseActions(actionsValue) {
	let parsedActions = actionsValue;

	if (typeof parsedActions === "string") {
		try {
			parsedActions = JSON.parse(parsedActions);
		} catch {
			parsedActions = [];
		}
	}

	if (!Array.isArray(parsedActions)) {
		return [];
	}

	return parsedActions.filter((action) => typeof action === "string" && action.trim() !== "");
}

function toNotificationItem(payload) {
	const timeValue = payload?.timestamp ?? payload?.timeStamp ?? Date.now();
	const actions = parseActions(payload?.actions);

	return {
		uid: `${payload?.package || "unknown"}-${payload?.id ?? "na"}-${timeValue}`,
		id: payload?.id,
		appName: payload?.appName || payload?.package || "Unknown App",
		packageName: payload?.package || "",
		title: payload?.title || "",
		content: payload?.content || payload?.body || "",
		image: payload?.image || "",
		timestamp: Number(timeValue) || Date.now(),
		actions,
	};
}

function formatTime(timestamp) {
	const date = new Date(Number(timestamp) || Date.now());
	return date.toLocaleTimeString("en-US", {
		hour: "2-digit",
		minute: "2-digit",
		hour12: true,
	});
}

function createNotificationCard(item) {
	const card = document.createElement("article");
	card.className = "notification-card";
	card.dataset.uid = item.uid;

	const hasReplyAction = item.actions.some((action) => action.toLowerCase() === "reply");
	const imageSrc = item.image ? `data:image/png;base64,${item.image}` : "./img/whatsapp.png";

	const meta = document.createElement("div");
	meta.className = "notification-meta";

	const appWrap = document.createElement("div");
	appWrap.className = "notification-app";

	const icon = document.createElement("img");
	icon.src = imageSrc;
	icon.alt = item.appName;

	const appName = document.createElement("h2");
	appName.textContent = item.appName;

	const time = document.createElement("time");
	time.textContent = formatTime(item.timestamp);

	appWrap.appendChild(icon);
	appWrap.appendChild(appName);
	meta.appendChild(appWrap);
	meta.appendChild(time);

	const dismissButton = document.createElement("button");
	dismissButton.className = "dismiss-btn";
	dismissButton.type = "button";
	dismissButton.setAttribute("aria-label", "Dismiss notification");
	dismissButton.textContent = "×";

	const title = document.createElement("h3");
	title.textContent = item.title;

	const content = document.createElement("p");
	content.textContent = item.content;

	card.appendChild(meta);
	card.appendChild(dismissButton);
	card.appendChild(title);
	card.appendChild(content);

	if (hasReplyAction) {
		const replyBox = document.createElement("div");
		replyBox.className = "reply-box";
		replyBox.setAttribute("aria-label", "Quick reply");
		replyBox.innerHTML = `
			<input type="text" aria-label="Enter your message" placeholder="Enter your message..." />
			<button type="button">Reply</button>
		`;
		card.appendChild(replyBox);
	}

	return card;
}

function renderNotifications() {
	const list = document.querySelector(".notification-list");
	if (!list) return;

	list.innerHTML = "";
	if (!currentDeviceID) return;
	const deviceList = notifications[currentDeviceID] || [];
	for (const item of deviceList) {
		list.appendChild(createNotificationCard(item));
	}
}

function renderDeviceSummary(device) {
	const batteryLabel = document.querySelector(".battery [data-battery-percentage]");
	const chargingIcon = document.querySelector(".battery [data-battery-state]");

	if (batteryLabel) {
		batteryLabel.textContent =
			device?.batteryPercentage !== null && device?.batteryPercentage !== undefined
				? `${device.batteryPercentage}%`
				: "--%";
	}

	if (chargingIcon) {
		const isCharging = device?.isCharging === true;
		chargingIcon.hidden = !isCharging;
		chargingIcon.setAttribute("aria-hidden", String(!isCharging));
	}
}


async function addIncomingNotification(payload) {
	const nextNotification = toNotificationItem(payload);
	console.log("New notification came:" ,nextNotification);
	const deviceID = payload?.deviceID || currentDeviceID || "unknown";
	ensureDeviceNotifications(deviceID);
	notifications[deviceID] = [
		nextNotification,
		...notifications[deviceID].filter((item) => item.uid !== nextNotification.uid),
	];
	console.log(`current deviceID: ${currentDeviceID} and payload id : ${deviceID}`)
	if (deviceID === currentDeviceID) renderNotifications();
}

document.addEventListener("DOMContentLoaded", async () => {
	const deviceName = document.querySelector(".device-head h1");
	const isConnected = document.querySelector(".status-pill");
	const list = document.querySelector(".notification-list");
	const clearButton = document.querySelector(".clear-btn");

	if (!deviceName || !isConnected) {
		return;
	}

	const deviceList = await window.appAPI.getDeviceList();

	let selectedDevice;
	for(const key of Object.keys(deviceList)){
		const data = deviceList[key];
		if(data.isSelected){
			selectedDevice = data;
			currentDeviceID = key.slice(2);
			console.log("and selected id",currentDeviceID)
		}
	}

	if (selectedDevice) {
		deviceName.textContent = selectedDevice["deviceName"] ? selectedDevice["deviceName"].slice(2) : "Unknown";
		isConnected.textContent = selectedDevice["isConnected"] ? "Connected" : "Not Connected";
		renderDeviceSummary(selectedDevice);
	}

	renderNotifications();

	if (clearButton) {
		clearButton.addEventListener("click", () => {
			if (!currentDeviceID) return;
			ensureDeviceNotifications(currentDeviceID);
			notifications[currentDeviceID] = [];
			renderNotifications();
		});
	}

	if (list) {
		list.addEventListener("click", async (event) => {
			const target = event.target;
			if (!(target instanceof HTMLElement)) return;

			if (target.classList.contains("dismiss-btn")) {
				const card = target.closest(".notification-card");
				const uid = card?.dataset?.uid;
				if (!uid || !currentDeviceID) return;

				ensureDeviceNotifications(currentDeviceID);
				notifications[currentDeviceID] = notifications[currentDeviceID].filter((item) => item.uid !== uid);
				renderNotifications();
			}
		});
	}
});

window.windowAPI.onNotificationPopUp(async (message) => {
	await addIncomingNotification(message);
});