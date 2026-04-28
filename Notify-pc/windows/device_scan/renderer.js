
function displayDevice(deviceName) {
        const radarRadius = 125; // 250px diameter / 2
        const deviceSize = 12;
        const labelOffset = 25;
        const minRadius = 50;
        const maxRadius = radarRadius * 0.75;

        const angle = Math.random() * 2 * Math.PI;
        const radius = Math.random() * (maxRadius - minRadius) + minRadius;

        const x = radarRadius + radius * Math.cos(angle);
        const y = radarRadius + radius * Math.sin(angle);

        const top = y - deviceSize / 2;
        const left = x - deviceSize / 2;

        const radar = document.querySelector('.radar');

        if (!radar) {
          return;
        }

        const wrapper = document.createElement('div');
        wrapper.className = 'device-info';
        wrapper.style.position = 'absolute';
        wrapper.style.top = top + 'px';
        wrapper.style.left = left + 'px';
        wrapper.style.cursor = 'pointer';

        const dot = document.createElement('div');
        dot.className = 'device-dot';
        dot.style.width = deviceSize + 'px';
        dot.style.height = deviceSize + 'px';
        dot.style.borderRadius = '50%';
        dot.style.background = '#ffffff';

        const labelEl = document.createElement('div');
        labelEl.className = 'label';
        labelEl.textContent = deviceName;
        labelEl.style.position = 'absolute';
        labelEl.style.top = -labelOffset + 'px';
        labelEl.style.left = '-5px';
        labelEl.style.whiteSpace = 'nowrap';

        wrapper.appendChild(dot);
        wrapper.appendChild(labelEl);
        radar.appendChild(wrapper);
      }

window.windowAPI.onPhoneFound((value) =>{
    console.log("Found device is ",value)
    displayDevice(value.deviceName);
})

const radarContainer = document.getElementById('radar') || document.body;

radarContainer.addEventListener('click', (e) => {
  const info = e.target.closest('.device-info');
  if (!info) return;
  const name = info.querySelector('.label')?.textContent;
  console.log(name);
  window.windowAPI.selectDeviceFound(name)
});

// displayDevice('iPhone 17')
// displayDevice('iPhone 15')

