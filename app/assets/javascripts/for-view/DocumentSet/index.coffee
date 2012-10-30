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

  start_upload = (file) ->
    if upload?
      if upload.file is file
        # Don't start a new upload--we're already uploading
        upload.start()
        return
      upload.stop() # and replace it

    upload = new Upload(file, url_prefix)
    upload.progress (e) ->
      $form.find('progress').attr('value', 100 * (e.loaded || 0) / (e.total || 1))
      $form.find('.status').text(e.state)
    upload.done ->
      window.location.reload()
    upload.fail ->
      console.log('fail', arguments)
    upload.start()

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

$ ->
  window.dcimport.import_project_with_login($('#documentcloud-import .with-login')[0])

  make_toggle_link('#documentcloud-import .manual')
  make_toggle_link('#documentcloud-import .sample')
  make_toggle_link('#documentcloud-import .upload')

  make_csv_upload_form($('#documentcloud-import .upload form')[0])
