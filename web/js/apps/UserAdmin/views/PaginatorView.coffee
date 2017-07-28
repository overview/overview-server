define [ 'jquery', 'underscore', 'backbone' ], ($, _, Backbone) ->
  class PaginatorView extends Backbone.View
    tagName: 'div'

    template: _.template("""
      <ul class="pagination">
        <li class="<%- (page == 1) ? "disabled" : "" %>"><a href="#page-<%- page - 1 %>" class="previous">«</a></li>
        <% _.range(1, Math.floor(total / pageSize) + 2).forEach(function(i) { %>
          <li class="<%- (i == page) ? 'active' : '' %>"><a href="#page=<%- i %>"><%- i %></a></li>
        <% }) %>
        <li class="<%- (page == Math.floor(total / pageSize) + 1) ? "disabled" : "" %>"><a href="#page-<%- page + 1 %>" class="next">»</a></li>
      </ul>
      """)

    events:
      'click a': '_onClick'

    initialize: ->
      throw 'must pass model, a Backbone.Model' if !@model?

      @listenTo(@model, 'change', @render)

    render: ->
      className = @_className()
      @el.className = "paginator #{className}"

      if className != 'multiple'
        @$el.empty()
      else
        html = @template(@model.toJSON())
        @$el.html(html)

    _className: ->
      json = @model.toJSON()
      if !json.total?
        'loading'
      else if json.total / json.pageSize > 1
        'multiple'
      else
        'single'

    _onClick: (e) ->
      e.preventDefault()
      $a = $(e.currentTarget)
      page = if $a.hasClass('next')
        Math.min(Math.floor(@model.get('total') / @model.get('pageSize')) + 1, @model.get('page') + 1)
      else if $a.hasClass('previous')
        Math.max(1, @model.get('page') - 1)
      else
        parseInt($a.text(), 10)
      @model.set(page: page)
