(function() {
  Timecop.install();

  require('globals').log = function() {};

  require('globals').create_logger = function() {
    return function() {};
  };
})();
