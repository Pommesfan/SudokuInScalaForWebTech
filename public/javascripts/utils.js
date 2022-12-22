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

function drawCard(number, color) {
    var c = document.createElement("canvas")
    c.width = 100
    c.height = 150
    var ctx = c.getContext("2d");

    function wave(ax,ay,bx,by,cx,cy,dx,dy,w) {
        ctx.beginPath()
        ctx.moveTo(bx,by)
        ctx.bezierCurveTo(cx/3, by+w, cx/3*2, by, cx, cy-w)
        ctx.lineTo(dx, dy)
        ctx.lineTo(ax,ay)
        ctx.closePath()
        ctx.fill()
    }

    function cutEdge(ax, ay, bx, by, cx, cy) {
        ctx.fillStyle = "white"
        ctx.beginPath()
        ctx.moveTo(ax, ay)
        ctx.lineTo(bx, by)
        ctx.lineTo(cx, cy)
        ctx.arcTo(bx, by, ax, ay, 20)
        ctx.closePath()
        ctx.fill()
    }

    function shadowColor() {
        switch (color) {
            case "red": return "orangered"
            case "yellow": return "lemonchiffon"
            case "blue": return "deepskyblue"
            case "green": return "lawngreen"
        }
        return ""
    }

    function digitColor() {
        switch (color) {
            case "red": return "firebrick"
            case "yellow": return "gold"
            case "blue": return "darkblue"
            case "green": return "darkgreen"
        }
        return ""
    }

    ctx.fillStyle = "ivory"
    ctx.fillRect(0,0,100,150)
    ctx.fillStyle = color

    ctx.shadowColor = shadowColor();
    ctx.shadowBlur = 15;
    //upper wave
    wave(0,0,0,30,100,30, 100, 0, 15)
    //bottom wave
    wave(0,150, 0,120,100,120,100,150, 15)
    ctx.shadowBlur = 0;

    ctx.fillStyle = digitColor()

    ctx.font = "64px serif"
    if(number < 10) {
        ctx.fillText(number.toString(), 32,100)
    } else {
        ctx.fillText(number.toString(), 10,100)
    }

    ctx.fillStyle = "white"
    cutEdge(0,25, 0,0, 25,0)
    cutEdge(100,25,100,0, 75, 0)
    cutEdge(0,125,0,150,25,150)
    cutEdge(100, 125, 100, 150, 75, 150)
    return c
}