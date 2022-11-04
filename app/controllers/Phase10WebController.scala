package controllers

import javax.inject._
import play.api.mvc._
import utils.{DoCreatePlayerEvent, DoDiscardEvent, DoInjectEvent, DoNoDiscardEvent, DoNoInjectEvent, DoSwitchCardEvent, ProgramStartedEvent, Utils}
import views.TUI
import controllers.{DiscardControllerState, InjectControllerState, SwitchCardControllerState}

@Singleton
class Phase10WebController @Inject()(cc: ControllerComponents) extends AbstractController(cc) {
  val c = new Controller
  val tui = new TUI(c)
  c.add(tui)
  c.notifyObservers(new ProgramStartedEvent) //set correct state in TUI

  def help = Action {
    Ok(views.html.help())
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

    c.solve(new DoCreatePlayerEvent(make_list()))
    phase10
  }

  def new_card(idx: String): Action[AnyContent] = {
    c.solve(new DoSwitchCardEvent(idx.toInt, Utils.NEW_CARD))
    phase10
  }

  def open_card(idx: String): Action[AnyContent] = {
    c.solve(new DoSwitchCardEvent(idx.toInt, Utils.OPENCARD))
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

  def inject(receiving_player:String, cardIndex:String, stashIndex:String, position:String): Action[AnyContent] = {
    def stash_index =
      if (position == "FRONT") Utils.INJECT_TO_FRONT
      else if(position=="AFTER") Utils.INJECT_AFTER
      else 0
    c.solve(new DoInjectEvent(receiving_player.toInt, cardIndex.toInt, stashIndex.toInt, stash_index))
    phase10
  }

  def get_input_panel(state: ControllerStateInterface) = state match {
    case _: SwitchCardControllerState => views.html.switch_card_from.apply()
    case _: DiscardControllerState => views.html.discard_form.apply()
    case _: InjectControllerState => views.html.inject_card_form.apply()
  }

  def render_player_status(c: Controller) = {
    val state = c.getState.asInstanceOf[GameRunningControllerStateInterface]
    def r = state.r
    def t = state.t
    def players = state.players
    def current_player = t.current_player
    def player_name = players(current_player)

    def get_new_card = state match {
      case s: SwitchCardControllerState => Some(s.newCard)
      case _ => None
    }

    views.html.player_status_view(player_name, t.openCard, get_new_card, t.playerCardDeck.cards(current_player))
  }

  def render_discarded_cards(c: Controller) = {
    def state = c.getState.asInstanceOf[GameRunningControllerStateInterface]
    def cards = state.t.discardedCardDeck.cards
    views.html.discarded_cards_views(state.players, cards)
  }

  def phase10 = Action {
    def state = c.getState
    if (state.isInstanceOf[InitialState]) {
      Ok(views.html.home())
    } else {
      def player_status = render_player_status(c)
      def discardedCards = render_discarded_cards(c)
      Ok(views.html.game(discardedCards, player_status, get_input_panel(state)))
    }
  }
}