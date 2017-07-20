import javax.inject.Inject
import play.api.http.DefaultHttpFilters
import play.filters.gzip.GzipFilter

class Filters @Inject() (
  logging: controllers.filters.LoggingFilter,
  canonicalHost: controllers.filters.CanonicalHostFilter,
  csrf: play.filters.csrf.CSRFFilter,
  cors: controllers.filters.CorsFilter,
  strictTransportSecurity: controllers.filters.StrictTransportSecurityFilter,
  gzip: GzipFilter
) extends DefaultHttpFilters(logging, canonicalHost, csrf, cors, strictTransportSecurity, gzip)
