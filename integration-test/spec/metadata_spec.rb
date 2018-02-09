#!/usr/bin/env ruby

require './spec/spec_helper'

describe 'Metadata' do
  before do
    @user = admin_session.create_test_user
    page.log_in_as(@user)
    page.create_document_set_from_csv('files/metadata-spec/basic.csv')
  end

  after do
    admin_session.destroy_test_user(@user)
  end

  describe 'documentsets/:id/show' do
    before do
      page.open_document_in_list_with_name('First')
      page.waiting_for_css_transitions_on_selector('.document-metadata') do
        page.click_link('Fields')
      end
    end

    it 'should show metadata' do
      assert page.has_field?('foo', with: 'foo0', wait: WAIT_FAST)
    end

    it 'should change metadata when browsing to the next document' do
      page.within('#document-current') do
        page.click_link('Next')
      end
      assert page.has_field?('foo', with: 'foo1', wait: WAIT_FAST)
    end

    it 'should modify metadata' do
      page.within('.document-metadata') do
        page.fill_in('foo', with: 'newFoo')
      end

      # Navigate away and back
      page.within('#document-current') do
        page.click_link('Next')
        page.click_link('Previous')
      end

      assert page.has_field?('foo', with: 'newFoo', wait: WAIT_FAST)
    end

    it 'should add/remove metadata fields' do
      # Add a field
      page.click_link('Organize fields…')
      page.click_button('Add Field', wait: WAIT_FAST)
      page.within('.metadata-schema tbody tr:last-child') do
        page.fill_in('name', with: 'baz')
      end
      page.click_button('Close')
      # Wait for everything to close
      page.assert_no_selector('.metadata-schema', wait: WAIT_FAST)

      # Set the field value
      page.fill_in('baz', with: 'a baz value')

      # Navigate away and back
      page.within('#document-current') do
        page.click_link('Next')
        page.click_link('Previous')
      end

      page.assert_no_selector('.transaction-queue-communicating', wait: WAIT_FAST)
      assert_equal('a baz value', page.find(:fillable_field, 'baz').value)

      # Remove the field
      page.click_link('Organize fields…')
      page.within('.metadata-schema tbody', wait: WAIT_FAST) do # wait for dialog to load
        page.accept_confirm(wait: WAIT_FAST) do # wait for modal to appear after click
          page.within('tr:last-child') do
            page.click_link('Delete')
          end
        end
      end
      page.click_button('Close')
      # Wait for everything to close
      page.assert_no_selector('.metadata-schema', wait: WAIT_FAST)

      # Navigate away and back
      page.within('#document-current') do
        page.click_link('Next')
        page.click_link('Previous')
      end

      page.assert_no_selector('.transaction-queue-communicating', wait: WAIT_FAST)
      # Assert the field is gone
      assert !page.has_field?('baz')
    end

    it 'should search by metadata' do
      page.search_for_q('foo:foo0 OR bar:bar1')

      page.assert_selector('#document-list li', text: 'First')
      page.assert_selector('#document-list li', text: 'Second')
      page.assert_no_selector('#document-list li', text: 'Third')
    end

    it 'should warn on invalid metadata column search' do
      page.search_for_q('foo2:x')

      page.within('#document-list') do
        page.assert_selector('ul.warnings', text: /There is no “foo2” field/)
        page.assert_selector('ul.validFieldNames li', text: 'text') # suggests a built-in field type
        page.assert_selector('ul.validFieldNames li', text: 'bar')  # suggests an existing metadata field
      end
    end
  end

  it 'should return metadata from the API' do
    api = page.create_api_browser
    document_set_id = page.current_path.split(/\//)[2]
    res = api.GET("/document-sets/#{document_set_id}/documents?fields=metadata")

    # Body items are sorted by title, and titles are alphabetical in basic.csv
    assert_equal([
      { 'foo' => 'foo0', 'bar' => 'bar0' },
      { 'foo' => 'foo1', 'bar' => 'bar1' },
      { 'foo' => 'foo2', 'bar' => 'bar2' },
    ], JSON.load(res.body)['items'].map{ |item| item['metadata'] })
  end
end
