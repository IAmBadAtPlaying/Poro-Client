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
        profileIcon.src = "/proxy/static/lol-game-data/assets/v1/profile-icons/" + value + ".jpg"
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
    setTimeout(createKeepAlive, 250000)
    socket.send("[]");
}

function createLobby(lobbyId) {
    makeLCURequest("POST", "/lol-lobby/v2/lobby", "{\"queueId\":" + lobbyId + "}");
}

function makeLCURequest(requestType, endpoint, body) {
    switch (requestType) {
        case "GET":
            axios.get("/proxy" + endpoint).then((response) => {
                console.log(response)
            }).catch((error) => {
                console.log(error)
            });
            break;
        case "POST":
            axios.post("/proxy" + endpoint, body, {
                headers: {
                    'Content-Type': 'application/json'
                }
            }).then((response) => {
                console.log(response)
            }).catch((error) => {
                console.log(error)
            });
            break;
        case "PUT":
            axios.put("/proxy" + endpoint, body, {
                headers: {
                    'Content-Type': 'application/json'
                }
            }).then((response) => {
                console.log(response)
            }).catch((error) => {
                console.log(error)
            });
            break;
        case "DELETE":
            axios.delete("/proxy" + endpoint).then((response) => {
                console.log(response)
            }).catch((error) => {
                console.log(error)
            });
            break
        case "PATCH":
            axios.patch("/proxy" + endpoint, body, {
                headers: {
                    'Content-Type': 'application/json'
                }
            }).then((response) => {
                console.log(response)
            }).catch((error) => {
                console.log(error)
            });
            break;
        default:
            console.error("Invalid request type: " + requestType);
            break;
    }
}

function makeRiotRequest(requestType, endpoint, body) {
    let request = new Array();
    request.push(5);
    request.push(requestType);
    request.push(endpoint);
    request.push(body);
    console.log(request);
    send(request);
}

function manualLCURequest() {
    const methodFilter = document.getElementById('requestType');
    const endpoint = document.getElementById('requestEndpoint');
    const body = document.getElementById('requestBody');
    makeLCURequest(methodFilter.value, endpoint.value, body.value);
}

function manualRiotRequest() {
    console.log("Riot Request");
    const methodFilter = document.getElementById('riotRequestType');
    const endpoint = document.getElementById('riotRequestEndpoint');
    const body = document.getElementById('riotRequestBody');
    makeRiotRequest(methodFilter.value, endpoint.value, body.value);
}

function send(jsonArray) {
    let request = jsonArray;
    let jsonRequest = JSON.stringify(request);
    console.log("Sending: " + jsonRequest);
    socket.send(jsonRequest);
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
                send([3, 1, switchId, {"delay": delay}]);
            } else if (switchId === "AutoPickChamp") {
                var delayInput = document.getElementById("AutoPickChampDelay");
                var champIdInput = document.getElementById("AutoPickChampId");
                var delay = parseInt(delayInput.value);
                var champId = parseInt(champIdInput.value);
                send([3, 1, switchId, {"delay": delay, "championId": champId}]);
            }
        } else {
            send([3, 0, switchId, {}]);
        }
    }
}

function restartRiotClientUX() {
    makeLCURequest("POST", "/riotclient/kill-and-restart-ux", "");
}
