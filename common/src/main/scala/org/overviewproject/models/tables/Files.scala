package org.overviewproject.models.tables

import org.overviewproject.database.Slick.simple._
import org.overviewproject.models.File

class FilesImpl(tag: Tag) extends Table[File](tag, "file") {
  def id = column[Long]("id", O.PrimaryKey)
  def referenceCount = column[Int]("reference_count")
  def name = column[String]("name")
  def contentsLocation = column[String]("contents_location")
  def contentsSize = column[Long]("contents_size")
  def viewLocation = column[String]("view_location")
  def viewSize = column[Long]("view_size")
  
  def * = (
    id,
    referenceCount,
    name,
    contentsLocation,
    contentsSize,
    viewLocation,
    viewSize
  ) <> (File.tupled, File.unapply)
}

object Files extends TableQuery(new FilesImpl(_))
