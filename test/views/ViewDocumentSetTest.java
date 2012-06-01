package views;

import java.util.*;
import java.util.regex.*;

import javassist.runtime.DotClass;

import play.mvc.*;
import play.test.*;
import views.html.*;
import models.*;

import static play.test.Helpers.*;
import static org.fest.assertions.Assertions.*;

import org.junit.Test;

public class ViewDocumentSetTest {

	@Test
	public void queryInHeader() {
		String query = "this is the query";
		String header = "<h1>Query: " + query + " </h1>";
		
		DocumentSet documentSet = new DocumentSet();
		documentSet.query = query;
		Content html = viewDocumentSet.render(documentSet, 0);
		assertThat(contentAsString(html)).contains(header);
				
	}
	
	@Test 
	public void documentLinks() {
		
		running(fakeApplication(inMemoryDatabase()), new Runnable() {
			public void run() {
				String query = "this is the query";
		
				List<String> titles = Arrays.asList("document1", "document2");
		
				String patternString = ".*";
				for (String title : titles) {
					patternString += "<li>.*" + title + ".*</li>.*";
				}
				Pattern pattern = Pattern.compile(patternString, Pattern.DOTALL);		
		
				DocumentSet documentSet = new DocumentSet();
				documentSet.save();
				Document doc1 = new Document("", titles.get(0), "");
				Document doc2 = new Document("", titles.get(1), "");
				doc1.save();
				doc2.save();
				documentSet.addDocument(doc1);
				documentSet.addDocument(doc2);
				Content html = viewDocumentSet.render(documentSet, 0);
		
				Matcher matcher = pattern.matcher(html.body());
				assertThat(matcher.matches()).overridingErrorMessage(html.body() + "[" + matcher.pattern() + "]").isTrue();
				
			}
		});
	}
	
	
	@Test 
	public void viewerInIframe() {
		running(fakeApplication(inMemoryDatabase()), new Runnable() {
			public void run() {
				
				String canonicalUrl = "http://documentcloud.org/canonicalURL";
				String iframe = "<iframe width=\"800px\" height=\"2000px\" src=" + canonicalUrl + "></iframe>";
		
				Document doc = new Document("id", "title", canonicalUrl);
				doc.save();
				DocumentSet documentSet = new DocumentSet();
				documentSet.save();
				documentSet.addDocument(doc);
				Content html = viewDocumentSet.render(documentSet, 0);
				assertThat(html.body()).contains(iframe);
			}
		});
		
	}
	

}
