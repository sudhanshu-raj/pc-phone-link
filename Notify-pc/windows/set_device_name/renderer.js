const setBtn = document.querySelector('.set-btn');
const deviceInput = document.querySelector('.device-input');
const backBtn = document.querySelector('.back-btn');

deviceInput.addEventListener('input', () => {
    deviceInput.setCustomValidity('');
});

setBtn.addEventListener('click', () => {
    const deviceName = deviceInput.value.trim();
    if (deviceName.length < 2) {
        deviceInput.setCustomValidity('Name must be at least 2 chars.');
        deviceInput.reportValidity();
        return;
    }

    deviceInput.setCustomValidity('');

    if (deviceName) {
        console.log('Setting device name:', deviceName);
        window.windowAPI.setDeviceName(deviceName);
        window.windowAPI.openDeviceScanWindow();
  
    }
});

backBtn.addEventListener('click', () => {
    window.windowAPI.openInstructionWindow()
});
