(function() {
  Timecop.MockDate.now = function() {
    return (new Date()).getTime(); // the mocked date
  };
  Timecop.install();
})();
