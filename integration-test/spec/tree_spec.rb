#!/usr/bin/env ruby

require './spec/spec_helper'

# In order to let users find patterns in documents
# We provide a clustering feature called "Tree"
describe 'Tree' do
  before do
    @user = admin_session.create_test_user
    page.log_in_as(@user)
    page.create_document_set_from_csv('files/tree-spec.csv')
    recluster('view1')
  end

  after do
    admin_session.destroy_test_user(@user)
  end

  def recluster(name, tag_name: nil)
    page.click_link('Add view')
    page.click_link('Tree')

    # Wait for focus: that's when the dialog is open
    page.wait_for_javascript_to_return_true('document.querySelector("#tree-options-tree-title") === document.activeElement', wait: WAIT_FAST)
    page.within('form.tree-options') do
      page.fill_in('Name', with: name)
      if tag_name
        page.select(tag_name, from: 'Only include documents with tag')
      end
    end

    page.click_button('Create Tree')
    page.assert_selector('#tree-app-tree canvas', wait: WAIT_SLOW) # wait for tree creation
  end

  it 'should rename properly' do
    page.rename_view('view1', 'view3')
    page.assert_selector('a', text: 'view3', wait: WAIT_LOAD)
    page.find('a', text: 'view3').find('.toggle-popover').click
    page.assert_selector('dd.title', text: 'view3')

    # Persists even after page load
    page.refresh
    page.assert_selector('a', text: 'view3', wait: WAIT_LOAD)
  end

  it 'should show all the documents' do
    # TODO er ... test the tree? This doesn't actually test the tree
    page.search_for_q('document')
    page.assert_selector('#document-list-title h3', text: '4 documents')
    page.assert_selector('#document-list h3', text: 'Fourth')
  end

  describe 'when reclustering just a tag' do
    before do
      recluster('view2', tag_name: 'foo')
    end

    it 'should search outside the tag when the tree has not been clicked' do
      page.search_for_q('document')
      page.assert_selector('#document-list-title h3', text: '4 documents')
    end
  end
end
