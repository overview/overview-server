# Adds universal methods: available in every "describe" block
module FeatureHelper
  include FeatureHelpers::AdminSessionHelper
  include FeatureHelpers::SessionHelper
end
