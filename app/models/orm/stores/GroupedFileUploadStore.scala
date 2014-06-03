package models.orm.stores

import org.overviewproject.tree.orm.stores.BaseGroupedFileUploadStore
import models.orm.Schema


object GroupedFileUploadStore extends BaseGroupedFileUploadStore(Schema.groupedFileUploads)