#!/usr/bin/env ruby

require './spec/spec_helper'

describe 'OCR' do
  before do
    @user = admin_session.create_test_user
    page.log_in_as(@user)
    page.click_link('Upload files')

    # Wait for page load, and make invisible file inputs visible.
    #
    # Returns true when:
    # * jQuery is loaded
    # * there is an invisible file input
    wait_js = <<-EOT
      window.jQuery && jQuery.isReady && jQuery('.invisible-file-input').css({ opacity: 1 }).length > 0
    EOT
    page.wait_for_javascript_to_return_true(wait_js, wait: WAIT_LOAD)

    page.attach_file('file', '/app/files/ocr-spec/image.pdf')
    page.click_on('Done adding files')
    # Wait for focus: that's when the dialog is open
    page.wait_for_javascript_to_return_true('document.querySelector("#import-options-name") === document.activeElement', wait: WAIT_FAST)
    page.fill_in('Document set name', with: 'spec')
  end

  after do
    admin_session.destroy_test_user(@user)
  end

  it 'should do OCR when the user selects' do
    page.creating_document_set do
      page.choose('Convert to text if needed (OCR)')
      page.click_button('Import documents')
    end

    page.assert_selector('#document-list h3', text: 'image.pdf', wait: WAIT_SLOW) # wait for import and page load
    page.open_document_in_list_with_name('image.pdf')

    # Check PDF has text
    page.assert_selector('#document-contents[src="/pdf-viewer"]', wait: WAIT_FAST) # wait for iframe to appear
    page.within_frame('document-contents') do
      page.assert_selector('.textLayer', text: /This.*is.*an.*image.*of.*text/, wait: WAIT_LOAD) # wait for pdf to load
    end

    # Check text has text
    page.within('.switch-text-mode') do
      page.find('a', text: 'Text').click
    end
    page.assert_selector('#document-current pre', text: 'This is an image of text', wait: WAIT_LOAD) # wait for text to load
  end

  it 'should not OCR when the user opts out' do
    page.creating_document_set do
      page.choose('Assume documents are already text (faster import)')
      page.click_button('Import documents')
    end

    page.assert_selector('#document-list h3', text: 'image.pdf', wait: WAIT_SLOW) # wait for import and page load
    page.open_document_in_list_with_name('image.pdf')

    # Check PDF has no text
    page.assert_selector('#document-contents[src="/pdf-viewer"]', wait: WAIT_FAST) # wait for iframe to appear
    page.within_frame('document-contents') do
      page.assert_selector('.textLayer .endOfContent', visible: false, wait: WAIT_LOAD) # wait for PDF to load
      page.assert_no_selector('.textLayer', text: /This.*is.*an.*image.*of.*text/)
    end

    # Check text has no text
    page.within('.switch-text-mode') do
      page.find('a', text: 'Text').click
    end
    page.assert_selector('#document-current pre', text: /\A\Z/, wait: WAIT_LOAD) # wait for text to load
  end
end
