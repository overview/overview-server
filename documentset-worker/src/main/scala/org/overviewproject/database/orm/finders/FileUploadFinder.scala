package org.overviewproject.database.orm.finders


import org.overviewproject.database.orm.Schema
import org.overviewproject.tree.orm.FileUpload

object FileUploadFinder extends FinderById[FileUpload](Schema.fileUploads)