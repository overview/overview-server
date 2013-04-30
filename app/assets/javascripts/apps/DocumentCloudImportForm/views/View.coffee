define [
  'backbone'
  'apps/DocumentCloudImportForm/models/Credentials'
  'apps/DocumentCloudImportForm/views/Credentials'
  'apps/DocumentCloudImportForm/views/DocumentCloudProject'
  'apps/DocumentCloudImportForm/views/Form'
], (Backbone, Credentials, CredentialsView, ProjectView, FormView) ->
  Backbone.View.extend({
    tagName: 'form'

    events:
      'submit': 'onSubmit'

    initialize: ->
      @render()

    render: ->
      @el.setAttribute('action', @options.submitUrl)
      @el.setAttribute('method', 'POST')

      credentialsView = new CredentialsView({ model: @model })
      projectView = new ProjectView({ model: @model })
      formView = new FormView({ model: @model })

      @$el.append("<input type=\"hidden\" name=\"project_id\" value=\"#{@model.get('project').id}\" />")

      @el.appendChild(credentialsView.el)
      @el.appendChild(projectView.el)
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
