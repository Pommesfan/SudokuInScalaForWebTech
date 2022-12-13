function get_player_name(idx) {
    return sessionStorage.getItem("player_" + idx)
}

function show_player_cards(cards, show_checkboxes, show_radio_buttons, cardGroupSize) {
    let html = ""
    for (let i = 0; i < cards.length; i++) {
        html += `
            <div class="row">
                <div class="col-7">
                    ${i}: ${cardToString(cards[i])}
                </div>
                ${checkboxes(i, cardGroupSize, show_checkboxes)}
                ${radio_buttons_player_cards(i, show_radio_buttons)}
            </div>`
        html += "\n<br>\n"
    }
    return html
}

function checkboxes(i, cardGroupSize, show_checkboxes) {
    let html = ""
    if (show_checkboxes) {
        for (let j = 0; j < cardGroupSize; j++) {
            html += `
            <div class="col-1">
                <div class="form-check form-check-inline">
                    <input class="form-check-input" type="checkbox" id=${"inlineCheckbox" + j + "_" + i}>
                </div>
            </div>`
            html += "\n"
        }
    }
    return html
}

function radio_buttons_player_cards(i, show_radio_buttons) {
    let html = ""
    if (show_radio_buttons) {
        html += `
            <div class="col-1">
                <div class="form-check form-check-inline">
                    <input id=${"selected_player_card_" + i} class="form-check-input" type="radio" name="card_index" value=${i}>
                </div>
            </div>`
    }
    return html + "\n"
}

function discarded_cards(cardStashes, show_radio_buttons) {
    let html = ""
    for (let i = 0; i < cardStashes.length; i++) {
        html += get_player_name(i) + `:<br>`
        let cardGroups = cardStashes[i]
        if (cardGroups == null) {
            html += `Keine Karten<br>` + "\n"
        } else {
            for(let j = 0; j < cardGroups.length; j++) {
                let cards = cardGroups[j]
                if (show_radio_buttons) {
                    html += `<input type="radio" name="inject_to" value=${i + "_" + j + "_FRONT"}>` + "<br>\n"
                }
                cards.forEach(c =>
                    html += cardToString(c) + "<br>\n"
                )
                if (show_radio_buttons) {
                    html += `<input type="radio" name="inject_to" value=${i + "_" + j + "_AFTER"}>` + "\n"
                }
                html += `<br>`
            }
        }
    }
    return html
}

function new_round_message(data) {
    let s = "Neue Runde:"
    const errorPoints = data['errorPoints']
    const number_of_phase = data['numberOfPhase']
    const phase_description = data['phaseDescription']
    for(let i = 0; i < sessionStorage.getItem("number_of_players"); i++) {
        s += ("\n" + get_player_name(i) + ": " + errorPoints[i] + " Fehlerpunkte; Phase: " + number_of_phase[i] + ": " + phase_description[i])
    }
    return s
}

function turnEnded(data) {
    let new_player_cards = show_player_cards(data['cardStash'], false, true, data['card_group_size'])
    let new_discarded_cards = discarded_cards(data['discardedStash'], false)
    document.getElementById("playerCards").innerHTML = new_player_cards
    document.getElementById("discardedCards").innerHTML = new_discarded_cards
    document.getElementById("newCard").innerHTML = cardToString(data['newCard'])
    document.getElementById("openCard").innerHTML = cardToString(data['openCard'])
    document.getElementById("currentPlayer").innerHTML = get_player_name(data['activePlayer'])
    document.getElementById("inputFormSwitch").hidden = false
    document.getElementById("inputFormDiscard").hidden = true
    document.getElementById("inputFormInject").hidden = true
}

function goToDiscard(data) {
    let new_html = show_player_cards(data['cardStash'], true, false, data['card_group_size'])
    document.getElementById("playerCards").innerHTML = new_html
    document.getElementById("inputFormSwitch").hidden = true
    document.getElementById("inputFormDiscard").hidden = false
}

function goToInject(data) {
    document.getElementById("inputFormSwitch").hidden = true
    document.getElementById("inputFormInject").hidden = false
    let new_player_cards = show_player_cards(data['cardStash'], false, true, 0)
    let new_discarded_cards = discarded_cards(data['discardedStash'], true)
    document.getElementById("playerCards").innerHTML = new_player_cards
    document.getElementById("discardedCards").innerHTML = new_discarded_cards
}

function update(data) {
    let event = data['event']
    if (event == "GoToDiscardEvent") {
        goToDiscard(data)
    } else if(event == "NewRoundEvent") {
        turnEnded(data)
        alert(new_round_message(data))
    } else if(data['event'] == "TurnEndedEvent") {
        turnEnded(data)
    } else if (event == "GoToInjectEvent") {
        goToInject(data)
    } else if (event == "GameStartedEvent") {
        turnEnded(data)
        alert(new_round_message(data))
    }
}

function connectWebSocket() {
    console.log("Connecting to Websocket");
    var websocket = new WebSocket("ws://localhost:9000/websocket");
    console.log("Connected to Websocket");

    websocket.onopen = function(event) {
        console.log("Trying to connect to Server");
        websocket.send(JSON.stringify({"cmd": "loginPlayer", "loggedInPlayer": sessionStorage.getItem("thisPlayer")}))
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

$( document ).ready(function() {
    console.log( "Document is ready." );
    websocket = connectWebSocket()
});
