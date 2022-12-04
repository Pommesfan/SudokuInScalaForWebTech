$( document ).ready(function() {
    console.log( "Document is ready." );
    connectWebSocket()
});

function submit_player_names() {
    let names = []
    for(let i = 1; i <= 4; i++) {
        let name = document.getElementById("p" + i).value
        if(name.length != 0) {
            names.push(name)
        }
    }
    if(names.length < 2) {
        alert("Mindestens zwei Spieler eingeben")
    } else {
        post_data('/set_players', { "length":names.length, "names": names})
    }
}

document.getElementById("submit_player_names").onclick = submit_player_names

function connectWebSocket() {
    console.log("Connecting to Websocket");
    var websocket = new WebSocket("ws://localhost:9000/websocket");
    console.log("Connected to Websocket");

    websocket.onopen = function(event) {
        console.log("Trying to connect to Server");
        websocket.send("Trying to connect to Server");
    }

    websocket.onclose = function () {
        console.log('Connection Closed!');
    };

    websocket.onerror = function (error) {
        console.log('Error Occured: ' + error);
    };

    websocket.onmessage = function (e) {
        if (typeof e.data === "string") {
            console.log('String message received: ' + e.data);
        }
        else if (e.data instanceof ArrayBuffer) {
            console.log('ArrayBuffer received: ' + e.data);
        }
        else if (e.data instanceof Blob) {
            console.log('Blob received: ' + e.data);
        }
    };
}