let socket;
let pktNr = 0;

window.onload = function () {
    connect("ws://127.0.0.1:8887");

}

window.addEventListener('DOMContentLoaded', () => {
   replaceProfileIcons();
})

function replaceProfileIcons() {
    let profileIcon = document.getElementById("preProfileIcon");
    if (profileIcon) {
        let value = profileIcon.className;
        profileIcon.src = "/proxy/static/lol-game-data/assets/v1/profile-icons/"+value+".jpg"
        console.log(profileIcon.src);
        profileIcon.addEventListener('load', () => {
            console.log("Should have replaced");
        });
        profileIcon.addEventListener('error', (e) => {
            console.log("error");
            console.log(e);
        })
    }
}



function connect(host) {
    socket = new WebSocket(host);
    socket.onopen = function (msg) {
        console.log("Connected to " + host);
        send([4]);
        createKeepAlive();
    }
    socket.onmessage = function (msg) {
        try {
            let parsedMsg = JSON.parse(msg.data);
            console.log(parsedMsg);
        } catch (e) {

        }
    }
    socket.onclose = function (msg) {
        console.log("Disconnected from Host!");
    }
}

function createKeepAlive() {
    setTimeout(createKeepAlive, 290000)
    socket.send("[]");
}

function createLobby(lobbyId) {
    makeLCURequest("POST","/lol-lobby/v2/lobby","{\"queueId\":" + lobbyId + "}");
}

function makeLCURequest(requestType, endpoint, body) {
    let request = new Array();
    request.push(0);
    request.push(requestType);
    request.push(endpoint);
    request.push(body);
    send(request);
}

function manualLCURequest() {
    const methodFilter = document.getElementById('requestType');
    const endpoint = document.getElementById('requestEndpoint');
    const body = document.getElementById('requestBody');
    makeLCURequest(methodFilter.value, endpoint.value, body.value);
}

function send(jsonArray) {
    let request = jsonArray;
    pktNr += 1;
    request.push(pktNr);
    console.log("Sending: " + JSON.stringify(request));
    socket.send(JSON.stringify(request));
}

function backendWebsocketConfig() {
    let request = new Array();
    request.push(1);
    const input = document.getElementById("websocketArray");
    let parsedInput = JSON.parse(input.value);
    request.push(parsedInput);
    send(request);
}

function updateTasks() {
    var switches = document.getElementsByClassName("taskSwitch");

    for (var i = 0; i < switches.length; i++) {
        var switchElement = switches[i];
        var switchInput = switchElement.querySelector("input[type='checkbox']");
        var switchId = switchElement.id;

        if (switchInput.checked) {
            if (switchId === "AutoAcceptQueue") {
                var delayInput = document.getElementById("AutoAcceptQueueDelay");
                var delay = parseInt(delayInput.value);
                send([3, 1, switchId, { "delay": delay }]);
            } else if (switchId === "AutoPickChamp") {
                var delayInput = document.getElementById("AutoPickChampDelay");
                var champIdInput = document.getElementById("AutoPickChampId");
                var delay = parseInt(delayInput.value);
                var champId = parseInt(champIdInput.value);
                send([3, 1, switchId, { "delay": delay, "championId": champId }]);
            }
        } else {
            send([3, 0, switchId, {}]);
        }
    }
}

function restartRiotClientUX() {
    makeLCURequest("POST","/riotclient/kill-and-restart-ux","");
}
