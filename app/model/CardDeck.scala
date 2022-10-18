package model

import utils.Utils
import utils.Utils.{INJECT_AFTER, INJECT_TO_FRONT}

class PlayerCardDeck(val cards: List[List[Card]]) {
  def removeSingleCard(index:Int, player:Int): (PlayerCardDeck, Card) = {
    def newStash = new PlayerCardDeck(cards.updated(player, cards(player).patch(index, Nil, 1)))
    def oldCard = cards(player)(index)
    (newStash, oldCard)
  }

  def removeCards(indices:List[List[Int]], player:Int): (PlayerCardDeck, List[List[Card]]) = {
    def toRemove = indices.map(idx => idx.map(idx2 => cards(player)(idx2)))
    def remaining = Utils.inverseIndexList(indices.flatten, 10).map(idx => cards(player)(idx))
    def newStash = new PlayerCardDeck(cards.updated(player, remaining))
    (newStash, toRemove)
  }

  def switchCard(player: Int, index: Int, c:Card): (PlayerCardDeck, Card) = {
    val old = cards(player)(index)
    (new PlayerCardDeck(cards.updated(player, cards(player).updated(index, c))), old)
  }

  def isEmpty(player:Int) = cards(player).isEmpty
}

class DiscardedCardDeck(val cards: List[Option[List[List[Card]]]]) {
  def setCards(player:Int, pCards: List[List[Card]]) = {
    if(cards(player).nonEmpty)
      throw new UnsupportedOperationException
    else
      new DiscardedCardDeck(cards.updated(player, Some(pCards)))
  }

  def isEmpty(player:Int) = cards(player).isEmpty

  def appendCard(card:Card, receiving_player:Int, stashIndex:Int, position:Int) = {
    if(isEmpty(receiving_player))
      throw new UnsupportedOperationException
    else {
      val discardedCards = cards(receiving_player).get
      def subStash = discardedCards(stashIndex)
      def newSubStash = position match {
        case INJECT_TO_FRONT => card :: subStash
        case INJECT_AFTER => (card :: (subStash).reverse).reverse
        case _ => throw new IllegalArgumentException
      }
      def newPlayerStash = discardedCards.updated(stashIndex, newSubStash)
      def newDiscardedStashList = cards.updated(receiving_player, Some(newPlayerStash))
      new DiscardedCardDeck(newDiscardedStashList)
    }
  }
}