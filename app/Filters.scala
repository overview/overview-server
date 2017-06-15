import javax.inject.Inject
import play.api.http.DefaultHttpFilters

class Filters @Inject() (
  logging: controllers.filters.LoggingFilter,
  canonicalHost: controllers.filters.CanonicalHostFilter,
  csrf: play.filters.csrf.CSRFFilter,
  cors: controllers.filters.CorsFilter,
  strictTransportSecurity: controllers.filters.StrictTransportSecurityFilter
) extends DefaultHttpFilters(logging, canonicalHost, csrf, cors, strictTransportSecurity)
