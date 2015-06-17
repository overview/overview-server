package views.odf

import java.io.{BufferedOutputStream,OutputStream}
import java.util.zip.{ZipEntry,ZipOutputStream}
import scala.concurrent.{ExecutionContext,Future,blocking}

import models.export.rows.Rows

case class OdfFile(rows: Rows) {
  def writeTo(out: OutputStream)(implicit executionContext: ExecutionContext): Future[Unit] = {
    val zipStream = new ZipOutputStream(new BufferedOutputStream(out))

    blocking {
      writeMimeTypeTo(zipStream)
      writeManifestTo(zipStream)
    }

    for {
      _ <- writeContentsTo(rows, zipStream)
    } yield {
      blocking {
        zipStream.finish
        zipStream.close // flush BufferedOutputStream
      }
    }
  }

  private def writeMimeTypeTo(zipStream: ZipOutputStream) : Unit = {
    val mimeTypeBytes = "application/vnd.oasis.opendocument.spreadsheet".getBytes("utf-8")

    zipStream.putNextEntry(new ZipEntry("mimetype"))
    zipStream.write(mimeTypeBytes, 0, mimeTypeBytes.length)
    zipStream.closeEntry()
  }

  private def writeManifestTo(zipStream: ZipOutputStream) : Unit = {
    val bytes = """<?xml version="1.0" encoding="utf-8"?>
<manifest:manifest xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0">
  <manifest:file-entry manifest:media-type="application/vnd.oasis.opendocument.spreadsheet" manifest:full-path="/"/>
  <manifest:file-entry manifest:media-type="text/xml" manifest:full-path="content.xml"/>
</manifest:manifest>""".getBytes("utf-8")

    zipStream.putNextEntry(new ZipEntry("META-INF/manifest.xml"))
    zipStream.write(bytes, 0, bytes.length)
    zipStream.closeEntry()
  }

  private def writeContentsTo(rows: Rows, zipStream: ZipOutputStream)(implicit executionContext: ExecutionContext)
  : Future[Unit] = {
    blocking(zipStream.putNextEntry(new ZipEntry("content.xml")))

    def write(s: String): Future[Unit] = Future(blocking(zipStream.write(s.getBytes("utf-8"))))

    OdsSpreadsheetContentXml(rows, write)
      .map { _ => blocking(zipStream.closeEntry()) }
  }
}
