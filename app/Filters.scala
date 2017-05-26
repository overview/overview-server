import javax.inject.Inject
import play.api.http.DefaultHttpFilters

class Filters @Inject() (
  logging: controllers.filters.LoggingFilter,
  csrf: play.filters.csrf.CSRFFilter,
  cors: controllers.filters.CorsFilter
) extends DefaultHttpFilters(logging, csrf, cors)
