requirejs.config({
  baseUrl: '/assets/javascripts'

  paths: {
    jquery: 'vendor/jquery-1-8-1'
  }
})

require [
  'for-view/Document/show'
], ->
