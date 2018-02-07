import $ from 'jquery'
import i18n from 'i18n'
import TransactionQueueMonitor from 'apps/Show/views/TransactionQueueMonitor'

describe('apps/Show/views/TransactionQueueMonitor', function() {
  beforeEach(function() {
    i18n.reset_messages({
      'views.DocumentSet.show.TransactionQueueMonitor.communicating': 'communicating',
      'views.DocumentSet.show.TransactionQueueMonitor.error.title': 'error.title',
      'views.DocumentSet.show.TransactionQueueMonitor.error.description': 'error.description',
      'views.DocumentSet.show.TransactionQueueMonitor.networkError.title': 'networkError.title',
      'views.DocumentSet.show.TransactionQueueMonitor.networkError.description': 'networkError.description',
      'views.DocumentSet.show.TransactionQueueMonitor.reload': 'reload',
      'views.DocumentSet.show.TransactionQueueMonitor.retry': 'retry',
    })

    this.div = document.createElement('div')
    document.body.appendChild(this.div)
    this.view = new TransactionQueueMonitor({
      target: this.div,
      data: {
        nAjaxIncomplete: 0,
        error: null,
        networkError: null,
      }
    })

    this.reload = sinon.spy()
    this.retry = sinon.spy()
    this.view.on('reload', this.reload)
    this.view.on('retry', this.retry)

    this.$el = $(this.div)
  })

  afterEach(function() {
    this.view.destroy()
    document.body.removeChild(this.div)
  })

  describe('with a fatal error', function() {
    beforeEach(function() {
      this.view.set({ error: {} })
    })

    it('should display the error title', function() { expect(this.$el).to.contain('error.title') })
    it('should display the error description', function() { expect(this.$el).to.contain('error.description') })
    it('should not offer to retry', function() { expect(this.$el.find('.retry')).not.to.exist })
    it('should offer to reload', function() { expect(this.$el.find('button.reload')).to.exist })

    it('should fire reload', function() {
      this.$el.find('.reload').click()
      expect(this.reload).to.have.been.called
    })
  })

  describe('with a network error', function() {
    beforeEach(function() {
      this.view.set({ networkError: {} })
    })

    it('should display the error title', function() { expect(this.$el).to.contain('networkError.title') })
    it('should display the error description', function() { expect(this.$el).to.contain('networkError.description') })
    it('should not offer to retry', function() { expect(this.$el.find('button.retry')).to.exist })
    it('should offer to reload', function() { expect(this.$el.find('button.reload')).to.exist })

    it('should fire retry', function() {
      this.$el.find('.retry').click()
      expect(this.retry).to.have.been.called
    })

    it('should fire reload', function() {
      this.$el.find('.reload').click()
      expect(this.reload).to.have.been.called
    })
  })
})
