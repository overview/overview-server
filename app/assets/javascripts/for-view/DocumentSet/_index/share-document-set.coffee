
loading_template = _.template("""<p>Loading sharing settings.""")
sharing_dialog_template =  _.template("""
    <div class="well" id="manage-user-roles">
      <ul class="unstyled">
        <% _.each(viewers, function(viewer) { %> <li> <%- viewer.email %></li> <% }); %>
      </ul>
    </div>
    <form method="post" class="form-inline add-viewer" action="/documentsets/179/users" >
      <input type="text" class="input-small" placeholder="Email" name="email" />
      <input type="hidden" name="role" value="Viewer" />
      <input type="submit" value="Add Viewer" />
    </form>""")


show_sharing_settings = (url, addViewerUrl) ->
  $modal = $('#sharing-options-modal')
  $modal.modal('show')
  $modal.find('.modal-body').html(loading_template)
  console.log(url)
  $.getJSON(url)
    .success((emails) ->
      console.log(emails)
      $modal.find('.modal-body').html(sharing_dialog_template(emails, url: addViewerUrl)))
    .error((a, b, c) ->
      console.log("error #{b}"))



$ ->
  $('#sharing-options-modal').one 'show', ->
    $('#sharing-options-modal').on 'submit', 'form.add-viewer', (e) ->
      e.stopImmediatePropagation()
      e.preventDefault()
      data = $(e.currentTarget).serialize()
      console.log(data)
      $.ajax({
        url: '/documentsets/179/users'
        type: 'POST'
        data: data
        })
        
  $('div.document-sets').on 'click', 'a.show-sharing-settings', (e) ->
    console.log('clickable')
    e.preventDefault()
    url = $(e.currentTarget).attr('href')
    addViewerUrl = $(e.currentTarget).attr('add-viewer-url')
    show_sharing_settings(url, addViewerUrl)


