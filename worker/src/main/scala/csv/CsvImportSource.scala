/*
 * CsvImportSource.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, November 2012
 */
package csv

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

      nextValidRow.map { c =>
        new CsvImportDocument(c(columns(TextColumn)), suppliedId(c))
      }.orNull
    }

    // Look for the next row with sufficient columns that
    // we can index the text column. Gratuitously recursive approach was
    // prettier than explicitly looping.
    @tailrec
    private def nextValidRow: Option[Array[String]] = {
      readRow match {
        case null => None
        case row if row.length > columns(TextColumn) => Some(row)
        case _ => nextValidRow
      }
    }

    // Return user supplied id value if found.
    private def suppliedId(row: Array[String]): Option[String] =
      columns.get(SuppliedIdColumn).flatMap(row(_) match {
        case s if s.isEmpty => None
        case s => Some(s)
      })

    // Read ahead and return current row
    private def readRow: Array[String] = {
      val row = nextLine
      nextLine = csvParser.readNext()
      row
    }

    // Convert a row to a map of header -> columnIndex
    private def readHeaders: Map[String, Int] = {
      val headerRow = readRow
      headerRow.map(_.trim.toLowerCase).zipWithIndex.toMap
    }
  }

}