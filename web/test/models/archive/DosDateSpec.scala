package models.archive

import java.util.Calendar
import org.specs2.mutable.Specification

class DosDateSpec extends Specification {
  "DosDate" should {
    "convert a calendar date to DOS date format" in {
      val date = Calendar.getInstance()
      date.set(2015, 11, 17, 17, 43, 20)
      val dosDate = DosDate(date)
      dosDate.date must beEqualTo(18289.toShort)
      dosDate.time must beEqualTo(-29334.toShort)
    }
  }
}
