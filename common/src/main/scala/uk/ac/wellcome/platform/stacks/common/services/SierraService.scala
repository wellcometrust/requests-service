package uk.ac.wellcome.platform.stacks.common.services

import java.time.Instant

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials}
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, Uri}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import cats.instances.future._
import cats.instances.list._
import cats.syntax.traverse._
import io.circe.Json
import uk.ac.wellcome.platform.sierra
import uk.ac.wellcome.platform.sierra.ApiException
import uk.ac.wellcome.platform.sierra.api.{V5itemsApi, V5patronsApi}
import uk.ac.wellcome.platform.sierra.models.{ErrorCode, Hold, PatronHoldPost}
import uk.ac.wellcome.platform.stacks.common.models._
import uk.ac.wellcome.platform.stacks.common.services.CatalogueService.WorkStub

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}

class SierraService2(val maybeBaseUri: Option[Uri])(
  implicit
    val system: ActorSystem,
    val mat: ActorMaterializer
) extends AkkaClientServiceWrappper {

  import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
  import io.circe.generic.auto._

  protected val defaultBaseUri = Uri(
    "https://api.wellcomecollection.org/catalogue/v2"
  )



//  def placeHold(
//                 userIdentifier: StacksUser,
//                 sierraItemIdentifier: SierraItemIdentifier,
//                 itemLocation: StacksLocation
//               ): Future[Unit] = for {
//    patronsApi <- patronsApi()
//
//    patronHoldPost = createPatronHoldPost(
//      sierraItemIdentifier,
//      itemLocation
//    )
//
//    result = Try {
//      patronsApi.placeANewHoldRequest(patronHoldPost, userIdentifier.value.toInt)
//    } match {
//      case Success(_) => ()
//      case Failure(e: ApiException) => ()
//    }
//
//  } yield result
//  for {
//    workStub <- query[WorkStub](
//      s"works/${workId.value}",
//      "include=items%2Cidentifiers"
//    )
//
//    items = getStacksItems(workStub.items)
//
//  } yield StacksWork(workStub.id, items)
}

class SierraService(baseUrl: Option[String], username: String, password: String)(
  implicit
  as: ActorSystem,
  am: ActorMaterializer,
  ec: ExecutionContext
) {
  val sierraApiClient = new sierra.ApiClient()

  baseUrl.foreach {
    sierraApiClient.setBasePath
  }

  private val tokenUrlBase =
    baseUrl.getOrElse(sierraApiClient.getBasePath)

  object AuthToken {

    import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._

    val tokenPath = "v5/token"
    val authHeader = Authorization(BasicHttpCredentials(username, password))

    val tokenRequest = HttpRequest(
      method = HttpMethods.POST,
      uri = f"$tokenUrlBase/$tokenPath",
      headers = List(authHeader),
    )

    def get(): Future[String] = for {
      response <- Http().singleRequest(tokenRequest)
      jsonDoc <- Unmarshal(response).to[Json]
      accessToken <- jsonDoc.hcursor.downField("access_token").as[String] match {
        case Left(e) => Future.failed(e)
        case Right(token) => Future.successful(token)
      }
    } yield accessToken
  }

  protected def itemsApi(): Future[V5itemsApi] = AuthToken.get().map { token =>
    sierraApiClient.setAccessToken(token)

    new sierra.api.V5itemsApi(sierraApiClient)
  }

  protected def patronsApi(): Future[V5patronsApi] =
    AuthToken.get().map { token =>
    sierraApiClient.setAccessToken(token)

    new sierra.api.V5patronsApi(sierraApiClient)
  }

  protected def getHoldResultSet(userIdentity: StacksUser): Future[List[Hold]] = for {
    patronsApi <- patronsApi()
    holdResultSet = patronsApi.getTheHoldsDataForASinglePatronRecord(
      userIdentity.value, 100, 0,
      List.empty[String].asJava
    )
  } yield holdResultSet.getEntries.asScala.toList

  protected def getStacksPickupFromHold(hold: Hold): StacksPickup = {
    StacksPickup(
      location = StacksLocation(
        hold.getPickupLocation.getCode,
        hold.getPickupLocation.getName
      ),
      // This should be a simpler conversion to a Java Instant
      pickUpBy = Option(hold.getPickupByDate)
        .map(_.toEpochSecond)
        .map(Instant.ofEpochSecond)
    )
  }

  protected def getSierraItemIdentifierFromHold(hold: Hold): Future[SierraItemIdentifier] = Future {
    hold.getRecordType match {
      case "i" => SierraItemIdentifier.createFromHold(hold)
      case _ => throw
        new Throwable(
          f"Could not get SierraItemIdentifier from hold! ($hold)"
        )

    }
  }

  protected def getStacksHoldStatusFromHold(hold: Hold): StacksHoldStatus =
    StacksHoldStatus(
      id = hold.getStatus.getCode,
      label = hold.getStatus.getName
    )

  def getStacksUserHolds(userId: StacksUser): Future[StacksUserHolds[SierraItemIdentifier]] = for {
    holds <- getHoldResultSet(userId)

    sierraItemIdentifiers <- holds.traverse(getSierraItemIdentifierFromHold)
    holdStatuses = holds.map(getStacksHoldStatusFromHold)
    stacksPickups = holds.map(getStacksPickupFromHold)

    userHolds = (sierraItemIdentifiers, holdStatuses, stacksPickups)
      .zipped.toList map {

      case (sierraItemIdentifier, holdStatus, stacksPickup) =>
        StacksHold(
          itemId = sierraItemIdentifier,
          pickup = stacksPickup,
          status = holdStatus
        )

    }

  } yield StacksUserHolds(
    userId = userId.value,
    holds = userHolds,
  )

  private def createPatronHoldPost(itemIdentifier: SierraItemIdentifier, itemLocation: StacksLocation) = {
    val patronHoldPost = new PatronHoldPost()

    patronHoldPost.setRecordType("i")
    patronHoldPost.setRecordNumber(itemIdentifier.value)
    patronHoldPost.setPickupLocation(itemLocation.id)

    patronHoldPost
  }

  def placeHold(
                 userIdentifier: StacksUser,
                 sierraItemIdentifier: SierraItemIdentifier,
                 itemLocation: StacksLocation
               ): Future[Unit] = for {
    patronsApi <- patronsApi()

    patronHoldPost = createPatronHoldPost(
      sierraItemIdentifier,
      itemLocation
    )

    result = Try {
      patronsApi.placeANewHoldRequest(patronHoldPost, userIdentifier.value.toInt)
    } match {
      case Success(_) => ()
      case Failure(e: ApiException) => ()
    }

  } yield result

  def getItemStatus(sierraId: SierraItemIdentifier): Future[StacksItemStatus] = for {
    itemsApi <- itemsApi()
    sierraItem = itemsApi.getAnItemByRecordID(
      sierraId.value.toString, List.empty[String].asJava
    )
  } yield StacksItemStatus(
    rawCode = sierraItem.getStatus.getCode
  )
}
