define [
  'backbone'
  'apps/ImportOptions/views/Options'
  'i18n'
], (Backbone, OptionsView, i18n) ->
  describe 'apps/ImportOptions/views/Options', ->
    view = undefined
    model = undefined

    beforeEach ->
      @sandbox = sinon.sandbox.create(useFakeServer: true) # for TagIdInput
      i18n.reset_messages
        'views.DocumentSet.index.ImportOptions.title': 'title'
        'views.DocumentSet.index.ImportOptions.split_documents.label_html': 'split_documents.label_html'
        'views.DocumentSet.index.ImportOptions.split_documents.false': 'split_documents.false'
        'views.DocumentSet.index.ImportOptions.split_documents.true': 'split_documents.true'
        'views.DocumentSet.index.ImportOptions.split_documents.too_few_documents': 'split_documents.too_few_documents'
        'views.DocumentSet.index.ImportOptions.lang.label': 'lang.label'
        'views.DocumentSet.index.ImportOptions.name.label': 'name.label'
        'views.DocumentSet.index.ImportOptions.click_for_help': 'click_for_help'
        'views.DocumentSet.show.DocumentMetadata.App.title': 'metadata.title'
        'views.DocumentSet.show.DocumentMetadata.App.loading': 'metadata.loading'
        'views.DocumentSet.show.DocumentMetadata.JsonView.help_html': 'metadata.help_html'
        'views.DocumentSet.show.DocumentMetadata.AddFieldView.expand': 'metadata.expand'
        'views.DocumentSet.show.DocumentMetadata.AddFieldView.label': 'metadata.label'
        'views.DocumentSet.show.DocumentMetadata.AddFieldView.placeholder': 'metadata.placeholder'
        'views.DocumentSet.show.DocumentMetadata.AddFieldView.submit': 'metadata.submit'
        'views.DocumentSet.show.DocumentMetadata.AddFieldView.reset': 'metadata.reset'
        'views.DocumentSet.show.DocumentMetadata.JsonView.delete': 'metadata.delete'
        'views.DocumentSet.show.DocumentMetadata.JsonView.confirmDelete': 'metadata.confirmDelete'

    afterEach ->
      @sandbox.restore()
      view?.remove()

    describe 'with some options', ->
      beforeEach ->
        model = new Backbone.Model({ lang: 'en' })
        model.supportedLanguages = [{code:'en',name:'English'},{code:'fr',name:'French'},{code:'de',name:'German'},{code:'es',name:'Spanish'},{code:'sv',name:'Swedish'}]
        view = new OptionsView({ model: model })

      it 'should not render the excluded options', ->
        expect(view.$('[name=split_documents]').length).to.eq(0)
        expect(view.$('[name=name]').length).to.eq(0)
        expect(view.$('[name=metadata_json]').length).to.eq(0)

    describe 'with all options', ->
      beforeEach ->
        model = new Backbone.Model
          name: ''
          split_documents: false
          lang: 'en'
          metadata_json: '{}'
        model.documentSet = new Backbone.Model(id: 1, metadataFields: [ 'metadataFoo' ])
        model.supportedLanguages = [{code:'en',name:'English'},{code:'fr',name:'French'},{code:'de',name:'German'},{code:'es',name:'Spanish'},{code:'sv',name:'Swedish'}]
        view = new OptionsView
          model: model
          tagListUrl: '/tags'

      it 'should start with the radio matching split_documents', ->
        expect(view.$('[name=split_documents]:checked').val()).to.eq(model.get('split_documents') && 'true' || 'false')

      it 'should set split_documents to true on the model', ->
        $input = view.$('[name=split_documents]')
        $input.val('true')
        $input.change()
        expect(model.get('split_documents')).to.be.true

      it 'should set split_documents to false on the model', ->
        $input = view.$('[name=split_documents]')
        $input.prop('checked', false)
        $input.change()
        expect(model.get('split_documents')).to.be.false

      it 'should start with lang matching lang', ->
        expect(view.$('[name=lang]').val()).to.eq(model.get('lang'))

      it 'should change lang on the model', ->
        $select = view.$('[name=lang]')
        $select.val('fr')
        $select.change()
        expect(model.get('lang')).to.eq('fr')

      it 'should start with name matching name', ->
        expect(view.$('[name=name]').val()).to.eq(model.get('name'))

      it 'should make name required', ->
        expect(view.$('[name=name]').prop('required')).to.be.ok

      it 'should change name on the model', ->
        $input = view.$('[name=name]')
        $input.val('a fine set of documents')
        $input.change()
        expect(model.get('name')).to.eq('a fine set of documents')

      it 'should edit existing DocumentSet metadata', ->
        expect(view.$('[name=metadataFoo]')).to.exist
