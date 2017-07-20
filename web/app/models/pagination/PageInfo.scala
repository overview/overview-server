package models.pagination

case class PageInfo(
  val request: PageRequest,
  val total: Int
) {
  def offset = request.offset
  def limit = request.limit
}
