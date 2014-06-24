define [
  'apps/Tree/views/TransactionQueueErrorMonitor'
  'i18n'
], (Subject, i18n) ->
  describe 'apps/Tree/views/TransactionQueueErrorMonitor', ->
    class TransactionQueue extends Backbone.Model

    beforeEach ->
      @sandbox = sinon.sandbox.create()
      i18n.reset_messages
        'views.DocumentSet.show.TransactionQueueErrorMonitor.error.title': 'error.title'
        'views.DocumentSet.show.TransactionQueueErrorMonitor.error.description': 'error.description'
        'views.DocumentSet.show.TransactionQueueErrorMonitor.networkError.title': 'networkError.title'
        'views.DocumentSet.show.TransactionQueueErrorMonitor.networkError.description': 'networkError.description'
        'views.DocumentSet.show.TransactionQueueErrorMonitor.reload': 'reload'
        'views.DocumentSet.show.TransactionQueueErrorMonitor.retry': 'retry'

      @queue = new TransactionQueue
      @subject = new Subject(model: @queue)
      @$el = @subject.$el

    afterEach ->
      @sandbox.restore()
      @subject.remove()
      @queue.off()

    describe 'with a fatal error', ->
      beforeEach ->
        @queue.trigger('error', {})

      it 'should display the error title', -> expect(@$el).to.contain('error.title')
      it 'should display the error description', -> expect(@$el).to.contain('error.description')
      it 'should not offer to retry', -> expect(@$el.find('.retry')).not.to.exist
      it 'should offer to reload', -> expect(@$el.find('button.reload')).to.exist

      it 'should reload the page', ->
        @sandbox.stub(window.location, 'reload')
        @$el.find('.reload').click()
        expect(window.location.reload).to.have.been.calledWith(true)

    describe 'with a network error', ->
      beforeEach ->
        @retry = sinon.spy()
        @queue.trigger('network-error', {}, @retry)

      it 'should display the error title', -> expect(@$el).to.contain('networkError.title')
      it 'should display the error description', -> expect(@$el).to.contain('networkError.description')
      it 'should not offer to retry', -> expect(@$el.find('button.retry')).to.exist
      it 'should offer to reload', -> expect(@$el.find('button.reload')).to.exist

      it 'should retry the request', ->
        @$el.find('.retry').click()
        expect(@retry).to.have.been.called
        expect(@$el.html()).to.eq('')

      it 'should reload the page', ->
        @sandbox.stub(window.location, 'reload')
        @$el.find('.reload').click()
        expect(window.location.reload).to.have.been.calledWith(true)
