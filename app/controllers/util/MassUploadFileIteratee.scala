package controllers.util

import org.overviewproject.tree.orm.FileGroup
import org.overviewproject.tree.orm.GroupedFileUpload
import play.api.libs.iteratee.Iteratee


trait MassUploadFileIteratee {

  val storage: Storage

  trait Storage {
    def findCurrentFileGroup: Option[FileGroup]
    def createFileGroup(name: String, user: String): FileGroup
    def createUpload(fileGroupId: Long): GroupedFileUpload
    def appendData(upload: GroupedFileUpload, data: Array[Byte]): GroupedFileUpload
  }

  def apply(): Iteratee[Array[Byte], GroupedFileUpload] = {
    val fileGroup = storage.createFileGroup("", "")
    val initialUpload = storage.createUpload(fileGroup.id)

    Iteratee.fold(initialUpload) { (upload, data) =>
      storage.appendData(upload, data)
    }
  }
}
