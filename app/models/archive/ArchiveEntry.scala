package models.archive

import play.api.libs.iteratee.Enumerator
import scala.concurrent.Future

case class ArchiveEntry(name: String, size: Long, data: () => Future[Enumerator[Array[Byte]]])
