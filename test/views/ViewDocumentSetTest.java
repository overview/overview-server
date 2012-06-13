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
}
