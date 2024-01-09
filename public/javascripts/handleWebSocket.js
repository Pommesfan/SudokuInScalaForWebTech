function connectWebSocket() {
    console.log("Connecting to Websocket");
    var websocket = new WebSocket("ws://" + location.host + "/websocket");
    console.log("Connected to Websocket");

    websocket.onopen = function(event) {
        console.log("Trying to connect to Server");
        websocket.send(JSON.stringify({
            "cmd": "loginPlayer",
            "loggedInPlayer": sessionStorage.getItem(str_thisPlayer),
            "team_id": sessionStorage.getItem(str_teamID)
        }))
        websocket.send(JSON.stringify({"cmd": "getStatus"}))
    }

    websocket.onclose = function () {
        console.log('Connection Closed!');
    };

    websocket.onerror = function (error) {
        console.log('Error Occured: ' + error);
    };

    websocket.onmessage = function (e) {
        if (typeof e.data === "string") {
            let js = JSON.parse(e.data)
            update(js)
        }
        else if (e.data instanceof ArrayBuffer) {
            console.log('ArrayBuffer received: ' + e.data);
            alert('ArrayBuffer received: ' + e.data)
        }
        else if (e.data instanceof Blob) {
            console.log('Blob received: ' + e.data);
            alert('Blob received: ' + e.data)
        }
    };
    return websocket
}