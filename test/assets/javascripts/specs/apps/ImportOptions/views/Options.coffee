require [
  'backbone'
  'apps/ImportOptions/views/Options'
  'i18n'
], (Backbone, OptionsView, i18n) ->
  describe 'apps/ImportOptions/views/Options', ->
    view = undefined
    model = undefined

    beforeEach ->
      i18n.reset_messages
        'views.DocumentSet.index.ImportOptions.title': 'title'
        'views.DocumentSet.index.ImportOptions.split_documents.label': 'split_documents.label'
        'views.DocumentSet.index.ImportOptions.lang.label': 'lang.label'
        'views.DocumentSet.index.ImportOptions.supplied_stop_words.label': 'supplied_stop_words.label'
        'views.DocumentSet.index.ImportOptions.supplied_stop_words.help': 'supplied_stop_words.help'

      model = new Backbone.Model({ split_documents: false, lang: 'en', supplied_stop_words: '' })
      model.supportedLanguages = [{code:'en',name:'English'},{code:'fr',name:'French'},{code:'de',name:'German'},{code:'es',name:'Spanish'},{code:'sv',name:'Swedish'}]
      view = new OptionsView({ model: model })

    afterEach ->
      view?.remove()

    it 'should start with the checkbox matching split_documents', ->
      expect(view.$('[name=split_documents]').prop('checked')).toEqual(model.get('split_documents'))

    it 'should set split_documents to true on the model', ->
      $input = view.$('[name=split_documents]')
      $input.prop('checked', true)
      $input.change()
      expect(model.get('split_documents')).toBe(true)

    it 'should set split_documents to false on the model', ->
      $input = view.$('[name=split_documents]')
      $input.prop('checked', false)
      $input.change()
      expect(model.get('split_documents')).toBe(false)

    it 'should start with lang matching lang', ->
      expect(view.$('[name=lang]').val()).toEqual(model.get('lang'))

    it 'should change lang on the model', ->
      $select = view.$('[name=lang]')
      $select.val('fr')
      $select.change()
      expect(model.get('lang')).toEqual('fr')

    it 'should start with supplied_stop_words matching supplied_stop_words', ->
      expect(view.$('[name=supplied_stop_words]').val()).toEqual(model.get('supplied_stop_words'))
