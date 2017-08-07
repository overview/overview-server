define [
  'apps/ImportDocumentsMetadata/App'
  'i18n'
], (App, i18n) ->
  describe 'apps/ImportDocumentsMetadata/App', ->
    beforeEach ->
      i18n.reset_messages_namespaced 'views.DocumentSet.show.DocumentMetadata',
        'App.title': 'title'
        'JsonView.help_html': 'help_html'
        'JsonView.delete': 'delete'
        'JsonView.confirmDelete': 'confirmDelete,{0}'
        'AddFieldView.label': 'label'
        'AddFieldView.expand': 'expand'
        'AddFieldView.placeholder': 'placeholder'
        'AddFieldView.submit': 'submit'
        'AddFieldView.reset': 'reset'

    describe 'with no document set', ->
      beforeEach ->
        @subject = App.forNoDocumentSet()

      it 'contain a form', ->
        expect(@subject.$('form')).to.exist
