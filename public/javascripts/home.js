function submit_player_names() {
    let names = []
    for(let i = 1; i <= 4; i++) {
        let name = document.getElementById("p" + i).value
        if(name.length != 0) {
            names.push(name)
        }
    }
    if(names.length < 2) {
        alert("Mindestens zwei Spieler eingeben")
    } else {
        post_data('/set_players', { "length":names.length, "names": names})
    }
}

document.getElementById("submit_player_names").onclick = submit_player_names