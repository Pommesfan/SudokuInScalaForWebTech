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

  def switch_cards = Action { request =>
    val mode = request.body.asInstanceOf[AnyContentAsJson].json.asInstanceOf[JsObject].value.get("mode").get.toString()
    val index = request.body.asInstanceOf[AnyContentAsJson].json.asInstanceOf[JsObject].value.get("index").get.toString().toInt
    def mode_to_Int = if(mode == "\"new\"") Utils.NEW_CARD else if(mode == "\"open\"") Utils.OPENCARD else -1
    c.solve(new DoSwitchCardEvent(index, mode_to_Int))
    Ok(get_post_response())
  }

  def set_players = Action { request =>
    val length = request.body.asInstanceOf[AnyContentAsJson].json.asInstanceOf[JsObject].value.get("length").get.toString().toInt
    val names = request.body.asInstanceOf[AnyContentAsJson].json.asInstanceOf[JsObject].value.get("names").get.result
    var l = List[String]()
    for(i <- 0 until length) {
      l = l :+ names(i).asInstanceOf[JsString].value
    }
    c.solve(new DoCreatePlayerEvent(l))
    Ok(get_post_response())
  }

  def discard = Action { request =>
    val cards = request.body.asInstanceOf[AnyContentAsJson].json.asInstanceOf[JsObject].value.get("cards").get
    c.solve(new DoDiscardEvent(Utils.makeGroupedIndexList(cards.asInstanceOf[JsString].value)))
    Ok(get_post_response())
  }

  def no_discard = Action {
    c.solve(new DoNoDiscardEvent)
    Ok(get_post_response())
  }

  def no_inject = Action {
    c.solve(new DoNoInjectEvent)
    Ok(get_post_response())
  }

  def inject = Action { request =>
    val card_to_inject = request.body.asInstanceOf[AnyContentAsJson].json.asInstanceOf[JsObject].value.get("card_to_inject").get.toString().toInt
    val player_to = request.body.asInstanceOf[AnyContentAsJson].json.asInstanceOf[JsObject].value.get("player_to").get.toString().toInt
    val group_to = request.body.asInstanceOf[AnyContentAsJson].json.asInstanceOf[JsObject].value.get("group_to").get.toString().toInt
    val position_to = request.body.asInstanceOf[AnyContentAsJson].json.asInstanceOf[JsObject].value.get("position_to").get.toString()
    def position_to_Int = if(position_to == "\"FRONT\"") Utils.INJECT_TO_FRONT else if(position_to == "\"AFTER\"") Utils.INJECT_AFTER else -1
    c.solve(new DoInjectEvent(player_to, card_to_inject, group_to, position_to_Int))
    Ok(get_post_response())
  }

  def reset(): Action[AnyContent] = {
    c = new Controller
    phase10
  }

  def get_input_panel() = lastEvent match {
    case _: GameStartedEvent => "switch_card"
    case _: NewRoundEvent => "switch_card"
    case _: TurnEndedEvent => "switch_card"
    case _: GoToDiscardEvent => "discard"
    case _: GoToInjectEvent => "inject"
  }

  def render_player_status(players:List[String], r:RoundData, t:TurnData) = {
    def current_player = t.current_player
    def player_name = players(current_player)

    def get_new_card = lastEvent match {
      case e: GameStartedEvent => Some(e.newCard)
      case e: NewRoundEvent => Some(e.newCard)
      case e: TurnEndedEvent => Some(e.newCard)
      case _ => None
    }

    def cardGroupSize = r.validators(current_player).getNumberOfInputs().size

    views.html.player_status_view(player_name, t.openCard, get_new_card, t.playerCardDeck.cards(current_player), lastEvent, cardGroupSize)
  }

  def render_discarded_cards(players: List[String], r: RoundData, t: TurnData) = {
    def cards = t.discardedCardDeck.cards
    views.html.discarded_cards_view(players, cards, lastEvent)
  }

  def phase10 = Action {
    def state = c.getState
    if (state.isInstanceOf[InitialState]) {
      Ok(views.html.home())
    } else {
      val gameRunningState = state.asInstanceOf[GameRunningControllerStateInterface]
      def players = gameRunningState.players
      def r = gameRunningState.r
      def t = gameRunningState.t
      def player_status = render_player_status(players, r, t)
      def discardedCards = render_discarded_cards(players, r, t)
      def alert_new_round = lastEvent match {
        case _: NewRoundEvent => Some(tui.printNewRound(players, r))
        case _: GameStartedEvent => Some("Erste Phase: " + r.validators(0).description)
        case _ => None
      }
      Ok(views.html.game(discardedCards, player_status, get_input_panel(), alert_new_round))
    }
  }

  def get_post_response(): JsObject = {
    val g = c.getGameData
    val r = g._1
    val t = g._2
    lastEvent match {
      case e: GameStartedEvent => {
        val players = c.getPlayers()
        JsObject(Seq(
          "event" -> JsString("GameStartedEvent"),
          "players" -> JsArray(players.map(p => JsString(p))),
          cardStashCurrentPlayer(t)
        ))
      }
      case e: NewRoundEvent => {
        JsObject(Seq(
          "event" -> JsString("NewRoundEvent"),
          "numberofPhase" -> JsArray(r.validators.map(v => JsNumber(v.getNumberOfPhase())).toSeq),
          "phaseDescription" -> JsArray(r.validators.map(v => JsString(v.description))),
          "errorPoints" -> JsArray(r.errorPoints.map(n => JsNumber(n)).toSeq),
          "activePlayer" -> JsNumber(t.current_player),
          "newCard" -> cardToJSon(e.newCard),
          "openCard" -> cardToJSon(t.openCard)
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
          cardStashCurrentPlayer(t),
          cardStashCurrentPlayer(t),
        ))
      }
      case _: GoToInjectEvent => {
        JsObject(Seq(
          "event" -> JsString("GoToInjectEvent"),
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

  object MyWebSocketActor {
    def props(out: ActorRef) = {
      println("Object created")
      Props(new MyWebSocketActor(out))
    }
  }

  class MyWebSocketActor(out: ActorRef) extends Actor {
    println("Class created")

    def receive = {
      case msg: String =>
        out ! ("I received your message: " + msg)
        println("Received message " + msg)
    }
  }
}