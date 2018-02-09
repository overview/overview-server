#!/usr/bin/env ruby

require './spec/spec_helper'

# In order to let users store their own information
# We need to let users identify themselves
describe 'Login' do
  before do
    @user = admin_session.create_test_user
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

  def try_log_in(email, password)
    page.visit('/')
    page.within('.session-form') do
      page.fill_in('email', with: email)
      page.fill_in('password', with: password)
      page.click_button('Log in')
    end
  end

  def log_out
    page.click_link('Log out')
  end

  it 'should log in' do
    try_log_in(@user[:email], @user[:password])
    assert_logged_in_as(@user[:email])
  end

  it 'should log out' do
    try_log_in(@user[:email], @user[:password])
    log_out
    assert_logged_out
  end

  it 'should not log in with wrong password' do
    try_log_in(@user[:email], @user[:password] + 'x')
    assert_log_in_failed
  end

  it 'should not log in with wrong username' do
    try_log_in('x' + @user[:email], @user[:password])
    assert_log_in_failed
  end
end
