package controllers

import javax.inject._
import play.api.mvc._
import utils.ProgramStartedEvent
import views.TUI
import play.twirl.api.Html

@Singleton
class Phase10WebController @Inject()(cc: ControllerComponents) extends AbstractController(cc) {
  val c = new Controller
  val tui = new TUI(c)
  c.add(tui)
  c.notifyObservers(new ProgramStartedEvent) //set correct state in TUI
  tui.handle_input("PlayerA PlayerB")

  def about = Action {
    Ok(views.html.index())
  }

  def help = Action {
    Ok(views.html.help())
  }

  def phase10 = Action {
    val html = Html.apply(tui.get_last_output.replace("\n", "<br>"))
    Ok(views.html.game(html))
  }

  def submitInput(x: String): Action[AnyContent] = {
    tui.handle_input(x)
    phase10
  }
}