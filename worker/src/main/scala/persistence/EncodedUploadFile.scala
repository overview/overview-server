/*
 * UploadedFileLoader.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, November 2012
 */
package persistence

import java.sql.Connection
import org.overviewproject.tree.orm.UploadedFile
import org.overviewproject.database.DB
import org.overviewproject.postgres.LO

/** Information describing an uploaded file */
trait EncodedUploadFile {
  val contentsOid: Option[Long]
  val contentType: String
  val size: Long
  
  private var ContentTypeEncoding = ".*charset=([^\\s]*)".r 

  /**
   * @return a string with the value of the charset field in contentType,
   * or None, if the contentType could not be parsed.
   */
  def encoding: Option[String] = contentType match {
    case ContentTypeEncoding(c) => Some(c)
    case _ => None
  }

  /**
   *  Delete the uploaded file content.
   *  @return an EncodedUploadFile with contentsOid set to None 
   */
  def deleteContent(implicit c: Connection): EncodedUploadFile
}

/** Helper for loading an UploadedFile from the database */
object EncodedUploadFile {
  import org.overviewproject.postgres.SquerylEntrypoint._

  /** @return the UploadedFile specified by the uploadedFileId */
  def load(uploadedFileId: Long)(implicit c: Connection): EncodedUploadFile = {
    val upload = Schema.uploadedFiles.lookup(uploadedFileId).get

    new EncodedUploadFileImpl(upload)
  }
  
  private class EncodedUploadFileImpl(uploadedFile: UploadedFile) extends EncodedUploadFile {
    val contentsOid: Option[Long] = uploadedFile.contentsOid
    val contentType: String = uploadedFile.contentType
    val size: Long = uploadedFile.size
    
    def deleteContent(implicit c: Connection): EncodedUploadFile = {
      val noContent = uploadedFile.copy(contentsOid = None)
      Schema.uploadedFiles.update(noContent)
      
      implicit val pgc = DB.pgConnection
      contentsOid.map( oid => LO.delete(oid) )
      
      new EncodedUploadFileImpl(noContent)
    }
    
  }
}