define [
  'jquery'
  'backbone'
  'apps/UserAdmin/views/UserView'
  'i18n'
], ($, Backbone, UserView, i18n) ->
  describe 'apps/UserAdmin/views/UserView', ->
    class MockUser extends Backbone.Model
      defaults:
        email: 'user@example.org'
        is_admin: false
        confirmed_at: '2014-02-17T20:48:50Z'
        last_sign_in_at: '2014-02-17T20:48:50Z'
        last_activity_at: null

      getDate: (key) ->
        if key in [ 'confirmed_at', 'last_sign_in_at' ]
          new Date(1392670130000)
        else
          null

    user = undefined
    view = undefined

    beforeEach ->
      @sandbox = sinon.sandbox.create()
      i18n.reset_messages
        'views.admin.User.index.action.promote': 'promote'
        'views.admin.User.index.action.demote': 'demote'
        'views.admin.User.index.action.delete': 'delete'
        'views.admin.User.index.action.changePassword': 'action.changePassword'
        'views.admin.User.index.changePassword.label': 'changePassword.label'
        'views.admin.User.index.changePassword.submit': 'changePassword.submit'
        'views.admin.User.index.changePassword.reset': 'changePassword.reset'
        'views.admin.User.index.confirm.delete': 'confirm.delete,{0}'
        'views.admin.User.index.td.is_admin.false': 'no'
        'views.admin.User.index.td.is_admin.true': 'yes'
        'views.admin.User.index.td.confirmed_at': 'views.admin.User.index.td.confirmed_at,{0}'
        'views.admin.User.index.td.last_activity_at': 'views.admin.User.index.td.last_activity_at,{0}'

      user = new MockUser()
      view = new UserView(model: user, adminEmail: 'admin@example.org')
      $('body').append(view.el) # make isVisible() work
      view.render()

    afterEach ->
      @sandbox.restore()
      view.remove()

    it 'should display the email', ->
      expect(view.$('.email').text()).to.eq('user@example.org')

    it 'should display is-admin with a promote link', ->
      expect(view.$('.is-admin').text()).to.contain('no promote')

    it 'should change is-admin to true on click', ->
      view.$('.is-admin a').click()
      expect(user.get('is_admin')).to.eq(true)

    it 'should display is-admin with a demote link', ->
      view.model.set('is_admin', true)
      expect(view.$('.is-admin').text()).to.contain('yes demote')

    it 'should not display demote link for current admin', ->
      view.model.set(email: 'admin@example.org', is_admin: true)
      expect(view.$('.is-admin a').length).to.eq(0)

    it 'should change is-admin to false on click', ->
      view.model.set('is_admin', true)
      view.$('.is-admin a').click()
      expect(user.get('is_admin')).to.eq(false)

    it 'should set updating class when updating', ->
      view.model.trigger('request')
      expect(view.$el).to.have.class('updating')

    it 'should remove updating class when done updating', ->
      view.$el.addClass('updating')
      view.model.trigger('sync')
      expect(view.$el).not.to.have.class('updating')

    it 'should add error class if updating fails', ->
      view.$el.addClass('updating')
      view.model.trigger('error')
      expect(view.$el).to.have.class('error')

    it 'should render confirmed-at title', ->
      expect(view.$('.confirmed-at').attr('title')).to.eq(new Date(1392670130000).toString())

    it 'should show a delete link', ->
      expect(view.$('a.delete').length).to.eq(1)

    it 'should not show a delete link for the current user', ->
      view.model.set(email: 'admin@example.org')
      expect(view.$('a.delete').length).to.eq(0)

    it 'should not delete when user does not confirm', ->
      @sandbox.stub(window, 'confirm', -> false)
      view.$('a.delete').click()
      expect(window.confirm).to.have.been.calledWith('confirm.delete,user@example.org')
      expect(view.model.has('deleting')).to.eq(false)

    it 'should delete when user confirms', ->
      @sandbox.stub(window, 'confirm', -> true)
      view.$('a.delete').click()
      expect(view.model.has('deleting')).to.eq(true)

    it 'should have a change-password link', ->
      expect(view.$('a.change-password').length).to.eq(1)

    describe 'after clicking change-password', ->
      $form = undefined
      beforeEach ->
        view.$('a.change-password').click()
        $form = view.$('form.change-password')

      it 'should show a change-password form', -> expect($form).to.be.visible
      it 'should focus the password field', ->
        $field = $form.find('input[name=password]')
        expect($field[0]).to.eq($field[0].ownerDocument.activeElement)
      it 'should hide the link', -> expect(view.$('a.change-password')).not.to.be.visible

      describe 'after entering a password', ->
        newPassword = 'n3Wp/\\ss!*'
        beforeEach -> $form.find('input[name=password]').val(newPassword)

        describe 'and clicking submit', ->
          beforeEach -> $form.find(':submit').click()

          it 'should change the model', -> expect(view.model.get('password')).to.eq(newPassword)
          it 'should hide the form', -> expect($form).not.to.be.visible
          it 'should show the link', -> expect(view.$('a.change-password')).to.be.visible
          it 'should reset the form', -> expect($form.find('input[name=password]').val()).to.eq('')

        describe 'and clicking reset', ->
          beforeEach -> $form.find(':reset').click()

          it 'should not change the model', -> expect(view.model.has('password')).to.be(false)
          it 'should hide the form', -> expect($form).not.to.be.visible
          it 'should show the link', -> expect(view.$('a.change-password')).to.be.visible
          it 'should reset the form', -> expect($form.find('input[name=password]').val()).to.eq('')
