package controllers

import javax.inject._
import play.api.mvc._
import de.htwg.se.sudoku.Sudoku
import de.htwg.se.sudoku.controller.controllerComponent.GameStatus
import controllers.Controller
import utils.ProgramStartedEvent
import views.TUI

@Singleton
class SudokuController @Inject()(cc: ControllerComponents) extends AbstractController(cc) {
  val c = new Controller
  val tui = new TUI(c)
  c.add(tui)
  c.notifyObservers(new ProgramStartedEvent) //set correct state in TUI
  tui.handle_input("PlayerA PlayerB")
  val gameController = Sudoku.controller
  def sudokuAsText =  gameController.gridToString + GameStatus.message(gameController.gameStatus)

  def about= Action {
    Ok(views.html.index())
  }

  def sudoku = Action {
    Ok(tui.get_last_output)
  }

}