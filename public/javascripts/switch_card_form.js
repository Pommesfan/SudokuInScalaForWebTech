function switch_card(mode, index) {
    fetch('/post_switch_cards', {
        method: 'POST',
        headers: {
            'Accept': 'application/json',
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ "mode": mode, "index": index })
    })
}

function new_card() {
    switch_card("new", 4)
}

function open_card() {
    switch_card("open", 4)
}

document.getElementById("btn_new_card").onclick = new_card
document.getElementById("btn_open_card").onclick = open_card