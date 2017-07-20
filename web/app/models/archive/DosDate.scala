package models.archive

import java.util.Calendar
import java.util.Calendar._

/** Convert a date and time to DOS format */
class DosDate(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int) {
  val date: Short = (day | (month << 5) | ((year - 1980) << 9)).toShort
  val time: Short = ((second / 2) | (minute << 5) | (hour << 11)).toShort
}

object DosDate {
  def apply(date: Calendar): DosDate = new DosDate(
    date.get(YEAR),
    date.get(MONTH),
    date.get(DATE),
    date.get(HOUR_OF_DAY),
    date.get(MINUTE),
    date.get(SECOND)
  )

  def now: DosDate = DosDate(Calendar.getInstance()) // .zip is not timezone-aware. Oh well.
}
