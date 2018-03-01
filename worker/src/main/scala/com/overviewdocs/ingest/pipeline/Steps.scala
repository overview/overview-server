package com.overviewdocs.ingest.pipeline

sealed trait Steps
object Steps {
  // OCR:
  //   if (pipelineSteps.ocr)
  //     run OCR ... output PendingTask and ProcessedFile
  //   else
  //     remove pipelineOptions and move back to pending
  //
  //
  // Split:
  //   remove pipelineOptions.ocr
  //   run split ... output ProcessedFiles
  //
  //
  // Office:
  //   remove pipelineOptions.ocr
  //   run office ... output PendingTask and ProcessedFile
  //
  //
  // Zip:
  //   run zip ... output WrittenFiles and ProcessedFiles
  //
  //
  // PST:
  //   run pst ... output WrittenFiles and ProcessedFiles
  //
  //
  // JPEG:
  //   run jpg ... output ProcessedFiles
}
