package models

import models.orm.DocumentSetCreationJob
import models.orm.DocumentSetCreationJob.State._
import models.orm.Schema
import org.squeryl.PrimitiveTypeMode._

object DocumentSetCreationJobQueue {

  case class Position(position: Long, length: Long)

  def position(id: Long) : Position = {
    val queue = from(Schema.documentSetCreationJobs)(ds =>
      where(ds.state === NotStarted) select(ds.id) orderBy(ds.id))

    val pos = queue.toSeq.indexOf(id)
    val length = queue.size

    Position(pos, length)
  }
}
