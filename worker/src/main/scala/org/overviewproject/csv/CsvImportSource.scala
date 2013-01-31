/*
 * CsvImportSource.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, November 2012
 */
package org.overviewproject.csv

import java.io.Reader
import scala.annotation.tailrec
import scala.collection.Iterable
import au.com.bytecode.opencsv.CSVReader

/**
 * Takes a Reader attached to a stream representing CSV data and provides
 * an iterator that returns each row as a CsvImportDocument.
 * The first row must have header labels for the columns. One column
 * must be labelled text. If a column is labelled id, its values will
 * be stored in the CsvImportDocument.suppliedId field.
 */
class CsvImportSource(reader: Reader) extends Iterable[CsvImportDocument] {
  private val TextColumn: String = "text"
  private val SuppliedIdColumn: String = "id"
  private val UrlColumn: String = "url"
  private val TitleColumn: String = "title"
  private val ByteOrderMarkUTF_8: String = "\uFEFF"

  /**
   * An iterator that returns CsvImportDocuments, based on the header information
   * in the first row of the input.
   */
  val iterator = new Iterator[CsvImportDocument] {
    private val Separator = ','
    private val Quote = '\"'
    private val NoEscape = '\0' // Setting escape char to \0 disables escaping

    private val csvParser = new CSVReader(reader, Separator, Quote, NoEscape)
    private var nextLine = csvParser.readNext() // read ahead to be able to evaluate hasNext

    // Column headers with index
    private val columns: Map[String, Int] = readHeaders

    def hasNext: Boolean = nextLine != null

    /**
     *  @return the next valid CsvImportDocument. Throws and exception if
     *  no column has the text header. Rows with not enough columns to have
     *  a text column are ignored.
     */
    def next(): CsvImportDocument = {
      require(columns.get(TextColumn).isDefined)

      readRow match {
        case null => null
        case c => new CsvImportDocument(text(c), suppliedId(c), url(c), title(c))
      }
    }

    // Return text if the column exists, "" otherwise.
    private def text(row: Array[String]): String = {
      if (row.length > columns(TextColumn)) row(columns(TextColumn))
      else ""
    }

    // Return user supplied id value if found.
    private def suppliedId(row: Array[String]): Option[String] = getOptColumn(row, SuppliedIdColumn)

    // return the url if url column exists
    private def url(row: Array[String]): Option[String] = getOptColumn(row, UrlColumn)

    // return the title if title column exists
    private def title(row: Array[String]): Option[String] = getOptColumn(row, TitleColumn)
    
    // if the columnName was defined in the header row, @return the value in the column, else None
    private def getOptColumn(row: Array[String], columnName: String): Option[String] =
      columns.get(columnName).flatMap(c =>
        if (row.size > c && !row(c).isEmpty) Some(row(c))
        else None)

    // Read ahead and return current row
    private def readRow: Array[String] = {
      val row = nextLine
      nextLine = csvParser.readNext()
      row
    }

    // Convert a row to a map of header -> columnIndex
    private def readHeaders: Map[String, Int] = {
      val headerRow = skipByteOrderMark(readRow)
      headerRow.map(_.trim.toLowerCase).zipWithIndex.toMap
    }
    
    // Ignore the UTF-8 ByteOrderMark if present
    private def skipByteOrderMark(row: Array[String]): Array[String] = {
      row.head.replace(ByteOrderMarkUTF_8, "") +: row.tail
    }
  }

}