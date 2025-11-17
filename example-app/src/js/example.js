import { PushApp } from 'pushapp-ionic';

window.testEcho = () => {
    const inputValue = document.getElementById("echoInput").value;
    PushApp.echo({ value: inputValue })
}
