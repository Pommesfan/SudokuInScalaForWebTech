package utils

import scala.util.Random
import model.Card

object Utils {
  val MULTIPLES = 1
  val SEQUENCE = 2
  val SAME_COLOR = 3

  val INJECT_TO_FRONT = 1
  val INJECT_AFTER = 2
  val NEW_CARD = 1
  val OPENCARD = 2

  val cardWidth = 120.0
  val cardProportion = 1.5
  val NumberSizeProportion = 1.0
  val space_between_cardstashes = 8

  private val r = new Random()
  def randomColor = r.nextInt(4)
  def randomValue = r.nextInt(12)

  def inverseIndexList(indexList:List[Int], maxIndex:Int): List[Int] =
    List.range(0, 10).partition(n => !indexList.contains(n))._1

  def indexesUnique(l:List[Int]): Boolean = {
    val sorted = l.sortWith((a,b) => a < b)
    for(i <- 0 until sorted.size - 1) {
      if(sorted(i) == sorted(i + 1))
        return false
    }
    true
  }

  def resolveMultiples(cards : List[Card]): Boolean = {
    val commonValue = cards.head.value
    for (c <- cards) {
      if(c.value != commonValue){
        return false
      }
    }
    true
  }

  def resolveSequence(cards: List[Card]): Boolean = {
    var i = cards.head.value
    def increment(): Unit = if (i == 12) i = 1 else i += 1
    for (index <- 1 until cards.size) {
      increment()
      if (cards(index).value != i) return false
    }
    true
  }

  def resolveSameColor(cards: List[Card]): Boolean = {
    val commonColor = cards.head.color
    for(c <- cards) {
      if (c.color != commonColor) return false
    }
    true
  }

  def makeGroupedIndexList(indices:String):List[List[Int]] =
    indices.split(":").toList.map { s =>
      s.trim.split(" ").map(n => n.toInt).toList
    }
    
  def groupCardIndexes(indices:List[Int], numberOfInputs:List[Int]):List[List[Int]] = {
    var start = 0
    var list = List[List[Int]]()
    for(i <- numberOfInputs) {
      list = list :+ indices.slice(start, start + i)
      start = i
    }
    list
  }

  def fitToSequence(cards: List[Card], cardToInject:Card, position: Int):Boolean = {
    def isInSequence(a:Int, b:Int) = a == 12 && b == 1 || b - a == 1
    if(position == INJECT_TO_FRONT)
      isInSequence(cardToInject.value, cards.head.value)
    else if(position == INJECT_AFTER)
      isInSequence(cards.last.value, cardToInject.value)
    else
      throw new IllegalArgumentException
  }


  abstract class IndexListener {
    val index:Int
    def onListen(index:Int): Unit
  }
}
