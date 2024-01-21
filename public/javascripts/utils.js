function get_player_name(idx) {
    return sessionStorage.getItem("player_" + idx)
}

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

function drawCard(number, color) {
    function getColor() {
        switch (color) {
            case 1:
                return "red"
            case 2:
                return "yellow"
            case 3:
                return "blue"
            case 4:
                return "green"
        }
    }
    if (typeof color == 'number') {
        color = getColor(color)
    }
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

function inverted_idx_list(n, indices) {
    let res = []
    for(let i = 0; i < n; i++) {
        if(!indices.includes(i)) {
            res.push(i)
        }
    }
    return res
}

function map_cards(playerCardIndices, cards) {
    let res = []
    for(let i = 0; i < playerCardIndices.length; i++) {
        res.push(cards[playerCardIndices[i]])
    }
    return res
}

function sort_cards(stash) {
    stash.sort(function(a,b) {return a.value >= b.value})
    for(let i = 0; i < stash.length - 1; i++) {
        let j = i+1
        if(stash[i].value + 1 != (stash[j].value)) {
            let a = stash.slice(0,j)
            let b = stash.slice(j,7)
            return b.concat(a)
        }
    }
    return stash
}

class InjectCardData {
    constructor(playerCard, playerTo, groupTo, positionTo) {
        this.playerCard = playerCard
        this.playerTo = playerTo
        this.groupTo = groupTo
        this.positionTo = positionTo
    }
}

const INJECT_TO_FRONT = 1
const INJECT_AFTER = 2
const NEW_CARD = 1
const OPEN_CARD = 2
const str_numberOfPlayers = "numberOfPlayers"
const str_thisPlayer = "thisPlayer"
const str_thisPlayerIdx = "thisPlayerIdx"
const str_teamID = "team_id"
const str_cardStash = "cardStash"
const str_discardedStash = "discardedStash"
const str_card_group_size = "card_group_size"
const str_errorPoints = "errorPoints"
const str_phaseDescription = "phaseDescription"
const str_numberOfPhase = "numberOfPhase"
const str_sortCards = "sortCards"