define [
  'apps/DocumentMetadata/App'
  'i18n'
], (App, i18n) ->
  describe 'apps/DocumentMetadata/App', ->
    beforeEach ->
      @sandbox = sinon.sandbox.create()

      @documentSet = new Backbone.Model(metadataFields: [ 'foo', 'bar' ])
      @document1 = new Backbone.Model()
      @document1.url = '/1'
      @sandbox.stub(Backbone, 'ajax')

      i18n.reset_messages_namespaced 'views.DocumentSet.show.DocumentMetadata',
        'App.loading': 'loading'
        'App.title': 'title'
        'JsonView.delete': 'delete'
        'JsonView.confirmDelete': 'confirmDelete,{0}'
        'AddFieldView.label': 'label'
        'AddFieldView.expand': 'expand'
        'AddFieldView.placeholder': 'placeholder'
        'AddFieldView.submit': 'submit'
        'AddFieldView.reset': 'reset'

      @subject = new App(documentSet: @documentSet, expanded: false)

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
      it 'should show a loading indicator', -> expect(@subject.$el.text()).to.contain('loading')
      it 'should not show a form', -> expect(@subject.$('form')).to.have.length(0)
      it 'should send an AJAX request', -> expect(Backbone.ajax).to.have.been.called

      it 'should ignore stale document metadata', ->
        document2 = new Backbone.Model()
        document2.url = '/2'
        @subject.setDocument(document2)
        Backbone.ajax.args[0][0].success(metadata: { foo: 'bar', bar: 'baz' })
        expect(@subject.$el.text()).to.contain('loading')

      it 'should expand when clicking h4', ->
        @subject.$('h4 a').click()
        expect(@subject.$el).to.have.class('expanded')

      it 'should collapse when clicking h4 again', ->
        @subject.$('h4 a').click().click()
        expect(@subject.$el).not.to.have.class('expanded')

      it 'should begin expanded if the previously-constructed DocumentMetadataApp was expanded', ->
        @subject.$('h4 a').click()
        subject2 = new App(documentSet: @documentSet)
        expect(subject2.$el).to.have.class('expanded')

      describe 'when fetch completes', ->
        beforeEach ->
          Backbone.ajax.args[0][0].success(metadata: { foo: 'bar', bar: 'baz' })

        it 'should set the document metadata', -> expect(@document1.get('metadata')).to.deep.eq(foo: 'bar', bar: 'baz')
        it 'should hide the loading indicator', -> expect(@subject.$el.text()).not.to.contain('loading')
        it 'should show a title', -> expect(@subject.$el.text()).to.contain('title')
        it 'should show a metadata form', -> expect(@subject.$('form.metadata-json')).to.have.length(1)
        it 'should show an add-field form', -> expect(@subject.$('form.add-metadata-field')).to.have.length(1)

        it 'should go back to empty on setDocument(null)', ->
          @subject.setDocument(null)
          expect(@subject.$el).to.have.html('')
