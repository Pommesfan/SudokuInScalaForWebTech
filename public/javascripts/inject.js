function inject() {
    let card_to_inject_qs = document.querySelector('input[name="card_index"]:checked')
    let target_qs = document.querySelector('input[name="inject_to"]:checked')

    if(card_to_inject_qs == null || target_qs == null)
        return

    let card_to_inject = parseInt(card_to_inject_qs.value)
    let target = target_qs.value.split("_")
    let player_to = parseInt(target[0])
    let group_to = parseInt(target[1])
    let position_to = parseInt(target[2])

    injectTo = new InjectCardData(card_to_inject, player_to, group_to, position_to)

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
