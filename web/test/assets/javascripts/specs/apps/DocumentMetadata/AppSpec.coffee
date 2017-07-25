define [
  'apps/DocumentMetadata/App'
  'i18n'
], (App, i18n) ->
  describe 'apps/DocumentMetadata/App', ->
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
        @subject = App.forNoDocumentSet(expanded: false)

      it 'contain a form', ->
        expect(@subject.$('form')).to.exist

    describe 'with a document set', ->
      beforeEach ->
        @sandbox = sinon.sandbox.create()

        @documentSet = new Backbone.Model(metadataFields: [ 'foo', 'bar' ])
        @document1 = new Backbone.Model(metadata: { foo: 'bar', bar: 'baz' })
        @document1.url = '/1'
        @sandbox.stub(Backbone, 'ajax')

        @subject = App.forDocumentSet(@documentSet, expanded: false)

      afterEach ->
        @sandbox.restore()

      it 'should start empty', ->
        expect(@subject.$el).to.have.html('')

      it 'should start unexpanded', ->
        expect(@subject.$el).not.to.have.class('expanded')

      describe 'when setting a document', ->
        beforeEach ->
          @subject.setDocument(@document1)

        it 'should set the @document', -> expect(@subject.document).to.eq(@document1)
        it 'should show a title', -> expect(@subject.$el.text()).to.contain('title')

        it 'should ignore stale document metadata', ->
          document2 = new Backbone.Model(metadata: { foo: 'baz' })
          document2.url = '/2'
          @subject.setDocument(document2)
          @document1.set(metadata: { foo: 'moo', bar: 'mar' })
          expect(@subject.$el.html()).not.to.contain('moo')

        it 'should expand when clicking h4', ->
          @subject.$('h4 a').click()
          expect(@subject.$el).to.have.class('expanded')

        it 'should collapse when clicking h4 again', ->
          @subject.$('h4 a').click().click()
          expect(@subject.$el).not.to.have.class('expanded')

        it 'should begin expanded if the previously-constructed DocumentMetadataApp was expanded', ->
          @subject.$('h4 a').click()
          subject2 = App.forDocumentSet(@documentSet)
          expect(subject2.$el).to.have.class('expanded')

        it 'should set the document metadata', -> expect(@document1.get('metadata')).to.deep.eq(foo: 'bar', bar: 'baz')
        it 'should show a title', -> expect(@subject.$el.text()).to.contain('title')
        it 'should show a metadata form', -> expect(@subject.$('form.metadata-json')).to.have.length(1)
        it 'should show an add-field form', -> expect(@subject.$('form.add-metadata-field')).to.have.length(1)

        it 'should go back to empty on setDocument(null)', ->
          @subject.setDocument(null)
          expect(@subject.$el).to.have.html('')
