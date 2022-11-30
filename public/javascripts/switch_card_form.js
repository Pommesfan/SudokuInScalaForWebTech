function switch_card(mode) {
    let card_index = parseInt(document.querySelector('input[name="card_index"]:checked').value)
    fetch('/switch_cards', {
        method: 'POST',
        headers: {
            'Accept': 'application/json',
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ "mode": mode, "index": card_index})
    }).then(response => document.location.reload())
}

function new_card() {
    switch_card("new")
}

function open_card() {
    switch_card("open")
}

document.getElementById("btn_new_card").onclick = new_card
document.getElementById("btn_open_card").onclick = open_card