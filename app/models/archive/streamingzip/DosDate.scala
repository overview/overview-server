package models.archive.streamingzip

import java.util.Calendar
import java.util.Calendar._

class DosDate(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int) {

  val date: Int =
    day << 0 |
    month << 5 |
    (year - 1980) << 9
    
    val time: Int =
      (second / 2) << 0 | 
      minute << 5 |       
      hour << 11          

}

object DosDate {
  def apply(date: Calendar): DosDate = new DosDate(
    date.get(YEAR),
    date.get(MONTH),
    date.get(DATE),
    date.get(HOUR_OF_DAY),
    date.get(MINUTE),
    date.get(SECOND))
}