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
    Ok(respond())
  }

  def switch_cards(json: JsValue): Unit = {
    val mode = json("mode").asInstanceOf[JsString].value
    val index = json("index").asInstanceOf[JsNumber].value.toInt
    def mode_to_Int = if (mode == "new") Utils.NEW_CARD else if (mode == "open") Utils.OPENCARD else -1
    c.solve(new DoSwitchCardEvent(index, mode_to_Int))
  }

  def discard(json: JsValue): Unit = {
    val cards = json("cards").asInstanceOf[JsString].value
    c.solve(new DoDiscardEvent(Utils.makeGroupedIndexList(cards)))
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

  def process_user_input(cmd: String, json: JsValue): JsObject = {
    cmd match {
      case "switch_cards" => switch_cards(json)
      case "discard" => discard(json)
      case "no_discard" => no_discard()
      case "inject" => inject(json)
      case "no_inject" => no_inject()
      case _ => ;
    }
    respond()
  }


  def respond(): JsObject = {
    val g = c.getGameData
    val r = g._1
    val t = g._2
    get_post_response(r,t)
  }

  def get_post_response(r: RoundData, t:TurnData): JsObject = {
    lastEvent match {
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
          "activePlayer" -> JsNumber(t.current_player),
          cardStashCurrentPlayer(t),
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
          "activePlayer" -> JsNumber(t.current_player),
          "newCard" -> cardToJSon(e.newCard),
          "openCard" -> cardToJSon(t.openCard),
          cardStashCurrentPlayer(t),
          discardedStash(t)
        ))
      }
      case e: TurnEndedEvent => {
        JsObject(Seq(
          "event" -> JsString("TurnEndedEvent"),
          "activePlayer" -> JsNumber(t.current_player),
          "newCard" -> cardToJSon(e.newCard),
          "openCard" -> cardToJSon(t.openCard),
          cardStashCurrentPlayer(t),
          discardedStash(t)
        ))
      }
      case _: GoToDiscardEvent => {
        JsObject(Seq(
          "event" -> JsString("GoToDiscardEvent"),
          "activePlayer" -> JsNumber(t.current_player),
          "card_group_size" -> JsNumber(r.validators(t.current_player).getNumberOfInputs().size),
          cardStashCurrentPlayer(t),
          discardedStash(t)
        ))
      }
      case _: GoToInjectEvent => {
        JsObject(Seq(
          "event" -> JsString("GoToInjectEvent"),
          "activePlayer" -> JsNumber(t.current_player),
          cardStashCurrentPlayer(t),
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

  private def cardStashCurrentPlayer(t: TurnData): (String, JsArray) = {
    "cardStash" -> JsArray(
        t.playerCardDeck.cards(t.current_player).map(c => cardToJSon(c))
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

    def receive = {
      case msg: String =>
        val json = Json.parse(msg)
        val cmd = json("cmd").asInstanceOf[JsString].value
        if(cmd == "loginPlayer") {
          reactor.name = json("loggedInPlayer").asInstanceOf[JsString].value
        } else {
          sendJsonToClient(process_user_input(cmd, json).toString())
        }
    }

    def sendJsonToClient(msg: String) = {
      println("Received event from Controller")
      out ! (msg)
    }
  }
}