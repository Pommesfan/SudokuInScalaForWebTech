function discard() {
    discardedCardIndices = []
    let values = ""
    let i = 0

    while(document.getElementById("inlineCheckbox"+i+"_0") != null) {
        let indices_in_group = [] //for local changes
        let atLeastOne = false
        for (let j = 0; j < 10; j++) {
            let check_box = document.getElementById("inlineCheckbox"+i+"_"+j)
            if (check_box.checked) {
                values += (j + " ")
                indices_in_group.push(j)
                atLeastOne = true
            }
        }
        if (!atLeastOne) {
            return
        }
        i += 1
        values += ": "
        discardedCardIndices.push(indices_in_group) //for local changes
    }
    //remove the separators at the and which are created in every iteration
    values = values.slice(0, values.length - 2)
    websocket.send(JSON.stringify({"cmd": "discard", "cards": values}))
}

function no_discard() {
    websocket.send(JSON.stringify({"cmd": "no_discard"}))
}
document.getElementById("submit_discard").onclick = discard
document.getElementById("no_discard").onclick = no_discard