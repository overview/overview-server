package org.overviewproject.jobhandler.filegroup

/**
 * Classes that are used to process uploaded files. Files are split into pages with extracted text.
 * If the uploaded file is not a pdf, it is first converted to a pdf file by executing a shell
 * command that calls LibreOffice.
 * 
 * The [[FileGroupTaskWorker]] interacts with the [[FileGroupJobQueue]] to get new tasks. 
 * 
 * @todo [[FileGroupTaskWorker]]s should be in a separate process on their own instance.
 * @todo [[FileGroupTaskWorker]]s should create [[Document]]s, instead of using the 
 * [[TempDocumentSetFile]].
 */
package object task {

}