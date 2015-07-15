package org.overviewproject.jobhandler.filegroup.task.step

import java.awt.image.BufferedImage
import scala.concurrent.Future

trait OcrTextExtractor {
   def extractText(image: BufferedImage, language: String): Future[String] 
}