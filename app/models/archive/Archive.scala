package models.archive

import play.api.libs.iteratee.Enumerator

trait Archive {
  def stream: Enumerator[Array[Byte]]
  def size: Long
}
