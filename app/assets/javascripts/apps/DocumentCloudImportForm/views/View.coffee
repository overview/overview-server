define [
  'backbone'
  'apps/DocumentCloudImportForm/models/Credentials'
  'apps/DocumentCloudImportForm/views/Credentials'
  'apps/DocumentCloudImportForm/views/DocumentCloudQuery'
  'apps/DocumentCloudImportForm/views/Form'
], (Backbone, Credentials, CredentialsView, QueryView, FormView) ->
  Backbone.View.extend({
    tagName: 'form'

    events:
      'submit': 'onSubmit'

    initialize: ->
      throw 'Must pass options.extraOptionsEl, an HTML element' if !@options.extraOptionsEl

      @render()

    render: ->
      @el.setAttribute('action', @options.submitUrl)
      @el.setAttribute('method', 'POST')

      credentialsView = new CredentialsView({ model: @model })
      queryView = new QueryView({ model: @model })
      formView = new FormView({
        model: @model
        extraOptionsEl: @options.extraOptionsEl
      })

      @$el.append("<input type=\"hidden\" name=\"query\" value=\"#{@model.get('query').id}\" />")
      @$el.append(window.csrfTokenHtml) if window.csrfTokenHtml?

      @el.appendChild(credentialsView.el)
      @el.appendChild(queryView.el)
      @el.appendChild(formView.el)

    onSubmit: (e) ->
      if @model.get('status') != 'fetched'
        credentials = new Credentials({
          email: @$('[name=documentcloud_username]').val()
          password: @$('[name=documentcloud_password]').val()
        })
        @model.set('credentials', credentials)
        e.preventDefault()
  })
