package com.overviewdocs.models

import org.specs2.mutable.Specification

import com.overviewdocs.test.factories.{PodoFactory=>factory}

class ImportJobSpec extends Specification {
  "FileGroupImportJob" should {
    val baseFileGroup = factory.fileGroup(
      addToDocumentSetId=Some(1L),
      lang=Some("fr"),
      splitDocuments=Some(true),
      nFiles=Some(2),
      nBytes=Some(3L),
      nFilesProcessed=Some(0),
      nBytesProcessed=Some(0L)
    )

    "give progress=0 at the beginning" in {
      FileGroupImportJob(baseFileGroup).progress must beSome(0.0)
    }

    "give progress=None when there are no files" in {
      FileGroupImportJob(baseFileGroup.copy(nFiles=Some(0), nBytes=Some(0L))).progress must beNone
    }

    "give progress=None when the FileGroup is deleted" in {
      FileGroupImportJob(baseFileGroup.copy(deleted=true)).progress must beNone
    }

    "report progress based on bytes" in {
      FileGroupImportJob(baseFileGroup.copy(
        nFiles=Some(2),
        nBytes=Some(100L),
        nFilesProcessed=Some(1),
        nBytesProcessed=Some(75)
      )).progress must beSome(0.75)
    }

    "give a description of 'starting'" in {
      FileGroupImportJob(baseFileGroup).description must beSome(("models.FileGroupImportJob.starting", Seq()))
    }

    "give a description of 'cleaning' when the FileGroup is deleted" in {
      FileGroupImportJob(baseFileGroup.copy(deleted=true))
        .description must beSome(("models.FileGroupImportJob.cleaning", Seq()))
    }

    "give a description of 'cleaning' when all bytes are processed" in {
      FileGroupImportJob(baseFileGroup.copy(nFilesProcessed=Some(2), nBytesProcessed=Some(3L)))
        .description must beSome(("models.FileGroupImportJob.cleaning", Seq()))
    }

    "give a description of 'processing' when in between" in {
      FileGroupImportJob(baseFileGroup.copy(
        nFiles=Some(2),
        nBytes=Some(100L),
        nFilesProcessed=Some(1),
        nBytesProcessed=Some(75L)
      )).description must beSome(("models.FileGroupImportJob.processing", Seq(75L, 100L, 1)))
    }

    "give description of 'cleaning' when nFiles=0" in {
      FileGroupImportJob(baseFileGroup.copy(nFiles=Some(0), nBytes=Some(0L)))
        .description must beSome(("models.FileGroupImportJob.cleaning", Seq()))
    }
  }
}
