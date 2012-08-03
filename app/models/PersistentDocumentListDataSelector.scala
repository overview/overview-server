package models

class PersistentDocumentListSelector {

  protected def combineWhereClauses(whereClauses: Seq[Option[String]]) : String = {
    val actualWheres = whereClauses.flatMap(_.toList)
    actualWheres match {
      case Nil => ""
      case _ => actualWheres.mkString("WHERE ", " AND ", " ")
    }
  }
}