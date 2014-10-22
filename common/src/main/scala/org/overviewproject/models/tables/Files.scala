package org.overviewproject.models.tables

import org.overviewproject.database.Slick.simple._
import org.overviewproject.models.File

class FilesImpl(tag: Tag) extends Table[File](tag, "file") {

  def id = column[Long]("id", O.PrimaryKey)
  def referenceCount = column[Int]("reference_count")
  def contentsOid = column[Long]("contents_oid")
  def viewOid = column[Long]("view_oid")
  def name = column[String]("name")
  def contentsSize = column[Option[Long]]("contents_size")
  def viewSize = column[Option[Long]]("view_size")
  
  def * = (
    id,
    referenceCount,
    contentsOid,
    viewOid,
    name,
    contentsSize,
    viewSize
  ) <> (File.tupled, File.unapply)
  
}

object Files extends TableQuery(new FilesImpl(_))