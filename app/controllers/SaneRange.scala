package controllers

object SaneRange {
  
  def apply(start: Int, end: Int) : (Int, Int) = {
    val saneStart = if (start >= 0) start else 0
    val saneEnd = if (end > saneStart) end else saneStart + 1
    
    (saneStart, saneEnd)
  }

}