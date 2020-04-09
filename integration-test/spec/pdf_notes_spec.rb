#!/usr/bin/env ruby

require './spec/spec_helper'

# In order to let users analyze document text
# We need to let them add highlights and notes to documents
describe 'PdfNotes' do
  def extra_session_helpers
    [
      SessionHelpers::PdfNotesHelper
    ]
  end

  before do
    @user = admin_session.create_test_user
    page.log_in_as(@user)
    page.create_document_set_from_pdfs_in_folder('files/pdf-notes-spec')
    page.open_document_in_list_with_name('doc1.pdf')
  end

  after do
    admin_session.destroy_test_user(@user)
  end

  it 'it should create a pdfNote' do
    page.create_pdf_note('This is the first PDF', 'If this method does not crash, the test succeeded')
  end

  it 'it should save and load pdfNotes on the server' do
    page.create_pdf_note('This is the first PDF', 'This note was saved')
    page.refresh
    page.assert_selector('#document-list', wait: WAIT_LOAD) # Wait for page to load
    page.open_document_in_list_with_name('doc1.pdf')

    # wait for PDF viewer to begin loading
    page.assert_selector('iframe#document-contents[src="/pdf-viewer"]', wait: WAIT_FAST)
    page.within_frame('document-contents') do
      # Wait for notes feature to load
      page.assert_selector('#viewer .noteLayer', visible: :all, wait: WAIT_LOAD)
      # Wait for document to load
      page.assert_selector('#viewer .textLayer span', text: 'This is the first PDF', wait: WAIT_LOAD)

      # Wait for note to appear
      page.assert_selector('#viewer .noteLayer section', wait: WAIT_LOAD)

      # Open note
      page.find('#viewer .noteLayer section').click
      # Test that note contents are correct
      page.within('.editNoteTool', wait: WAIT_FAST) do # Wait for popup to appear
        assert page.has_field?('note', with: 'This note was saved', wait: WAIT_FAST) # wait for note to fill in
      end
    end
  end

  it 'pdfNotes should be searchable' do
    page.create_pdf_note('This is the first PDF', 'This note was saved')
    page.go_back_to_document_list
    page.search_for_q('notes:"note was saved"')
    page.assert_selector('#document-list h3', text: 'doc1.pdf', wait: WAIT_FAST)
  end

  it 'should delete pdfNotes' do
    page.create_pdf_note('This is the first PDF', 'This note was saved')
    page.within_frame('document-contents') do
      page.find('#viewer .noteLayer section').click
      page.within('.editNoteTool', wait: WAIT_FAST) do # wait for dialog to appear
        page.click_button('Delete')
      end
      page.assert_no_selector('.editNoteTool', wait: WAIT_FAST) # wait for note tool to disappear
    end
    page.assert_no_selector('.transaction-queue-communicating', wait: WAIT_LOAD) # wait for AJAX to complete

    # Refresh to prove the note is gone
    page.refresh
    page.assert_selector('#document-list', wait: WAIT_LOAD) # Wait for page to load
    page.open_document_in_list_with_name('doc1.pdf')

    # wait for PDF viewer to begin loading
    page.assert_selector('iframe#document-contents[src="/pdf-viewer"]', wait: WAIT_FAST)
    page.within_frame('document-contents') do
      # Wait for notes feature to load
      page.assert_selector('#viewer .noteLayer', visible: :all, wait: WAIT_LOAD)
      # Wait for document to load
      page.assert_selector('#viewer .textLayer span', text: 'This is the first PDF', wait: WAIT_LOAD)

      # Make sure no note is rendered
      page.assert_no_selector('#viewer .noteLayer section')
    end
  end
end
