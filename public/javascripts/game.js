const inputFormSwitch = document.getElementById("inputFormSwitch")
const inputFormDiscard = document.getElementById("inputFormDiscard")
const inputFormInject = document.getElementById("inputFormInject")
const newCardDiv = document.getElementById("newCardDiv")
const openCardDiv = document.getElementById("openCardDiv")
const newCardP = document.getElementById("newCardP")
const openCardP = document.getElementById("openCardP")
const playerCardsDiv = document.getElementById("playerCards")
const discardedCardsDiv = document.getElementById("discardedCards")
const currentPlayer = document.getElementById("currentPlayer")

function get_player_name(idx) {
    return sessionStorage.getItem("player_" + idx)
}

function show_player_cards(cards, show_checkboxes, show_radio_buttons, cardGroupSize) {
    playerCardsDiv.innerHTML = ""
    let rowDiv = document.createElement("div")
    rowDiv.setAttribute("class", "row")
    for (let i = 0; i < cards.length; i++) {
        let colDiv = document.createElement("div")
        colDiv.setAttribute("class", "col")
        colDiv.appendChild(drawCard(cards[i]['value'], cards[i]['color']))
        colDiv.appendChild(document.createElement("br"))
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
                let col = document.createElement('div')
                col.setAttribute("class", "col")
                if(show_radio_buttons) {
                    col.appendChild(radio_buttons_discarded_Cards(i,j,"FRONT"))
                }

                for (let c in cards) {
                    let card = cards[c]
                    let cardView = drawCard(card['value'], card['color'])
                    col.appendChild(cardView)
                }

                if(show_radio_buttons) {
                    col.appendChild(radio_buttons_discarded_Cards(i,j,"AFTER"))
                }
                discardedCardsDiv.appendChild(col)
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
    show_player_cards(data['cardStash'], false, false, data['card_group_size'])
    discarded_cards(data['discardedStash'], false)
    inputFormSwitch.hidden = true
    inputFormDiscard.hidden = true
    inputFormInject.hidden = true
    newCardDiv.hidden = true
    openCardDiv.hidden = true
}

function playersTurn(data) {
    show_player_cards(data['cardStash'], false, true, data['card_group_size'])
    discarded_cards(data['discardedStash'], false)

    let newCard = data['newCard']
    let openCard = data['openCard']
    newCardP.innerHTML = ""
    openCardP.innerHTML = ""
    newCardP.appendChild(drawCard(newCard['value'], newCard['color']))
    openCardP.appendChild(drawCard(openCard['value'], openCard['color']))

    currentPlayer.innerHTML = get_player_name(data['activePlayer'])
    inputFormSwitch.hidden = false
    inputFormDiscard.hidden = true
    inputFormInject.hidden = true
    newCardDiv.hidden = false
    openCardDiv.hidden = false
}

function goToDiscard(data) {
    show_player_cards(data['cardStash'], true, false, data['card_group_size'])
    inputFormSwitch.hidden = true
    inputFormDiscard.hidden = false
    newCardDiv.hidden = true
    openCardDiv.hidden = true
}

function goToInject(data) {
    inputFormSwitch.hidden = true
    inputFormInject.hidden = false
    newCardDiv.hidden = true
    openCardDiv.hidden = true
    show_player_cards(data['cardStash'], false, true, 0)
    discarded_cards(data['discardedStash'], true)
}

function newGame(data) {
    let msg = "Neues Spiel\nPhase " + data['numberOfPhase'] + ": " + data['phaseDescription'] + "\n\nSpieler:"

    let names = data['players']
    const len = data['numberOfPlayers']
    for(let i = 0; i < len; i++) {
        sessionStorage.setItem("player_" + i, names[i])
        msg += "\n" + names[i]
    }
    sessionStorage.setItem("number_of_players", len)

    alert(msg)
}

function gameEnded(data) {
    let msg = "Spieler " + data['winningPlayer'] + " hat gewonnen\n"

    const length = sessionStorage.getItem("number_of_players")
    const phases = data['phases']
    const errorPoints = data['errorPoints']

    for(let i = 0; i < length; i++) {
        const player = sessionStorage.getItem("player_" + i)
        msg += "\n" + player + ": Phase " + phases[i] + "; " + errorPoints[i] + " Fehlerpunkte"
    }

    alert(msg)
    sessionStorage.clear()
    document.location.replace("/")
}

function update(data) {
    let event = data['event']
    if (event == "GoToDiscardEvent") {
        goToDiscard(data)
    } else if(event == "NewRoundEvent") {
        alert(new_round_message(data))
    } else if(data['event'] == "TurnEndedEvent") {
        turnEnded(data)
    } else if(data['event'] == "PlayersTurnEvent") {
        alert("Du bist dran!")
        playersTurn(data)
    } else if (event == "GoToInjectEvent") {
        goToInject(data)
    }  else if (event == "NewGameEvent") {
        newGame(data)
    } else if(event == "GameEndedEvent") {
        gameEnded(data)
    }
}

function connectWebSocket() {
    console.log("Connecting to Websocket");
    var websocket = new WebSocket("ws://" + location.host + "/websocket");
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

if(sessionStorage.getItem("thisPlayer") == null) {
    document.location.replace("/")
}

$( document ).ready(function() {
    console.log( "Document is ready." );
    websocket = connectWebSocket()
});
