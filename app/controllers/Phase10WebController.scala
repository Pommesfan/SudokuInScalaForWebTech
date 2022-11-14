package controllers

import model.{RoundData, TurnData}

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

  def set_players(p1:String, p2:String, p3:String, p4:String) = {
    def make_list(): List[String] = {
      var l = List[String]()
      if (!(p1.isEmpty || p2.isEmpty)) {
        l = p1 :: l
        l = p2 :: l
        if (p3.isEmpty) {
          return l
        }
        l = p3 :: l
        if (p4.isEmpty) {
          return l
        }
        l = p4 :: l
      }
      l
    }

    c.solve(new DoCreatePlayerEvent(make_list().reverse))
    phase10
  }

  def new_card(idx: String): Action[AnyContent] = {
    if(idx.nonEmpty) {
      c.solve(new DoSwitchCardEvent(idx.toInt, Utils.NEW_CARD))
    }
    phase10
  }

  def open_card(idx: String): Action[AnyContent] = {
    if (idx.nonEmpty) {
      c.solve(new DoSwitchCardEvent(idx.toInt, Utils.OPENCARD))
    }
    phase10
  }

  def no_discard(): Action[AnyContent] = {
    c.solve(new DoNoDiscardEvent)
    phase10
  }

  def discard(indices: String): Action[AnyContent] = {
    c.solve(new DoDiscardEvent(Utils.makeGroupedIndexList(indices)))
    phase10
  }

  def no_inject(): Action[AnyContent] = {
    c.solve(new DoNoInjectEvent)
    phase10
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

    views.html.player_status_view(player_name, t.openCard, get_new_card, t.playerCardDeck.cards(current_player), lastEvent)
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
      Ok(views.html.game(discardedCards, player_status, get_input_panel()))
    }
  }
}