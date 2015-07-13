define [
  'apps/DocumentMetadata/App'
  'i18n'
], (App, i18n) ->
  describe 'apps/DocumentMetadata/App', ->
    beforeEach ->
      @documentSet = new Backbone.Model(metadataFields: [ 'foo', 'bar' ])
      @document1 = new Backbone.Model()
      @document1.fetch = sinon.spy()
      @document2 = new Backbone.Model()
      @document2.fetch = sinon.spy()

      i18n.reset_messages
        'views.DocumentSet.show.DocumentMetadata.App.loading': 'loading'

      @subject = new App(documentSet: @documentSet)

    it 'should start with a loading indicator', ->
      expect(@subject.$el.text()).to.contain('loading')

    describe 'when setting a document', ->
      beforeEach ->
        @subject.setDocument(@document1)

      it 'should set the @document', -> expect(@subject.document).to.eq(@document1)
      it 'should show a loading indicator', -> expect(@subject.$el.text()).to.contain('loading')
      it 'should not show a form', -> expect(@subject.$('form')).to.have.length(0)
      it 'should fetch from the document', -> expect(@document1.fetch).to.have.been.called

      describe 'when fetch completes', ->
        beforeEach ->
          @document1.set(metadata: { foo: 'bar', bar: 'baz' })
          @document1.fetch.args[0][0].success()

        it 'should hide the loading indicator', -> expect(@subject.$el.text()).not.to.contain('loading')
        it 'should show a metadata form', -> expect(@subject.$('form.metadata-json')).to.have.length(1)
