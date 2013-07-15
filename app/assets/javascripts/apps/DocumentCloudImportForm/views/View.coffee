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
      throw 'Must pass options.supportedLanguages, an Array of { code: "en", name: "English" }' if !@options.supportedLanguages
      throw 'Must pass options.defaultLanguageCode, a 2-letter code like "en"' if !@options.defaultLanguageCode

      @render()

    render: ->
      @el.setAttribute('action', @options.submitUrl)
      @el.setAttribute('method', 'POST')

      credentialsView = new CredentialsView({ model: @model })
      queryView = new QueryView({ model: @model })
      formView = new FormView({
        model: @model
        supportedLanguages: @options.supportedLanguages
        defaultLanguageCode: @options.defaultLanguageCode
      })

      @$el.append("<input type=\"hidden\" name=\"query\" value=\"#{@model.get('query').id}\" />")

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
