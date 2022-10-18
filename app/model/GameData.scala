package model

import controllers.ValidatorStrategyInterface

case class RoundData(validators:List[ValidatorStrategyInterface], errorPoints:List[Int])
case class TurnData(current_player:Int, playerCardDeck: PlayerCardDeck, openCard:Card, discardedCardDeck: DiscardedCardDeck)
