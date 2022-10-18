package controllers

import model.Card

trait ValidatorStrategyInterface {

  def description: String

  def getNumberOfPhase(): Int

  def getNumberOfInputs(): List[Int]

  def validate(cards: List[Card], selectedCardIndexes: List[List[Int]]): Boolean

  def canAppend(cards: List[Card], cardToInject: Card, stashIndex: Int, position: Int): Boolean
}

trait ValidatorFactoryInterface {
  def getValidator(index: Int): ValidatorStrategyInterface
}