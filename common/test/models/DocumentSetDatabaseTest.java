package models;


import static org.fest.assertions.Assertions.*;
import org.junit.Test;

public class DocumentSetDatabaseTest extends DatabaseTest {

	@Test
	public void saveToDatabase() {
	  DocumentSet documentSet = new DocumentSet();
		
	  for (int i = 0; i < 3; i++) {
		Document document = new Document("title" + i, "textUrl" + i, "viewUrl" + i);
		documentSet.addDocument(document);
	  }
		  
	  documentSet.save();

	  DocumentSet foundDocumentSet = DocumentSet.find.byId(documentSet.id);
		
	  assertThat(foundDocumentSet).isNotNull();
	  assertThat(foundDocumentSet.documents).hasSize(3);
	  assertThat(foundDocumentSet.documents).onProperty("title")
	                                         .contains("title0", "title1", "title2");
	}

}
