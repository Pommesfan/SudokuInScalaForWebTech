function get_player_name(idx) {
    return sessionStorage.getItem("player_" + idx)
}

function show_player_cards(cards, show_checkboxes, show_radio_buttons, cardGroupSize) {
    let playerCardsDiv = document.getElementById("playerCards")
    playerCardsDiv.innerHTML = ""
    let rowDiv = document.createElement("div")
    rowDiv.setAttribute("class", "row")
    for (let i = 0; i < cards.length; i++) {
        let colDiv = document.createElement("div")
        colDiv.setAttribute("class", "col")
        colDiv.appendChild(drawCard(cards[i]['value'], cards[i]['color']))
        if(show_radio_buttons) {
            colDiv.appendChild(radio_buttons_player_cards(i))
        }
        if(show_checkboxes) {
            checkboxes(i, cardGroupSize, colDiv)
        }
        rowDiv.appendChild(colDiv)
    }
    playerCardsDiv.appendChild(rowDiv)
}

function checkboxes(i, cardGroupSize, colDiv) {
    for (let j = 0; j < cardGroupSize; j++) {
        let checkbox = document.createElement("input")
        checkbox.setAttribute("class", "form-check-input")
        checkbox.type = "checkbox"
        checkbox.id = "inlineCheckbox" + j + "_" + i
        colDiv.appendChild(checkbox)
    }
}

function radio_buttons_player_cards(i) {
    let radioButton = document.createElement("input")
    radioButton.id = "selected_player_card_" + i
    radioButton.type="radio"
    radioButton.name="card_index"
    radioButton.value=i
    return radioButton
}

function radio_buttons_discarded_Cards(i,j,position) {
    let radioButton = document.createElement("input")
    radioButton.type = "radio"
    radioButton.name = "inject_to"
    radioButton.value = i + "_" + j + "_" + position
    return radioButton
}

function discarded_cards(cardStashes, show_radio_buttons) {
    let discardedCardsDiv = document.getElementById("discardedCards")
    discardedCardsDiv.innerHTML = ""
    for (let i = 0; i < cardStashes.length; i++) {
        let textView = document.createElement("p")
        textView.innerHTML = get_player_name(i)
        discardedCardsDiv.appendChild(textView)
        let cardGroups = cardStashes[i]
        if (cardGroups == null) {
            let textView2 = document.createElement("p")
            textView2.innerHTML = "Keine Karten"
            discardedCardsDiv.appendChild(textView2)
        } else {
            for(let j = 0; j < cardGroups.length; j++) {
                let cards = cardGroups[j]

                if(show_radio_buttons) {
                    discardedCardsDiv.appendChild(radio_buttons_discarded_Cards(i,j,"AFTER"))
                }

                for (let c in cards) {
                    let card = cards[c]
                    let cardView = drawCard(card['value'], card['color'])
                    discardedCardsDiv.appendChild(cardView)
                }

                if(show_radio_buttons) {
                    discardedCardsDiv.appendChild(radio_buttons_discarded_Cards(i,j,"AFTER"))
                }
            }
        }
    }
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
    show_player_cards(data['cardStash'], false, true, data['card_group_size'])
    document.getElementById("inputFormSwitch").hidden = true
    document.getElementById("inputFormDiscard").hidden = true
    document.getElementById("inputFormInject").hidden = true
}

function playersTurn(data) {
    show_player_cards(data['cardStash'], false, true, data['card_group_size'])
    discarded_cards(data['discardedStash'], false)

    let newCard = data['newCard']
    let openCard = data['openCard']
    let newCardDiv = document.getElementById("newCard")
    let openCardDiv = document.getElementById("openCard")
    newCardDiv.innerHTML = ""
    openCardDiv.innerHTML = ""
    newCardDiv.appendChild(drawCard(newCard['value'], newCard['color']))
    openCardDiv.appendChild(drawCard(openCard['value'], openCard['color']))

    document.getElementById("currentPlayer").innerHTML = get_player_name(data['activePlayer'])
    document.getElementById("inputFormSwitch").hidden = false
    document.getElementById("inputFormDiscard").hidden = true
    document.getElementById("inputFormInject").hidden = true
}

function goToDiscard(data) {
    show_player_cards(data['cardStash'], true, false, data['card_group_size'])
    document.getElementById("inputFormSwitch").hidden = true
    document.getElementById("inputFormDiscard").hidden = false
}

function goToInject(data) {
    document.getElementById("inputFormSwitch").hidden = true
    document.getElementById("inputFormInject").hidden = false
    show_player_cards(data['cardStash'], false, true, 0)
    discarded_cards(data['discardedStash'], true)
    document.getElementById("discardedCards").innerHTML = new_discarded_cards
}

function update(data) {
    let event = data['event']
    if(event == "sendPlayerNames") {
        let names = data['players']
        for(let i = 0; i < data['length']; i++) {
            sessionStorage.setItem("player_" + i, names[i])
        }
    }
    if (event == "GoToDiscardEvent") {
        goToDiscard(data)
    } else if(event == "NewRoundEvent") {
        turnEnded(data)
        alert(new_round_message(data))
    } else if(data['event'] == "TurnEndedEvent") {
        turnEnded(data)
    } else if(data['event'] == "PlayersTurnEvent") {
        alert("Du bist dran!")
        playersTurn(data)
    } else if (event == "GoToInjectEvent") {
        goToInject(data)
    } else if (event == "GameStartedEvent") {
        playersTurn(data)
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
