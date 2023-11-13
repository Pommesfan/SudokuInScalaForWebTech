package utils

import model.Card

trait Event

trait InputEvent extends Event

class DoCreatePlayerEvent(val players:List[String]) extends InputEvent
class DoSwitchCardEvent(val index:Int, val mode:Int) extends InputEvent
class DoDiscardEvent(val indices: List[List[Int]]) extends InputEvent
class DoNoDiscardEvent extends InputEvent
class DoInjectEvent(val receiving_player:Int, val cardIndex:Int, val stashIndex:Int, val position:Int) extends InputEvent
class DoNoInjectEvent extends InputEvent


trait OutputEvent extends Event

class ProgramStartedEvent extends OutputEvent
class GoToDiscardEvent extends OutputEvent
class GoToInjectEvent extends OutputEvent
class TurnEndedEvent(val newCard: Card, val success: Boolean) extends OutputEvent
class NewRoundEvent(val newCard: Card, val success: Boolean) extends OutputEvent

class GameEndedEvent(val winningPlayer: String, val players: List[String], val phases: List[Int], val errorPoints: List[Int]) extends OutputEvent
class GameStartedEvent(val newCard: Card) extends OutputEvent
