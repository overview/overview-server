define [ 'jquery', 'backbone', 'apps/UserAdmin/App', 'i18n' ], ($, Backbone, App, i18n) ->
  describe 'apps/UserAdmin/App', ->
    $el = undefined
    app = undefined

    beforeEach ->
      i18n.reset_messages
        'views.admin.User.index.th.email': 'th.email'
        'views.admin.User.index.th.admin': 'th.admin'
        'views.admin.User.index.th.confirmed_at': 'th.confirmed_at'
        'views.admin.User.index.th.last_activity_at': 'th.last_activity_at'
        'views.admin.User.index.th.actions': 'th.actions'
        'views.admin.User.index.new.title': 'new.title'
        'views.admin.User.index.new.email': 'new.email'
        'views.admin.User.index.new.password': 'new.password'
        'views.admin.User.index.new.submit': 'new.submit'

      @sandbox = sinon.sandbox.create()
      @sandbox.stub(Backbone, 'sync')
      $el = $('<div id="user-admin-app"></div>').appendTo($('body'))
      app = new App
        el: $el.get(0)
        adminEmail: 'admin@example.org'

    afterEach ->
      @sandbox.restore()
      $el?.remove()
      app?.remove()

    it 'should use an existing element', ->
      expect(app.el.getAttribute('id')).to.eq('user-admin-app')

    it 'should have a controller', ->
      expect(app.controller).not.to.be.undefined

    it 'should sync the users collection', ->
      expect(Backbone.sync).to.have.been.called

    it 'should create a MainView with @el', ->
      expect($(app.el).find('table.users').length).to.eq(1)
