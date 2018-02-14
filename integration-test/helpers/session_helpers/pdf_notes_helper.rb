module SessionHelpers
  module PdfNotesHelper
    def wait_for_pdf_load(text)
      within_frame('document-contents') do
        assert_selector('.textLayer', text: text, wait: WAIT_LOAD) # wait for text to load
      end
    end

    # Creates a PDF note over the PDF specified PDF text
    def create_pdf_note(text_to_highlight, note_text)
      # Wait for PDF iframe to become available
      assert_selector('iframe#document-contents[src="/pdf-viewer"]', wait: WAIT_FAST)

      within_frame('document-contents') do
        # Wait for notes feature to load
        assert_selector('#viewer .noteLayer', visible: :all, wait: WAIT_LOAD)
        # Wait for document to load
        assert_selector('#viewer .textLayer div', text: text_to_highlight, wait: WAIT_LOAD)

        click_button('Add Note')
        # Wait for the page to be ready for a click-and-drag
        assert_selector('#viewerContainer.addingNote', wait: WAIT_FAST)

        # Drag a box for the note
        div = find('#viewer .textLayer div', text: text_to_highlight).native
        driver.browser.action
          .click_and_hold(div)
          .move_by(50, 50)
          .release
          .perform

        assert_selector('.editNoteTool', wait: WAIT_FAST) # wait for tool to appear
        fill_in('Type your comments here', with: note_text)
        click_button('Save')
        click_button('Close')
        assert_no_selector('.editNoteTool', wait: WAIT_FAST) # wait for tool to disappear
      end

      assert_no_selector('.transaction-queue-communicating', wait: WAIT_LOAD) # wait for note to save
    end
  end
end
