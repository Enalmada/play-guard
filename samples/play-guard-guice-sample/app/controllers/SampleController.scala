package controllers

import javax.inject._

import akka.actor.ActorSystem
import com.digitaltangible.playguard._
import play.api.Configuration
import play.api.mvc._

import scala.concurrent.ExecutionContext

/**
  * This controller creates an `Action` to handle HTTP requests to the
  * application's home page.
  */
@Singleton
class SampleController @Inject()(implicit components: ControllerComponents, system: ActorSystem, conf: Configuration, ec: ExecutionContext, bodyParsers: PlayBodyParsers) extends AbstractController(components) {

  implicit val parser = bodyParsers.anyContent

  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }


  // allow 3 requests immediately and get a new token every 5 seconds
  private val ipRateLimitedAction = IpRateLimitAction[AnyContent](new RateLimiter(3, 1f / 5, "test limit by IP address")) {
    implicit r: RequestHeader => TooManyRequests( s"""rate limit for ${r.remoteAddress} exceeded""")
  }

  def limitedByIp: Action[AnyContent] = ipRateLimitedAction {
    Ok("limited by IP")
  }


  // allow 4 requests immediately and get a new token every 15 seconds
  private val keyRateLimitedAction = KeyRateLimitAction[AnyContent](new RateLimiter(4, 1f / 15, "test by token")) _

  def limitedByKey(key: String): Action[AnyContent] = keyRateLimitedAction(_ => TooManyRequests( s"""rate limit for '$key' exceeded"""), key, bodyParsers.anyContent, ec) {
    Ok("limited by token")
  }


  // allow 2 failures immediately and get a new token every 10 seconds
  private val httpErrorRateLimited = HttpErrorRateLimitAction[AnyContent](new RateLimiter(2, 1f / 10, "test failure rate limit")) {
    implicit r: RequestHeader => BadRequest("failure rate exceeded")
  }

  def failureLimitedByIp(fail: Boolean): Action[AnyContent] = httpErrorRateLimited {
    if (fail) BadRequest("failed")
    else Ok("Ok")
  }

  // combine tokenRateLimited and httpErrorRateLimited
  def limitByKeyAndHttpErrorByIp(key: String, fail: Boolean): Action[AnyContent] =
    (keyRateLimitedAction(_ => TooManyRequests( s"""rate limit for '$key' exceeded"""), key, bodyParsers.anyContent, ec) andThen httpErrorRateLimited) {

      if (fail) BadRequest("failed")
      else Ok("Ok")
    }
}
