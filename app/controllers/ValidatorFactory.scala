package controllers

import model.Card
import utils.Utils

object GroupType extends Enumeration {
  val MULTIPLES, SAME_COLOR, SEQUENCE = Value
}

class ValidatorFactory() extends ValidatorFactoryInterface {
  def getValidator(index : Int): ValidatorStrategy = index match {
    case 1 => new Phase1Validator()
    case 2 => new Phase2Validator()
    case 3 => new Phase3Validator()
    case 4 => new Phase4Validator()
    case 5 => new Phase5Validator()
    case 6 => new Phase6Validator()
    case 7 => new Phase7Validator()
    case 8 => new Phase8Validator()
    case 9 => new Phase9Validator()
    case 10 => new Phase10Validator()
  }
}

protected class CardGroup(val groupType:GroupType.Value, val numberOfCards:Int)

protected abstract class ValidatorStrategy(val numberOfPhase:Int) extends ValidatorStrategyInterface {
  protected val cardGroups: List[CardGroup]
  def group_types = cardGroups.map(cg => cg.groupType)
  def getNumberOfPhase(): Int = numberOfPhase
  def getNumberOfInputs(): List[Int] = cardGroups.map(cg => cg.numberOfCards)

  def validate(cards: List[Card], selectedCardIndexes: List[List[Int]]): Boolean = {
    //no cards-index selected multiple
    if (!Utils.indexesUnique(selectedCardIndexes.flatten))
      return false

    val card_stashes = selectedCardIndexes.map(l => l.map(n => cards(n)))
    cardGroups.indices.foreach { idx => {
      def subList = card_stashes(idx)
      def number_of_cards = cardGroups(idx).numberOfCards

      def enoughCards = subList.size == number_of_cards
      def validateCardGroup: Boolean = group_types(idx) match {
        case GroupType.SEQUENCE => Utils.resolveSequence(subList)
        case GroupType.MULTIPLES => Utils.resolveMultiples(subList)
        case GroupType.SAME_COLOR => Utils.resolveSameColor(subList)
      }
      if (!(enoughCards && validateCardGroup)) return false
    }}
    true
  }

  def canAppend(cards:List[Card], cardToInject:Card, stashIndex:Int, position:Int): Boolean = {
    group_types(stashIndex) match {
      case GroupType.SEQUENCE => Utils.fitToSequence(cards, cardToInject, position)
      case GroupType.MULTIPLES => cards.head.value == cardToInject.value
      case GroupType.SAME_COLOR => cards.head.color == cardToInject.color
    }
  }
}

private class Phase1Validator extends ValidatorStrategy(1) {
  override protected val cardGroups: List[CardGroup] = List(new CardGroup(GroupType.MULTIPLES, 3), new CardGroup(GroupType.MULTIPLES, 3))
  override def description: String = "Zwei Drillinge"
}

private class Phase2Validator extends ValidatorStrategy(2) {
  override protected val cardGroups: List[CardGroup] = List(new CardGroup(GroupType.MULTIPLES, 3), new CardGroup(GroupType.SEQUENCE, 4))
  override def description: String = "Drilling und Viererfolge"
}

private class Phase3Validator extends ValidatorStrategy(3) {
  override protected val cardGroups: List[CardGroup] = List(new CardGroup(GroupType.MULTIPLES, 4), new CardGroup(GroupType.SEQUENCE, 4))
  override def description: String = "Vierling und Viererfolge"
}

private class Phase4Validator extends ValidatorStrategy(4) {
  override protected val cardGroups: List[CardGroup] = List(new CardGroup(GroupType.SEQUENCE, 7))

  override def description: String = "Siebenerfolge"
}

private class Phase5Validator extends ValidatorStrategy(5) {
  override protected val cardGroups: List[CardGroup] = List(new CardGroup(GroupType.SEQUENCE, 8))
  override def description: String = "Achterfolge"
}

private class Phase6Validator extends ValidatorStrategy(6) {
  override protected val cardGroups: List[CardGroup] = List(new CardGroup(GroupType.SEQUENCE, 9))
  override def description: String = "Neunerfolge"
}

private class Phase7Validator extends ValidatorStrategy(7) {
  override protected val cardGroups: List[CardGroup] = List(new CardGroup(GroupType.MULTIPLES, 4), new CardGroup(GroupType.MULTIPLES, 4))
  override def description: String = "Zwei Vierlinge"
}

private class Phase8Validator extends ValidatorStrategy(8) {
  override protected val cardGroups: List[CardGroup] = List(new CardGroup(GroupType.SAME_COLOR, 7))
  override def description: String = "Sieben Karten einer Farbe"
}

private class Phase9Validator extends ValidatorStrategy(9) {
  override protected val cardGroups: List[CardGroup] = List(new CardGroup(GroupType.MULTIPLES, 5), new CardGroup(GroupType.MULTIPLES, 2))
  override def description: String = "Fünfling und Zwilling"
}
private class Phase10Validator extends ValidatorStrategy(10) {
  override protected val cardGroups: List[CardGroup] = List(new CardGroup(GroupType.MULTIPLES, 5), new CardGroup(GroupType.MULTIPLES, 3))
  override def description: String = "Fünfling und Drilling"
}
