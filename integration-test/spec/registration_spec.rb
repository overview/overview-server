#!/usr/bin/env ruby

require './spec/spec_helper'

# In order to let users store their own information
# We need to let users identify themselves
# By creating accounts
describe 'Registration' do
  TEST_USER_EMAIL = 'test-RANDOM@example.org'
  TEST_USER_PASSWORD = 'created during test'

  before do
    # start the admin session first. That way, when we start the user session
    # in run-browser, we'll see the user session atop the admin session. (In
    # most tests this is implicit, but since we don't create a user with the
    # admin session in this one, we have to be explicit here.)
    admin_session # will open the admin section

    # @user is not logged in (unlike other tests)
    @user = {
      email: TEST_USER_EMAIL.sub(/RANDOM/, rand(9999999999).to_s),
      password: TEST_USER_PASSWORD
    }
    page.visit('/login')
  end

  def assert_logged_in_as(email)
    page.assert_selector('div.logged-in', text: email, wait: WAIT_LOAD)
  end

  def assert_log_in_failed
    page.assert_selector('h1', text: /Log in/i, wait: WAIT_LOAD)
    page.assert_selector('.error', text: 'Wrong email address or password')
  end

  def try_log_in(email, password)
    page.within('.session-form') do
      page.fill_in('Email', with: email)
      page.fill_in('Password', with: password)
      page.click_button('Log in')
    end
  end

  def try_register(email, password, password2)
    page.within('.user-form') do
      page.fill_in('Email', with: email)
      page.fill_in('Password', with: password)
      page.fill_in('Password (again)', with: password2)
      page.click_button('Register and send confirmation email')
    end
  end

  def log_out
    page.click_link('Log out')
  end

  def get_confirmation_token(user)
    admin_session.get_user_token(user, 'confirmation')
  end

  it 'should not register a bad email format' do
    try_register(@user[:email] + '@foo', @user[:password], @user[:password])
    page.assert_selector('.user-form [name=email]:invalid')
  end

  it 'should not register a weak password' do
    try_register(@user[:email] + '@foo', 'weak', 'weak')
    page.assert_selector('p.help-block', text: 'characters long')
  end

  it 'should not register mismatched passwords' do
    try_register(@user[:email], @user[:password], @user[:password] + '1')
    page.assert_selector('p.help-block', text: 'doesn’t match')
  end

  it 'should error on invalid token link' do
    page.visit('/confirm/invalidtoken')
    page.assert_selector('h1', text: /Broken confirmation link/i, wait: WAIT_LOAD) # wait for page to load
  end

  describe 'when the user already exists' do
    before do
      @user = admin_session.create_test_user
      try_register(@user[:email], @user[:password] + '1', @user[:password] + '1')
    end

    after do
      admin_session.refresh
      admin_session.assert_selector('tr:not(.updating) td.email', text: @user[:email], wait: WAIT_LOAD) # wait for users to load
      admin_session.destroy_test_user(@user)
    end

    it 'should not indicate to an attacker that the account already exists' do
      page.assert_selector('h1', text: /Check your email/i, wait: WAIT_LOAD)
    end

    it 'should not let the attacker set a new password' do
      page.visit('/login')
      try_log_in(@user[:email], @user[:password] + '1')
      page.assert_selector('.help-block', text: 'Wrong email address or password')
    end

    it 'should not let the attacker prevent the good user from logging in' do
      page.visit('/login')
      try_log_in(@user[:email], @user[:password])
      assert_logged_in_as(@user[:email])
    end

    it 'should not create a confirmation token' do
      assert_equal(nil, get_confirmation_token(@user))
    end
  end

  describe 'when creating a new user' do
    before do
      try_register(@user[:email], @user[:password], @user[:password])
    end

    after do
      admin_session.refresh
      admin_session.assert_selector('tr:not(.updating) td.email', text: @user[:email], wait: WAIT_LOAD) # wait for users to load
      admin_session.destroy_test_user(@user)
    end

    it 'should prompt the user to check his or her email' do
      page.assert_selector('h1', text: /Check your email/i, wait: WAIT_LOAD) # wait for page to load
    end

    it 'should set a confirmation token on the user' do
      page.assert_selector('h1', text: /Check your email/i, wait: WAIT_LOAD) # wait for HTTP request to complete
      assert_match(/\A\w+\Z/, get_confirmation_token(@user))
    end

    it 'should not let the user log in yet' do
      page.visit('/login')
      try_log_in(@user[:email], @user[:password])
      page.assert_selector('h1', text: /Log in/i, wait: WAIT_LOAD)
      page.assert_selector('.error', text: 'click the link in the confirmation email')
    end

    describe 'after confirming' do
      before do
        page.assert_selector('h1', text: /Check your email/i, wait: WAIT_LOAD) # wait for HTTP request to complete
        page.visit("/confirm/#{get_confirmation_token(@user)}")
      end

      it 'should log you in and tell you all is well' do
        assert_logged_in_as(@user[:email])
        page.assert_selector('.alert-success', text: 'Welcome')
      end

      it 'should disable the confirmation token' do
        assert_equal(nil, get_confirmation_token(@user))
      end
    end
  end
end
