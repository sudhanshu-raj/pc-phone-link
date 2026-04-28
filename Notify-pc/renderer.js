const phoneMssg = document.getElementById('phoneMssg');
const sendBtn = document.getElementById('sendMssg');
const mssgInput = document.getElementById("mssgInput");

window.appAPI.phoneMssg((value) => {

    console.log("got the value in rendered ",value)
    phoneMssg.textContent = value;
});

sendBtn.addEventListener('click', () =>{
    if(mssgInput.value){
        window.appAPI.sendMssg(mssgInput.value);
    }
})