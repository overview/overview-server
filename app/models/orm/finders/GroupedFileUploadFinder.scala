package models.orm.finders

import org.overviewproject.tree.orm.finders.{ Finder, FinderResult }
import org.overviewproject.tree.orm.GroupedFileUpload
import java.util.UUID
import models.orm.Schema
import org.overviewproject.postgres.SquerylEntrypoint._

object GroupedFileUploadFinder extends Finder {
  type GroupedFileUploadFinderResult = FinderResult[GroupedFileUpload]

  def byFileGroupAndGuid(fileGroupId: Long, guid: UUID): GroupedFileUploadFinderResult = {
    from(Schema.groupedFileUploads)(gfu =>
      where(gfu.fileGroupId === fileGroupId and gfu.guid === guid)
      select(gfu)
    )
  }
}
