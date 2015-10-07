package com.overviewdocs.jobhandler.filegroup.task.step

import java.awt.image.BufferedImage
import scala.concurrent.{ExecutionContext,Future}

trait OcrTextExtractor {
   def extractText(image: BufferedImage, language: String)(implicit ec: ExecutionContext): Future[String] 
}
