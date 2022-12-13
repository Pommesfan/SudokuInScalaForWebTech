function inject() {
    let card_to_inject = parseInt(document.querySelector('input[name="card_index"]:checked').value)
    let target = document.querySelector('input[name="inject_to"]:checked').value.split("_")
    let player_to = parseInt(target[0])
    let group_to = parseInt(target[1])
    let position_to = target[2]

    websocket.send(JSON.stringify({
        "cmd": "inject",
        "card_to_inject": card_to_inject,
        "player_to": player_to,
        "group_to": group_to,
        "position_to": position_to
    }))
}

function no_inject() {
    websocket.send(JSON.stringify({"cmd": "no_inject"}))
}

document.getElementById("btn_inject").onclick = inject
document.getElementById("btn_no_inject").onclick = no_inject
