package models;


import static play.test.Helpers.*;
import static org.fest.assertions.Assertions.*;

import org.junit.Test;

public class DocumentTest {

	@Test
	public void saveDocument() {
	  running(fakeApplication(inMemoryDatabase()), new Runnable() {
		public void run() {
		  DocumentSet documentSet = new DocumentSet();
          documentSet.save();

          Document document = new Document(documentSet, "title", "http://text", "http://view");
		
          document.save();
          Document storedDocument = Document.find.byId(document.id);
				
          assertThat(storedDocument.title).isEqualTo("title");
	    }
	  });
	}

  @Test
  public void createTags() {
    running(fakeApplication(inMemoryDatabase()), new Runnable() {
      public void run() {
        DocumentSet documentSet = new DocumentSet();
        documentSet.save();

        Document document = new Document(documentSet, "title", "http://text", "http://view");
        document.save();

        document.setTags("  foo , bar");
        document.save();

        assertThat(document.tags).onProperty("name")
        						 .contains("foo")
        						 .contains("bar");

        // DO NOT document.save(); -- workaround EBean double-saving bug #380, fixed in 2.7.5

        documentSet.refresh();
        assertThat(documentSet.tags.size()).isEqualTo(2);

        assertThat(documentSet.tags).onProperty("name")
        							.contains("foo")
        							.contains("bar");
        

        assertThat(Tag.find.all()).onProperty("name")
        						  .contains("foo")
        						  .contains("bar");
      }
    });
  }
}
