let alert_text = document.getElementById('hidden_text_alert')
if (alert_text != null) {
    alert(alert_text.innerText)
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
        html += i + `<br>`
        let cardGroups = cardStashes[i]
        if (cardGroups == null) {
            html += `Keine Karten<br>` + "\n"
        } else {
            for(let j = 0; j < cardGroups.length; j++) {
                let cards = cardGroups[j]
                if (show_radio_buttons) {
                    html += `<input type="radio" name="inject_to" value=${i.toString + "_" + j + "_FRONT"}>` + "<br>\n"
                }
                cards.forEach(c =>
                    html += cardToString(c) + "<br>\n"
                )
                if (show_radio_buttons) {
                    html += `<input type="radio" name="inject_to" value=${i.toString + "_" + j + "_AFTER"}>` + "\n"
                }
                html += `<br>`
            }
        }
    }
    return html
}
function update(data) {
    if (data['event'] == "GoToDiscardEvent") {
        let new_html = show_player_cards(data['cardStash'], true, false, data['card_group_size'])
        document.getElementById("playerCards").innerHTML = new_html
        document.getElementById("inputFormSwitch").hidden = true
        document.getElementById("inputFormDiscard").hidden = false
    } else if(data['event'] == "NewRoundEvent" || data['event'] == "TurnEndedEvent") {
        let new_player_cards = show_player_cards(data['cardStash'], false, true, data['card_group_size'])
        let new_discarded_cards = discarded_cards(data['discardedStash'], false)
        document.getElementById("playerCards").innerHTML = new_player_cards
        document.getElementById("discardedCards").innerHTML = new_discarded_cards
        document.getElementById("newCard").innerHTML = cardToString(data['newCard'])
        document.getElementById("openCard").innerHTML = cardToString(data['openCard'])
        document.getElementById("currentPlayer").innerHTML = data['activePlayer']
        document.getElementById("inputFormSwitch").hidden = false
        document.getElementById("inputFormDiscard").hidden = true
        document.getElementById("inputFormInject").hidden = true
    } else {
        document.location.reload()
    }
}
