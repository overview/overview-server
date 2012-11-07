FILE_UPLOAD_XAP_URL = '/assets/silverlight/file-upload.xap'

Upload = require('util/net/upload').Upload

make_toggle_link = (parent_selector) ->
  $parent = $(parent_selector)
  $parent.find('a:eq(0)').on 'click', (e) ->
    e.preventDefault()
    $parent.children().slice(1).toggle()
  undefined

make_csv_upload_form = (form_element) ->
  $form = $(form_element)

  given_url = $form.attr('action')
  url_prefix = given_url.split(/\//)[0..-2].join('/') + '/'

  upload = undefined

  start_upload = (file, options = {}) ->
    if upload?
      if upload.file is file
        # Don't start a new upload--we're already uploading
        upload.start()
        return
      upload.stop() # and replace it

    upload = new Upload(file, url_prefix, options)
    upload.progress (e) ->
      $form.find('progress').attr('value', 100 * (e.loaded || 0) / (e.total || 1))
      $form.find('.status').text(e.state)
    upload.done ->
      window.location.reload()
    upload.fail ->
      console.log('fail', arguments)
    upload.start()

  if window.File? # util.net.Upload will use HTML5
    refresh_submit_enabled = () ->
      file = $form.find(':file')[0].files[0]
      $form.find(':submit').attr('disabled', !file? && 'disabled' || false)

    $form.find(':file').on 'change', (e) ->
      refresh_submit_enabled()

    $form.on 'submit', (e) ->
      e.preventDefault()

      $form.find(':file, :submit').attr('disabled', 'disabled')

      $form.append('<progress max="100" value="0" /><span class="status"></span>')

      file = $form.find(':file')[0].files[0]
      start_upload(file)

    refresh_submit_enabled()

  else if window.SilverlightFileUploadPlugin? # util.net.Upload will use IE9+Silverlight
    selected_file = undefined
    file_upload_api = undefined

    refresh_submit_enabled = () ->
      $form.find(':submit').attr('disabled', !selected_file? && 'disabled' || false)

    on_silverlight_load = (obj, __, sender) ->
      host = sender.getHost()
      file_upload_api = new window.SilverlightFileUploadPlugin host, (new_selected_file) ->
        selected_file = new_selected_file
        refresh_submit_enabled()

      refresh_submit_enabled()

    refresh_submit_enabled()

    $form.on 'submit', (e) ->
      e.preventDefault()
      $form.find(':submit').attr('disabled', 'disabled')
      $form.append('<progress max="100 value="0" /><span class="status"></span>')
      # Don't worry if file_upload_api isn't initialized here: if that's the
      # case selected_file will be undefined so xhr_factory() won't be called.
      start_upload(selected_file, {
        xhr_factory: (callback) ->
          console.log("Exciting xhr_factory!")
          file_upload_api.createXMLHttpUploadRequest (progress_obj) ->
            callback(progress_obj.loaded, progress_obj.total)
      })

    # Add the Silverlight HTML, which will call on_silverlight_load
    silverlight_html = Silverlight.createObjectEx({
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
      height: $file.outerHeight()
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

$ ->
  window.dcimport.import_project_with_login($('#documentcloud-import .with-login')[0])

  make_toggle_link('#documentcloud-import .manual')
  make_toggle_link('#documentcloud-import .sample')
  make_toggle_link('#documentcloud-import .upload')

  make_csv_upload_form($('#documentcloud-import .upload form')[0])
