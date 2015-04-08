define [
  'underscore'
  'backbone'
  'i18n'
  'elements/jquery-time_display'
], (_, Backbone, i18n) ->
  t = i18n.namespaced('views.ApiTokens')

  class TableView extends Backbone.View
    template: _.template("""
      <h3><%- t('table.caption') %></h3>
      <table class="table table-striped">
        <thead>
          <tr>
            <th class="token"><%- t('th.token') %></th>
            <th class="description"><%- t('th.description') %></th>
            <th class="created-at"><%- t('th.createdAt') %></th>
            <th class="delete"></th>
          </tr>
        </thead>
        <tbody>
          <% collection.forEach(function(token) { %>
            <tr>
              <td class="token">
                <% if (token.get('token')) { %>
                  <%- token.get('token') %>
                <% } else { %>
                  <i class="icon icon-spinner icon-spin"/>
                <% } %>
              </td>
              <td class="description"><%- token.get('description') %></td>
              <td class="created-at"><time data-format="datetime.medium" datetime="<%- token.attributes.createdAt && token.attributes.createdAt.toISOString() %>">...</time></td>
              <td class="delete"><a href="#" class="delete" data-cid="<%- token.cid %>"><%- t('delete') %></td>
            </tr>
          <% }); %>
        </tbody>
      </table>
    """)

    events:
      'click a.delete': '_onDelete'

    initialize: ->
      @listenTo(@collection, 'add remove reset sort sync', @render)

    render: ->
      html = if @collection.isEmpty()
        ''
      else
        @template
          t: t
          collection: @collection

      @$el.html(html)

      @$el.find('time').time_display()

      this

    _onDelete: (e) ->
      e.preventDefault()
      if window.confirm(t('delete.confirm'))
        cid = e.currentTarget.getAttribute('data-cid')
        model = @collection.get(cid)
        model?.destroy()
