package controllers

import javax.inject._
import play.api.mvc._
import utils.{DoCreatePlayerEvent, ProgramStartedEvent}
import views.TUI
import play.twirl.api.Html

@Singleton
class Phase10WebController @Inject()(cc: ControllerComponents) extends AbstractController(cc) {
  val c = new Controller
  val tui = new TUI(c)
  c.add(tui)
  c.notifyObservers(new ProgramStartedEvent) //set correct state in TUI
  //tui.handle_input("PlayerA PlayerB")

  def about = Action {
    Ok(views.html.index())
  }

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

  def phase10 = Action {
    def state = c.getState
    if (state.isInstanceOf[InitialState]) {
      Ok(views.html.home())
    } else {
      val html = Html.apply(tui.get_last_output.replace("\n", "<br>"))
      Ok(views.html.game(html))
    }
  }

  def submitInput(x: String): Action[AnyContent] = {
    tui.handle_input(x)
    phase10
  }
}