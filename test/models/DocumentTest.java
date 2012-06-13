package models;

import play.mvc.*;
import play.test.*;

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

        int fooCount = 0;
        int barCount = 0;
        int otherCount = 0;

        for (Tag tag : document.tags) {
          if ("foo".equals(tag.name)) fooCount++;
          if ("bar".equals(tag.name)) barCount++;
          else otherCount++;
        }

        assertThat(fooCount).isEqualTo(1);
        assertThat(barCount).isEqualTo(1);
        assertThat(otherCount).isEqualTo(1);

        // DO NOT document.save(); -- workaround EBean double-saving bug #380, fixed in 2.7.5

        documentSet.refresh();
        assertThat(documentSet.tags.size()).isEqualTo(2);
        documentSet.tags.toString(); // Resolves the set, somehow. (Play bug?)
        for (Tag tag : documentSet.tags) {
          if ("foo".equals(tag.name)) fooCount++;
          if ("bar".equals(tag.name)) barCount++;
          else otherCount++;
        }

        assertThat(fooCount).overridingErrorMessage("DocumentSet is missing 'foo' tag").isEqualTo(2);
        assertThat(barCount).isEqualTo(2);
        assertThat(otherCount).isEqualTo(2);

        for (Tag tag : Tag.find.all()) {
          if ("foo".equals(tag.name)) fooCount++;
          if ("bar".equals(tag.name)) barCount++;
          else otherCount++;
        }

        assertThat(fooCount).isEqualTo(3);
        assertThat(barCount).isEqualTo(3);
        assertThat(otherCount).isEqualTo(3);
      }
    });
  }
}
