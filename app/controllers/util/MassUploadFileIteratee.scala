package controllers.util

import org.overviewproject.tree.orm.FileGroup
import org.overviewproject.tree.orm.GroupedFileUpload
import play.api.libs.iteratee.Iteratee


trait MassUploadFileIteratee {

  val storage: Storage

  trait Storage {
    def findCurrentFileGroup: Option[FileGroup]
    def createFileGroup(userEmail: String): FileGroup
    def createUpload(fileGroupId: Long): GroupedFileUpload
    def appendData(upload: GroupedFileUpload, data: Array[Byte]): GroupedFileUpload
  }

  def apply(userEmail: String): Iteratee[Array[Byte], GroupedFileUpload] = {
    val fileGroup = storage.createFileGroup(userEmail)
    val initialUpload = storage.createUpload(fileGroup.id)

    Iteratee.fold(initialUpload) { (upload, data) =>
      storage.appendData(upload, data)
    }
  }
}
