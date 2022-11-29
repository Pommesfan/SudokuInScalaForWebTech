function inject() {
}

function no_inject() {
    fetch('/post_no_inject', {
        method: 'POST',
        headers: {
            'Accept': 'application/json',
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({})
    }).then(response => document.location.reload())
}

document.getElementById("btn_inject").onclick = inject
document.getElementById("btn_no_inject").onclick = no_inject