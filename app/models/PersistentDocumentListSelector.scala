package models

trait PersistentDocumentListSelector {

  protected def combineWhereClauses(whereClauses: Seq[Option[String]]) : String = {
    val actualWheres = whereClauses.collect { case Some(w) => w }
    actualWheres match {
      case Nil => ""
      case _ => actualWheres.mkString("WHERE ", " AND ", " ")
    }
  }
}
