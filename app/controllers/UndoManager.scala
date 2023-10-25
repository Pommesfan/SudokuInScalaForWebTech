package controllers

import utils.{OutputEvent, ProgramStartedEvent}

class UndoManager[C <: ControllerInterface] {
  private var undoStack: List[CommandTemplate[C]] = Nil
  private var redoStack: List[CommandTemplate[C]] = Nil

  def doStep(command: CommandTemplate[C], c:C):(ControllerStateInterface, OutputEvent) = {
    undoStack = command :: undoStack
    command.doStep(c)
  }

  def undoStep(c:C):(ControllerStateInterface, OutputEvent) = {
    undoStack match {
      case Nil => (c.getInitialState(), new ProgramStartedEvent)
      case head :: stack => {
        val res = head.undoStep(c)
        undoStack = stack
        redoStack = head :: redoStack
        res
      }
    }
  }

  def reset() = {
    undoStack = Nil
    redoStack = Nil
  }
}
