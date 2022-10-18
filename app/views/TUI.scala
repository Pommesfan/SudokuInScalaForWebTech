package views

import controllers.ControllerInterface
import model.{Card, DiscardedCardDeck, RoundData, TurnData}
import utils.Utils._
import utils._

import java.util.Scanner
import scala.util.{Failure, Success, Try}

class TUI(controller: ControllerInterface) extends Observer {
  val CREATE_PLAYERS = 1
  val SWITCH = 2
  val DISCARD = 3
  val INJECT = 4

  private var mode = 0

  val sc = new Scanner(System.in)

  def start(): Unit =
    controller.add(this)
    new Thread {
      override def run(): Unit =
        while (true) {
          val input = sc.nextLine()
          input match {
            case "undo" => controller.undo
            case "exit" => System.exit(0)
            case _ =>
              val inputEvent_try = Try(createInputEvent(input, mode))
              inputEvent_try match {
                case Success(inputEvent) => controller.solve(inputEvent)
                case Failure(inputEvent) => println("Eingaben ungültig")
              }
          }
        }
    }.start()

  def createInputEvent(input:String, mode:Int): InputEvent = mode match {
    case CREATE_PLAYERS => new DoCreatePlayerEvent(input.split(" ").toList)
    case SWITCH => {
      val inputs = input.split(" ").toList
      def index = inputs(0)
      def mode = inputs(1) match {
        case "new" => NEW_CARD
        case "open" => OPENCARD
      }
      new DoSwitchCardEvent(inputs(0).toInt, mode)
    }
    case DISCARD => {
      if (input == "n")
        new DoNoDiscardEvent
      else
        new DoDiscardEvent(getCardsToDiscard(input))
    }
    case INJECT => {
      if(input == "n")
        new DoNoInjectEvent
      else {
        val l = input.split(" ")
        val pos = if (l(3) == "FRONT") INJECT_TO_FRONT else if (l(3) == "AFTER") INJECT_AFTER else throw new IllegalArgumentException
        new DoInjectEvent(l(0).toInt, l(1).toInt, l(2).toInt, pos)
      }
    }
  }

  def update(e: OutputEvent): String = {
    val s = e match {
      case e: ProgramStartedEvent => {
        mode = CREATE_PLAYERS
        "Namen eingeben:"
      }
      case e: OutputEvent =>
        val g = controller.getGameData
        val r = g._1
        val t = g._2
        def currentPlayer = t.current_player
        val playerName = controller.getPlayers()
        e match {
          case e1: GoToInjectEvent => {
            mode = INJECT
            printDiscardedCards(playerName, t.discardedCardDeck) + printCards(t.playerCardDeck.cards(currentPlayer)) +
              "\nKarten anlegen? Angabe: Spieler, Karte, Stapel, Position (FRONT/AFTER)"
          }
          case e2: GoToDiscardEvent => {
            mode = DISCARD
            printCards(t.playerCardDeck.cards(currentPlayer)) +
              "\nAbzulegende Karten angeben oder n für nicht ablegen:"
          }
          case e3: TurnEndedEvent => {
            mode = SWITCH
            printNewTurn(playerName, t, e3.newCard)
          }
          case e4: NewRoundEvent => {
            mode = SWITCH
            printNewRound(playerName, r) + printNewTurn(playerName, t, e4.newCard)
          }
          case e5: GameStartedEvent => {
            mode = SWITCH
            printNewRound(playerName, r) + printNewTurn(playerName, t, e5.newCard)
          }
        }
    }
    println(s)
    s
  }

  def printNewTurn(playerNames:List[String], t:TurnData, newCard:Card):String = {
    printDiscardedCards(playerNames, t.discardedCardDeck) + printPlayerStatus(playerNames(t.current_player), t.playerCardDeck.cards(t.current_player), t.openCard, newCard) +
      "\nAuszutauschende Karte angeben + Offenliegende oder neue nehmen (open/new)"
  }

  def printNewRound(playerNames:List[String], r:RoundData): String = {
    val s = new StringBuilder
    s.append("\nNeue Runde\n")
    playerNames.indices.foreach { idx =>
      def v = r.validators(idx)
      s.append(playerNames(idx) + ": " + r.errorPoints(idx).toString + " Fehlerpunkte; Phase: " + v.getNumberOfPhase().toString + ": " + v.description + "\n")
    }
    s.toString()
  }

  def printPlayerStatus(player: String, cards: List[Card], openCard: Card, newCard:Card) : String = {
    val sb = new StringBuilder
    sb.append("Aktueller Spieler: " + player)
    sb.append("\n\nNeue Karte:\n")
    sb.append(newCard)
    sb.append("\nOffenliegende Karte:\n")
    sb.append(openCard)
    sb.append("\n\n" + printCards(cards))
    sb.toString()
  }

  def printCards(cards: List[Card]): String = {
    val sb = new StringBuilder
    sb.append("Karten des Spielers:\n")
    cards.zipWithIndex.foreach(c => sb.append(c._2.toString + ": " + c._1.toString + '\n'))
    sb.toString()
  }

  def printDiscardedCards(playerNames:List[String], discardedCardDeck: DiscardedCardDeck): String = {
    val sb = new StringBuilder
    sb.append("-"*32 + '\n')
    sb.append("Abgelegte Karten\n")
    for(idx <- playerNames.indices) {
      sb.append(playerNames(idx) + "\n")
      discardedCardDeck.cards(idx) match {
        case s: Some[List[List[Card]]] => {
          s.get.foreach { c =>
            c.zipWithIndex.foreach(c => sb.append(c._2.toString + ": " + c._1.toString + '\n'))
          }
        }
        case None => sb.append("Keine Karten\n")
      }
    }
    sb.append("\n")
    sb.toString()
  }


  def getCardsToDiscard(input: String):List[List[Int]] = {
    val g = controller.getGameData
    def r = g._1
    def t = g._2
    def numberOfInputs = r.validators(t.current_player).getNumberOfInputs()
    makeGroupedIndexList(input, numberOfInputs)
  }
}
