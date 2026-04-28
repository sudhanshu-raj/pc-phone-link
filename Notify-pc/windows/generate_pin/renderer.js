
window.windowAPI.onPINFound(value => {
    console.log("pin got:", value);
    const pinStr = String(value);
    const inputs = document.querySelectorAll('.pin');
    inputs.forEach((input, idx) => {
        input.value = pinStr[idx] || '';
    });
});