/*
 * Copyright 2015 Webtrends (http://www.webtrends.com)
 *
 * See the LICENCE.txt file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.webtrends.harness.component.spray.route

import java.lang.reflect.InvocationTargetException

import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.server.Directive0
import com.webtrends.harness.HarnessConstants
import com.webtrends.harness.command.{BaseCommandResponse, Command, CommandBean, CommandResponse}
import com.webtrends.harness.component.spray.authentication.{OAuth, Token}
import com.webtrends.harness.component.spray.command.SprayCommandResponse
import com.webtrends.harness.component.spray.directive.{CORS, CommandDirectives, HttpCompression}
import com.webtrends.harness.component.spray.route.RouteAccessibility.RouteAccessibility
import com.webtrends.harness.component.spray.{HttpReloadRoutes, SprayManager}
import com.webtrends.harness.utils.ConfigUtil
import org.json4s.ext.JodaTimeSerializers
import org.json4s.jackson.JsonMethods._
import org.json4s.native.Serialization
import org.json4s.{JObject, JValue, _}
import spray.http.MediaTypes._
import spray.http._
import spray.httpx.marshalling.{BasicMarshallers, BasicToResponseMarshallers, Marshaller, ToResponseMarshaller}
import spray.httpx.unmarshalling._
import spray.routing._
import spray.routing.authentication.{BasicAuth, UserPass}
import spray.routing.directives.MethodDirectives

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class AkkaCommandBean(var authInfo: Option[Map[String, Any]], val headers: List[HttpHeader]) extends CommandBean

object AkkaRoutes {
  val KeyEntityType = "Request-Entity-Type"
}

/**
 * Used for command functions that are required for all Spray traits that you can add to commands
 * to add GET, POST, DELETE, UPDATE routes to the command
 *
 * @author Michael Cuthbert, Spencer Wood
 */
trait AkkaRoutes extends CommandDirectives
    with CommandRouteHandler with BasicToResponseMarshallers {
  this : Command =>
  import BasicMarshallers._
  def serialization = Serialization
  import context.dispatcher
  implicit def json4sFormats = Serialization.formats(NoTypeHints) ++ JodaTimeSerializers.all

  val sprayConfig = ConfigUtil.prepareSubConfig(context.system.settings.config, "wookiee-spray")

  protected def getRejectionHandler : Directive0 = rejectionHandler

  protected def getExceptionHandler : Directive0 = ConfigUtil.getDefaultValue("debug-exception-handler", sprayConfig.getBoolean, false) match {
    case true => debugExceptionHandler
    case false => exceptionHandler
  }

  protected val sprayManager = context.actorSelection(HarnessConstants.ComponentFullName + "/" + SprayManager.ComponentName)

  // There will potentially be two HTTP servers - One for internal use, and one that can be exposed externally.
  // By default, all routes are available on the internal port. To also expose on the external port, override this set
  // and include [[RouteAccessibility.EXTERNAL]] or use the [[RouteAccessibility.ExternalAndInternal]] trait
  def routeAccess: Set[RouteAccessibility] = Set(RouteAccessibility.INTERNAL)

  // components can have categories that they fall into, if a component has a category only a single component
  // of that category can be available. Then a user can message that category, so it would then be possible
  // to not know at all what the underlying component you are using, as long has they handle the same messages.

  // default the marshaller to the lift json marshaller
  def json4sUnmarshaller[T: Manifest] = {
    implicit def json4sFormats = Serialization.formats(NoTypeHints) ++ JodaTimeSerializers.all
    Unmarshaller[T](MediaTypes.`application/json`) {
      case x: HttpEntity.NonEmpty ⇒
        try serialization.read[T](x.asString(defaultCharset = HttpCharsets.`UTF-8`))
        catch {
          case MappingException("unknown error", ite: InvocationTargetException) ⇒ throw ite.getCause
        }
    }
  }

  //override this value if you require a different response code
  def responseStatusCode: StatusCode = StatusCodes.OK

  /**
   * Function that allows you to override the headers for a response. The Map allows you to specifically
   * set headers for specific HTTPMethods, if None set for HttpMethod, then the header will apply to all
   * methods. This is done so that if you have multiple traits like SprayPut, SprayPost, SprayOptions, SprayGet, etc.
   * you can apply different headers depending on what you are doing.
   *
   * @return
   */
  def getResponseHeaders : Map[String, List[HttpHeader]] = {
    Map[String, List[HttpHeader]]()
  }

  /**
    * Override to provide basic auth functionality before evaluating a command
    *
    * @param userPass Holds both the user and password send on the header
    * @return Some(Map[String, AnyRef]) if auth successful, None if failed, the map can be anything
    *         that is desired to be passed down on the SprayCommandBean
    */
  def basicAuth(userPass: Option[UserPass]): Future[Option[Map[String, Any]]] = Future {
    userPass.map(it => Some(Map("user" -> it.user))).getOrElse(Some(Map()))
  }

  /**
    * Override to provide bearer token auth functionality before evaluating a command, this method
    * is executed before basicAuth() and by default will fail authentication (causing us to pass
    * through to basicAuth()) so if only using basic auth there is no need to override this method
    *
    * @param tokenScope Holds both the token and the scope in which it should be executed
    * @return Some(String, String) if auth successful, None if failed, the map can be anything
    *         that is desired to be passed down on the SprayCommandBean
    */
  def tokenAuth(tokenScope: Option[Token]): Future[Option[Map[String, Any]]] = Future {
    None
  }

  /**
   * Function can be used to override any directives that you wish to use at the beginning of
   * the route
   */
  def preRoute : Directive0 = {
    pass
  }

  /**
    * Default behavior will decompress requests, Override if you want to change behavior
    * See [[HttpCompression.compression]] for more details
    */
  def compression: Directive0 = {
    decompressRequest()
  }

  /**
    * Used for optional CORS functionality.
    * See [[CORS.corsPreflight]] for more details
    */
  def corsPreflight : Directive0 = {
    pass
  }

  /**
    * Used for optional CORS functionality.
    * See [[CORS.corsRequest]] for more details
    */
  def corsRequest : Directive0 = {
    pass
  }

  /**
    * Used for optional CORS functionality.
    * See [[CORS.corsResponse]] for more details
    */
  def corsResponse : Directive0 = {
    pass
  }

  protected def innerExecute[T<:AnyRef:Manifest](bean:Option[CommandBean]=None) = {
    parameterMap {
      params =>
        val updatedBean = bean match {
          case Some(b) => b.appendMap(params); b
          case None => CommandBean(params)
        }
        onComplete[BaseCommandResponse[T]](execute[T](Some(updatedBean)).mapTo[BaseCommandResponse[T]]) {
          case Success(s) =>

            val (status, additionalHeaders) = s match {
              case scr: SprayCommandResponse[T] =>
                (scr.status, scr.additionalHeaders)
              case cr: CommandResponse[T] =>
                val statusCode = if (s.data.nonEmpty) responseStatusCode else StatusCodes.NoContent
                (statusCode, List.empty)
            }

            s.data match {
              case Some(data) =>
                val media = MediaTypes.forExtension(s.responseType) match {
                  case Some(m) => m
                  case None =>
                    val mt = Try({
                      val rt = s.responseType.split("/")
                      MediaTypes.getForKey((rt(0), rt(1))).get
                    }) recover {
                      case _ => MediaTypes.`application/json`
                    }
                    mt getOrElse MediaTypes.`application/json`
                }
                respondWithMediaType(media) {
                  respondWithHeaders(additionalHeaders) {
                    data match {
                      case streamResponse: SprayStreamResponse =>
                        new SprayStreamingResponder(streamResponse, context, status).respond
                      case _ =>
                        implicit def marshaller[A <: AnyRef] = media match {
                          case `application/json` =>
                            Marshaller.delegate[A, String](`application/json`)(it => serialization.write(it))
                          case _ if data.isInstanceOf[Array[Byte]] =>
                            Marshaller.delegate[A, Array[Byte]](media)(it => data.asInstanceOf[Array[Byte]])
                          case _ =>
                            Marshaller.delegate[A, String](media)(_.toString)
                        }
                        complete {
                          status -> data
                        }
                    }
                  }
                }
              case None =>
                complete(status)
            }
          case Failure(f) => throw f
        }
    }
  }

  protected def buildRoute(httpMethod:Directive0) : Route = {
    corsResponse {
      corsRequest {
        getRejectionHandler {
          getExceptionHandler {
            compression {
              preRoute {
                httpMethod {
                  mapHeaders(getResponseHeaders) {
                    commandPaths(paths) { bean =>
                      authenticate(OAuth(tokenAuth _, "session")) { info =>
                        bean.authInfo = Some(info)
                        innerExecute(Some(bean))
                      } ~
                        authenticate(BasicAuth(basicAuth _, "session")) { info =>
                          bean.authInfo = Some(info)
                          innerExecute(Some(bean))
                        }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  protected def addRoute(name:String, route:Route) = {
    RouteManager.addRoute(name, route, routeAccess)
    sprayManager ! HttpReloadRoutes
  }
}

/**
 * Trait for building your own custom spray route
 *
 * Use innerExecute to help with hooking up the command correctly
 */
trait SprayCustom extends SprayRoutes {
  this : Command =>
  addRoute(commandName + "_custom", customRoute)

  def customRoute : Route
}

/**
 * Trait for adding get routes to Command
 */
trait SprayGet extends SprayRoutes {
  this : Command =>
  addRoute(commandName + "_get", buildRoute(MethodDirectives.get))
}


/**
 * Based trait for any routes that grab the entity from the body of the request.
 * Currently Put and Post, due to the implicit manifest that is required for marshalling
 * any traits that use this base trait cannot be mixed in together.
 */
sealed protected trait EntityRoutes extends SprayRoutes {
  this: Command =>

  import context.dispatcher

  protected def entityRoute[T <: AnyRef](httpMethod: Directive0)(implicit unmarshaller: Unmarshaller[T]): Route = {
    corsResponse {
      corsRequest {
        getRejectionHandler {
          getExceptionHandler {
            compression {
              preRoute {
                commandPaths(paths) { bean =>
                  httpMethod {
                    entity(as[T]) { po =>
                      extractContentType { ct =>
                        bean.appendMap(Map(CommandBean.KeyEntity -> po, SprayRoutes.KeyEntityType -> ct))
                        mapHeaders(getResponseHeaders) {
                          authenticate(OAuth(tokenAuth _, "session")) { info =>
                            bean.authInfo = Some(info)
                            innerExecute(Some(bean))
                          } ~
                          authenticate(BasicAuth(basicAuth _, "session")) { info =>
                            bean.authInfo = Some(info)
                            innerExecute(Some(bean))
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

trait ByteUnmarshalling {
  implicit def InputUnmarshaller[Array[Byte]] = BasicUnmarshallers.ByteArrayUnmarshaller
}


/**
 * Trait for adding post routes to Command entity extraction will default to JObject
 */
trait SprayPost extends EntityRoutes {
  this : Command =>

  implicit def InputUnmarshaller[T: Manifest] = json4sUnmarshaller[T]

  protected def postRoute[T<:AnyRef:Manifest] = entityRoute[T](MethodDirectives.post)
  def setRoute : Route = postRoute[JObject]
  addRoute(commandName + "_post", setRoute)
}

/**
  * Trait for adding post routes to Command that will perform no unmarshalling.
  * The request data will be handed off as a raw Array[Byte]
  */
trait SprayPostBytes extends EntityRoutes with ByteUnmarshalling { this : Command =>
  addRoute(commandName + "_post", entityRoute[Array[Byte]](MethodDirectives.post))
}

/**
 * Trait for adding delete routes to Command
 */
trait SprayDelete extends SprayRoutes {
  this : Command =>
  addRoute(commandName + "_delete", buildRoute(MethodDirectives.delete))
}

/**
 * Trait for adding options routes to Command
 */
trait SprayOptions extends SprayRoutes {
  this : Command =>
  addRoute(commandName + "_options", optionsRoute)

  /**
   * Function will return all the allowed headers for the command. Basically if you mixin a trait like
   * SprayGet, it will add the Get method to the allow header. This method should be overridden if you
   * are using the SprayCustom trait as then you would be defining your own routes and methods.
   *
   * @return
   */
   def getMethods = {
    def getMethod[I<:SprayRoutes](klass:Class[I], method:HttpMethod) : Option[HttpMethod] = {
      klass.isAssignableFrom(this.getClass) match {
        case true => Some(method)
        case false => None
      }
    }

    Seq[Option[HttpMethod]] (
      getMethod(classOf[SprayGet], HttpMethods.GET),
      getMethod(classOf[SprayHead], HttpMethods.HEAD),
      getMethod(classOf[SprayPatch], HttpMethods.PATCH),
      getMethod(classOf[SprayPut], HttpMethods.PUT),
      getMethod(classOf[SprayPost], HttpMethods.POST),
      getMethod(classOf[SprayOptions], HttpMethods.OPTIONS),
      getMethod(classOf[SprayDelete], HttpMethods.DELETE)
    ).flatten
  }

  /**
   * Override this function to give the options specific information about the command
   */
  def optionsResponse : JValue =  parse("""{}""")

  def optionsRoute: Route = {
    respondJson {
      corsPreflight {
        getRejectionHandler {
          getExceptionHandler {
            preRoute {
              commandPaths(paths) { bean =>
                options {
                   ctxComplete
                }
              }
            }
          }
        }
      }
    }
  }

  def ctxComplete: Route = {
    respondWithHeaders(HttpHeaders.Allow(getMethods: _*), HttpHeaders.`Access-Control-Allow-Methods`(getMethods)) {
      mapHeaders(getResponseHeaders) { ctx =>
        implicit def jsonMarshaller[T <: AnyRef] =
          Marshaller.delegate[T, String](`application/json`)(serialization.write(_))
        ctx.complete(StatusCodes.OK -> optionsResponse)
        ToResponseMarshaller.fromMarshaller[JValue](StatusCodes.OK)(jsonMarshaller)
      }
    }
  }
}

/**
 * Trait for adding head routes to Command
 */
trait SprayHead extends SprayRoutes {
  this : Command =>
  addRoute(commandName + "_head", buildRoute(MethodDirectives.head))
}

/**
 * Trait for adding patch routes to Command
 */
trait SprayPatch extends SprayRoutes {
  this : Command =>
  addRoute(commandName + "_patch", buildRoute(MethodDirectives.patch))
}

/**
 * Trait for adding put routes to Command
 */
trait SprayPut extends EntityRoutes {
  this : Command =>

  implicit def InputUnmarshaller[T: Manifest] = json4sUnmarshaller[T]

  protected def putRoute[T<:AnyRef:Manifest] = entityRoute[T](MethodDirectives.put)
  def setRoute : Route = putRoute[JObject]
  addRoute(commandName + "_put", setRoute)
}

/**
  * Trait for adding put routes to Command that will perform no unmarshalling.
  * The request data will be handed off as a raw Array[Byte]
  */
trait SprayPutBytes extends EntityRoutes with ByteUnmarshalling { this : Command =>
  addRoute(commandName + "_put", entityRoute[Array[Byte]](MethodDirectives.put))
}


