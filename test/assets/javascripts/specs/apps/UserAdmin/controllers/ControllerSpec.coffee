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
      subject?.stopListening()

    it 'should sync a user when it changes', ->
      user = new MockUser({ email: 'user@example.org', is_admin: false })
      spyOn(user, 'save')
      users.add(user)
      user.set({ is_admin: true })
      expect(user.save).toHaveBeenCalled()

    it 'should clear the password after syncing a user', ->
      user = new MockUser({ email: 'user@example.org', is_admin: false })
      spyOn(user, 'save')
      users.add(user)
      user.set(password: 'as;dj#$xfF')
      expect(user.save).toHaveBeenCalled()
      expect(user.save.callCount).toEqual(1)
      expect(user.has('password')).toBe(false)

    it 'should destroy a user when it is deleting, but not remove it from the collection', ->
      spyOn(Backbone, 'sync')
      user = new MockUser(email: 'user@example.org', is_admin: false)
      users.add(user)
      user.set(deleting: true)
      expect(Backbone.sync).toHaveBeenCalled()
      expect(Backbone.sync.mostRecentCall.args[0]).toEqual('delete')
      expect(Backbone.sync.mostRecentCall.args[1]).toBe(user)
      expect(users.length).toEqual(1)

    it 'should remove a user from the collection when delete is done', ->
      user = new MockUser(email: 'user@example.org', is_admin: false)
      users.add(user)
      spyOn(Backbone, 'sync').andCallFake((__, __1, options) -> options.success())
      user.set(deleting: true)
      expect(users.length).toEqual(0)

    it 'should re-fetch the collection when pagination changes', ->
      spyOn(users, 'fetch')
      paginator.set(page: 2)
      expect(users.pagination).toEqual({ page: 2, pageSize: 50 })
      expect(users.fetch).toHaveBeenCalled()

    it 'should set the paginator when the collection parses it', ->
      users.trigger('parse-pagination', page: 2, pageSize: 50, total: 72)
      expect(paginator.toJSON()).toEqual(page: 2, pageSize: 50, total: 72)

    it 'should not fetch users when changing pagination via parse', ->
      spyOn(users, 'fetch')
      users.trigger('parse-pagination', page: 2, pageSize: 50, total: 72)
      expect(users.fetch).not.toHaveBeenCalled()

    describe 'when trying to create', ->
      params = { email: 'user@example.org', password: 'as;dj#$xfF' }

      beforeEach ->
        spyOn(Backbone, 'sync')

      describe 'before sync is done', ->
        beforeEach -> mainView.trigger('create', params)

        it 'should add a model to the collection', -> expect(users.length).toEqual(1)
        it 'should call Backbone.sync', -> expect(Backbone.sync).toHaveBeenCalled()
