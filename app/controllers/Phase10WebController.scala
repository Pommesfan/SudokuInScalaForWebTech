package controllers

import model.{Card, RoundData, TurnData}
import play.api.libs.json.{JsArray, JsNull, JsNumber, JsObject, JsString, JsValue, Json}

import javax.inject._
import play.api.mvc._
import utils.{DoCreatePlayerEvent, DoDiscardEvent, DoInjectEvent, DoNoDiscardEvent, DoNoInjectEvent, DoSwitchCardEvent, GameStartedEvent, GoToDiscardEvent, GoToInjectEvent, NewRoundEvent, Observer, OutputEvent, ProgramStartedEvent, TurnEndedEvent, Utils}
import views.TUI
import play.api.libs.streams.ActorFlow
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.actor._



@Singleton
class Phase10WebController @Inject()(cc: ControllerComponents) (implicit system: ActorSystem, mat: Materializer)  extends AbstractController(cc) with Observer {
  private var lastEvent: OutputEvent = new ProgramStartedEvent
  var c = new Controller
  val tui = new TUI(c)
  c.add(this)
  c.notifyObservers(new ProgramStartedEvent) //set correct state in TUI

  override def update(e: OutputEvent): String = {
    lastEvent = e
    ""
  }

  def help = Action {
    Ok(views.html.help())
  }

  def about = Action {
    Ok(views.html.about())
  }

  def home() = Action {
    Ok(views.html.home())
  }

  def phase10 = Action {
    Ok(views.html.game())
  }

  def set_players = Action { request =>
    val length = request.body.asInstanceOf[AnyContentAsJson].json.asInstanceOf[JsObject].value.get("length").get.toString().toInt
    val names = request.body.asInstanceOf[AnyContentAsJson].json.asInstanceOf[JsObject].value.get("names").get.result
    var l = List[String]()
    for(i <- 0 until length) {
      l = l :+ names(i).asInstanceOf[JsString].value
    }
    c.solve(new DoCreatePlayerEvent(l))
    Ok("{}")
  }

  def switch_cards(json: JsValue): Unit = {
    val mode = json("mode").asInstanceOf[JsString].value
    val index = json("index").asInstanceOf[JsNumber].value.toInt
    def mode_to_Int = if (mode == "new") Utils.NEW_CARD else if (mode == "open") Utils.OPENCARD else -1
    c.solve(new DoSwitchCardEvent(index, mode_to_Int))
  }

  def discard(json: JsValue): Unit = {
    val cards = json("cards").asInstanceOf[JsString].value
    val cards_sorted = sort_sequences(Utils.makeGroupedIndexList(cards))
    c.solve(new DoDiscardEvent(cards_sorted))
  }

  def sort_sequences(cards: List[List[Int]]): List[List[Int]] = {
    val g = c.getGameData
    def r = g._1
    def t = g._2

    val card_types = r.validators(t.current_player).getCardGroups()
    def playerCards = t.playerCardDeck.cards(t.current_player)

    cards.zipWithIndex.map { e =>
      def c = e._1
      def n = e._2
      if(card_types(n) == Utils.SEQUENCE) {
        val a = c.sortWith((c1,c2) => playerCards(c1).value < playerCards(c2).value)
        a
      } else {
        c
      }
    }
  }
  def no_discard(): Unit = {
    c.solve(new DoNoDiscardEvent)
  }

  def no_inject(): Unit = {
    c.solve(new DoNoInjectEvent)
  }

  def inject(json: JsValue): Unit = {
    val card_to_inject = json("card_to_inject").asInstanceOf[JsNumber].value.toInt
    val player_to = json("player_to").asInstanceOf[JsNumber].value.toInt
    val group_to = json("group_to").asInstanceOf[JsNumber].value.toInt
    val position_to = json("position_to").asInstanceOf[JsString].value
    def position_to_Int = if (position_to == "FRONT") Utils.INJECT_TO_FRONT else if (position_to == "AFTER") Utils.INJECT_AFTER else -1
    c.solve(new DoInjectEvent(player_to, card_to_inject, group_to, position_to_Int))
  }

  def reset(): Action[AnyContent] = {
    c = new Controller
    phase10
  }

  class PlayersTurnEvent(val newCard: Card) extends OutputEvent

  def process_user_input(cmd: String, json: JsValue, player: String): JsObject = {
    val g = c.getGameData
    val r = g._1
    val t = g._2
    val players = c.getPlayers()
    val current_player = t.current_player
    //block action of player who is not at turn
    if (player == players(t.current_player)) {
      cmd match {
        case "switch_cards" => switch_cards(json)
        case "discard" => discard(json)
        case "no_discard" => no_discard()
        case "inject" => inject(json)
        case "no_inject" => no_inject()
        case _ => ;
      }

      val new_g = c.getGameData
      val new_r = new_g._1
      val new_t = new_g._2

      val isTurnEnded = lastEvent.isInstanceOf[TurnEndedEvent]
      val isNewRound = lastEvent.isInstanceOf[NewRoundEvent]

      //alert to all by new round
      if(isNewRound) {
        val newRoundMsg = get_post_response(new_r, new_t, players, 0, lastEvent).toString()
        for (r <- webSocketReactors) {
          r.publish(newRoundMsg)
        }
      }
      //Turn Ended -> inform opponent
      if(isTurnEnded || isNewRound) {
        val next_player = new_t.current_player
        def newCard = if(isNewRound) lastEvent.asInstanceOf[NewRoundEvent].newCard
                      else lastEvent.asInstanceOf[TurnEndedEvent].newCard
        val eventToOpponent = new PlayersTurnEvent(newCard)
        val reactor = getReactor(players(next_player))
        reactor.get.publish(get_post_response(new_r, new_t, players, next_player, eventToOpponent).toString())
      }
      //By new round, send turn-ended back
      def eventToCaller =
        if(isNewRound)
          new TurnEndedEvent(lastEvent.asInstanceOf[NewRoundEvent].newCard)
        else
          lastEvent

      get_post_response(new_r,new_t, players, current_player, eventToCaller)
    } else {
      JsObject(Seq())
    }
  }

  def get_post_response(r: RoundData, t:TurnData, player: List[String], referring_player: Int, event: OutputEvent): JsObject = {
    event match {
      case e: GameStartedEvent => {
        val players = c.getPlayers()
        JsObject(Seq(
          "event" -> JsString("GameStartedEvent"),
          "players" -> JsArray(players.map(p => JsString(p))),
          "newCard" -> cardToJSon(e.newCard),
          "openCard" -> cardToJSon(t.openCard),
          "phaseDescription" -> JsArray(r.validators.map(v => JsString(v.description))),
          "numberOfPhase" -> JsArray(r.validators.map(v => JsNumber(v.getNumberOfPhase()))),
          "errorPoints" -> JsArray(r.errorPoints.map(n => JsNumber(n)).toSeq),
          "activePlayer" -> JsNumber(referring_player),
          cardStashCurrentPlayer(t, referring_player),
          discardedStash(t)
        ))
      }
      case e: NewRoundEvent => {
        JsObject(Seq(
          "event" -> JsString("NewRoundEvent"),
          "numberOfPhase" -> JsArray(r.validators.map(v => JsNumber(v.getNumberOfPhase())).toSeq),
          "phaseDescription" -> JsArray(r.validators.map(v => JsString(v.description))),
          "numberOfPhase" -> JsArray(r.validators.map(v => JsNumber(v.getNumberOfPhase()))),
          "errorPoints" -> JsArray(r.errorPoints.map(n => JsNumber(n)).toSeq),
          "activePlayer" -> JsNumber(referring_player),
        ))
      }
      case e: PlayersTurnEvent => {
        JsObject(Seq(
          "event" -> JsString("PlayersTurnEvent"),
          "activePlayer" -> JsNumber(referring_player),
          "newCard" -> cardToJSon(e.newCard),
          "openCard" -> cardToJSon(t.openCard),
          cardStashCurrentPlayer(t, referring_player),
          discardedStash(t)
        ))
      }
      case e: TurnEndedEvent => {
        JsObject(Seq(
          "event" -> JsString("TurnEndedEvent"),
          cardStashCurrentPlayer(t, referring_player),
          discardedStash(t)
        ))
      }
      case _: GoToDiscardEvent => {
        JsObject(Seq(
          "event" -> JsString("GoToDiscardEvent"),
          "activePlayer" -> JsNumber(referring_player),
          "card_group_size" -> JsNumber(r.validators(referring_player).getNumberOfInputs().size),
          cardStashCurrentPlayer(t, referring_player),
          discardedStash(t)
        ))
      }
      case _: GoToInjectEvent => {
        JsObject(Seq(
          "event" -> JsString("GoToInjectEvent"),
          "activePlayer" -> JsNumber(referring_player),
          cardStashCurrentPlayer(t, referring_player),
          discardedStash(t)
        ))
      }
      case _ => JsObject(Seq("" -> JsString("")))
    }
  }

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


  def socket = WebSocket.accept[String, String] { request =>
    ActorFlow.actorRef { out =>
      println("Connect received")
      MyWebSocketActor.props(out)
    }
  }

  def getReactor(player: String): Option[WebSocketReactor] = {
    for(r <- webSocketReactors) {
      if(r.name == player) {
        return Some(r)
      }
    }
    None
  }

  object MyWebSocketActor {
    def props(out: ActorRef) = {
      println("Object created")
      Props(new MyWebSocketActor(out))
    }
  }

  var webSocketReactors = List[WebSocketReactor]()
  abstract class WebSocketReactor() {
    var name = ""
    def publish(msg: String)
  }

  class MyWebSocketActor(out: ActorRef) extends Actor {
    println("Class created")
    private val reactor = new WebSocketReactor {
      override def publish(msg: String): Unit = sendJsonToClient(msg)
    }
    webSocketReactors = webSocketReactors :+ reactor

    def login_player(json: JsValue) = {
      reactor.name = json("loggedInPlayer").asInstanceOf[JsString].value

      val players = c.getPlayers()

      def playersToJsArray = JsArray(players.map(s => JsString(s)))

      sendJsonToClient(JsObject(Seq("event" -> JsString("sendPlayerNames"),
        "length" -> JsNumber(players.length),
        "players" -> playersToJsArray)).toString())
    }

    def receive = {
      case msg: String =>
        val json = Json.parse(msg)
        val cmd = json("cmd").asInstanceOf[JsString].value
        if(cmd == "loginPlayer") {
          login_player(json)
        } else {
          sendJsonToClient(process_user_input(cmd, json, reactor.name).toString())
        }
    }

    def sendJsonToClient(msg: String) = {
      println("Received event from Controller")
      out ! (msg)
    }
  }
}