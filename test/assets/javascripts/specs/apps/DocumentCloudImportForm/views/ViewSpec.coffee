define [
  'jquery'
  'backbone'
  'apps/DocumentCloudImportForm/views/View'
  'i18n'
], ($, Backbone, View, i18n) ->
  describe 'apps/DocumentCloudImportForm/views/View', ->
    model = undefined
    view = undefined
    extraOptionsEl = undefined

    class MockCredentials extends Backbone.Model
      defaults:
        email: undefined
        password: undefined

      isComplete: -> true

    setup = (attrs) ->
      extraOptionsEl = $('<div class="extra-options"></div>').get(0)
      model = new Backbone.Model(attrs)
      view = new View(model: model, extraOptionsEl: extraOptionsEl)

    beforeEach ->
      i18n.reset_messages
        'views.DocumentCloudImportJob.new.query.preamble': 'query.preamble'
        'views.DocumentCloudImportJob.new.query.document_count': 'query.document_count,{0}'
        'views.DocumentCloudImportJob.new.fetched': 'fetched'
        'views.DocumentCloudImportJob.new.form_preamble': 'form_preamble'
        'views.DocumentCloudImportJob.new.title.label': 'title.label'
        'views.DocumentCloudImportJob.new.title.placeholder': 'title.placeholder'
        'views.DocumentCloudImportJob.new.title.value': 'title.value,{0}'
        'views.DocumentCloudImportJob.new.submit.label': 'submit.label'
        'views.DocumentCloudImportJob.new.submit.preamble': 'submit.preamble'

    afterEach ->
      view?.remove()
      $(extraOptionsEl).remove()

    describe 'with a normal model', ->
      beforeEach ->
        setup
          query: new Backbone.Model
            id: 'projectid:1'
            title: 'title'
            description: null
          credentials: new MockCredentials
          status: 'fetched'

      it 'should be a form', -> expect(view.el.tagName).to.eq('FORM')
      it 'should put the query ID in an input', ->
        expect(view.$('input[name=query]').val()).to.eq('projectid:1')

    describe 'with quotes in the query', ->
      beforeEach ->
        setup
          query: new Backbone.Model
            id: """<>"&'."""
            title: 'title'
            description: null
          credentials: new MockCredentials
          status: 'fetched'

      it 'should escape the query', ->
        expect(view.$('input[name=query]').val()).to.eq("""<>"&'.""")
