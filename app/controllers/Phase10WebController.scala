package controllers

import model.{Card, RoundData, TurnData}
import play.api.libs.json.{JsArray, JsNull, JsNumber, JsObject, JsString}

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

  def get_game_state() = Action { request =>
    val player = request.body.asInstanceOf[AnyContentAsJson].json.asInstanceOf[JsObject].value.get("player").get.toString()
    Ok(respond())
  }

  def switch_cards = Action { request =>
    val mode = request.body.asInstanceOf[AnyContentAsJson].json.asInstanceOf[JsObject].value.get("mode").get.toString()
    val index = request.body.asInstanceOf[AnyContentAsJson].json.asInstanceOf[JsObject].value.get("index").get.toString().toInt
    def mode_to_Int = if(mode == "\"new\"") Utils.NEW_CARD else if(mode == "\"open\"") Utils.OPENCARD else -1
    c.solve(new DoSwitchCardEvent(index, mode_to_Int))
    Ok(respond())
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

  def discard = Action { request =>
    val cards = request.body.asInstanceOf[AnyContentAsJson].json.asInstanceOf[JsObject].value.get("cards").get
    c.solve(new DoDiscardEvent(Utils.makeGroupedIndexList(cards.asInstanceOf[JsString].value)))
    Ok(respond())
  }

  def no_discard = Action {
    c.solve(new DoNoDiscardEvent)
    Ok(respond())
  }

  def no_inject = Action {
    c.solve(new DoNoInjectEvent)
    Ok(respond())
  }

  def inject = Action { request =>
    val card_to_inject = request.body.asInstanceOf[AnyContentAsJson].json.asInstanceOf[JsObject].value.get("card_to_inject").get.toString().toInt
    val player_to = request.body.asInstanceOf[AnyContentAsJson].json.asInstanceOf[JsObject].value.get("player_to").get.toString().toInt
    val group_to = request.body.asInstanceOf[AnyContentAsJson].json.asInstanceOf[JsObject].value.get("group_to").get.toString().toInt
    val position_to = request.body.asInstanceOf[AnyContentAsJson].json.asInstanceOf[JsObject].value.get("position_to").get.toString()
    def position_to_Int = if(position_to == "\"FRONT\"") Utils.INJECT_TO_FRONT else if(position_to == "\"AFTER\"") Utils.INJECT_AFTER else -1
    c.solve(new DoInjectEvent(player_to, card_to_inject, group_to, position_to_Int))
    Ok(respond())
  }

  def reset(): Action[AnyContent] = {
    c = new Controller
    phase10
  }


  def respond(): JsObject = {
    val g = c.getGameData
    val r = g._1
    val t = g._2
    val players = c.getPlayers()
    val res = get_post_response(r,t)
    if(lastEvent.isInstanceOf[TurnEndedEvent]) {
      val reactorOption = getReactor(players(t.current_player))
      if (reactorOption.nonEmpty) {
        reactorOption.get.publish("Du bist an der Reihe")
      }
    }
    res
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
        if(msg.startsWith("setPlayer")) {
          reactor.name = msg.split(":")(1)
        }
        out ! ("I received your message: " + msg)
        println("Received message " + msg)
    }

    def sendJsonToClient(msg: String) = {
      println("Received event from Controller")
      out ! (msg)
    }
  }
}