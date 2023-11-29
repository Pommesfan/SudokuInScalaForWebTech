package controllers

import utils._

class CreatePlayerCommand(players:List[String], oldState: ControllerStateInterface) extends CommandTemplate[Controller] {
  override def doStep(c: Controller): (ControllerStateInterface, OutputEvent) = oldState.asInstanceOf[InitialState].createPlayers(players, c)

  override def undoStep(c: Controller): (ControllerStateInterface, OutputEvent) = (oldState, new ProgramStartedEvent)
}

class SwitchCardCommand(index:Int, mode:Int, oldState: ControllerStateInterface) extends CommandTemplate[Controller] {
  override def doStep(c: Controller): (ControllerStateInterface, OutputEvent) = oldState.asInstanceOf[SwitchCardControllerState].switchCards(index, mode)

  override def undoStep(c: Controller): (ControllerStateInterface, OutputEvent) = (oldState, new TurnEndedEvent(oldState.asInstanceOf[SwitchCardControllerState].newCard, true))
}

class DiscardCommand(indices: List[List[Int]], oldState: ControllerStateInterface) extends CommandTemplate[Controller] {
  override def doStep(c: Controller): (ControllerStateInterface, OutputEvent) = oldState.asInstanceOf[DiscardControllerState].discardCards(indices, c)

  override def undoStep(c: Controller): (ControllerStateInterface, OutputEvent) = (oldState, new GoToDiscardEvent)
}

class NoDiscardCommand(oldState: ControllerStateInterface) extends CommandTemplate[Controller] {
  override def doStep(c: Controller): (ControllerStateInterface, OutputEvent) = oldState.asInstanceOf[DiscardControllerState].skipDiscard(c)

  override def undoStep(c: Controller): (ControllerStateInterface, OutputEvent) = (oldState, new GoToDiscardEvent)
}

class InjectCommand(receiving_player:Int, cardIndex:Int, stashIndex:Int, position:Int, oldState: ControllerStateInterface) extends CommandTemplate[Controller] {
  override def doStep(c: Controller): (ControllerStateInterface, OutputEvent) = oldState.asInstanceOf[InjectControllerState].injectCard(receiving_player, cardIndex, stashIndex, position, c)

  override def undoStep(c: Controller): (ControllerStateInterface, OutputEvent) = (oldState, new GoToInjectEvent)
}

class NoInjectCommand(oldState: ControllerStateInterface) extends CommandTemplate[Controller] {
  override def doStep(c: Controller): (ControllerStateInterface, OutputEvent) = oldState.asInstanceOf[InjectControllerState].skipInject(c)

  override def undoStep(c: Controller): (ControllerStateInterface, OutputEvent) = (oldState, new GoToInjectEvent)
}
