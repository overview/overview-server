/*
 * The test framework requires jQuery through "karma-jquery", which loads up
 * jQuery into a global variable. Then along comes "karma-chai-jquery", which
 * sets up a bunch of functions on that jQuery instance.
 *
 * But all our code and tests require "jquery" through RequireJS. D'oh! We need
 * that to be the same global variable.
 *
 * So this is it: our implementation of jQuery, for use through RequireJS.
 */
define([], function() { return window.jQuery });
