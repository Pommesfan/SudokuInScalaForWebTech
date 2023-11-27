package controllers

import model.{Card, RoundData, TurnData}
import play.api.libs.json.{JsArray, JsBoolean, JsNull, JsNumber, JsObject, JsString, JsValue, Json}
import javax.inject._
import play.api.mvc._
import utils.{DoCreatePlayerEvent, DoDiscardEvent, DoInjectEvent, DoNoDiscardEvent, DoNoInjectEvent, DoSwitchCardEvent, GameEndedEvent, GameStartedEvent, GoToDiscardEvent, GoToInjectEvent, InputEvent, NewRoundEvent, Observer, OutputEvent, Phase10WebUtils, ProgramStartedEvent, TurnEndedEvent, Utils}
import play.api.libs.streams.ActorFlow
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.actor._
import play.api.libs.json
import scala.collection.mutable.TreeMap

@Singleton
class Phase10WebController @Inject()(cc: ControllerComponents) (implicit system: ActorSystem, mat: Materializer)  extends AbstractController(cc) with Observer {
  private var lastEvent: OutputEvent = new ProgramStartedEvent
  var c = new Controller
  c.add(this)
  c.notifyObservers(new ProgramStartedEvent) //set correct state in TUI

  override def update(e: OutputEvent): String = {
    lastEvent = e
    ""
  }

  def help: Action[AnyContent] = Action {
    Ok(views.html.help())
  }

  def about: Action[AnyContent] = Action {
    Ok(views.html.about())
  }

  def home(): Action[AnyContent] = Action {
    Ok(views.html.home())
  }

  def phase10: Action[AnyContent] = Action {
    Ok(views.html.game())
  }

  def set_players: Action[AnyContent] = Action { request =>
    val length = request.body.asInstanceOf[AnyContentAsJson].json.asInstanceOf[JsObject].value("length").toString().toInt
    val names = request.body.asInstanceOf[AnyContentAsJson].json.asInstanceOf[JsObject].value("names").result
    var l = List[String]()
    for(i <- 0 until length) {
      l = l :+ names(i).asInstanceOf[JsString].value
    }
    c.solve(new DoCreatePlayerEvent(l))
    Ok("{}")
  }

  private def switch_cards(json: JsValue): InputEvent = {
    val mode = json("mode").asInstanceOf[JsString].value
    val index = json("index").asInstanceOf[JsNumber].value.toInt
    def mode_to_Int = if (mode == "new") Utils.NEW_CARD else if (mode == "open") Utils.OPENCARD else -1
    val evt = new DoSwitchCardEvent(index, mode_to_Int)
    c.solve(evt)
    evt
  }

  private def discard(json: JsValue): InputEvent= {
    val g = c.getGameData
    def r = g._1
    def t = g._2
    val cards = json("cards").asInstanceOf[JsString].value
    val cards_sorted = Phase10WebUtils.sort_sequences(Utils.makeGroupedIndexList(cards), r, t)
    val evt = new DoDiscardEvent(cards_sorted)
    c.solve(evt)
    evt
  }

  private def no_discard(): InputEvent = {
    val evt = new DoNoDiscardEvent
    c.solve(evt)
    evt
  }

  private def no_inject(): InputEvent = {
    val evt = new DoNoInjectEvent
    c.solve(evt)
    evt
  }

  def inject(json: JsValue): InputEvent = {
    val card_to_inject = json("card_to_inject").asInstanceOf[JsNumber].value.toInt
    val player_to = json("player_to").asInstanceOf[JsNumber].value.toInt
    val group_to = json("group_to").asInstanceOf[JsNumber].value.toInt
    val position_to = json("position_to").asInstanceOf[JsString].value
    def position_to_Int = if (position_to == "FRONT") Utils.INJECT_TO_FRONT else if (position_to == "AFTER") Utils.INJECT_AFTER else -1
    val evt = new DoInjectEvent(player_to, card_to_inject, group_to, position_to_Int)
    c.solve(evt)
    evt
  }

  def reset(): Action[AnyContent] = {
    c = new Controller
    phase10
  }

  private def proceedCommand(cmd: String, json: JsValue): Option[InputEvent] = cmd match {
    case "switch_cards" => Some(switch_cards(json))
    case "discard" => Some(discard(json))
    case "no_discard" => Some(no_discard())
    case "inject" => Some(inject(json))
    case "no_inject" => Some(no_inject())
    case "getStatus" => None
    case _ => throw new Exception("No such Command");
  }

  private def sendNewRound(players: List[String], r:RoundData, t:TurnData): Unit = players.zipWithIndex.foreach { p =>
    def name = p._1
    def idx = p._2
    val wr = webSocketReactors(name)
    wr.publish(json_newRound(r, t, idx).toString())
  }

  private def proceedOutput(old_t: TurnData, reactor: WebSocketReactor, cmd: String, inputEvent: Option[InputEvent]): Unit = {
    def inform_all(msg: String): Unit = webSocketReactors.foreach { reactor =>
      reactor._2.publish(msg)
    }
    val players = c.getPlayers()

    lastEvent match {
      case event: GameEndedEvent =>
        inform_all(json_gameEnded(event).toString())
        for(r <- webSocketReactors) r._2.close()
        webSocketReactors.clear()
        return
      case _ =>
    }

    val g = c.getState.asInstanceOf[GameRunningControllerStateInterface]
    def new_r = g.r
    def new_t = g.t
    val is_get_status = cmd == "getStatus"

    def publishToNext(json: JsValue): Unit = {
      getReactor(c.getPlayers()(new_t.current_player)) match {
        case Some(r) => r.publish(json.toString())
        case None =>
      }
    }

    def publishToOpponents(json: JsValue): Unit = {
      webSocketReactors.filter(wsr => wsr._1 != players(old_t.current_player)).foreach(wsr => wsr._2.publish(json.toString()))
    }

    def turnEnded(success: Boolean): Unit = reactor.publish(json_turnEnded(success).deepMerge(fullLoad).toString())

    def fullLoad = json_full_load(is_get_status, new_r, new_t, new_t.current_player)

    lastEvent match {
      case e :GameStartedEvent =>
        reactor.publish(json_playersTurn(new_t, new_t.current_player, e.newCard).deepMerge(fullLoad).toString)
      case e :NewRoundEvent =>
        if(!is_get_status)
          sendNewRound(players, new_r,new_t)
        turnEnded(true)
        publishToNext(json_playersTurn(new_t, new_t.current_player, e.newCard))
      case e :TurnEndedEvent =>
        if(cmd == "discard" && e.success)
          publishToOpponents(json_player_has_discarded(old_t.current_player, new_t))
        if (!is_get_status)
          turnEnded(e.success)
        publishToNext(json_playersTurn(new_t, new_t.current_player, e.newCard).deepMerge(fullLoad))
      case _ :GoToDiscardEvent => reactor.publish(json_discarded(new_t.current_player).deepMerge(fullLoad).toString())
      case _ :GoToInjectEvent =>
        reactor.publish(json_inject(new_t.current_player).deepMerge(fullLoad).toString())
        if (cmd == "inject" && inputEvent.nonEmpty) {
          publishToOpponents(json_player_has_injected(inputEvent.get.asInstanceOf[DoInjectEvent], old_t).deepMerge(fullLoad))
        }
    }
  }

  private def process_user_input(cmd: String, json: JsValue, reactor: WebSocketReactor): Unit = {
    val g = c.getGameData
    val t = g._2
    val players = c.getPlayers()
    //block action of player who is not at turn
    if (reactor.name == players(t.current_player)) {
      val inputEvent = proceedCommand(cmd, json)
      proceedOutput(t, reactor, cmd, inputEvent)
    } else {
      val r = g._1
      val idxPlayer = players.indexOf(reactor.name)
      reactor.publish(json_turnEnded(true).deepMerge(json_full_load(true, r, t, idxPlayer)).toString())
    }
  }

  private def json_full_load(fullLoad: Boolean, r: RoundData, t: TurnData, referringPlayer: Int): JsObject = if(fullLoad)
    JsObject(Seq(
      "fullLoad" -> JsBoolean(true),
      "numberOfPhase" -> JsNumber(r.validators.head.getNumberOfPhase()),
      "phaseDescription" -> JsString(r.validators.head.description),
      "card_group_size" -> JsNumber(r.validators(referringPlayer).getNumberOfInputs().size),
      cardStashCurrentPlayer(t, referringPlayer),
      discardedStash(t)
    ))
  else
    JsObject(Seq("fullLoad" -> JsBoolean(false)))

  private def json_player_has_injected(doInjectEvent: DoInjectEvent, t:TurnData): JsObject = JsObject(Seq(
    "event" -> JsString("PlayerHasInjected"),
    "playerTo" -> JsNumber(doInjectEvent.receiving_player),
    "stashTo" -> JsNumber(doInjectEvent.stashIndex),
    "position" -> json.JsNumber(doInjectEvent.position),
    "card" -> cardToJSon(t.playerCardDeck.cards(t.current_player)(doInjectEvent.cardIndex))
  ))
  private def json_player_has_discarded(referringPlayer: Int, t: TurnData): JsObject = JsObject(Seq(
    "event" -> JsString("PlayerHasDiscarded"),
    "player" -> JsNumber(referringPlayer),
    "cards" -> JsArray(t.discardedCardDeck.cards(referringPlayer).get.map(cs =>
      JsArray(cs.map(c => cardToJSon(c)))
    ))))

  private def json_newGame(r:RoundData, players: List[String], t:TurnData, referringPlayer: Int): JsObject = JsObject(Seq(
    "event" -> JsString("NewGameEvent"),
    "numberOfPhase" -> JsNumber(r.validators.head.getNumberOfPhase()),
    "phaseDescription" -> JsString(r.validators.head.description),
    "players" -> JsArray(players.map(s => JsString(s))),
    "numberOfPlayers" -> JsNumber(players.size),
    "card_group_size" -> JsNumber(r.validators(referringPlayer).getNumberOfInputs().size),
    cardStashCurrentPlayer(t, referringPlayer)))

  private def json_gameEnded(e: GameEndedEvent): JsObject = JsObject(Seq(
    "event" -> JsString("GameEndedEvent"),
    "winningPlayer" -> JsString(e.winningPlayer),
    "players" -> JsArray(e.players.map(p => JsString(p))),
    "phases" -> JsArray(e.phases.map(n => JsNumber(n))),
    "errorPoints" -> JsArray(e.errorPoints.map(n => JsNumber(n)))
  ))

  private def json_newRound(r:RoundData, t:TurnData, referringPlayer: Int): JsObject = JsObject(Seq(
    "event" -> JsString("NewRoundEvent"),
    "numberOfPhase" -> JsArray(r.validators.map(v => JsNumber(v.getNumberOfPhase()))),
    "phaseDescription" -> JsArray(r.validators.map(v => JsString(v.description))),
    "errorPoints" -> JsArray(r.errorPoints.map(n => JsNumber(n))),
    "card_group_size" -> JsNumber(r.validators(referringPlayer).getNumberOfInputs().size),
    cardStashCurrentPlayer(t, referringPlayer)))

  private def json_playersTurn(t: TurnData, referringPlayer:Int, newCard:Card): JsObject = JsObject(Seq(
    "event" -> JsString("PlayersTurnEvent"),
    "activePlayer" -> JsNumber(referringPlayer),
    "newCard" -> cardToJSon(newCard),
    "openCard" -> cardToJSon(t.openCard)))

  private def json_turnEnded(success: Boolean): JsObject = JsObject(Seq(
    "event" -> JsString("TurnEndedEvent"),
    "success" -> JsBoolean(success)))

  private def json_discarded(referringPlayer:Int): JsObject = JsObject(Seq(
    "event" -> JsString("GoToDiscardEvent"),
    "activePlayer" -> JsNumber(referringPlayer)))

  private def json_inject(referringPlayer:Int): JsObject = JsObject(Seq(
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


  def socket: WebSocket = WebSocket.accept[String, String] { _ =>
    ActorFlow.actorRef { out =>
      println("Connect received")
      MyWebSocketActor.props(out)
    }
  }

  private def getReactor(player: String): Option[WebSocketReactor] = webSocketReactors.get(player)

  private object MyWebSocketActor {
    def props(out: ActorRef): Props = {
      println("Object created")
      Props(new MyWebSocketActor(out))
    }
  }

  val webSocketReactors = new TreeMap[String, WebSocketReactor]()
  abstract class WebSocketReactor() {
    var name = ""
    def publish(msg: String): Unit
    def close(): Unit
  }

  private class MyWebSocketActor(out: ActorRef) extends Actor {
    println("Class created")
    private val reactor = new WebSocketReactor {
      override def publish(msg: String): Unit = sendJsonToClient(msg)

      override def close(): Unit = out ! PoisonPill
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

      if(!webSocketReactors.contains(name)) {
        val g = c.getGameData
        val idx = players.indexOf(name)
        sendJsonToClient(json_newGame(g._1, players, g._2, idx).toString)
      }
      webSocketReactors.put(name, reactor)
      reactor.name = name
    }

    def receive: Receive = {
      case msg: String =>
        val json = Json.parse(msg)
        val cmd = json("cmd").asInstanceOf[JsString].value
        if(cmd == "loginPlayer") {
          login_player(json)
        } else if(webSocketReactors.contains(reactor.name)) {
          process_user_input(cmd, json, reactor)
        }
    }

     private def sendJsonToClient(msg: String): Unit = {
      out ! msg
    }
  }
}