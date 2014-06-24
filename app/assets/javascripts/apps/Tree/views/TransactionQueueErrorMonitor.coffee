define [
  'underscore'
  'backbone'
  'i18n'
], (_, Backbone, i18n) ->
  t = i18n.namespaced('views.DocumentSet.show.TransactionQueueErrorMonitor')

  # Warns the user when there is an error in the transactionQueue.
  #
  # Usage:
  #
  #   transactionQueue = new TransactionQueue()
  #   view = new TransactionQueueErrorMonitor(model: transactionQueue)
  #   document.body.appendChild(view.el)
  class TransactionQueueErrorMonitor extends Backbone.View
    className: 'transaction-queue-error-monitor'

    templates:
      error: _.template("""
        <div class="modal-backdrop fade in"></div>
        <div class="modal fade in" style="display:block;">
          <div class="modal-dialog">
            <div class="modal-content">
              <div class="modal-header">
                <h4 class="modal-title"><%- t('error.title') %></h4>
              </div>
              <div class="modal-body">
                <p><%- t('error.description') %></p>
              </div>
              <div class="modal-footer">
                <button type="button" class="btn btn-primary reload"><%- t('reload') %>
              </div>
            </div>
          </div>
        </div>
      """)

    events:
      'click .reload': '_onReload'

    initialize: (options) ->
      throw 'Must pass options.model, a TransactionQueue' if !options.model

      @listenTo(@model, 'error', @_onError)

    _onError: ->
      html = @templates.error(t: t)
      @$el.html(html)

    _onReload: (e) ->
      e.preventDefault()
      window.location.reload(true)
