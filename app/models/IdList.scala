package models

object IdList {
  
  def apply(ids: String) : Seq[Long] = ids match {
    case "" => Nil
    case _ => {
      val values = ids.split("""\s*,\s*""")
      values.map(_.toLong)
    }
  }

}