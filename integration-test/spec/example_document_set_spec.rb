#!/usr/bin/env ruby

require './spec/spec_helper'

# In order to let users find patterns in documents
# We provide a clustering feature called "Tree"
describe 'Example document sets' do
  before do
    @admin_user = admin_session.create_test_user
    admin_session.promote_user(@admin_user)
    @user = admin_session.create_test_user

    @admin = new_session
    @admin.log_in_as(@admin_user)
    @admin.create_document_set_from_csv('files/speedy-import.csv')
    @admin.click_link('speedy-import.csv') # open nav menu
    @admin.click_link('Share')
    @admin.assert_selector('iframe[name=share-document-set]', wait: WAIT_FAST) # wait for dialog to start loading
    @admin.within_frame('share-document-set') do
      @admin.check('Set as example document set', wait: WAIT_FAST) # wait for dialog to load
    end
    # There's a race: checking the checkbox just sends of an AJAX request,
    # and there's no feedback when it completes. But that's okay in practice,
    # because we haven't yet created the `page` Capybara session. We assume
    # `page.log_in_as(@user)` will take far longer than the AJAX request.
  end

  after do
    admin_session.destroy_test_user(@admin_user)
    admin_session.destroy_test_user(@user)
  end

  it 'should let the user clone the document set' do
    page.log_in_as(@user)
    page.wait_for_javascript_to_return_true('window.jQuery && jQuery.isReady', wait: WAIT_LOAD) # wait for page load

    page.assert_selector('.document-set', text: 'speedy-import.csv')

    page.creating_document_set do
      page.within('.document-set', text: 'speedy-import.csv') do
        page.click_button('Clone')
      end
    end

    # Documents are all there
    page.assert_selector('#document-list-title h3', text: '5 documents')

    # Search works
    page.search_for_q('designed')
    page.assert_selector('#document-list-title h3', text: 'one document')
    page.assert_selector('#document-list h3', text: 'Third')
  end

  it 'should let the admin remove the example document set' do
    @admin.within_frame('share-document-set') do
      sleep(0.25) # avoid race: make sure "check" SQL is executed before "uncheck" SQL
      @admin.uncheck('Set as example document set', wait: WAIT_FAST)
      # ... and now there's our usual race, which we don't sleep for
    end

    page.log_in_as(@user)
    page.assert_no_selector('.document-set', text: 'speedy-import.csv')
  end

  it 'should leave the clone even after the original is deleted' do
    page.log_in_as(@user)
    page.wait_for_javascript_to_return_true('window.jQuery && jQuery.isReady', wait: WAIT_LOAD) # wait for page load

    # 1. Clone document set
    page.creating_document_set do
      page.within('.document-set', text: 'speedy-import.csv') do
        page.click_button('Clone')
      end
    end

    # 2. Delete original
    @admin.visit('/documentsets')
    @admin.wait_for_javascript_to_return_true('window.jQuery && jQuery.isReady', wait: WAIT_LOAD) # wait for page load
    @admin.find('.document-sets .dropdown-toggle').click
    @admin.accept_confirm(wait: WAIT_FAST) do
      @admin.click_link('Delete')
    end
    @admin.assert_no_selector('.document-sets', text: 'speedy-import.csv', wait: WAIT_LOAD) # wait for page reload

    # 3. Refresh page
    page.refresh
    page.assert_selector('h2', text: 'speedy-import.csv', wait: WAIT_LOAD) # wait for page load
  end
end
