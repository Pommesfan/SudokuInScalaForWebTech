package controllers

import javax.inject._

import play.api.mvc._
import de.htwg.se.sudoku.Sudoku
import de.htwg.se.sudoku.controller.controllerComponent.GameStatus

@Singleton
class SudokuController @Inject()(cc: ControllerComponents) extends AbstractController(cc) {
  val gameController = Sudoku.controller
  def sudokuAsText =  gameController.gridToString + GameStatus.message(gameController.gameStatus)

  def about= Action {
    Ok(views.html.index())
  }

  def sudoku = Action {
    Ok(sudokuAsText)
  }

}