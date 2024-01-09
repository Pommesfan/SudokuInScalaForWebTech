package utils

import model.{RoundData, TurnData}

object Phase10WebUtils {
  def sort_sequences(cards: List[List[Int]], r: RoundData, t: TurnData): List[List[Int]] = {
    val card_types = r.validators(t.current_player).getCardGroups()

    def playerCards = t.playerCardDeck.cards(t.current_player)

    def detectBound(l: List[Int]): Int = {
      for (i <- 0 until l.size - 1) {
        val j = i + 1
        val a = playerCards(l(i))
        val b = playerCards(l(j))
        if (a.value + 1 != b.value) {
          return j
        }
      }
      -1
    }

    def shiftCards(l: List[Int]): List[Int] = {
      val bound = detectBound(l)
      if (bound == -1) {
        l
      } else {
        (0 until l.size).map(idx => l((idx + bound) % l.size)).toList
      }
    }

    cards.zipWithIndex.map { e =>
      def c = e._1
      def n = e._2

      if (card_types(n) == Utils.SEQUENCE) {
        val a = c.sortWith((c1, c2) => playerCards(c1).value < playerCards(c2).value)
        val b = shiftCards(a)
        b
      } else {
        c
      }
    }
  }
}
