package models.pagination

case class PageRequest(
  val offset: Int,
  val limit: Int
)
