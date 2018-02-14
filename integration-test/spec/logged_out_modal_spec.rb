#!/usr/bin/env ruby

require './spec/spec_helper'

# In order to help users access their data
# We need to prompt them to log in
# If they logged out in another tab
describe 'Logged-out modal dialog' do
  before do
    @user = admin_session.create_test_user
    page.log_in_as(@user)
    page.create_document_set_from_csv('files/speedy-import.csv')

  end

  after do
    admin_session.destroy_test_user(@user)
  end

  def make_unauthenticated_ajax_request
    # Log out by clearing all cookies
    page.driver.browser.manage.delete_all_cookies

    # Queue up an AJAX request that will fail
    page.click_link('speedy-import.csv')
    page.click_link('Edit Fields')
    page.within('.metadata-schema') do
      page.click_button('Add Field')
      page.fill_in('name', with: 'test')
      page.click_button('Add Field')
    end
  end

  it 'should show a modal when a logged-out user tries to perform an ajax action' do
    make_unauthenticated_ajax_request
    page.assert_selector('#logged-out-modal', wait: WAIT_LOAD) # wait for AJAX response
  end

  it 'should prompt you to log back in to the same URL' do
    expected_url = page.current_url

    make_unauthenticated_ajax_request
    page.click_button('Log back in', wait: WAIT_LOAD) # wait for AJAX response
    page.assert_selector('.session-form', wait: WAIT_LOAD) # wait for login page
    page.within('.session-form') do
      page.fill_in('Email', with: @user[:email])
      page.fill_in('Password', with: @user[:password])
      page.click_button('Log in')
    end

    assert_equal(expected_url, page.current_url)
  end
end
