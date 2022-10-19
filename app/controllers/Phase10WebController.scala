package controllers

import javax.inject._
import play.api.mvc._
import utils.ProgramStartedEvent
import views.TUI

@Singleton
class Phase10WebController @Inject()(cc: ControllerComponents) extends AbstractController(cc) {
  val c = new Controller
  val tui = new TUI(c)
  c.add(tui)
  c.notifyObservers(new ProgramStartedEvent) //set correct state in TUI
  tui.handle_input("PlayerA PlayerB")
  def phase10AsText =  tui.get_last_output

  def about= Action {
    Ok(views.html.index())
  }

  def sudoku = Action {
    Ok(tui.get_last_output)
  }

}