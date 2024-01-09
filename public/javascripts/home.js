function submit_player_names() {
    let names = []
    for(let i = 1; i <= 4; i++) {
        let name = document.getElementById("p" + i).value
        if(name.length != 0) {
            names.push(name)
        }
    }
    sessionStorage.setItem("thisPlayer", names[0])
    if(names.length < 2) {
        alert("Mindestens zwei Spieler eingeben")
    } else {
        post_data('/set_players', { "length":names.length, "names": names})
    }
}

function submit_admission() {
    sessionStorage.setItem("thisPlayer", document.getElementById("admission_name").value)
    sessionStorage.setItem(str_teamID, document.getElementById("team_id").value)
    document.location.replace("/game")
}

function update(data) {
    let team_id = data[str_teamID]
    sessionStorage.setItem(str_teamID, team_id)
    alert("Team-ID:\n" + team_id)
    document.location.replace("/game")
}

document.getElementById("submit_player_names").onclick = submit_player_names
document.getElementById("submit_admission").onclick = submit_admission
