function post_data(route, json) {
    fetch(route, {
        method: 'POST',
        headers: {
            'Accept': 'application/json',
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(json)
    }).then(response => response.json().then(data => update(data)))
}

function cardToString(c) {
    function getColor() {
        switch (c['color']) {
            case 1:
                return "Rot"
            case 2:
                return "Gelb"
            case 3:
                return "Blau"
            case 4:
                return "Grün"
        }
    }
    return "Farbe: " + getColor() + "; Wert = " + c['value']
}