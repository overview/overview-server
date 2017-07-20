define [
  'backbone'
  'apps/ApiTokens/views/TableView'
  'i18n'
], (Backbone, TableView, i18n) ->
  describe 'apps/ApiTokens/views/TableView', ->
    class MockApiToken extends Backbone.Model
      defaults:
        token: null
        createdAt: null
        description: null

    class MockApiTokens extends Backbone.Collection
      model: MockApiToken

    beforeEach ->
      i18n.reset_messages
        'time_display.datetime.medium': 'datetime.medium'
        'views.ApiTokens.table.caption': 'table.caption'
        'views.ApiTokens.th.token': 'th.token'
        'views.ApiTokens.th.createdAt': 'th.createdAt'
        'views.ApiTokens.th.description': 'th.description'
        'views.ApiTokens.delete': 'delete'
        'views.ApiTokens.delete.confirm': 'delete.confirm'

      @collection = new MockApiTokens
      @subject = new TableView
        collection: @collection
      @subject.render()

    afterEach ->
      @subject.stopListening()

    it 'should show nothing when empty', ->
      expect(@subject.$el.html()).to.eq('')

    it 'should show nothing after the final token is removed', ->
      @collection.add({})
      @collection.remove(@collection.at(0))
      expect(@subject.$el.html()).to.eq('')

    it 'should show nothing after reset to empty', ->
      @collection.add({})
      @collection.reset([])
      expect(@subject.$el.html()).to.eq('')

    describe 'after adding a token', ->
      beforeEach ->
        @collection.add({ token: '12345', description: 'description', createdAt: new Date(1405543902680) })

      it 'should show a table', ->
        expect(@subject.$('h3')).to.contain('table.caption')
        expect(@subject.$('table tbody tr')).to.exist

      it 'should show the token', ->
        $el = @subject.$('table tbody tr')
        expect($el).to.exist
        expect($el.find('.token')).to.contain('12345')
        expect($el.find('.description')).to.contain('description')
        expect($el.find('.created-at time')).to.have.attr('datetime', '2014-07-16T20:51:42.680Z')
        expect($el.find('.created-at time')).to.contain('datetime.medium')

      describe 'when clicking delete', ->
        beforeEach ->
          @sandbox = sinon.sandbox.create()
          @$link = @subject.$('table tbody tr a.delete')
          @confirmStub = @sandbox.stub(window, 'confirm')
          @collection.at(0).destroy = sinon.spy()

        it 'should confirm', ->
          @$link.click()
          expect(@confirmStub).to.have.been.calledWith('delete.confirm')

        it 'should do nothing on confirm=false', ->
          @confirmStub.returns(false)
          @$link.click()
          expect(@collection.at(0).destroy).not.to.have.been.called

        it 'should destroy the model on confirm=true', ->
          @confirmStub.returns(true)
          @$link.click()
          expect(@collection.at(0).destroy).to.have.been.called

        afterEach ->
          @sandbox.restore()

    describe 'after adding an un-synced token', ->
      beforeEach ->
        @collection.add(token: null, description: 'new token', createdAt: null)

      it 'should show the token with a spinner', ->
        $el = @subject.$('table tbody tr')
        expect($el).to.exist
        expect($el.find('td.token i.icon-spinner.icon-spin')).to.exist

    describe 'after reset', ->
      beforeEach ->
        @collection.reset([{ token: '12345', description: 'description', createdAt: new Date(1405543902680) }])

      it 'should show a table', -> expect(@subject.$('table')).to.exist
