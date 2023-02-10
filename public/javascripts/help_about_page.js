document.getElementById("back_from_about_or_help").onclick =

function back_to_home_or_game() {
    if(sessionStorage.getItem('thisPlayer') == null) {
        document.location.replace("/")
    } else {
        document.location.replace("/game")
    }
}