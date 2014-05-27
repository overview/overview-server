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
        'views.DocumentSet.index.ImportOptions.tree_title.label': 'tree_title.label'
        'views.DocumentSet.index.ImportOptions.supplied_stop_words.label_html': 'supplied_stop_words.label_html'
        'views.DocumentSet.index.ImportOptions.supplied_stop_words.help': 'supplied_stop_words.help'
        'views.DocumentSet.index.ImportOptions.important_words.label_html': 'important_words.label_html'
        'views.DocumentSet.index.ImportOptions.important_words.help': 'important_words.help'
        'views.DocumentSet.index.ImportOptions.click_for_help': 'click_for_help'
        'views.DocumentSet.index.ImportOptions.tag_id.label': 'tag_id.label'
        'views.DocumentSet.index.ImportOptions.tag.loading': 'tag.loading'
        'views.DocumentSet.index.ImportOptions.tag.error': 'tag.error'
        'views.DocumentSet.index.ImportOptions.tag.allDocuments': 'tag.allDocuments'

    afterEach ->
      @sandbox.restore()
      view?.remove()

    describe 'with some options', ->
      beforeEach ->
        model = new Backbone.Model({ lang: 'en' })
        model.supportedLanguages = [{code:'en',name:'English'},{code:'fr',name:'French'},{code:'de',name:'German'},{code:'es',name:'Spanish'},{code:'sv',name:'Swedish'}]
        view = new OptionsView({ model: model })

      it 'should not render the excluded options', ->
        expect(view.$('[name=supplied_stop_words]').length).to.eq(0)
        expect(view.$('[name=split_documents]').length).to.eq(0)
        expect(view.$('[name=important_words]').length).to.eq(0)
        expect(view.$('[name=name]').length).to.eq(0)
        expect(view.$('[name=tree_title]').length).to.eq(0)
        expect(view.$('[name=tag_id]').length).to.eq(0)

    describe 'with all options', ->
      beforeEach ->
        model = new Backbone.Model
          name: ''
          tree_title: ''
          split_documents: false
          lang: 'en'
          supplied_stop_words: ''
          important_words: ''
          tag_id: ''
        model.supportedLanguages = [{code:'en',name:'English'},{code:'fr',name:'French'},{code:'de',name:'German'},{code:'es',name:'Spanish'},{code:'sv',name:'Swedish'}]
        view = new OptionsView
          model: model
          tagListUrl: '/tags'

      it 'should start with the radio matching split_documents', ->
        expect(view.$('[name=split_documents]:checked').val()).to.eq(model.get('split_documents') && 'true' || 'false')

      describe 'when there are too few documents', ->
        beforeEach -> view.setTooFewDocuments(true)
        it 'should disable split_documents=false', -> expect(view.$('[name="split_documents"][value="false"]')).to.be.disabled
        it 'should mute the radio for split_documents=false', -> expect(view.$('[name="split_documents"][value="false"]').closest('label')).to.have.class('text-muted')
        it 'should check split_documents=true', -> expect(view.$('[name=split_documents][value="true"]')).to.be.checked
        it 'should set split_documents=true on the model', -> expect(model.get('split_documents')).to.eq(true)

      it 'should set split_documents to true on the model', ->
        $input = view.$('[name=split_documents]')
        $input.val('true')
        $input.change()
        expect(model.get('split_documents')).to.be(true)

      it 'should set split_documents to false on the model', ->
        $input = view.$('[name=split_documents]')
        $input.prop('checked', false)
        $input.change()
        expect(model.get('split_documents')).to.be(false)

      it 'should start with lang matching lang', ->
        expect(view.$('[name=lang]').val()).to.eq(model.get('lang'))

      it 'should change lang on the model', ->
        $select = view.$('[name=lang]')
        $select.val('fr')
        $select.change()
        expect(model.get('lang')).to.eq('fr')

      it 'should start with supplied_stop_words matching supplied_stop_words', ->
        expect(view.$('[name=supplied_stop_words]').val()).to.eq(model.get('supplied_stop_words'))

      it 'should start with important_words matching important_words', ->
        expect(view.$('[name=important_words]').val()).to.eq(model.get('important_words'))

      it 'should start with name matching name', ->
        expect(view.$('[name=name]').val()).to.eq(model.get('name'))

      it 'should make name required', ->
        expect(view.$('[name=name]').prop('required')).to.be.ok

      it 'should change name on the model', ->
        $input = view.$('[name=name]')
        $input.val('a fine set of documents')
        $input.change()
        expect(model.get('name')).to.eq('a fine set of documents')

      it 'should start with tree_title matching tree_title', ->
        expect(view.$('[name=tree_title]').val()).to.eq(model.get('tree_title'))

      it 'should make tree_title required', ->
        expect(view.$('[name=tree_title]').prop('required')).to.be.ok

      it 'should change tree_title on the model', ->
        $input = view.$('[name=tree_title]')
        $input.val('a fine title')
        $input.change()
        expect(model.get('tree_title')).to.eq('a fine title')

      it 'should add a tag-id dropdown', ->
        $input = view.$('select[name=tag_id]')
        expect($input.length).to.eq(1)
        expect($input.val()).to.eq('')
