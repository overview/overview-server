/*
 * DocumentSet.scala
 *
 * Overview Project
 * Created by Adam Hooper, Aug 2012
 */
package models.orm

import scala.language.implicitConversions
import scala.language.postfixOps
import anorm.SQL
import anorm.SqlParser._
import java.util.Date
import java.sql.Timestamp
import org.squeryl.dsl.OneToMany
import org.squeryl.{ KeyedEntity, Query }
import org.overviewproject.postgres.SquerylEntrypoint._
import org.squeryl.annotations.{ Column, Transient }
import scala.annotation.target.field

import org.overviewproject.postgres.PostgresqlEnum
import org.overviewproject.tree.Ownership
import org.overviewproject.tree.orm.{ DocumentSetCreationJob, DocumentSetCreationJobType, UploadedFile }
import org.overviewproject.tree.orm.DocumentSetCreationJobType._
import models.OverviewDatabase

class DocumentSetType(v: String) extends PostgresqlEnum(v, "document_set_type")

object DocumentSetType {
  val DocumentCloudDocumentSet = new DocumentSetType("DocumentCloudDocumentSet")
  val CsvImportDocumentSet = new DocumentSetType("CsvImportDocumentSet")
}

import DocumentSetType._

case class DocumentSet(
  @Column("type") val documentSetType: DocumentSetType,
  override val id: Long = 0,
  title: String = "",
  query: Option[String] = None,
  @Column("public") isPublic: Boolean = false,
  createdAt: Timestamp = new Timestamp((new Date()).getTime),
  documentCount: Int = 0,
  documentProcessingErrorCount: Int = 0,
  importOverflowCount: Int = 0,
  uploadedFileId: Option[Long] = None,
  @(Transient @field) val uploadedFile: Option[UploadedFile] = None) extends KeyedEntity[Long] {

  def this() = this(documentSetType = DocumentCloudDocumentSet) // For Squeryl

  lazy val logEntries = Schema.documentSetLogEntries.left(this)

  lazy val orderedLogEntries = from(logEntries)(le => select(le).orderBy(le.date desc))

  /**
   * Create a new DocumentSetCreationJob for the document set.
   *
   * The job will be inserted into the database in the state NotStarted.
   *
   * Should only be called after the document set has been inserted into the database.
   */
  def createDocumentSetCreationJob(username: Option[String] = None, password: Option[String] = None, contentsOid: Option[Long] = None): DocumentSetCreationJob = {
    require(id != 0l)
    val jobType = documentSetType match {
      case DocumentCloudDocumentSet => DocumentCloudJob
      case CsvImportDocumentSet => CsvImportJob
    }

    val documentSetCreationJob = new DocumentSetCreationJob(id, jobType, documentcloudUsername = username, documentcloudPassword = password, contentsOid = contentsOid)
    Schema.documentSetDocumentSetCreationJobs.left(this).associate(documentSetCreationJob)
  }

  def withUploadedFile = uploadedFile match {
    case None => copy(uploadedFile = uploadedFileId.flatMap(Schema.uploadedFiles.lookup(_)))
    case Some(uploadedFile) => this
  }

  def errorCount: Long = from(Schema.documentProcessingErrors)(dpe => where(dpe.documentSetId === this.id) compute (count)).single.measures

  // https://www.assembla.com/spaces/squeryl/tickets/68-add-support-for-full-updates-on-immutable-case-classes#/followers/ticket:68
  override def isPersisted(): Boolean = (id > 0)

  def save: DocumentSet = Schema.documentSets.insertOrUpdate(this)
}

object DocumentSet {
  implicit def toLong(documentSet: DocumentSet) = documentSet.id

  def findById(id: Long) = Schema.documentSets.lookup(id)

  def findByUserIdOrderedByCreatedAt(userEmail: String): Query[DocumentSet] = {
    from(Schema.documentSetUsers, Schema.documentSets)((dsu, ds) =>
      where(dsu.documentSetId === ds.id and dsu.userEmail === userEmail)
        select (ds)
        orderBy (ds.createdAt.desc))
  }

  /**
   * Deletes a DocumentSet by ID.
   *
   * @return true if the deletion succeeded, false if it failed.
   */
  def delete(id: Long): Boolean = {
    implicit val connection = OverviewDatabase.currentConnection

    val documentSet = DocumentSet.findById(id)
    val uploadedFileId = documentSet.map(_.uploadedFileId)

    SQL("DELETE FROM log_entry WHERE document_set_id = {id}").on('id -> id).executeUpdate()
    SQL("DELETE FROM document_tag WHERE tag_id IN (SELECT id FROM tag WHERE document_set_id = {id})").on('id -> id).executeUpdate()
    SQL("DELETE FROM tag WHERE document_set_id = {id}").on('id -> id).executeUpdate()
    SQL("DELETE FROM node_document WHERE node_id IN (SELECT id FROM node WHERE document_set_id = {id})").on('id -> id).executeUpdate()
    SQL("DELETE FROM node WHERE document_set_id = {id}").on('id -> id).executeUpdate()
    SQL("DELETE FROM document WHERE document_set_id = {id}").on('id -> id).executeUpdate()
    SQL("DELETE FROM document_set_user WHERE document_set_id = {id}").on('id -> id).executeUpdate()

    Schema.documentSetCreationJobs.deleteWhere(dscj => dscj.documentSetId === id)

    val success = Schema.documentSets.delete(id)

    uploadedFileId.map { u =>
      SQL("DELETE FROM uploaded_file WHERE id = {id}").on('id -> u).executeUpdate()
    }

    success
  }
}
