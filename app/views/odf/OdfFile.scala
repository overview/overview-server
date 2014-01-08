package views.odf

import java.io.OutputStream
import java.util.zip.{ZipEntry,ZipOutputStream}

case class OdfFile(manifest: models.odf.OdfManifest) {
  def writeTo(out: OutputStream) : Unit = {
    val zipStream = new ZipOutputStream(out)

    writeMimeTypeTo(zipStream)
    writeManifestTo(zipStream)
    manifest.files.foreach(writeFileTo(_, zipStream))

    zipStream.finish()
  }

  private def writeMimeTypeTo(zipStream: ZipOutputStream) : Unit = {
    val mimeTypeBytes = "application/vnd.oasis.opendocument.spreadsheet".getBytes("utf-8")

    zipStream.putNextEntry(new ZipEntry("mimetype"))
    zipStream.write(mimeTypeBytes, 0, mimeTypeBytes.length)
    zipStream.closeEntry()
  }

  private def writeManifestTo(zipStream: ZipOutputStream) : Unit = {
    val xml = views.xml.odf.OdfManifestXml(manifest).toString
    val bytes = xml.getBytes("utf-8")

    zipStream.putNextEntry(new ZipEntry("META-INF/manifest.xml"))
    zipStream.write(bytes, 0, bytes.length)
    zipStream.closeEntry()
  }

  private def writeFileTo(file: models.odf.OdfInternalFile, zipStream: ZipOutputStream) : Unit = {
    val bytes = file match {
      case (s: models.odf.OdsSpreadsheet) => views.xml.odf.OdsSpreadsheetContentXml(s).toString.getBytes("utf-8")
    }

    zipStream.putNextEntry(new ZipEntry("content.xml"))
    zipStream.write(bytes, 0, bytes.length)
    zipStream.closeEntry()
  }
}
