package controllers

import model.{RoundData, TurnData}
import utils.{InputEvent, Observable}

trait ControllerInterface extends Observable {
  def getState: ControllerStateInterface

  def getGameData: (RoundData, TurnData)

  def getInitialState(): ControllerStateInterface

  def getPlayers(): List[String]

  def solve(e: InputEvent, executePlatform_runLater: Boolean = true): ControllerStateInterface

  def undo: ControllerStateInterface
  
}


trait ControllerStateInterface


trait GameRunningControllerStateInterface extends ControllerStateInterface {
  val players: List[String]
  val r: RoundData
  val t: TurnData

  def currentPlayer = t.current_player
}