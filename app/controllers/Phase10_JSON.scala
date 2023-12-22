package controllers

import model.{Card, RoundData, TurnData}
import play.api.libs.json
import play.api.libs.json.{JsArray, JsBoolean, JsNull, JsNumber, JsObject, JsString}
import utils.{DoInjectEvent, GameEndedEvent}

object Phase10_JSON {
  def json_full_load(fullLoad: Boolean, r: RoundData, t: TurnData, referringPlayer: Int, players: List[String]): JsObject = if(fullLoad)
    JsObject(Seq(
      "fullLoad" -> JsBoolean(true),
      "numberOfPhase" -> JsArray(r.validators.map(v => JsNumber(v.getNumberOfPhase()))),
      "phaseDescription" -> JsArray(r.validators.map(v => JsString(v.description))),
      "card_group_size" -> JsNumber(r.validators(referringPlayer).getNumberOfInputs().size),
      "players" -> JsArray(players.map(s => JsString(s))),
      "numberOfPlayers" -> JsNumber(players.size),
      cardStashCurrentPlayer(t, referringPlayer),
      discardedStash(t)
    ))
  else
    JsObject(Seq("fullLoad" -> JsBoolean(false)))

  def json_player_has_injected(doInjectEvent: DoInjectEvent, t:TurnData): JsObject = JsObject(Seq(
    "event" -> JsString("PlayerHasInjected"),
    "playerTo" -> JsNumber(doInjectEvent.receiving_player),
    "stashTo" -> JsNumber(doInjectEvent.stashIndex),
    "position" -> json.JsNumber(doInjectEvent.position),
    "card" -> cardToJSon(t.playerCardDeck.cards(t.current_player)(doInjectEvent.cardIndex))
  ))
  def json_player_has_discarded(referringPlayer: Int, t: TurnData): JsObject = JsObject(Seq(
    "event" -> JsString("PlayerHasDiscarded"),
    "player" -> JsNumber(referringPlayer),
    "cards" -> JsArray(t.discardedCardDeck.cards(referringPlayer).get.map(cs =>
      JsArray(cs.map(c => cardToJSon(c)))
    ))))

  def json_newGame(r:RoundData, players: List[String], t:TurnData, referringPlayer: Int): JsObject = JsObject(Seq(
    "event" -> JsString("NewGameEvent"),
    "numberOfPhase" -> JsArray(r.validators.map(v => JsNumber(v.getNumberOfPhase()))),
    "phaseDescription" -> JsArray(r.validators.map(v => JsString(v.description))),
    "players" -> JsArray(players.map(s => JsString(s))),
    "numberOfPlayers" -> JsNumber(players.size),
    "card_group_size" -> JsNumber(r.validators(referringPlayer).getNumberOfInputs().size),
    cardStashCurrentPlayer(t, referringPlayer)))

  def json_gameEnded(e: GameEndedEvent): JsObject = JsObject(Seq(
    "event" -> JsString("GameEndedEvent"),
    "winningPlayer" -> JsString(e.winningPlayer),
    "players" -> JsArray(e.players.map(p => JsString(p))),
    "phases" -> JsArray(e.phases.map(n => JsNumber(n))),
    "errorPoints" -> JsArray(e.errorPoints.map(n => JsNumber(n)))
  ))

  def json_newRound(r:RoundData, t:TurnData, referringPlayer: Int): JsObject = JsObject(Seq(
    "event" -> JsString("NewRoundEvent"),
    "numberOfPhase" -> JsArray(r.validators.map(v => JsNumber(v.getNumberOfPhase()))),
    "phaseDescription" -> JsArray(r.validators.map(v => JsString(v.description))),
    "errorPoints" -> JsArray(r.errorPoints.map(n => JsNumber(n))),
    "card_group_size" -> JsNumber(r.validators(referringPlayer).getNumberOfInputs().size),
    cardStashCurrentPlayer(t, referringPlayer)))

  def json_playersTurn(t: TurnData, referringPlayer:Int, newCard:Card): JsObject = JsObject(Seq(
    "event" -> JsString("PlayersTurnEvent"),
    "activePlayer" -> JsNumber(referringPlayer),
    "newCard" -> cardToJSon(newCard),
    "openCard" -> cardToJSon(t.openCard)))

  def json_turnEnded(success: Boolean): JsObject = JsObject(Seq(
    "event" -> JsString("TurnEndedEvent"),
    "success" -> JsBoolean(success)))

  def json_discarded(referringPlayer:Int): JsObject = JsObject(Seq(
    "event" -> JsString("GoToDiscardEvent"),
    "activePlayer" -> JsNumber(referringPlayer)))

  def json_inject(referringPlayer:Int): JsObject = JsObject(Seq(
    "event" -> JsString("GoToInjectEvent"),
    "activePlayer" -> JsNumber(referringPlayer)))

  private def discardedStash(t:TurnData): (String, JsArray) = {
    "discardedStash" -> JsArray(
      t.discardedCardDeck.cards.map(o =>
        if (o.nonEmpty)
          JsArray(o.get.map(cs =>
            JsArray(cs.map(c =>
              cardToJSon(c)
            ))
          ))
        else
          JsNull
      )
    )
  }

  private def cardStashCurrentPlayer(t: TurnData, referring_player: Int): (String, JsArray) = {
    "cardStash" -> JsArray(
      t.playerCardDeck.cards(referring_player).map(c => cardToJSon(c))
    )
  }

  private def cardToJSon(c: Card) = JsObject(Seq(
    "color" -> JsNumber(c.color),
    "value" -> JsNumber(c.value)
  ))
}
