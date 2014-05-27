define [ 'backbone', 'apps/UserAdmin/controllers/Controller' ], (Backbone, Controller) ->
  describe 'apps/UserAdmin/controllers/Controller', ->
    paginator = undefined
    users = undefined
    subject = undefined
    mainView = undefined
    class MockUser extends Backbone.Model
      idAttribute: 'email'
      url: 'foo'
    class MockUsers extends Backbone.Collection
      model: MockUser

    beforeEach ->
      @sandbox = sinon.sandbox.create()
      paginator = new Backbone.Model
        page: 1
        pageSize: 50
      users = new MockUsers
      mainView = new Backbone.View
      subject = new Controller
        users: users
        paginator: paginator
        mainView: mainView

    afterEach ->
      @sandbox.restore()
      subject?.stopListening()

    it 'should sync a user when it changes', ->
      user = new MockUser({ email: 'user@example.org', is_admin: false })
      user.save = sinon.spy()
      users.add(user)
      user.set({ is_admin: true })
      expect(user.save).to.have.been.called

    it 'should clear the password after syncing a user', ->
      user = new MockUser({ email: 'user@example.org', is_admin: false })
      user.save = sinon.spy()
      users.add(user)
      user.set(password: 'as;dj#$xfF')
      expect(user.save).to.have.been.called
      expect(user.save.callCount).to.eq(1)
      expect(user.has('password')).to.be(false)

    it 'should destroy a user when it is deleting, but not remove it from the collection', ->
      @sandbox.stub(Backbone, 'sync')
      user = new MockUser(email: 'user@example.org', is_admin: false)
      users.add(user)
      user.set(deleting: true)
      expect(Backbone.sync).to.have.been.called
      expect(Backbone.sync.lastCall.args[0]).to.eq('delete')
      expect(Backbone.sync.lastCall.args[1]).to.be(user)
      expect(users.length).to.eq(1)

    it 'should remove a user from the collection when delete is done', ->
      user = new MockUser(email: 'user@example.org', is_admin: false)
      users.add(user)
      @sandbox.stub(Backbone, 'sync', (__, __1, options) -> options.success())
      user.set(deleting: true)
      expect(users.length).to.eq(0)

    it 'should re-fetch the collection when pagination changes', ->
      users.fetch = sinon.spy()
      paginator.set(page: 2)
      expect(users.pagination).to.deep.eq({ page: 2, pageSize: 50 })
      expect(users.fetch).to.have.been.called

    it 'should set the paginator when the collection parses it', ->
      users.trigger('parse-pagination', page: 2, pageSize: 50, total: 72)
      expect(paginator.toJSON()).to.deep.eq(page: 2, pageSize: 50, total: 72)

    it 'should not fetch users when changing pagination via parse', ->
      users.fetch = sinon.spy()
      users.trigger('parse-pagination', page: 2, pageSize: 50, total: 72)
      expect(users.fetch).not.to.have.been.called

    describe 'when trying to create', ->
      params = { email: 'user@example.org', password: 'as;dj#$xfF' }

      beforeEach ->
        @sandbox.stub(Backbone, 'sync')

      describe 'before sync is done', ->
        beforeEach -> mainView.trigger('create', params)

        it 'should add a model to the collection', -> expect(users.length).to.eq(1)
        it 'should call Backbone.sync', -> expect(Backbone.sync).to.have.been.called
