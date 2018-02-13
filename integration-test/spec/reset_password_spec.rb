#!/usr/bin/env ruby

require './spec/spec_helper'

# In order to let users store their own information
# We need to let users identify themselves
# Even after they forget their passwords
describe 'Login' do
  before do
    @user = admin_session.create_test_user
    page.visit('/login')
    start_reset_password(@user[:email])
  end

  after do
    admin_session.destroy_test_user(@user)
  end

  def assert_logged_in_as(email)
    page.assert_selector('div.logged-in', text: email, wait: WAIT_LOAD)
  end

  def assert_log_in_failed
    page.assert_selector('h1', text: /Log in/i, wait: WAIT_LOAD)
    page.assert_selector('.error', text: 'Wrong email address or password')
  end

  def assert_logged_out
    page.assert_selector('.session-form', wait: WAIT_LOAD)
  end

  def start_reset_password(email)
    page.click_link('Reset it', wait: WAIT_LOAD) # wait for link to appear
    page.fill_in('Email', with: email, wait: WAIT_LOAD) # wait for page to load
    page.click_button('Email instructions to this address')
  end

  def assert_shows_reset_password_form
    assert page.has_field?('password', wait: WAIT_LOAD) # wait for page to load
    assert page.has_field?('password2')
  end

  def fill_passwords_and_click_submit(password1, password2)
    page.fill_in('password', with: password1, wait: WAIT_LOAD) # wait for page to load
    page.fill_in('password2', with: password2)
    page.click_button('Set new password and log in')
  end

  def try_log_in(email, password)
    page.visit('/login')
    page.within('.session-form') do
      page.fill_in('email', with: email)
      page.fill_in('password', with: password)
      page.click_button('Log in')
    end
  end

  def log_out
    page.click_link('Log out')
  end

  def get_reset_token(user)
    admin_session.get_user_token(user, 'reset-password')
  end

  it 'should alert the user to check his or her email' do
    page.assert_selector('.alert-success', text: 'sent', wait: WAIT_LOAD) # wait for page to load
  end

  it 'should alert even on second attempt' do
    page.visit('/login')
    start_reset_password(@user[:email])
    page.assert_selector('.alert-success', text: 'sent', wait: WAIT_LOAD) # wait for page to load
  end

  describe 'when the user clicks the token from the email' do
    before do
      page.visit("/reset-password/#{get_reset_token(@user)}")
    end

    it 'should not click through if the passwords are not set' do
      fill_passwords_and_click_submit('', '')
      assert_shows_reset_password_form
    end

    it 'should not click through if passwords do not match' do
      fill_passwords_and_click_submit(@user[:password] + 'FOO', @user[:password] + 'BAR')
      assert_shows_reset_password_form
      page.assert_selector('p.help-block', text: 'doesn’t match')
    end

    it 'should not click through if password is too short' do
      fill_passwords_and_click_submit('short', 'short')
      assert_shows_reset_password_form
      page.assert_selector('p.help-block', text: 'characters long')
    end

    it 'should reset the password' do
      new_password = 'gabty3kAg/'
      fill_passwords_and_click_submit(new_password, new_password)
      page.assert_selector('.alert-success', text: 'You have updated your password', wait: WAIT_LOAD) # wait for page to load
      assert_logged_in_as(@user[:email])

      # Old password doesn't work
      log_out
      try_log_in(@user[:email], @user[:password])
      assert_log_in_failed

      # New password works
      try_log_in(@user[:email], new_password)
      assert_logged_in_as(@user[:email])
    end
  end
end
