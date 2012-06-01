package controllers;

import org.codehaus.jackson.JsonNode;

import play.*;
import play.data.*;
import play.libs.F.Function;
import play.libs.WS;
import play.mvc.*;

import views.html.*;

import models.*;

import java.util.*;

public class Application extends Controller {

    static Form<DocumentSet> documentSetForm = form(DocumentSet.class);

    public static Result index() {
    	return ok(index.render("The Overview Project"));
    }

    public static Result showDocumentSets() {
    	return ok(documentSets.render(DocumentSet.all(), documentSetForm));
    }

    public static Result newDocumentSet() {
    	Form<DocumentSet> filledForm = documentSetForm.bindFromRequest();
    	
    	if(filledForm.hasErrors()) {
    		return badRequest(
    				views.html.documentSets.render(DocumentSet.all(), filledForm)
    				);
    	}
    	else {
    		DocumentSet.create(filledForm.get());

    		return redirect(routes.Application.showDocumentSets());
    	}
    }
    
    public static Result deleteDocumentSet(Long id) {
    	DocumentSet.delete(id);
    	
    	return redirect(routes.Application.showDocumentSets());
    }

    public static Result viewDocumentSet2(Long id, Integer currentDocument) {
    	DocumentSet documentSet = DocumentSet.find.byId(id);

    	return ok(viewDocumentSet.render(documentSet, currentDocument));

    }
    
        
    public static Result viewDocumentSet(Long id) {
    	final DocumentSet documentSet = DocumentSet.find.byId(id);
    	String queryString = documentSet.query;
    	String documentCloudQuery = "http://www.documentcloud.org/api/search.json";

    	final Long dsId = id;
    	
    	return async(
    			WS.url(documentCloudQuery).setQueryParameter("q", queryString).get().map(
    					new Function<WS.Response, Result>() {
    						public Result apply(WS.Response response) {
    							JsonNode documentReferences = response.asJson().get("documents");
						
    							for (JsonNode document : documentReferences) {
    								String documentId = document.get("id").toString();
    								String title = document.get("title").toString();
    								title = title.replace("\"", "");
    								String canonicalUrl = document.get("canonical_url").toString();
    								canonicalUrl = canonicalUrl.replace("\"", "");
    								Document newDoc = new Document(documentId, title, canonicalUrl);
    								newDoc.save();
    								
    								documentSet.addDocument(newDoc);
    							}
    							documentSet.update(dsId);
    							return redirect(routes.Application.viewDocumentSet2(dsId, 0));
    						}
    					}
    				)
    			);
    			
    }
}