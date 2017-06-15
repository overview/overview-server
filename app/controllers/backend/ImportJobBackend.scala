package controllers.backend

import com.google.inject.ImplementedBy
import javax.inject.Inject
import scala.concurrent.Future

import com.overviewdocs.models.{CloneImportJob,CloneJob,CsvImport,CsvImportJob,DocumentSet,DocumentCloudImport,DocumentCloudImportJob,DocumentSetUser,FileGroup,FileGroupImportJob,ImportJob}
import com.overviewdocs.models.tables.{CloneJobs,CsvImports,DocumentCloudImports,DocumentSetUsers,DocumentSets,FileGroups}

@ImplementedBy(classOf[DbImportJobBackend])
trait ImportJobBackend extends Backend {
  /** All ImportJobs for the user. */
  def indexByUser(userEmail: String): Future[Seq[ImportJob]]

  /** All ImportJobs. */
  def indexWithDocumentSetsAndOwners: Future[Seq[(ImportJob,DocumentSet,Option[String])]]

  /** All ImportJobs for the given DocumentSet. */
  def indexByDocumentSet(documentSetId: Long): Future[Seq[ImportJob]]
}

class DbImportJobBackend @Inject() extends ImportJobBackend with DbBackend {
  import database.api._
  import database.executionContext

  private def userDocumentSetIds(userEmail: Rep[String]) = {
    DocumentSetUsers.filter(_.userEmail === userEmail).map(_.documentSetId)
  }

  private lazy val cloneJobsByUserEmail = Compiled { userEmail: Rep[String] =>
    CloneJobs.filter(_.destinationDocumentSetId in userDocumentSetIds(userEmail))
  }

  private lazy val csvImportsByUserEmail = Compiled { userEmail: Rep[String] =>
    CsvImports.filter(_.documentSetId in userDocumentSetIds(userEmail))
  }

  private lazy val documentCloudImportsByUserEmail = Compiled { userEmail: Rep[String] =>
    DocumentCloudImports.filter(_.documentSetId in userDocumentSetIds(userEmail))
  }

  private lazy val fileGroupsByUserEmail = Compiled { userEmail: Rep[String] =>
    FileGroups.filter(_.addToDocumentSetId in userDocumentSetIds(userEmail))
  }

  private lazy val cloneJobsByDocumentSetId = Compiled { documentSetId: Rep[Long] =>
    CloneJobs.filter(_.destinationDocumentSetId === documentSetId)
  }

  private lazy val csvImportsByDocumentSetId = Compiled { documentSetId: Rep[Long] =>
    CsvImports.filter(_.documentSetId === documentSetId)
  }

  private lazy val documentCloudImportsByDocumentSetId = Compiled { documentSetId: Rep[Long] =>
    DocumentCloudImports.filter(_.documentSetId === documentSetId)
  }

  private lazy val fileGroupsByDocumentSetId = Compiled { documentSetId: Rep[Long] =>
    FileGroups.filter(_.addToDocumentSetId === documentSetId)
  }

  private lazy val cloneJobsWithDocumentSetsAndOwners = {
    CloneJobs
      .join(DocumentSets)
        .on(_.destinationDocumentSetId === _.id)
      .joinLeft(DocumentSetUsers.filter(_.role === DocumentSetUser.Role(true)))
        .on(_._1.destinationDocumentSetId === _.documentSetId)
      .map(t => (t._1._1, t._1._2, t._2.map(_.userEmail)))
  }

  private lazy val csvImportsWithDocumentSetsAndOwners = {
    CsvImports
      .join(DocumentSets)
        .on(_.documentSetId === _.id)
      .joinLeft(DocumentSetUsers.filter(_.role === DocumentSetUser.Role(true)))
        .on(_._1.documentSetId === _.documentSetId)
      .map(t => (t._1._1, t._1._2, t._2.map(_.userEmail)))
  }

  private lazy val documentCloudImportsWithDocumentSetsAndOwners = {
    DocumentCloudImports
      .join(DocumentSets)
        .on(_.documentSetId === _.id)
      .joinLeft(DocumentSetUsers.filter(_.role === DocumentSetUser.Role(true)))
        .on(_._1.documentSetId === _.documentSetId)
      .map(t => (t._1._1, t._1._2, t._2.map(_.userEmail)))
  }

  private lazy val fileGroupsWithDocumentSetsAndOwners = {
    FileGroups
      .filter(_.addToDocumentSetId.nonEmpty)
      .join(DocumentSets)
        .on(_.addToDocumentSetId === _.id)
      .joinLeft(DocumentSetUsers.filter(_.role === DocumentSetUser.Role(true)))
        .on(_._1.addToDocumentSetId === _.documentSetId)
      .map(t => (t._1._1, t._1._2, t._2.map(_.userEmail)))
  }

  override def indexByUser(userEmail: String) = {
    for {
      jobs1 <- database.seq(cloneJobsByUserEmail(userEmail))
      jobs2 <- database.seq(csvImportsByUserEmail(userEmail))
      jobs3 <- database.seq(documentCloudImportsByUserEmail(userEmail))
      jobs4 <- database.seq(fileGroupsByUserEmail(userEmail))
    } yield {
      jobs1.map(CloneImportJob.apply _)
        .++(jobs2.map(CsvImportJob.apply _))
        .++(jobs3.map(DocumentCloudImportJob.apply _))
        .++(jobs4.map(FileGroupImportJob.apply _))
    }
  }

  override def indexByDocumentSet(documentSetId: Long) = {
    for {
      jobs1 <- database.seq(cloneJobsByDocumentSetId(documentSetId))
      jobs2 <- database.seq(csvImportsByDocumentSetId(documentSetId))
      jobs3 <- database.seq(documentCloudImportsByDocumentSetId(documentSetId))
      jobs4 <- database.seq(fileGroupsByDocumentSetId(documentSetId))
    } yield {
      jobs1.map(CloneImportJob.apply _)
        .++(jobs2.map(CsvImportJob.apply _))
        .++(jobs3.map(DocumentCloudImportJob.apply _))
        .++(jobs4.map(FileGroupImportJob.apply _))
    }
  }

  override def indexWithDocumentSetsAndOwners = {
    for {
      jobs1 <- database.seq(cloneJobsWithDocumentSetsAndOwners)
      jobs2 <- database.seq(csvImportsWithDocumentSetsAndOwners)
      jobs3 <- database.seq(documentCloudImportsWithDocumentSetsAndOwners)
      jobs4 <- database.seq(fileGroupsWithDocumentSetsAndOwners)
    } yield {
      jobs1.map(t => (CloneImportJob(t._1), t._2, t._3))
        .++(jobs2.map(t => (CsvImportJob(t._1), t._2, t._3)))
        .++(jobs3.map(t => (DocumentCloudImportJob(t._1), t._2, t._3)))
        .++(jobs4.map(t => (FileGroupImportJob(t._1), t._2, t._3)))
    }
  }
}
