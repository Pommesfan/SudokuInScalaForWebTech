function discard_form() {
    let values = ""
    let i = 0
    while(document.getElementById("inlineCheckbox"+i+"_0") != null) {
        let atLeastOne = false
        for (let j = 0; j < 10; j++) {
            let check_box = document.getElementById("inlineCheckbox"+i+"_"+j)
            if (check_box.checked) {
                values += (j + " ")
                atLeastOne = true
            }
        }
        if (!atLeastOne) {
            return
        }
        i += 1
        values += ": "
    }
    //remove the separators at the and which are created in every iteration
    values = values.slice(0, values.length - 2)
    fetch('/discard', {
        method: 'POST',
        headers: {
            'Accept': 'application/json',
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({"cards": values})
    }).then(response => document.location.reload())
}

function no_discard() {
    fetch('/no_discard', {
        method: 'POST',
        headers: {
            'Accept': 'application/json',
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({})
    }).then(response => document.location.reload())
}
document.getElementById("submit_discard").onclick = discard_form
document.getElementById("no_discard").onclick = no_discard