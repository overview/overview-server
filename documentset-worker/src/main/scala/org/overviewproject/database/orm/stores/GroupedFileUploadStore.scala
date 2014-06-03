package org.overviewproject.database.orm.stores

import org.overviewproject.database.orm.Schema
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.stores.BaseGroupedFileUploadStore

object GroupedFileUploadStore extends BaseGroupedFileUploadStore(Schema.groupedFileUploads) 