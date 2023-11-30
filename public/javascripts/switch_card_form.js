function switch_card(mode) {
    let qs = document.querySelector('input[name="card_index"]:checked')
    if(qs == null)
        return
    let card_index = parseInt(qs.value)
    websocket.send(JSON.stringify({"cmd": "switch_cards", "mode": mode, "index": card_index}))
    selectedPlayerCard = card_index
    switchMode = mode
}

function new_card() {
    switch_card("new")
}

function open_card() {
    switch_card("open")
}

document.getElementById("btn_new_card").onclick = new_card
document.getElementById("btn_open_card").onclick = open_card