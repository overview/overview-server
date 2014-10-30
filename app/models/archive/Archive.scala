package models.archive

import java.io.InputStream

trait Archive {

  def stream: InputStream
  def size: Long
}