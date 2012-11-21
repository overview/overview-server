package csv

import scala.collection.Iterable
import java.io.Reader
import au.com.bytecode.opencsv.CSVReader
import scala.annotation.tailrec

class CsvImportSource(reader: Reader) extends Iterable[CsvImportDocument] {
  private val TextColumn: String = "text"

  val iterator = new Iterator[CsvImportDocument] {
    private val csvParser = new CSVReader(reader)
    private var nextLine = csvParser.readNext()

    private val columns = readHeaders	

    def hasNext: Boolean = nextLine != null

    def next(): CsvImportDocument = {
      require(columns.get(TextColumn).isDefined)
      
      var currentLine: Array[String] = nextValidRow
      Option(currentLine).map(c => new CsvImportDocument(c(columns(TextColumn)))).orNull
    }

    @tailrec
    private def nextValidRow: Array[String] = {
      readRow match {
        case null => null
        case row if row.length > columns(TextColumn) => row
        case _ => nextValidRow
      }
    }
    
    private def readRow: Array[String] = {
      val row = nextLine
      nextLine = csvParser.readNext()
      row
    }
    
    private def readHeaders: Map[String, Int] = {
      val headerRow = readRow
      headerRow.map(_.trim.toLowerCase).zipWithIndex.toMap
    }
  }

}