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
      creating_document_set do
        visit('/')
        click_on('Upload files', wait: WAIT_LOAD)
        assert_selector('.upload-folder-prompt', wait: WAIT_LOAD)
        execute_script('document.querySelector(".upload-prompt .invisible-file-input").style.opacity = 1')
        for path in Dir.glob(File.join(folder, '*.*'))
          attach_file('file', path)
        end
        click_on('Done adding files')
        # Wait for focus: that's when the dialog is open
        wait_for_javascript_to_return_true('document.querySelector("#import-options-name") === document.activeElement', wait: WAIT_FAST)
        fill_in('Document set name', with: folder)
        click_on('Import documents')
      end
    end
  end
end
