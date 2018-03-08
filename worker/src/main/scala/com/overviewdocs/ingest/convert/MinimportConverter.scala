//package com.overviewdocs.ingest.convert
//
//import akka.stream.scaladsl.{Source,Sink,MergeHub}
//
//class MinimportConverter(
//  workerType: MinimportWorkerType,
//  minimportServer: MinimportHttpServer
//)(implicit ec: ExecutionContext, mat: Materializer) {
//  def requestConvert(file2: WrittenFile2): Source[StepOutputFragment, akka.NotUsed] = {
//    MergeHub.source[StepOutputFragment]
//      .mapMaterializedValue { mergeSink =>
//        minimportServer.register(workerType, writtenFile2, mergeSink)
//      }
//  }
//}
