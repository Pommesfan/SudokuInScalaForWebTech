function switch_card(mode) {
    let index = -1
    for(let i = 0; i < 10; i++) {
        let radioButton = document.getElementById("selected_player_card_" + i)
        if(radioButton.checked) {
            index = i
            break
        }
    }
    if (index < 0) {
        return
    }
    fetch('/post_switch_cards', {
        method: 'POST',
        headers: {
            'Accept': 'application/json',
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ "mode": mode, "index": index })
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