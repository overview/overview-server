$ ->
  $('#document-set-import').one 'show', ->
    dcimport_div = document.getElementById('import-from-documentcloud-account')
    window.dcimport.import_project_with_login(dcimport_div)

  $dcquery_submit = $('#import-from-documentcloud-query button')
  refresh_dcquery_submit = () ->
    $form = $dcquery_submit.closest('form')
    title = $form.find('input[name=title]').val() || ''
    query = $form.find('input[name=query]').val() || ''
    $button_span = $dcquery_submit.find('span.query')
    $button_span.text(query)
    if title && query
      $dcquery_submit.removeClass('disabled')
    else
      $dcquery_submit.addClass('disabled')
  $dcquery_submit.closest('form').on('change keyup cut paste blur', 'input', refresh_dcquery_submit)
  refresh_dcquery_submit()
