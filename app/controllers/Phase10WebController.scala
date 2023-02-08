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
import scala.collection.mutable.TreeMap



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

  def proceedCommand(cmd: String, json: JsValue) = cmd match {
    case "switch_cards" => switch_cards(json)
    case "discard" => discard(json)
    case "no_discard" => no_discard()
    case "inject" => inject(json)
    case "no_inject" => no_inject()
    case _ => ;
  }

  def proceedOutput(old_r: RoundData, old_t: TurnData, reactor: WebSocketReactor) = {
    val g = c.getGameData
    def new_r = g._1
    def new_t = g._2
    def publishToOpponent(json: JsValue) = {
      getReactor(c.getPlayers()(new_t.current_player)) match {
        case Some(r) => r.publish(json.toString())
        case None =>
      }
    }

    def inform_all_of_new_round(): Unit =
      for (reactor <- webSocketReactors) {
        reactor._2.publish(json_newRound(new_r, new_t.current_player).toString())
      }

    def turnEnded() = reactor.publish(json_turnEnded(new_t, old_t.current_player).toString())

    lastEvent match {
      case e :GameStartedEvent =>
        reactor.publish(json_gameStarted(new_r, new_t, c.getPlayers(), new_t.current_player, e.newCard).toString())
      case e :NewRoundEvent =>
        inform_all_of_new_round()
        turnEnded()
        publishToOpponent(json_playersTurn(new_r, new_t, new_t.current_player, e.newCard))
      case e :TurnEndedEvent => {
        turnEnded()
        publishToOpponent(json_playersTurn(new_r, new_t, new_t.current_player, e.newCard))
      }
      case _ :GoToDiscardEvent => reactor.publish(json_discarded(new_r,new_t,new_t.current_player).toString())
      case _ :GoToInjectEvent => reactor.publish(json_inject(new_t, new_t.current_player).toString())
    }
  }

  def process_user_input(cmd: String, json: JsValue, reactor: WebSocketReactor): Unit = {
    val g = c.getGameData
    val r = g._1
    val t = g._2
    val players = c.getPlayers()
    //block action of player who is not at turn
    if (reactor.name == players(t.current_player)) {
      proceedCommand(cmd, json)
      proceedOutput(r, t, reactor)
    } else {
      reactor.publish(json_turnEnded(t, players.indexOf(reactor.name)).toString())
    }
  }

  def json_gameStarted(r: RoundData, t:TurnData, players:List[String], referringPlayer: Int, newCard:Card) = JsObject(Seq(
    "event" -> JsString("GameStartedEvent"),
    "players" -> JsArray(players.map(p => JsString(p))),
    "newCard" -> cardToJSon(newCard),
    "openCard" -> cardToJSon(t.openCard),
    "phaseDescription" -> JsArray(r.validators.map(v => JsString(v.description))),
    "numberOfPhase" -> JsArray(r.validators.map(v => JsNumber(v.getNumberOfPhase()))),
    "errorPoints" -> JsArray(r.errorPoints.map(n => JsNumber(n))),
    "activePlayer" -> JsNumber(referringPlayer),
    cardStashCurrentPlayer(t, referringPlayer),
    discardedStash(t)))

  def json_newRound(r:RoundData, referringPlayer:Int) = JsObject(Seq(
    "event" -> JsString("NewRoundEvent"),
    "numberOfPhase" -> JsArray(r.validators.map(v => JsNumber(v.getNumberOfPhase())).toSeq),
    "phaseDescription" -> JsArray(r.validators.map(v => JsString(v.description))),
    "numberOfPhase" -> JsArray(r.validators.map(v => JsNumber(v.getNumberOfPhase()))),
    "errorPoints" -> JsArray(r.errorPoints.map(n => JsNumber(n)).toSeq),
    "activePlayer" -> JsNumber(referringPlayer)))

  def json_playersTurn(r:RoundData, t: TurnData, referringPlayer:Int, newCard:Card) = JsObject(Seq(
    "event" -> JsString("PlayersTurnEvent"),
    "activePlayer" -> JsNumber(referringPlayer),
    "newCard" -> cardToJSon(newCard),
    "openCard" -> cardToJSon(t.openCard),
    cardStashCurrentPlayer(t, referringPlayer),
    discardedStash(t)))

  def json_turnEnded(t: TurnData, referringPlayer:Int) = JsObject(Seq(
    "event" -> JsString("TurnEndedEvent"),
    cardStashCurrentPlayer(t, referringPlayer),
    discardedStash(t)))
  def json_discarded(r: RoundData, t: TurnData, referringPlayer:Int) = JsObject(Seq(
    "event" -> JsString("GoToDiscardEvent"),
    "activePlayer" -> JsNumber(referringPlayer),
    "card_group_size" -> JsNumber(r.validators(referringPlayer).getNumberOfInputs().size),
    cardStashCurrentPlayer(t, referringPlayer),
    discardedStash(t)))
  def json_inject(t: TurnData, referringPlayer:Int) = JsObject(Seq(
    "event" -> JsString("GoToInjectEvent"),
    "activePlayer" -> JsNumber(referringPlayer),
    cardStashCurrentPlayer(t, referringPlayer),
    discardedStash(t)))

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

  def getReactor(player: String): Option[WebSocketReactor] = webSocketReactors.get(player)

  object MyWebSocketActor {
    def props(out: ActorRef) = {
      println("Object created")
      Props(new MyWebSocketActor(out))
    }
  }

  val webSocketReactors = new TreeMap[String, WebSocketReactor]()
  abstract class WebSocketReactor() {
    var name = ""
    def publish(msg: String)
  }

  class MyWebSocketActor(out: ActorRef) extends Actor {
    println("Class created")
    private val reactor = new WebSocketReactor {
      override def publish(msg: String): Unit = sendJsonToClient(msg)
    }

    private def login_player(json: JsValue): Unit = {
      if (c.isInstanceOf[InitialState]) {
        return
      }
      val players = c.getPlayers()
      val name = json("loggedInPlayer").asInstanceOf[JsString].value
      if(!players.contains(name)) {
        return
      }

      webSocketReactors.put(name, reactor)
      reactor.name = name

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
        } else if(webSocketReactors.contains(reactor.name)) {
          process_user_input(cmd, json, reactor)
        }
    }

    def sendJsonToClient(msg: String) = {
      println("Received event from Controller")
      out ! (msg)
    }
  }
}