//package com.overviewdocs.ingest.pipeline.logic
//
//import com.overviewdocs.ingest.pipeline.StepLogic
//
//class SplitExtractStepLogic(blobStorage: BlobStorage) extends StepLogic {
//  override def toChildFragments(
//    input: WrittenFile2
//  )(implicit ec: ExecutionContext, mat: Materializer): Source[StepOutputFragment, akka.NotUsed] = {
//    val futureSource = blobStorage.withBlobInTempFile(file2.blob.location) { fileOnDisk =>
//      pathToChildFragments(input, fileOnDisk.toPath)
//    }
//    Source.fromFuture(futureSource)
//  }
//
//  private def pathToChildFragments(
//    input: WrittenFile2,
//    path: Path
//  )(implicit ec: ExecutionContext, mat: Materializer): Source[StepOutputFragment, akka.NotUsed] = {
//    val split = input.pipelineOptions.splitByPage
//
//    case class State(
//
//    val parser = PdfSplitter.tokenize(path, split)
//
//    Source
//      .unfoldResourceAsync(start, readToken, stop)
//      .map(tokenToFragment)
//  }
//}
