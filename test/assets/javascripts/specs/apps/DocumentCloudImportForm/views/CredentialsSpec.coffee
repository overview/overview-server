require [
  'jquery'
  'backbone'
  'apps/DocumentCloudImportForm/views/Credentials'
  'i18n'
], (
  $,
  Backbone,
  CredentialsView,
  i18n
) ->
  describe 'app/DocumentCloudImportForm/views/Credentials', ->
    model = undefined
    view = undefined

    beforeEach ->
      model = new Backbone.Model({
        project: new Backbone.Model({
          id: 123
          title: ''
          description: null
          document_ids: []
        }),
        credentials: undefined
        status: 'unknown'
      })
      i18n.reset_messages({
        'views.DocumentCloudImportJob.new.preamble': 'credentials_preamble'
        'views.DocumentCloudImportJob.new.email.label': 'email.label'
        'views.DocumentCloudImportJob.new.email.placeholder': 'email.placeholder'
        'views.DocumentCloudImportJob.new.password.label': 'password.label'
        'views.DocumentCloudImportJob.new.submit_credentials.label': 'password.label'
        'views.DocumentCloudImportJob.new.submit_credentials.preamble.cors': 'preamble.cors'
        'views.DocumentCloudImportJob.new.submit_credentials.preamble.no_cors': 'preamble.no_cors'
        'views.DocumentCloudImportJob.new.credentials_preamble': 'credentials_preamble'
        'views.DocumentCloudImportJob.new.fetching': 'fetching'
        'views.DocumentCloudImportJob.new.fetched': 'fetched'
      })
      view = new CredentialsView({ model: model })

    it 'should not render anything when status is unknown', ->
      expect(view.$el.html()).toEqual('')

    it 'should show a spinner when status is fetching', ->
      model.set('status', 'fetching')
      expect(view.$('div.loading').length).toEqual(1)

    it 'should prompt for username/password on error', ->
      model.set('status', 'error')
      expect(view.$('input[type=email]').length).toEqual(1)
      expect(view.$('input[type=password]').length).toEqual(1)
      expect(view.$(':submit').length).toEqual(1)

    it 'should show success', ->
      model.set({
        credentials: { isComplete: (-> true), get: -> 'user@example.org' }
        status: 'fetched'
      })
      expect(view.$('p.fetched').text()).toEqual('fetched')

    it 'should show hidden fields when project is loaded', ->
      credentials = new Backbone.Model({
        email: 'user@example.org'
        password: 'password'
      })
      credentials.isComplete = -> true
      model.set({ credentials: credentials, status: 'fetched' })
      expect(view.$('input[name=documentcloud_username]').val()).toEqual('user@example.org')
      expect(view.$('input[name=documentcloud_password]').val()).toEqual('password')
