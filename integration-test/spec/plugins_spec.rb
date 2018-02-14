#!/usr/bin/env ruby

require './spec/spec_helper'

describe 'Plugins' do
  def extra_session_helpers; [ SessionHelpers::PdfNotesHelper ]; end

  before do
    @user = admin_session.create_test_user
    page.log_in_as(@user)
  end

  after do
    admin_session.destroy_test_user(@user)
  end

  def click_view_button(text)
    page.within_frame('view-app-iframe') do
      page.assert_selector('body.loaded', wait: WAIT_LOAD) # wait for iframe to load
      page.click_button(text)
    end
  end

  describe 'with a CSV' do
    before do
      page.create_document_set_from_csv('files/plugins-spec/basic.csv')
    end

    it 'should pass server, origin, apiToken and documentSetId in the plugin query string' do
      page.with_mock_plugin_and_view('show-query-string', server: 'https://server') do
        page.within_frame('view-app-iframe') do
          page.assert_selector('pre', text: /./, wait: WAIT_LOAD) # wait for <pre> to be non-empty
          text = page.find('pre').text
          params = text
            .split(/[?&]/).reject(&:empty?)                      # Array of key=value (URI-encoded)
            .map{ |s| s.split(/=/).map{ |v| CGI::unescape(v) } } # Array of [ key, value ] (unencoded)
            .to_h                                                # Hash of { "key" => "value" }
          assert_equal('https://server', params['server'])
          assert_match(/\A\d+\Z/, params['documentSetId'])
          assert_equal(OVERVIEW_URL, params['origin'])
          assert_match(/\A[a-z0-9A-Z]+\Z/, params['apiToken'])
        end
      end
    end

    describe 'with a plugin that calls setRightPane' do
      it 'should create a right pane' do
        page.with_mock_plugin_and_view('right-pane') do
          page.assert_no_selector('#tree-app-vertical-split-2') # vertical-split-2 is invisible by default
          click_view_button('Set Right Pane')

          page.assert_selector('#tree-app-vertical-split-2', wait: WAIT_FAST) # wait for divider to appear
          page.waiting_for_css_transitions_on_selector('#tree-app-right-pane') do
            page.find('#tree-app-vertical-split-2 button').click
          end
          page.assert_selector('#view-app-right-pane-iframe') # assert frame exists
          src = page.find('#view-app-right-pane-iframe')[:src]
          assert_match(/\?placement=right-pane/, src)
        end
      end

      it 'should delete the right pane when deleting the view' do
        page.with_mock_plugin_and_view('right-pane') do
          click_view_button('Set Right Pane')

          page.assert_selector('#tree-app-vertical-split-2', wait: WAIT_FAST) # wait for divider to appear
          page.delete_current_view

          page.assert_no_selector('#tree-app-vertical-split-2') # divider should be gone immediately
          page.assert_no_selector('#view-app-right-pane-iframe') # iframe should be gone immediately
        end
      end
    end

    it 'should create and close a modal dialog with setModalDialog' do
      page.with_mock_plugin_and_view('modal-dialog') do
        click_view_button('Set Modal Dialog')

        page.assert_selector('#view-app-modal-dialog', wait: WAIT_FAST) # wait for modal to appear

        page.within_frame('view-app-modal-dialog-iframe') do
          page.assert_selector('body.loaded', wait: WAIT_LOAD) # wait for page to load
          page.click_button('Set Modal Dialog to Null')
        end

        page.assert_no_selector('#view-app-modal-dialog', wait: WAIT_FAST) # wait for modal to disappear
      end
    end

    it 'should send messages from one plugin to another' do
      page.with_mock_plugin_and_view('modal-dialog') do
        click_view_button('Set Modal Dialog')

        page.assert_selector('#view-app-modal-dialog', wait: WAIT_FAST) # wait for modal to appear
        page.within_frame('view-app-modal-dialog-iframe') do
          page.assert_selector('body.loaded', wait: WAIT_LOAD) # wait for modal to load
          page.click_button('Send Message')
        end

        ## Close modal, so we can get back to the other frame
        ##(unneeded, for some reason: semitransparency, perhaps?)
        #page.find('#view-app-modal-dialog').click(x: 1, y: 1) # click outside dialog to close it
        #page.assert_no_selector('#view-app-modal-dialog')

        page.within_frame('view-app-iframe') do
          page.assert_selector('pre', text: '{"This is":"a message"}', wait: WAIT_FAST) # wait for message+JS
        end
      end
    end

    describe 'with a plugin that calls setViewFilter' do
      before do
        @last_request = nil
        @server = MockPlugin.create_server('view-filter', 3333)
        @server.config[:BindAddress] = '0.0.0.0'
        @server.mount_proc '/filter/01010101' do |req, res|
          @last_request = req
          res.status = 200
          res['Content-Type'] = 'application/octet-stream'
          res['Cache-Control'] = 'no-cache'
          res.body = [ '01010101' ].pack('B*')
        end

        @thread = MockPlugin.start_server_in_thread(@server)

        page.create_custom_view({ name: 'mock plugin', url: "http://#{Socket.gethostname}:3333" })
      end

      after do
        MockPlugin.stop_server_and_join_thread(@server, @thread)
      end

      it 'should allow filtering by view' do
        page.click_link('view-filter placeholder', wait: WAIT_LOAD) # wait for plugin to load and setViewFilter
        page.find('.document-filter-popup .name', text: 'VF-Foo').click
        page.assert_selector('#document-list-title h3', text: 'one document', wait: WAIT_LOAD) # wait for search
        page.assert_no_selector('#document-list h3', text: 'First')
        page.assert_selector('#document-list h3', text: 'Second')
        page.assert_no_selector('#document-list h3', text: 'Third')

        assert_equal('/filter/01010101', @last_request.path)
        assert_match(/\A[a-z0-9]+\Z/, @last_request.query['apiToken'])
        assert_equal('foo', @last_request.query['ids'])
        assert_equal('any', @last_request.query['operation'])
      end

      it 'should allow setViewFilterChoices' do
        click_view_button('setViewFilterChoices')
        sleep(0.1) # make sure postMessage() goes through. TODO better notification mechanism?

        page.click_link('view-filter placeholder')
        page.assert_selector('.document-filter-popup .name', text: 'VF-Foo2')
      end

      it 'should remove the ViewFilter when deleting the view' do
        page.delete_current_view
        page.assert_no_selector('a', text: 'view-filter placeholder', wait: WAIT_LOAD) # wait for view to disappear
      end

      it 'should allow setViewFilterSelection' do
        click_view_button('setViewFilterSelection')

        page.assert_selector('#document-list-title h3', text: 'one document', wait: WAIT_LOAD) # wait for search
        assert_equal('/filter/01010101', @last_request.path) # the search happened
        page.assert_selector('a', text: 'selectedOneHtml,VF-Foo') # the tag was selected
      end
    end

    describe 'with a plugin that calls setDocumentDetailLink' do
      before do
        page.open_document_in_list_with_name('First')
      end

      it 'should add a link' do
        page.with_mock_plugin_and_view('view-document-detail-links') do
          click_view_button('setUrl(foo)')
          page.assert_selector('a', text: 'Text foo', wait: WAIT_FAST) # wait for link to appear
        end
      end

      it 'should show the link even after page refresh' do
        page.with_mock_plugin_and_view('view-document-detail-links') do
          click_view_button('setUrl(foo)')

          # Reload page and navigate back to a document
          page.refresh
          page.assert_selector('#document-list-title h3', text: '3 documents') # wait for page reload
          page.open_document_in_list_with_name('First')

          page.assert_selector('a', text: 'Text foo')
        end
      end

      it 'should not create a duplicate link (to the same URL)' do
        page.with_mock_plugin_and_view('view-document-detail-links') do
          click_view_button('setUrl(foo)')

          # Create another view to the same thing, and click its button
          page.create_custom_view(name: 'another view', url: 'http://localhost:3333')
          click_view_button('setUrl(foo, foo with different text)')

          page.assert_no_selector('.transaction-queue-communicating', wait: WAIT_LOAD) # wait for AJAX

          # Since the URL is the same, we expect the second link to never appear
          page.assert_no_selector('a', text: 'foo with different text')
        end
      end

      it 'should open the popup' do
        page.with_mock_plugin_and_view('view-document-detail-links') do
          click_view_button('setUrl(foo)')
          page.click_link('Text foo', wait: WAIT_LOAD) # wait for link to appear

          page.assert_selector('iframe#view-document-detail', wait: WAIT_FAST) # wait for click to create iframe
          src = page.find('iframe#view-document-detail')[:src]
          assert_match(/\?documentId=\d+/, src)
          assert_match(/&foo=foo/, src)
        end
      end

      it 'should remove the link when deleting the view' do
        page.with_mock_plugin_and_view('view-document-detail-links') do
          click_view_button('setUrl(foo)')
          page.assert_selector('a', text: 'Text foo') # wait for link to appear, so we can check it disappears later
          page.delete_current_view
          page.assert_no_selector('a', text: 'Text foo')
        end
      end
    end

    it 'should change the View title with setViewTitle' do
      page.with_mock_plugin_and_view('set-view-title') do
        click_view_button('Set title to new-title')
        page.assert_selector('.view.active .title', text: 'new-title', wait: WAIT_LOAD) # wait for title to be saved
        page.assert_no_selector('.transaction-queue-communicating', wait: WAIT_LOAD) # wait for AJAX to finish

        # Refresh the page and verify the title is still there
        page.refresh
        page.assert_selector('.view.active .title', text: 'new-title', wait: WAIT_LOAD) # wait for page load
      end
    end
  end

  describe 'with a plugin that interacts with PdfNotes' do
    before do
      page.create_document_set_from_pdfs_in_folder('files/pdf-notes-spec')
      page.open_document_in_list_with_name('doc1.pdf')
      page.wait_for_pdf_load(/first.*PDF/)
    end

    it 'should begin PdfNote creation' do
      page.with_mock_plugin_and_view('pdf-notes') do
        click_view_button('Create PDF Note')
        page.within_frame('document-contents') do
          page.assert_selector('button.addNote.toggled', wait: WAIT_FAST) # wait for postMessage
        end
      end
    end

    it 'should send document.pdfNotes to the plugin' do
      page.with_mock_plugin_and_view('pdf-notes') do
        page.create_pdf_note('first', 'FOO')
        page.within_frame('view-app-iframe') do
          page.assert_selector('pre', text: 'FOO', wait: WAIT_FAST) # wait for postMessage
        end
      end
    end

    it 'should allow goToPdfNote' do
      page.with_mock_plugin_and_view('pdf-notes') do
        page.create_pdf_note('first', 'FOO') # will send note to plugin

        page.within_frame('view-app-iframe') do
          page.assert_selector('pre', text: /FOO/, wait: WAIT_FAST) # wait for postMessage
        end

        click_view_button('Go To Last PDF Note')

        page.within_frame('document-contents') do
          page.assert_selector('.editNoteTool', wait: WAIT_FAST) # wait for postMessage
        end
      end
    end
  end
end
