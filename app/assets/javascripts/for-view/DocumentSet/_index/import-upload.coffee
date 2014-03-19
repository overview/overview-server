define [ 'jquery', 'underscore', 'util/csv_reader', 'util/net/upload', 'i18n', 'apps/ImportOptions/app', 'util/shims/file' ], ($, _, CsvReader, Upload, i18n, ImportOptionsApp) ->
  FILE_UPLOAD_XAP_URL = '/assets/silverlight/file-upload.xap'
  FILE_PREVIEW_SIZE = 204800 # 200kb
  TOLERATED_ENCODING_ERRORS = 0.01 # 1%: ratio of bad-chars : total-chars

  if !window.requestAnimationFrame
    window.requestAnimationFrame = (callback) ->
      cur_time = new Date().getTime()
      time_to_call = Math.max(0, 16 - (cur_time - last_time))
      id = window.setTimeout((-> callback(cur_time + time_to_call)), time_to_call)
      last_time = cur_time + time_to_call
      id

  show_hidden_characters = (s) ->
    # Copied from PegJS's parser's quote() function
    s.replace(/\x08/g, '\\b'
    ).replace(/\t/g, '\\t'
    ).replace(/\n/g, '\\n'
    ).replace(/\f/g, '\\f'
    ).replace(/\r/g, '\\r'
    ).replace(/[\x00-\x07\x0B\x0E-\x1F]/g, escape)

  make_csv_upload_form = ($form, $modal) ->
    importOptionsApp = new ImportOptionsApp
      onlyOptions: [ 'lang', 'important_words', 'supplied_stop_words' ]
      supportedLanguages: window.supportedLanguages
      defaultLanguageCode: window.defaultLanguageCode
    $form.find('.ok').prepend(importOptionsApp.el)
    given_url = $form.attr('action')
    url_prefix = given_url.split(/\//)[0..-2].join('/') + '/'

    file = undefined
    upload = undefined
    upload_options = {}
    csv_reader = undefined
    csv_reader_options = {}
    ready_to_submit = false

    select_file = (new_file) ->
      return if new_file is file

      file = new_file
      on_file_or_charset_changed()

    $form.find('select[name=charset]').on 'change', (e) ->
      on_file_or_charset_changed()

    on_file_or_charset_changed = () ->
      csv_reader = undefined
      refresh_charset()
      refresh_from_csv_reader()

      charset = $form.find('select[name=charset]').val()

      if file
        preview_blob = file.slice(0, Math.min(file.size, FILE_PREVIEW_SIZE))

        csv_reader = new CsvReader(csv_reader_options)
        csv_reader.onloadend = on_preview_loadend
        csv_reader.read(preview_blob, charset)

        ready_to_submit = false

      $form.find(':reset').attr('disabled', !file? && 'disabled' || false)

    refresh_from_csv_reader = () ->
      refresh_requirements()
      refresh_preview()
      refresh_form_enabled()

    on_preview_loadend = () ->
      # This stuff comes in async, but we only care about the last one. We know
      # csv_reader is the most recent one we created. So if it's ready, then this
      # is the call we care about.
      if csv_reader?.readyState == 2
        refresh_from_csv_reader()

    refresh_charset = () ->
      $form.find('p.charset').toggle(file?)

    refresh_requirements = () ->
      if csv_reader?
        throw 'CsvReader must be in readyState=2' if csv_reader.readyState != 2 # DONE

      $requirements = $form.find('.requirements')
      $ul = $requirements.children('ul')

      if csv_reader # done reading
        error = false
        error = check_requirement($ul.find('.text'), has_text, error)
        error = check_requirement($ul.find('.csv'), has_csv, error)
        error = check_requirement($ul.find('.header'), has_header, error)
        error = check_requirement($ul.find('.data'), has_data, error)

        $requirements.children('.error').toggle(error)
        $requirements.children('.ok').toggle(!error)

        ready_to_submit = !error

      else # cleared file
        $ul.children().removeClass('ok').removeClass('bad')
        $requirements.children('.error').hide()
        $requirements.children('.ok').hide()
        ready_to_submit = false

    check_requirement = ($elem, ok_function, error) ->
      $elem.removeClass('ok')
      $elem.removeClass('bad')
      if !error
        ok = ok_function()

        if ok
          $elem.addClass('ok')
        else
          $elem.addClass('bad')

        !ok
      else
        error

    has_text = () ->
      text = csv_reader.text
      return false if !text

      # Decoding (to Unicode) will never give a hard fail. Instead, each failed
      # character will be replaced by "U+FFFD REPLACEMENT CHARACTER". We count
      # how many characters failed, and if it's over a threshold, we fail.

      n_chars = text.length # > 0, otherwise !text would be true
      n_bad_chars = 0
      for c in text
        n_bad_chars += 1 if c == '\ufffd' # replacement character, means decoding failed

      n_bad_chars / n_chars <= TOLERATED_ENCODING_ERRORS

    has_csv = () ->
      #return false if !has_text()
      !csv_reader.error

    minimum_row_length = () ->
      header = csv_reader.result.header.map($.trim)
      lower_header = header.map((s) -> s.toLowerCase())

      text_index = lower_header.indexOf('text')
      contents_index = lower_header.indexOf('contents')
      snippet_index = lower_header.indexOf('snippet')
      alt_text_index = Math.max(contents_index, snippet_index)

      if text_index >= 0
        text_index + 1
      else if alt_text_index >= 0
        alt_text_index + 1
      else
        -1

    has_header = () ->
      #return false if !has_csv()
      minimum_row_length() > 0

    has_data = () ->
      #return false if !has_header()
      records = csv_reader.result.records
      min_length = minimum_row_length()
      records.filter((r) -> r.length >= min_length).length > 2

    refresh_preview = () ->
      if csv_reader?
        throw 'CsvReader must be in readyState=2' if csv_reader.readyState != 2 # DONE

      $preview = $form.find('div.preview')

      if !csv_reader
        $preview.hide()
      else
        $preview.show()
        $preview.children(':not(h4)').hide()

        error = csv_reader.error

        if error
          $error = $preview.children('.error')
          console?.log("Error reading CSV:", error)
          refresh_preview_error($error, error)
          $error.show()

        csv = csv_reader.result
        text = csv_reader.text

        if csv
          $table = $preview.children('table')
          refresh_preview_table($table, csv, error)
          $table.show()
        else if text
          $pre = $preview.children('pre')
          refresh_preview_pre($pre, text, error)
          $pre.show()

    refresh_preview_table = ($table, csv, error) ->
      thead_template = '<% _.each(data.header, function(col) { %><th><%- col %></th><% }); %>'
      tbody_template = '<% _.each(data.records, function(record) { %><tr><% _.each(record, function(value) { %><td><div><%- value %></td><% }); %></div></tr><% }); %>'

      # You can't set innerHTML on a <table> or <thead> or <tbody> in IE
      table_template = "<table><thead>#{thead_template}</thead><tbody>#{tbody_template}</tbody></table>"

      records = csv.records
      if records.length > 2
        # Since we're dealing with a truncated file, the final row may have been
        # trucnated. Hide it.
        # (We don't do this if we only have one or two records, because we want
        # at least *some* data to be shown.)
        records = records.slice(0, -1)

      table_html = _.template(
        table_template,
        { header: csv.header, records: records },
        { variable: 'data' }
      )

      $table.replaceWith(table_html)

    refresh_preview_pre = ($pre, text, error) ->
      lines = text.split(/\r\n|\r|\n/g)

      pre_template = '<ol><% _.each(data.lines, function(line) { %><li><%- line %></li><% }); %></ol>'
      pre_html = _.template(pre_template, { lines: lines }, { variable: 'data' })

      $pre[0].innerHTML = pre_html

    refresh_preview_error = ($error, error) ->
      text = if error.name == 'SyntaxError'
        i18n('views.DocumentSet._uploadForm.error.SyntaxError', error.line, error.column, show_hidden_characters(error.found))
      else
        error.name

      $error.text(text)

    start_upload = () ->
      if upload?
        upload.abort()
        upload = undefined

      if file?
        charset = $form.find('[name=charset]').val()

        upload = new Upload(
          file,
          url_prefix,
          _.extend({ contentType: "text/csv; charset=#{charset}" }, upload_options))

        progress_elem = $modal.find('progress')[0]
        bytes_uploaded = 0
        last_rendered_bytes_uploaded = -1 # so refresh_progress runs
        bytes_total = 1
        stopped = false

        refresh_progress = ->
          if !stopped
            if last_rendered_bytes_uploaded != bytes_uploaded
              # We don't want to change things when we don't need to
              last_rendered_bytes_uploaded = bytes_uploaded

              percent = 100 * bytes_uploaded / bytes_total
              progress_elem.value = percent

            # requestAnimationFrame is too fast. We'll slow it by 50ms
            window.setTimeout((-> requestAnimationFrame(refresh_progress)), 50)

        requestAnimationFrame(refresh_progress)

        $(window).on('beforeunload.document-set-index-upload', -> i18n('views.DocumentSet._uploadForm.leavePageWarning'))
        $modal.modal('show')

        # There are *lots* of progress events with a fast connection. They become
        # a bottleneck. Hence the requestAnimationFrame...
        upload.progress (e) ->
          bytes_uploaded = e.loaded || 0
          bytes_total = e.total || 1
        upload.always -> stopped = true
        upload.done ->
          # POST to .../finish with the options
          $(window).off('beforeunload.document-set-index-upload')
          $submitForm = $('<form method="post" style="display:none;"><input type="submit" value="submit"/></form>')
            .attr('action', "#{upload.url}/finish")
            .append(importOptionsApp.el)
            .append($form.find('[name=csrfToken]').clone())
            .appendTo('body')
          $submitForm[0].submit()
        upload.fail -> console?.log('Upload failed', arguments)
        upload.start()

      refresh_form_enabled()

    refresh_form_enabled = () ->
      $form.find(':submit').attr('disabled', (!ready_to_submit || upload?) && 'disabled' || false)
      $form.find(':file, select[name=charset]').attr('disabled', upload? && 'disabled' || false)
      $form.find(':reset').attr('disabled', !file? && 'disabled' || false)

    $form.on 'submit', (e) ->
      e.preventDefault()
      start_upload()

    cancel = () ->
      upload?.abort()

      file = undefined
      upload = undefined
      csv_reader = undefined
      ready_to_submit = false
      $(window).off('beforeunload.document-set-index-upload')

      refresh_from_csv_reader()
      refresh_charset()

    $form.on 'reset', (e) ->
      # don't preventDefault()
      cancel()

    $modal.on 'hidden', () ->
      cancel()
      $form[0].reset()

    $modal.find('button[type=reset]').on 'click', (e) ->
      e.preventDefault()
      $modal.modal('hide')

    if window.File? # util.net.Upload will use HTML5
      $form.find(':file').on 'change', (e) ->
        select_file(e.target.files[0])

    else if window.Silverlight && window.SilverlightFileUploadPlugin? # util.net.Upload will use IE9+Silverlight
      file_upload_api = undefined

      on_silverlight_load = (obj, __, sender) ->
        host = sender.getHost()
        file_upload_api = new window.SilverlightFileUploadPlugin host, (new_selected_file) ->
          select_file(new_selected_file)

        upload_options.xhr_factory = (callback) ->
          file_upload_api.createXMLHttpUploadRequest (progress_obj) ->
            callback(progress_obj.loaded, progress_obj.total)

        csv_reader_options.file_reader_factory = file_upload_api.createFileReader

      # Add the Silverlight HTML, which will call on_silverlight_load
      silverlight_html = window.Silverlight.createObjectEx({
        source: FILE_UPLOAD_XAP_URL
        parentElement: null # to return HTML
        properties: {
          windowless: 'true'
          background: 'transparent'
        }
        events: {
          onLoad: on_silverlight_load
        }
      })
      $file = $form.find(':file')
      $silverlight_div = $('<div class="silverlight-file-upload"></div>')
      $silverlight_div.css({
        display: 'inline-block'
        height: 30 # HACK -- we'd like to use :file's height, but we can't compute it here
        width: 200
        verticalAlign: 'middle'
      })
      $silverlight_div.append(silverlight_html)
      $silverlight_div.children().css({
        width: '100%'
        height: '100%'
        margin: 0
        padding: 0
      })
      $file.replaceWith($silverlight_div)

    refresh_charset()
    refresh_from_csv_reader()

  $ ->
    $('#import-from-upload').one 'activate', ->
      $form = $('#import-from-upload').find('form')
      $modal = $('#document-set-upload-progress').modal({ show: false })
      make_csv_upload_form($form, $modal)
