define [
  'backbone'
  'apps/ApiTokens/views/FormView'
  'i18n'
], (Backbone, FormView, i18n) ->
  describe 'apps/ApiTokens/views/FormView', ->
    class MockApiToken extends Backbone.Model

    class MockApiTokens extends Backbone.Collection
      model: MockApiToken

    beforeEach ->
      i18n.reset_messages
        'views.ApiTokens.form.heading': 'heading'
        'views.ApiTokens.form.description.label': 'description.label'
        'views.ApiTokens.form.description.placeholder': 'description.placeholder'
        'views.ApiTokens.form.submit.label': 'submit.label'

      @collection = new MockApiTokens
      @collection.create = sinon.spy()

      @subject = new FormView
        collection: @collection
      @subject.render()

    afterEach ->
      @subject.stopListening()

    it 'should show a form', ->
      $el = @subject.$el
      expect($el).to.be('form')
      expect($el.find('h3')).to.contain('heading')
      expect($el.find(':submit')).to.contain('submit.label')
      expect($el.find('label[for=api-token-description]')).to.contain('description.label')

    it 'should do nothing when submitting empty', ->
      @subject.$('input').val('  ')
      @subject.$(':submit').click()
      expect(@collection.create).not.to.have.been.called

    describe 'upon submit', ->
      beforeEach ->
        @subject.$('input').val('foo')
        @subject.$(':submit').click()

      it 'should create the API token', ->
        expect(@collection.create).to.have.been.calledWith(description: 'foo')

      it 'should reset the form', ->
        expect(@subject.$('input').val()).to.eq('')
