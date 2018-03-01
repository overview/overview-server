package com.overviewdocs.pdfocr

import java.nio.file.{Files,Path,Paths}

import com.overviewdocs.util.Configuration

object PdfSplitter {
  def command(in: Path, createPdfs: Boolean): Vector[String] = Vector(
    softLimitPath, "-d", pdfMemoryNBytes.toString,
    "/opt/overview/split-pdf-and-extract-text",
    ("--only-extract=" + (if (createPdfs) "false" else "true")),
    in.toString
  )

  private lazy val pdfMemoryNBytes: Long = {
    val GigR = "^(\\d+)[gG]$".r
    val MegR = "^(\\d+)[mM]$".r
    val KiloR = "^(\\d+)[kK]$".r
    val ByteR = "^(\\d+)[bB]?$".r
    Configuration.getString("pdf_memory") match {
      case GigR(gigs) => gigs.toLong * 1024 * 1024 * 1024
      case MegR(megs) => megs.toLong * 1024 * 1024
      case KiloR(kilos) => kilos.toLong * 1024
      case ByteR(bytes) => bytes.toLong
      case _ => throw new RuntimeException("pdf_memory must be specified as a number with 'G' or 'M', like '1g' or '1500m'")
    }
  }

  private lazy val softLimitPath: String = {
    if (Files.exists(Paths.get("/usr/bin/softlimit"))) {
      "/usr/bin/softlimit" // Ubuntu with daemontools installed (dev)
    } else {
      "/sbin/chpst"        // Alpine Linux w/ runit installed (prod)
    }
  }
}
