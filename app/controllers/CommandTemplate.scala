package controllers

import utils.OutputEvent

trait CommandTemplate[C <: ControllerInterface] {
  def doStep(c: C): (ControllerStateInterface, OutputEvent)

  def undoStep(c: C): (ControllerStateInterface, OutputEvent)
}
