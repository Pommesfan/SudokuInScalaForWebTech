function submit_player_names() {
    let names = []
    for(let i = 1; i <= 4; i++) {
        let name = document.getElementById("p" + i).value
        if(name.length != 0) {
            names.push(name)
        }
    }
    for(let i = 0; i < names.length; i++) {
        sessionStorage.setItem("player_" + i, names[i])
    }
    sessionStorage.setItem("number_of_players", names.length)
    sessionStorage.setItem("thisPlayer", names[0])
    if(names.length < 2) {
        alert("Mindestens zwei Spieler eingeben")
    } else {
        post_data('/set_players', { "length":names.length, "names": names})
    }
}

function submit_admission() {
    sessionStorage.setItem("thisPlayer", document.getElementById("admission_name").value)
    update()
}

function update() {
    document.location.replace("/game")
}

document.getElementById("submit_player_names").onclick = submit_player_names
document.getElementById("submit_admission").onclick = submit_admission
