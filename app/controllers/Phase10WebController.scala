package controllers

import model.{RoundData, TurnData}
import play.api.libs.json.{JsObject, JsString}

import javax.inject._
import play.api.mvc._
import utils.{DoCreatePlayerEvent, DoDiscardEvent, DoInjectEvent, DoNoDiscardEvent, DoNoInjectEvent, DoSwitchCardEvent, GameStartedEvent, GoToDiscardEvent, GoToInjectEvent, NewRoundEvent, Observer, OutputEvent, ProgramStartedEvent, TurnEndedEvent, Utils}
import views.TUI

@Singleton
class Phase10WebController @Inject()(cc: ControllerComponents) extends AbstractController(cc) with Observer {
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

  def no_inject(): Action[AnyContent] = {
    c.solve(new DoNoInjectEvent)
    phase10
  }

  def post_switch_cards = Action { request =>
    val mode = request.body.asInstanceOf[AnyContentAsJson].json.asInstanceOf[JsObject].value.get("mode").get.toString()
    val index = request.body.asInstanceOf[AnyContentAsJson].json.asInstanceOf[JsObject].value.get("index").get.toString().toInt
    def mode_to_Int = if(mode == "\"new\"") Utils.NEW_CARD else if(mode == "\"open\"") Utils.OPENCARD else -1
    c.solve(new DoSwitchCardEvent(index, mode_to_Int))
    Ok("")
  }

  def post_set_players = Action { request =>
    val length = request.body.asInstanceOf[AnyContentAsJson].json.asInstanceOf[JsObject].value.get("length").get.toString().toInt
    val names = request.body.asInstanceOf[AnyContentAsJson].json.asInstanceOf[JsObject].value.get("names").get.result
    var l = List[String]()
    for(i <- 0 until length) {
      l = l :+ names(i).asInstanceOf[JsString].value
    }
    c.solve(new DoCreatePlayerEvent(l))
    Ok("")
  }

  def post_discard = Action { request =>
    val cards = request.body.asInstanceOf[AnyContentAsJson].json.asInstanceOf[JsObject].value.get("cards").get
    c.solve(new DoDiscardEvent(Utils.makeGroupedIndexList(cards.asInstanceOf[JsString].value)))
    Ok("")
  }

  def post_no_discard = Action {
    c.solve(new DoNoDiscardEvent)
    Ok("")
  }

  def inject(inject_to: String, card_index: String): Action[AnyContent] = {
    if(inject_to.nonEmpty && card_index.nonEmpty) {
      val inject_to_split = inject_to.split("_")

      def receiving_player = inject_to_split(0)
      def stashIndex = inject_to_split(1)
      def position = inject_to_split(2)

      def stash_index =
        if (position == "FRONT") Utils.INJECT_TO_FRONT
        else if (position == "AFTER") Utils.INJECT_AFTER
        else 0

      c.solve(new DoInjectEvent(receiving_player.toInt, card_index.toInt, stashIndex.toInt, stash_index))
    }
    phase10
  }

  def reset(): Action[AnyContent] = {
    c = new Controller
    phase10
  }

  def get_input_panel() = lastEvent match {
    case _: GameStartedEvent => views.html.switch_card_form.apply()
    case _: NewRoundEvent => views.html.switch_card_form.apply()
    case _: TurnEndedEvent => views.html.switch_card_form.apply()
    case _: GoToDiscardEvent => views.html.discard_form.apply()
    case _: GoToInjectEvent => views.html.inject_card_form.apply()
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
}