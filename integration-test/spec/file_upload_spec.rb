#!/usr/bin/env ruby

require './spec/spec_helper'

describe 'FileUpload' do
  before do
    @user = admin_session.create_test_user
    page.log_in_as(@user)
  end

  after do
    admin_session.destroy_test_user(@user)
  end

  describe 'after uploading a file with metadata' do
    before do
      page.creating_document_set do
        page.click_on('Upload files', wait: WAIT_LOAD)
        page.assert_selector('.upload-folder-prompt', wait: WAIT_LOAD)
        page.execute_script('document.querySelector(".upload-prompt .invisible-file-input").style.opacity = 1')
        page.attach_file('file', '/app/files/file-upload-spec/Cat0.pdf')
        page.click_on('Done adding files')
        # Wait for focus: that's when the dialog is open
        page.wait_for_javascript_to_return_true('document.querySelector("#import-options-name") === document.activeElement', wait: WAIT_FAST)
        page.fill_in('Document set name', with: 'spec')

        page.click_link('Add new field…')
        page.fill_in('New field name', with: 'foo', wait: WAIT_FAST) # wait for field to appear
        page.click_button('Add')
        page.fill_in('foo', with: 'bar', wait: WAIT_FAST) # wait for field to appear

        page.click_button('Import documents')
      end
    end

    it 'should add metadata to the imported file' do
      page.open_document_in_list_with_name('Cat0.pdf')
      page.waiting_for_css_transitions_on_selector('.document-metadata') do
        page.click_link('Fields')
      end
      assert page.has_field?('foo', with: 'bar', wait: WAIT_FAST)
    end

    it 'should add more metadata in a second import to the same document set' do
      # (this also tests that we can add files to an existing document set)

      # 1. Click "Add Documents"
      page.within('nav') do
        page.click_link('spec')
        page.click_link('Add Documents')
      end

      # 2. Add documents and get to the "Import Options" dialog
      page.assert_selector('.upload-folder-prompt', wait: WAIT_LOAD) # wait for page to load
      page.execute_script('document.querySelector(".upload-prompt .invisible-file-input").style.opacity = 1')
      page.attach_file('file', '/app/files/file-upload-spec/Cat1.docx')
      page.click_on('Done adding files', wait: WAIT_FAST) # wait for field to be enabled
      page.assert_selector('.import-options label', text: 'foo', wait: WAIT_FAST) # Wait for dialog (and field) to appear

      # 3. Set new metadata
      page.click_link('Add new field…')
      page.fill_in('New field name', with: 'moo', wait: WAIT_FAST) # wait for field to appear
      page.click_button('Add')
      page.fill_in('moo', with: 'mar', wait: WAIT_FAST) # wait for field to appear
      page.fill_in('foo', with: 'baz') # fill in the _first_ metadata field with a new value

      # 4. Submit
      page.click_button('Import documents')
      page.finish_creating_document_set(hide_tour: false) # the tour is already hidden

      # 5. Check metadata of original doc (it should not be changed)
      page.open_document_in_list_with_name('Cat0.pdf')
      page.waiting_for_css_transitions_on_selector('.document-metadata') do
        page.click_link('Fields')
      end
      assert page.has_field?('foo', with: 'bar', wait: WAIT_FAST)
      assert page.has_field?('moo', with: '')

      # 6. Check metadata of new doc (it should be what was just entered)
      page.within('#document-current') do
        page.click_link('Next')
      end
      assert page.has_field?('foo', with: 'baz', wait: WAIT_FAST)
      assert page.has_field?('moo', with: 'mar')
    end
  end

  describe 'when splitting a file into pages' do
    before do
      page.creating_document_set do
        page.click_on('Upload files', wait: WAIT_LOAD)
        page.assert_selector('.upload-folder-prompt', wait: WAIT_LOAD)
        page.execute_script('document.querySelector(".upload-prompt .invisible-file-input").style.opacity = 1')
        page.attach_file('file', '/app/files/file-upload-spec/Cat0.pdf')
        page.click_on('Done adding files')
        # Wait for focus: that's when the dialog is open
        page.wait_for_javascript_to_return_true('document.querySelector("#import-options-name") === document.activeElement', wait: WAIT_FAST)
        page.fill_in('Document set name', with: 'spec')
        page.choose('Each page is one document')
        page.choose('Assume documents are already text (faster import)')
        page.click_button('Import documents')
      end
    end

    it 'should create a file per page' do
      page.assert_selector('#document-list h3', text: 'Cat0.pdf – page 1')
      page.assert_selector('#document-list h3', text: 'Cat0.pdf – page 2')
      page.assert_selector('#document-list h3', text: 'Cat0.pdf – page 3')
      page.assert_selector('#document-list h3', text: 'Cat0.pdf – page 4')
    end

    it 'should search all pages' do
      page.search_for_q('face')
      page.assert_selector('#document-list-title h3', text: '4 documents')
    end
  end

  it 'should support all file types' do
    page.create_document_set_from_pdfs_in_folder('files/file-upload-spec')
    for title in %w(Cat0.pdf Cat1.docx Cat2.txt Cat3.rtf Cat4.html Cats.zip/Cat0.pdf Cats.zip/Cat1.docx Image.jpg Image.png Jules1.doc Jules2.pptx Jules3.xlsx)
      page.assert_selector('#document-list h3', text: title)
    end
  end
end
