package models

trait OverviewTag {
  val id: Long
  val name: String
  val color: Option[String]
  val documentIds: models.core.DocumentIdList
}

object OverviewTag {

  private class OverviewImpl(val id: Long, val name: String, val color: Option[String],
    val documentIds: models.core.DocumentIdList) extends OverviewTag 

  
  def apply(id: Long, name: String, color: Option[String], documentIds: models.core.DocumentIdList):
    OverviewTag = new OverviewImpl(id, name, color, documentIds)
      
}
