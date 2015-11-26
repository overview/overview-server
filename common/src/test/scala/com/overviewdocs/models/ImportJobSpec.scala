package com.overviewdocs.models

import java.time.Instant
import org.specs2.mutable.Specification

import com.overviewdocs.test.factories.{PodoFactory=>factory}

class ImportJobSpec extends Specification {
  "CloneImportJob" should {
    "give progress=0 at step 0" in {
      CloneImportJob(factory.cloneJob()).progress must beSome(0.0)
    }

    "give progress=1.0 at step 5" in {
      CloneImportJob(factory.cloneJob(stepNumber=5)).progress must beSome(1.0)
    }

    "give progress=None when cancelled" in {
      CloneImportJob(factory.cloneJob(stepNumber=1, cancelled=true)).progress must beNone
    }

    "give description=processing" in {
      CloneImportJob(factory.cloneJob()).description must beSome(("models.CloneImportJob.processing"), Seq())
    }

    "give description=cleaning at step 5" in {
      CloneImportJob(factory.cloneJob(stepNumber=5)).description must beSome(("models.CloneImportJob.cleaning"), Seq())
    }

    "give description=cleaning when cancelled" in {
      CloneImportJob(factory.cloneJob(cancelled=true)).description must beSome(("models.CloneImportJob.cleaning"), Seq())
    }
  }

  "CsvImportJob" should {
    "give progress=0 at the beginning" in {
      CsvImportJob(factory.csvImport(nBytes=2L, nBytesProcessed=0L)).progress must beSome(0.0)
    }

    "give progress=0.5 in the middle" in {
      CsvImportJob(factory.csvImport(nBytes=2L, nBytesProcessed=1L)).progress must beSome(0.5)
    }

    "give progress=1.0 at the end" in {
      CsvImportJob(factory.csvImport(nBytes=2L, nBytesProcessed=2L)).progress must beSome(1.0)
    }

    "give progress=1.0 on empty file" in {
      CsvImportJob(factory.csvImport(nBytes=0L, nBytesProcessed=0L)).progress must beSome(1.0)
    }

    "give description of 'starting'" in {
      CsvImportJob(factory.csvImport(nBytes=1L, nBytesProcessed=0L))
        .description must beSome(("models.CsvImportJob.starting", Seq()))
    }

    "give description of 'processing'" in {
      CsvImportJob(factory.csvImport(nBytes=2L, nBytesProcessed=1L))
        .description must beSome(("models.CsvImportJob.processing", Seq(1L, 2L)))
    }

    "give description of 'cleaning' when progress=1.0" in {
      CsvImportJob(factory.csvImport(nBytes=2L, nBytesProcessed=2L))
        .description must beSome(("models.CsvImportJob.cleaning", Seq()))
    }

    "pass through CsvImport.estimatedCompletionTime" in {
      val instant = Instant.now
      CsvImportJob(factory.csvImport(estimatedCompletionTime=Some(instant)))
        .estimatedCompletionTime must beSome(instant)
    }
  }

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

    "pass through to FileGroup.estimatedCompletionTime" in {
      val instant = Instant.now()
      FileGroupImportJob(baseFileGroup.copy(estimatedCompletionTime=Some(instant)))
        .estimatedCompletionTime must beSome(instant)
    }
  }
}
