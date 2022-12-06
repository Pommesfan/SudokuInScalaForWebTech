let alert_text = document.getElementById('hidden_text_alert')
if (alert_text != null) {
    alert(alert_text.innerText)
}

function show_player_cards(cards, show_checkboxes) {
    let html = ""
    for (let i = 0; i < cards.length; i++) {
        html += `
            <div className="row">
                <div className="col-7">
                    ${i}: ${cardToString(cards[i])}
                </div>
                ${checkboxes(i, 2, show_checkboxes)}
            </div>`
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

function getFile(name) {
    let data = "";
    jQuery.get("/assets/" + name).then(response => {
        data = response
    })
    return data
}

const switchCardForm = getFile("javascripts/utils.js")

function update(data) {
    if (data['event'] == "GoToDiscardEvent") {
        document.getElementById("playerCards").innerHTML = show_player_cards(data['cardStash'], true)
    } else {
        document.location.reload()
    }
}
