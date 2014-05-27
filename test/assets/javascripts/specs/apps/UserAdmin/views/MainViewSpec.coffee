define [ 'apps/UserAdmin/views/MainView', 'i18n' ], (MainView, i18n) ->
  describe 'apps/UserAdmin/views/MainView', ->
    paginator = undefined
    users = undefined
    view = undefined
    adminEmail = 'admin@example.org'

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
      paginator = new Backbone.Model
      users = new Backbone.Collection
      view = new MainView
        paginator: paginator
        users: users
        adminEmail: adminEmail
      view.render()

    afterEach -> view?.remove()

    it 'should render a users table with a tbody', ->
      expect(view.$('table.users tbody').length).to.eq(1)

    it 'should render pagination', ->
      expect(view.$('div.paginator').length).to.eq(1)

    it 'should render a new-user form', ->
      expect(view.$('table.users tfoot.new-user').length).to.eq(1)

    it 'should bubble up new-user-create events', ->
      spy = sinon.spy()
      view.on('create', spy)
      params = { email: 'user@example.org', password: '2093qwsDDSF3#' }
      view.newUserView.trigger('create', params)
      expect(spy).to.have.been.calledWith(params)
