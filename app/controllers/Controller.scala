package controllers

import model._
import utils.Utils.{NEW_CARD, OPENCARD, randomColor, randomValue}
import utils._

class Controller extends ControllerInterface {
  private val validatorFactory: ValidatorFactoryInterface = new ValidatorFactory
  private val undoManager = new UndoManager[Controller]

  def createCard: Card = Card(randomColor + 1, randomValue + 1)
   private def createPlayerCardDeck(numberOfPlayers: Int): PlayerCardDeck = new PlayerCardDeck(
    List.fill(numberOfPlayers)(createCheat))
  def nextPlayer(currentPlayer: Int, numberOfPlayers: Int): Int = (currentPlayer + 1) % numberOfPlayers
  def createInitialTurnData(numberOfPlayers:Int, currentPlayer:Int): TurnData = TurnData(
    currentPlayer,
    createPlayerCardDeck(numberOfPlayers),
    createCard,
    new DiscardedCardDeck(List.fill(numberOfPlayers)(None)))

  def createNewRound(r:RoundData, cardStashes: List[List[Card]], discarded:List[Boolean]):RoundData = {
    def updateValidators() = r.validators.indices.map { idx =>
      if(discarded(idx))
        validatorFactory.getValidator(r.validators(idx).getNumberOfPhase() + 1)
      else
        r.validators(idx)
    }.toList
    def countErrorpoints = r.errorPoints.indices.map(idx =>
      r.errorPoints(idx) + cardStashes(idx).map(c =>
        c.errorPoints).sum).toList
    RoundData(updateValidators(), countErrorpoints)
  }

  def createCheat = List(Card(1,11),Card(2,11),Card(4,11),Card(3,7),Card(1,7),Card(4,7), Card(1,11),Card(2,11), Card(1,7),Card(4,7))

  def getInitialState():ControllerStateInterface = new InitialState(validatorFactory)

  private var state:ControllerStateInterface = getInitialState()
  def getState: ControllerStateInterface = state

  def getGameData: (RoundData, TurnData) = {
    val s = state.asInstanceOf[GameRunningControllerStateInterface]
    (s.r, s.t)
  }

  def reset_undo_manager(): Unit = undoManager.reset()

  def getPlayers(): List[String] = state.asInstanceOf[GameRunningControllerStateInterface].players

  def solve(e: InputEvent, executePlatform_runLater:Boolean = true):ControllerStateInterface = {
    val command = e match {
      case e1: DoCreatePlayerEvent => new CreatePlayerCommand(e1.players, state)
      case e2: DoSwitchCardEvent => new SwitchCardCommand(e2.index, e2.mode, state)
      case e3: DoDiscardEvent => new DiscardCommand(e3.indices, state)
      case _: DoNoDiscardEvent => new NoDiscardCommand(state)
      case e5: DoInjectEvent => new InjectCommand(e5.receiving_player, e5.cardIndex, e5.stashIndex, e5.position, state)
      case _: DoNoInjectEvent => new NoInjectCommand(state)
    }
    val res = undoManager.doStep(command, this)
    state = res._1
    notifyObservers(res._2)
    state
  }

  def undo:ControllerStateInterface = {
    val res = undoManager.undoStep(this)
    state = res._1
    notifyObservers(res._2)
    state
  }
}

class InitialState(validator: ValidatorFactoryInterface) extends ControllerStateInterface {
  def createPlayers(pPlayers: List[String], controller:Controller): (ControllerStateInterface, OutputEvent) = {
    def numberOfPlayers = pPlayers.size
    val newCard = controller.createCard
    (new SwitchCardControllerState(pPlayers,
      RoundData(List.fill(numberOfPlayers)(validator.getValidator(1)), List.fill(numberOfPlayers)(0)),
      controller.createInitialTurnData(numberOfPlayers, 0),
      newCard),
      new GameStartedEvent(newCard))
  }
}


class SwitchCardControllerState(pPlayers: List[String], pR:RoundData, pT:TurnData, pNewCard:Card) extends GameRunningControllerStateInterface {
  override val players: List[String] = pPlayers
  override val r: RoundData = pR
  override val t: TurnData = pT
  val newCard: Card = pNewCard

  def switchCards(index: Int, mode: Int):(GameRunningControllerStateInterface, OutputEvent) = {
    val resNewPlayerCardDeck = {
      if (mode == NEW_CARD) t.playerCardDeck.switchCard(currentPlayer, index, newCard)
      else if(mode == OPENCARD) t.playerCardDeck.switchCard(currentPlayer, index, t.openCard)
      else throw new IllegalArgumentException
    }

    def newPlayerCardDeck = resNewPlayerCardDeck._1
    def newOpenCard = resNewPlayerCardDeck._2

    def newTurnData = TurnData(t.current_player, newPlayerCardDeck, newOpenCard, t.discardedCardDeck)

    if(t.discardedCardDeck.isEmpty(currentPlayer))
      (new DiscardControllerState(players, r, newTurnData), new GoToDiscardEvent)
    else
      (new InjectControllerState(players, r, newTurnData), new GoToInjectEvent)
  }
}


class DiscardControllerState(pPlayers: List[String], pR:RoundData, pT:TurnData) extends GameRunningControllerStateInterface {
  override val players: List[String] = pPlayers
  override val r: RoundData = pR
  override val t: TurnData = pT

  private def nextPlayerOnly(c:Controller) = TurnData(c.nextPlayer(t.current_player, players.size), t.playerCardDeck, t.openCard, t.discardedCardDeck)

  def discardCards(cardIndices: List[List[Int]], controller: Controller): (GameRunningControllerStateInterface, OutputEvent) = {
    val (newTurnData, success): (TurnData, Boolean) =
      if (r.validators(currentPlayer).validate(t.playerCardDeck.cards(currentPlayer), cardIndices)) {
        val res = t.playerCardDeck.removeCards(cardIndices, currentPlayer)
        def newPlayerCardDeck = res._1
        def removedCards = res._2
        def newDiscardedCardDeck = t.discardedCardDeck.setCards(currentPlayer, removedCards)
        (TurnData(controller.nextPlayer(currentPlayer, players.size), newPlayerCardDeck, t.openCard, newDiscardedCardDeck), true)
      } else {
        (nextPlayerOnly(controller), false)
      }
    val newCard = controller.createCard
    (new SwitchCardControllerState(players, r, newTurnData, newCard), new TurnEndedEvent(newCard, success))
  }

  def skipDiscard(controller:Controller): (SwitchCardControllerState, TurnEndedEvent) = {
    val newCard = controller.createCard
    (new SwitchCardControllerState(players, r, nextPlayerOnly(controller), newCard), new TurnEndedEvent(newCard, true))
  }
}

class InjectControllerState(pPlayers: List[String], pR:RoundData, pT:TurnData) extends GameRunningControllerStateInterface {
  override val players: List[String] = pPlayers
  override val r: RoundData = pR
  override val t: TurnData = pT

  private def newTurnDataNextPlayer(c:Controller) = TurnData(c.nextPlayer(t.current_player, players.size), t.playerCardDeck, t.openCard, t.discardedCardDeck)

  def injectCard(receiving_player:Int, cardIndex:Int, stashIndex:Int, position:Int, controller:Controller): (ControllerStateInterface, OutputEvent) = {
    def newCard = controller.createCard
    def subStash = t.discardedCardDeck.cards(receiving_player).get(stashIndex)
    def canAppend = t.discardedCardDeck.cards(receiving_player).nonEmpty &&
      r.validators(receiving_player).canAppend(subStash, t.playerCardDeck.cards(currentPlayer)(cardIndex), stashIndex, position)

    if(canAppend) {
      val resCardRemoved = t.playerCardDeck.removeSingleCard(cardIndex, currentPlayer)
      def newPlayerCardDeck = resCardRemoved._1
      def cardToInject = resCardRemoved._2

      //create new Round when player has no cards anymore
      if(newPlayerCardDeck.isEmpty(currentPlayer)) {
        if (newPlayerCardDeck.isEmpty(currentPlayer))
          return handle_round_ended(controller, newCard)
      }

      val newDiscardedDeck = t.discardedCardDeck.appendCard(cardToInject, receiving_player, stashIndex, position)

      def newTurnData = TurnData(t.current_player, newPlayerCardDeck, t.openCard, newDiscardedDeck)
      (new InjectControllerState(players, r, newTurnData), new GoToInjectEvent)
    } else
      (new SwitchCardControllerState(players, r, newTurnDataNextPlayer(controller), newCard), new TurnEndedEvent(newCard, false))
  }

  private def getWinningPlayer(playersHaveDiscarded: List[Boolean], newErrorpoints: List[Int]): Int = {
    r.validators.zipWithIndex
      .filter(v => v._1.getNumberOfPhase() == 10 && playersHaveDiscarded(v._2))
      .map(v => v._2)
      .minBy(idx => newErrorpoints(idx))
  }

  private def handle_round_ended(controller: Controller, newCard: Card): (ControllerStateInterface, OutputEvent) = {
    def playersHaveDiscarded = players.indices.map(idx => !t.discardedCardDeck.isEmpty(idx)).toList

    val (newDeck, _) = t.playerCardDeck.removeSingleCard(0, currentPlayer)

    if (r.validators.zipWithIndex.find(v => v._1.getNumberOfPhase() == 10 && playersHaveDiscarded(v._2)).nonEmpty) {
      controller.reset_undo_manager()
      val newErrorPoints = r.errorPoints.zipWithIndex.map((e) => e._1 + newDeck.getErrorpoints(e._2))
      val event = new GameEndedEvent(
        players(getWinningPlayer(playersHaveDiscarded, newErrorPoints)),
        players,
        r.validators.zipWithIndex.map(v=>
          if (playersHaveDiscarded(v._2)) v._1.getNumberOfPhase()
          else v._1.getNumberOfPhase() - 1),
        newErrorPoints) //add new error points
      (controller.getInitialState(), event)
    } else {
      val newState = new SwitchCardControllerState(
        players,
        controller.createNewRound(r, newDeck.cards, playersHaveDiscarded),
        controller.createInitialTurnData(players.size, controller.nextPlayer(t.current_player, players.size)),
        newCard)
      (newState, new NewRoundEvent(newCard, true))
    }
  }
  
  def skipInject(controller:Controller): (SwitchCardControllerState, TurnEndedEvent) = {
    val newCard = controller.createCard
    (new SwitchCardControllerState(players, r, newTurnDataNextPlayer(controller), newCard), new TurnEndedEvent(newCard, true))
  }
}
