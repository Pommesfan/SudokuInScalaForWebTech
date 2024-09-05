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
import scala.collection.mutable
import scala.util.Random

@Singleton
class Phase10WebController @Inject()(cc: ControllerComponents) (implicit system: ActorSystem, mat: Materializer)  extends AbstractController(cc) {

   private def createNewTeam(l: List[String]) = {
    val r = Random
    val id = r.alphanumeric.take(4).toArray.mkString
    val c = new Controller
    c.solve(new DoCreatePlayerEvent(l))
    val team = new Team(id, c, List.fill(l.size)(None))
    player_teams.put(id, team)
    id
  }

  def set_players: Action[AnyContent] = Action { request =>
    val length = request.body.asInstanceOf[AnyContentAsJson].json.asInstanceOf[JsObject].value("length").toString().toInt
    val names = request.body.asInstanceOf[AnyContentAsJson].json.asInstanceOf[JsObject].value("names").result
    val l = (0 until length).foldLeft(List[String]())((l, i) => l :+ names(i).asInstanceOf[JsString].value)
    val id = createNewTeam(l)
    println("new team:")
    println(l)
    println("ID: " + id)

    Ok(teamIdToJSon(id))
  }

  private def switch_cards(json: JsValue, c: Controller): InputEvent = {
    val mode = json("mode").asInstanceOf[JsNumber].value.toInt
    val index = json("index").asInstanceOf[JsNumber].value.toInt
    val evt = new DoSwitchCardEvent(index, mode)
    c.solve(evt)
    evt
  }

  private def discard(json: JsValue, c: Controller): InputEvent= {
    val g = c.getGameData
    def r = g._1
    def t = g._2
    val cards = json("cards").asInstanceOf[JsString].value
    val cards_sorted = Phase10WebUtils.sort_sequences(Utils.makeGroupedIndexList(cards), r, t)
    val evt = new DoDiscardEvent(cards_sorted)
    c.solve(evt)
    evt
  }

  private def no_discard(c: Controller): InputEvent = {
    val evt = new DoNoDiscardEvent
    c.solve(evt)
    evt
  }

  private def no_inject(c: Controller): InputEvent = {
    val evt = new DoNoInjectEvent
    c.solve(evt)
    evt
  }

  def inject(json: JsValue, c: Controller): InputEvent = {
    val card_to_inject = json("card_to_inject").asInstanceOf[JsNumber].value.toInt
    val player_to = json("player_to").asInstanceOf[JsNumber].value.toInt
    val group_to = json("group_to").asInstanceOf[JsNumber].value.toInt
    val position_to = json("position_to").asInstanceOf[JsNumber].value.toInt
    val evt = new DoInjectEvent(player_to, card_to_inject, group_to, position_to)
    c.solve(evt)
    evt
  }

  private def proceedCommand(cmd: String, json: JsValue, c: Controller): Option[InputEvent] = cmd match {
    case "switch_cards" => Some(switch_cards(json, c))
    case "discard" => Some(discard(json, c))
    case "no_discard" => Some(no_discard(c))
    case "inject" => Some(inject(json, c))
    case "no_inject" => Some(no_inject(c))
    case "getStatus" => None
    case _ => throw new Exception("No such Command");
  }

  private def proceedOutput(old_t: TurnData, reactor: WebSocketReactor, cmd: String, inputEvent: Option[InputEvent], team: Team): Unit = {
    def webSocketReactors = team.webSocketReactors
    def lastEvent = team.getLastEvent

    def inform_all(msg: String): Unit = webSocketReactors.foreach(
      wsr_opt => wsr_opt.fold(ifEmpty = {})(wsr => wsr.publish(msg)))

    lastEvent match {
      case event: GameEndedEvent =>
        inform_all(json_gameEnded(event).toString())
        webSocketReactors.foreach(wsr_opt => wsr_opt.fold(ifEmpty = {})(wsr => wsr.close()))
        player_teams.remove(team.id)
        println("Game Ended TeamID: " + team.id + "; Winner: " + event.winningPlayer)
      case _ => process_output_game_running(cmd, team, reactor, inputEvent, old_t)
    }
  }

  private def process_output_game_running(cmd: String, team: Team, reactor: WebSocketReactor, inputEvent: Option[InputEvent], old_t: TurnData): Unit = {
    def c = team.controller
    def webSocketReactors = team.webSocketReactors
    def lastEvent = team.getLastEvent
    def state = c.getState.asInstanceOf[GameRunningControllerStateInterface]
    def new_r = state.r
    def new_t = state.t
    def players = state.players

    def is_get_status = cmd == "getStatus"

    def publishToNext(json: JsValue): Unit =
      webSocketReactors(new_t.current_player).fold(ifEmpty = {})(wsr => wsr.publish(json.toString()))

    def publishToOpponents(json: JsValue): Unit = {
      webSocketReactors.foreach(wsr_opt => wsr_opt.fold(ifEmpty = {})(wsr => {
        if(wsr.name_idx != old_t.current_player)
          wsr.publish(json.toString())
      }))
    }

    def sendNewRound(): Unit = players.indices.foreach { idx =>
      webSocketReactors(idx).fold(
        ifEmpty = {})(wr => wr.publish(json_newRound(new_r, new_t, idx).toString()))
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
    def team = reactor.team
    def c = team.controller

    val g = c.getGameData
    val t = g._2
    //block action of player who is not at turn
    reactor.name_idx match {
      case t.current_player =>
        val inputEvent = proceedCommand(cmd, json, c)
        proceedOutput(t, reactor, cmd, inputEvent, team)
      case _ =>
        val r = g._1
        val idxPlayer = reactor.name_idx
        reactor.publish(json_turnEnded(true)
          .deepMerge(json_full_load(fullLoad = true, r, t, idxPlayer, c.getPlayers())).toString())
    }
  }


  def socket: WebSocket = WebSocket.accept[String, String] { _ =>
    ActorFlow.actorRef { out =>
      MyWebSocketActor.props(out)
    }
  }

  private object MyWebSocketActor {
    def props(out: ActorRef): Props = {
      Props(new MyWebSocketActor(out))
    }
  }

  private val player_teams: mutable.HashMap[String, Team] = mutable.HashMap()
  abstract class WebSocketReactor(val name_idx: Int, val team: Team) {
    def publish(msg: String): Unit
    def close(): Unit
  }

  class Team(val id:String, val controller: Controller, var webSocketReactors: List[Option[WebSocketReactor]]) extends Observer {
    private var lastEvent: OutputEvent = new GameStartedEvent(controller.getState.asInstanceOf[SwitchCardControllerState].newCard)
    override def update(e: OutputEvent): String = {
      lastEvent = e
      ""
    }

    def getLastEvent: OutputEvent = lastEvent
    controller.add(this)
    controller.notifyObservers(lastEvent) //set correct state in TUI
  }

  private class MyWebSocketActor(out: ActorRef) extends Actor {
    private var reactor: Option[WebSocketReactor] = None

    private def login_player(json: JsValue): Unit = {
      def loginFailed(): Unit = sendJsonToClient(json_login_failed().toString())

      val name = json("loggedInPlayer").asInstanceOf[JsString].value
      val team_id = json("team_id").asInstanceOf[JsString].value
      val team_opt = player_teams.get(team_id)
      if (team_opt.isEmpty) {
        loginFailed()
        return
      }

      val team = team_opt.get

      val players = team.controller.getPlayers()
      if(!players.contains(name)) {
        loginFailed()
        return
      }
      val idx = players.indexOf(name)

      team.webSocketReactors(idx).fold ({
        val g = team.controller.getGameData
        sendJsonToClient(json_newGame(g._1, players, g._2, idx).toString)
      }) (_ => {})

      val new_reactor = new WebSocketReactor(idx, team) {
        override def publish(msg: String): Unit = sendJsonToClient(msg)
        override def close(): Unit = out ! PoisonPill
      }

      team.webSocketReactors = team.webSocketReactors.updated(idx, Some(new_reactor))
      reactor = Some(new_reactor)
    }

    def receive: Receive = {
      case msg: String =>
        val json = Json.parse(msg)
        val cmd = json("cmd").asInstanceOf[JsString].value

        reactor match {
          case Some(r) => process_user_input(cmd, json, r)
          case None => if (cmd == "loginPlayer") login_player(json)
        }
    }

     private def sendJsonToClient(msg: String): Unit = {
      out ! msg
    }
  }
}