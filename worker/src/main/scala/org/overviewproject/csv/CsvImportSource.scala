/*
 * CsvImportSource.scala
 *
 * Overview
 * Created by Jonas Karlsson, November 2012
 */
package org.overviewproject.csv

import com.opencsv.CSVReader
import java.io.Reader
import java.util.{Iterator=>JIterator}
import scala.collection.Iterable

/**
  * Takes a Reader attached to a stream representing CSV data and provides
  * an iterator that returns each row as a CsvImportDocument.
  * The first row must have header labels for the columns. One column
  * must be labelled text. If a column is labelled id, its values will
  * be stored in the CsvImportDocument.suppliedId field.
  *
  * Arguments:
  * * textify: see org.overviewproject.util.Textify
  * * reader: a Reader
  */
class CsvImportSource(textify: (String) => String, reader: Reader) extends Iterable[CsvImportDocument] {
  class CsvHeader(columnNames: Array[String]) {
    private val asMap = columnNames.map(_.toLowerCase).zipWithIndex.toMap

    val textIndex: Option[Int] = asMap.get("text").orElse(asMap.get("contents")).orElse(asMap.get("snippet"))
    val idIndex: Option[Int] = asMap.get("id")
    val urlIndex: Option[Int] = asMap.get("url")
    val titleIndex: Option[Int] = asMap.get("title")
    val tagsIndex: Option[Int] = asMap.get("tags")

    private val nonMetadataIndices: Seq[Int] = Seq(textIndex, idIndex, urlIndex, titleIndex, tagsIndex).flatten

    val metadataIndices: Array[Int] = (columnNames.indices.toSet -- nonMetadataIndices).toArray.sorted
    val metadataColumnNames = metadataIndices.map(columnNames)

    private def fetch(array: Array[String], maybeIndex: Option[Int]): Option[String] = {
      maybeIndex.flatMap(index => if (array.length > index) Some(array(index)) else None)
    }

    def createDocumentFromRow(row: Array[String]): CsvImportDocument = CsvImportDocument(
      fetch(row, textIndex).getOrElse(""),
      fetch(row, idIndex).getOrElse(""),
      fetch(row, urlIndex),
      fetch(row, titleIndex).getOrElse(""),
      fetch(row, tagsIndex).getOrElse("").split(",").map(_.trim).filterNot(_.isEmpty).toSet,
      metadataIndices.map(index => columnNames(index) -> fetch(row, Some(index)).getOrElse("")).toMap
    )
  }

  class CsvImportDocumentIterator(header: CsvHeader, rowIterator: JIterator[Array[String]])
    extends Iterator[CsvImportDocument]
  {
    override def hasNext = {
      if (header.textIndex.isDefined) {
        csvIterator.hasNext
      } else {
        throw new RuntimeException("CSV file is missing a `text` header")
      }
    }
    override def next = {
      if (header.textIndex.isDefined) {
        header.createDocumentFromRow(rowIterator.next)
      } else {
        throw new RuntimeException("CSV file is missing a `text` header")
      }
    }
  }

  private class TextifiedArrayIterator(rowIterator: JIterator[Array[String]]) extends JIterator[Array[String]] {
    // If we need to optimize this, put textification on the other side of the
    // BufferedReader. (The Reader passed to CsvImportSource is a
    // BufferedReader.)
    override def hasNext = rowIterator.hasNext
    override def next = rowIterator.next.map(textify)
  }

  private val csvReader = new CSVReader(reader, ',', '\"', '\u0000') // Setting escape to \0 disables escaping
  private val csvIterator: JIterator[Array[String]] = csvReader.iterator
  private val textifiedRowIterator: JIterator[Array[String]] = new TextifiedArrayIterator(csvIterator)

  private val header: CsvHeader = if (textifiedRowIterator.hasNext) {
    new CsvHeader(textifiedRowIterator.next)
  } else {
    new CsvHeader(Array.empty)
  }

  val metadataColumnNames: Seq[String] = header.metadataColumnNames
  override val iterator: Iterator[CsvImportDocument] = new CsvImportDocumentIterator(header, textifiedRowIterator)
}
