require 'pathname'

module SessionHelpers
  module DocumentSetHelper
    def create_document_set_from_pdfs_in_folder(folder, options=nil)
      creating_document_set(options) do
        folder = Pathname.new(folder).expand_path # absolute path

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
        choose('Assume documents are already text (faster import)')
        click_on('Import documents')
      end
    end

    def create_document_set_from_csv(path, options=nil)
      creating_document_set(options) do
        path = Pathname.new(path).expand_path # absolute path

        visit('/')
        click_on('Import from a CSV file', wait: WAIT_LOAD)
        attach_file('csv-upload-file', path, wait: WAIT_LOAD)
        click_button('Upload', match: :first, wait: WAIT_LOAD) # wait for upload to complete and JS to run
      end
    end

    def with_mock_plugin_and_view(slug, view_options={}, &block)
      mock_plugin = MockPlugin.new(slug)
      mock_plugin.with_server_on_port(3333) do
        create_custom_view({
          name: 'mock plugin',
          url: 'http://localhost:3333'
        }.merge(view_options))
        block.call
        # We don't delete the view after the block is done. There aren't many
        # tests that would need that. But tests shouldn't rely on this behavior.
      end
    end

    def create_custom_view(options)
      raise ArgumentError.new('missing options[:name]') if !options[:name]
      raise ArgumentError.new('missing options[:url]') if !options[:url]
      click_on('Add view', wait: WAIT_FAST)
      click_on('Custom…', wait: WAIT_FAST)
      # Wait for focus: that's when the dialog is open
      wait_for_javascript_to_return_true('document.querySelector("#new-view-dialog-title") === document.activeElement', wait: WAIT_FAST)
      fill_in('Name', with: options[:name])
      fill_in('App URL', with: options[:url])
      fill_in('Overview’s URL from App server', with: options[:server] || OVERVIEW_URL)
      if options[:url] =~ /^http:/
        # dismiss HTTPS warning
        click_on('Create visualization')
        click_on('use it anyway', wait: WAIT_FAST)
      end
      click_on('Create visualization')

      # Wait for dialog to disappear.
      assert_no_selector('#new-view-dialog', wait: WAIT_LOAD)

      # Wait for new view to appear
      assert_selector('li.view span.title', text: options[:name], wait: WAIT_FAST)
      # And wait for it to begin loading. Without specific plugin knowledge, we
      # can't tell when it _ends_ loading.
      assert_selector("iframe#view-app-iframe[src^=\"#{options[:url]}\"]", wait: WAIT_FAST)
    end

    def rename_view(old_name, new_name)
      within('#tree-app-views') do
        find('a', text: old_name).find('.toggle-popover').click
        within('.popover') do
          click_link('rename')
          fill_in('New Title', with: new_name)
          click_button('Save')
        end
      end

      assert_selector('#tree-app-views a', text: new_name, wait: WAIT_LOAD)
      within('.popover') do
        click_link('Close')
      end
      assert_no_selector('.popover')
    end

    # Assumes you are browsed to a document set and have not changed Views.
    #
    # Creates an API token, and leaves the session on the API-tokens page.
    def create_api_browser
      visit(current_url + '/api-tokens')
      fill_in('App name', with: 'integration-test')
      click_button('Generate token')
      api_token = find('tr', text: 'integration-test', wait: WAIT_LOAD).find('td.token span').text
      ApiBrowser.new(base_url: OVERVIEW_URL, api_token: api_token)
    end

    def delete_current_view
      n_views_before = all('ul.view-tabs>li.view').count
      find('li.view.active .toggle-popover').click
      within('li.view.active .popover', wait: WAIT_FAST) do
        accept_confirm(wait: WAIT_FAST) do
          _HACK_hide_view_iframe_to_workaround_chromium_79_disabling_clicks_over_iframes
          click_on('Delete View')
        end
      end
      # Wait for the view to disappear
      assert_selector('ul.view-tabs>li.view', count: n_views_before - 1, wait: WAIT_LOAD)
    end

    def open_document_in_list_with_name(name)
      waiting_for_css_transitions_on_selector('#document-current') do
        find('#document-list h3', text: name, wait: WAIT_LOAD).click # wait for doclist to load
      end
    end

    def go_back_to_document_list
      waiting_for_css_transitions_on_selector('#document-current') do
        within('#document-current') do
          click_link('Back to list')
        end
      end
    end

    def search_for_q(q)
      within('#document-list-params') do
        fill_in('query', with: q)
        click_button('Search')
      end

      # Wait for new document list to load
      assert_no_selector('#document-list.loading', wait: WAIT_LOAD)
    end

    def creating_document_set(options=nil, &block)
      yield
      finish_creating_document_set(options)
    end

    # * wait for import to complete
    # * wait for document-set page to load
    # * hide the tour (unless options[:hide_tour] == false)
    def finish_creating_document_set(options=nil)
      assert_selector('body.document-set-show', wait: WAIT_SLOW) # wait for import to complete
      assert_selector('#document-list:not(.loading) li.document', wait: WAIT_LOAD) # wait for document list to load
      # There are no plugins, so we don't need to wait for them

      # Hide the Tour
      if options.nil? || options[:hide_tour] != false
        click_link('Don’t show any more tips', wait: WAIT_FAST)
        assert_no_selector('.popover', wait: WAIT_FAST)
      end
    end

    def _HACK_hide_view_iframe_to_workaround_chromium_79_disabling_clicks_over_iframes
      # TODO delete this function and all calls to it
      #
      # [2020-04-09] Chromium 79 seems to silently prevent us from clicking a
      # popover atop an iframe. When Chromium allows this, delete the next line.
      execute_script 'document.querySelector("#view-app-iframe").style.display = "none";'
    end
  end
end
