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
      view.remove()

    it 'should display the email', ->
      expect(view.$('.email').text()).toEqual('user@example.org')

    it 'should display is-admin with a promote link', ->
      expect(view.$('.is-admin').text()).toContain('no promote')

    it 'should change is-admin to true on click', ->
      view.$('.is-admin a').click()
      expect(user.get('is_admin')).toEqual(true)

    it 'should display is-admin with a demote link', ->
      view.model.set('is_admin', true)
      expect(view.$('.is-admin').text()).toContain('yes demote')

    it 'should not display demote link for current admin', ->
      view.model.set(email: 'admin@example.org', is_admin: true)
      expect(view.$('.is-admin a').length).toEqual(0)

    it 'should change is-admin to false on click', ->
      view.model.set('is_admin', true)
      view.$('.is-admin a').click()
      expect(user.get('is_admin')).toEqual(false)

    it 'should set updating class when updating', ->
      view.model.trigger('request')
      expect(view.$el).toHaveClass('updating')

    it 'should remove updating class when done updating', ->
      view.$el.addClass('updating')
      view.model.trigger('sync')
      expect(view.$el).not.toHaveClass('updating')

    it 'should add error class if updating fails', ->
      view.$el.addClass('updating')
      view.model.trigger('error')
      expect(view.$el).toHaveClass('error')

    it 'should render confirmed-at title', ->
      expect(view.$('.confirmed-at').attr('title')).toEqual(new Date(1392670130000).toString())

    it 'should show a delete link', ->
      expect(view.$('a.delete').length).toEqual(1)

    it 'should not show a delete link for the current user', ->
      view.model.set(email: 'admin@example.org')
      expect(view.$('a.delete').length).toEqual(0)

    it 'should not delete when user does not confirm', ->
      spyOn(window, 'confirm').and.returnValue(false)
      view.$('a.delete').click()
      expect(window.confirm).toHaveBeenCalledWith('confirm.delete,user@example.org')
      expect(view.model.has('deleting')).toEqual(false)

    it 'should delete when user confirms', ->
      spyOn(window, 'confirm').and.returnValue(true)
      view.$('a.delete').click()
      expect(view.model.has('deleting')).toEqual(true)

    it 'should have a change-password link', ->
      expect(view.$('a.change-password').length).toEqual(1)

    describe 'after clicking change-password', ->
      $form = undefined
      beforeEach ->
        view.$('a.change-password').click()
        $form = view.$('form.change-password')

      it 'should show a change-password form', -> expect($form).toBeVisible()
      it 'should focus the password field', -> expect($form.find('input[name=password]')).toBeFocused()
      it 'should hide the link', -> expect(view.$('a.change-password')).not.toBeVisible()

      describe 'after entering a password', ->
        newPassword = 'n3Wp/\\ss!*'
        beforeEach -> $form.find('input[name=password]').val(newPassword)

        describe 'and clicking submit', ->
          beforeEach -> $form.find(':submit').click()

          it 'should change the model', -> expect(view.model.get('password')).toEqual(newPassword)
          it 'should hide the form', -> expect($form).not.toBeVisible()
          it 'should show the link', -> expect(view.$('a.change-password')).toBeVisible()
          it 'should reset the form', -> expect($form.find('input[name=password]').val()).toEqual('')

        describe 'and clicking reset', ->
          beforeEach -> $form.find(':reset').click()

          it 'should not change the model', -> expect(view.model.has('password')).toBe(false)
          it 'should hide the form', -> expect($form).not.toBeVisible()
          it 'should show the link', -> expect(view.$('a.change-password')).toBeVisible()
          it 'should reset the form', -> expect($form.find('input[name=password]').val()).toEqual('')
