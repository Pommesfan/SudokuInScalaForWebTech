package model

case class Card(color:Int, value:Int) {
  override def toString: String = {
    def colorName: String = color match {
      case 1 => "Rot"
      case 2 => "Gelb"
      case 3 => "Blau"
      case 4 => "Grün"
    }

    "Farbe: " + colorName + "; Wert = " + value.toString
  }

  def errorPoints = if (value < 10) 5 else 10
}
