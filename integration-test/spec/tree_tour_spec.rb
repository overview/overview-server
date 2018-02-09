#!/usr/bin/env ruby

require './spec/spec_helper'

describe 'TreeTour' do
  before do
    @user = admin_session.create_test_user
    page.log_in_as(@user)
    page.create_document_set_from_csv('files/speedy-import.csv', hide_tour: false)
  end

  after do
    admin_session.destroy_test_user(@user)
  end

  it 'should show a tooltip on first load' do
    page.assert_selector('.popover', text: 'Document list', wait: WAIT_FAST)
  end

  it 'should flip through tooltips' do
    page.within('.popover') do
      page.assert_selector('.popover-title', text: 'Document list', wait: WAIT_FAST)
      page.click_link('Next')
      page.assert_selector('.popover-title', text: 'Tagging', wait: WAIT_FAST)
      page.click_link('Next')
      page.assert_selector('.popover-title', text: 'Select', wait: WAIT_FAST)
      page.click_link('Done')
    end

    page.assert_no_selector('.popover', wait: WAIT_FAST)
  end

  it 'should not show the tour after being dismissed' do
    page.within('.popover') do
      page.click_link('Don’t show any more tips', wait: WAIT_FAST)
    end
    page.assert_no_selector('.transaction-queue-communicating', wait: WAIT_LOAD) # Wait for AJAX
    page.assert_no_selector('.popover')

    page.refresh
    page.assert_selector('#document-list:not(.loading) li.document', wait: WAIT_LOAD) # wait for document list to load
    page.assert_no_selector('.popover')
  end
end
