package controllers;

import play.data.*;

import play.mvc.*;

import views.html.*; // TODO: remove this (or, preferably, remove most of the Application class

import models.*;


public class Application extends Controller {
    static Form<models.DocumentSet> documentSetForm = form(models.DocumentSet.class);

    public static Result index() {
    	return ok(index.render("The Overview Project"));
    }

    public static Result showDocumentSets() {
    	return ok(documentSets.render(models.DocumentSet.find.all(), documentSetForm));
    }

    public static Result deleteDocumentSet(Long id) {
    	models.DocumentSet documentSet = models.DocumentSet.find.ref(id);
    	documentSet.delete();
    	
    	return redirect(routes.Application.showDocumentSets());
    }

    public static Result showJobs() {
    	return ok(viewJobs.render(DocumentSetCreationJob.find.all()));
    	
    }
}
