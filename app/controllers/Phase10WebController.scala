package controllers

import akka.actor.{ActorSystem, _}
import akka.stream.Materializer
import model.TurnData
import play.api.libs.json._
import play.api.libs.streams.ActorFlow
import play.api.mvc._
import utils._
import javax.inject._
import Phase10_JSON._

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
    webSocketReactors = List.fill(length)(None)
    Ok("{}")
  }

  private def switch_cards(json: JsValue): InputEvent = {
    val mode = json("mode").asInstanceOf[JsNumber].value.toInt
    val index = json("index").asInstanceOf[JsNumber].value.toInt
    val evt = new DoSwitchCardEvent(index, mode)
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
    val position_to = json("position_to").asInstanceOf[JsNumber].value.toInt
    val evt = new DoInjectEvent(player_to, card_to_inject, group_to, position_to)
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

  private def proceedOutput(old_t: TurnData, reactor: WebSocketReactor, cmd: String, inputEvent: Option[InputEvent]): Unit = {
    def inform_all(msg: String): Unit = webSocketReactors.foreach(
      wsr_opt => wsr_opt.fold(ifEmpty = {})(wsr => wsr.publish(msg)))

    lastEvent match {
      case event: GameEndedEvent =>
        inform_all(json_gameEnded(event).toString())
        webSocketReactors.foreach(wsr_opt => wsr_opt.fold(ifEmpty = {})(wsr => wsr.close()))
        webSocketReactors = Nil
        return
      case _ =>
    }

    val players = c.getPlayers()

    val g = c.getState.asInstanceOf[GameRunningControllerStateInterface]
    def new_r = g.r
    def new_t = g.t
    val is_get_status = cmd == "getStatus"

    def publishToNext(json: JsValue): Unit =
      webSocketReactors(new_t.current_player).fold(ifEmpty = {})(wsr => wsr.publish(json.toString()))

    def publishToOpponents(json: JsValue): Unit = {
      webSocketReactors.foreach(wsr_opt => wsr_opt.fold(ifEmpty = {})(wsr => {
        if(wsr.name_idx != old_t.current_player)
          wsr.publish(json.toString())
      }))
    }

    def sendNewRound(): Unit = players.indices.foreach { idx =>
      val wsr_opt = webSocketReactors(idx)
      wsr_opt.fold(ifEmpty = {})(wr => wr.publish(json_newRound(new_r, new_t, idx).toString()))
    }

    def turnEnded(success: Boolean): Unit = reactor.publish(json_turnEnded(success).deepMerge(fullLoad).toString())

    def fullLoad = json_full_load(is_get_status, new_r, new_t, new_t.current_player, players)

    lastEvent match {
      case e :GameStartedEvent =>
        reactor.publish(json_playersTurn(new_t, new_t.current_player, e.newCard).deepMerge(fullLoad).toString)
      case e :NewRoundEvent =>
        if(!is_get_status)
          sendNewRound()
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
    //block action of player who is not at turn
    if (reactor.name_idx == t.current_player) {
      val inputEvent = proceedCommand(cmd, json)
      proceedOutput(t, reactor, cmd, inputEvent)
    } else {
      val r = g._1
      val idxPlayer = reactor.name_idx
      reactor.publish(json_turnEnded(true).deepMerge(json_full_load(fullLoad = true, r, t, idxPlayer, c.getPlayers())).toString())
    }
  }


  def socket: WebSocket = WebSocket.accept[String, String] { _ =>
    ActorFlow.actorRef { out =>
      println("Connect received")
      MyWebSocketActor.props(out)
    }
  }

  private object MyWebSocketActor {
    def props(out: ActorRef): Props = {
      println("Object created")
      Props(new MyWebSocketActor(out))
    }
  }

  private var webSocketReactors: List[Option[WebSocketReactor]] = Nil
  private abstract class WebSocketReactor {
    var name_idx: Int = -1
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
      val idx = players.indexOf(name)

      webSocketReactors(idx).fold ({
        val g = c.getGameData
        sendJsonToClient(json_newGame(g._1, players, g._2, idx).toString)
      }) (_ => {})
      webSocketReactors = webSocketReactors.updated(idx, Some(reactor))
      reactor.name_idx = idx
    }

    def receive: Receive = {
      case msg: String =>
        val json = Json.parse(msg)
        val cmd = json("cmd").asInstanceOf[JsString].value
        if(cmd == "loginPlayer") {
          login_player(json)
        } else if(reactor.name_idx >= 0) {
          process_user_input(cmd, json, reactor)
        }
    }

     private def sendJsonToClient(msg: String): Unit = {
      out ! msg
    }
  }
}