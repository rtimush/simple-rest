package com.rtimushev.demo.rest

import org.squeryl._
import org.squeryl.dsl.ast._
import org.squeryl.adapters.H2Adapter
import org.squeryl.PrimitiveTypeMode.{& => &&, _}
import java.util._
import java.sql.DriverManager
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.codehaus.jackson.map.ObjectMapper
import org.codehaus.jackson.annotate.JsonIgnoreProperties
import unfiltered.response._
import unfiltered.request._
import unfiltered.netty._
import scala.util.control.Exception._
import scalaz.Scalaz._

@JsonIgnoreProperties(Array("_isPersisted"))
case class Customer(id: String,
                    firstName: String,
                    lastName: String,
                    email: Option[String],
                    birthday: Option[Date]) extends KeyedEntity[String]

object Boot extends App {

  Class.forName("org.h2.Driver")

  SessionFactory.concreteFactory =
    Some(() => Session.create(DriverManager.getConnection("jdbc:h2:test", "sa", ""), new H2Adapter))

  object DB extends Schema {
    val customer = table[Customer]
  }

  transaction { allCatch opt DB.create }

  val mapper = new ObjectMapper().withModule(DefaultScalaModule)

  def parseCustomerJson(json: String): Option[Customer] =
    allCatch opt mapper.readValue(json, classOf[Customer])
  def readCustomer(req: HttpRequest[_], id: => String): Option[Customer] =
    parseCustomerJson(Body.string(req)) map (_.copy(id = id)) filter (_.productIterator.forall(_ != null))

  case class ResponseJson(o: Any) extends ComposeResponse(
    ContentType("application/json") ~> ResponseString(mapper.writeValueAsString(o)))

  def nextId = UUID.randomUUID().toString

  val field: PartialFunction[String, Customer => TypedExpressionNode[_]] = {
    case "id" => _.id
    case "firstName" => _.firstName
    case "lastName" => _.lastName
    case "email" => _.email
    case "birthday" => _.birthday
  }

  val ordering: PartialFunction[String, TypedExpressionNode[_] => OrderByExpression] = {
    case "asc" => _.asc
    case "desc" => _.desc
  }

  val service = cycle.Planify {
    case req@Path(Seg("customer" :: id :: Nil)) => req match {
      case GET(_) => transaction { DB.customer.lookup(id) cata(ResponseJson, NotFound) }
      case PUT(_) => transaction { readCustomer(req, id) ∘ DB.customer.update cata(_ => Ok, BadRequest) }
      case DELETE(_) => transaction { DB.customer.delete(id); NoContent }
      case _ => Pass
    }
    case req@Path(Seg("customer" :: Nil)) => req match {
      case POST(_) =>
        transaction {
          readCustomer(req, nextId) ∘ DB.customer.insert ∘ ResponseJson cata(_ ~> Created, BadRequest)
        }
      case GET(_) & Params(params) =>
        transaction {
          import Params._
          val orderBy = (params.get("orderby") ∗ first orElse Some("id")) ∗ field.lift
          val order = (params.get("order") ∗ first orElse Some("asc")) ∗ ordering.lift
          val pageNum = params.get("pagenum") ∗ (first ~> int)
          val pageSize = params.get("pagesize") ∗ (first ~> int)
          val offset = ^(pageNum, pageSize)(_ * _)
          val query = from(DB.customer) { q => select(q) orderBy ^(orderBy, order)(_ andThen _ apply q).toList }
          val pagedQuery = ^(offset, pageSize)(query.page) getOrElse query
          ResponseJson(pagedQuery.toList)
        }
      case _ => Pass
    }
  }

  Http(8080).plan(service).run()

}
