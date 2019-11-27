package uk.ac.wellcome.platform.stacks.common.services

import java.time.Instant

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.platform.stacks.common.fixtures.ServicesFixture
import uk.ac.wellcome.platform.stacks.common.models._
import com.github.tomakehurst.wiremock.client.WireMock._


class SierraServiceTest
  extends FunSpec
    with ServicesFixture
    with ScalaFutures
    with IntegrationPatience
    with Matchers {

  describe("SierraService") {
    describe("getItemStatus") {
      it("should get a StacksItemStatus") {
        withSierraService { case (sierraService, _) =>
          val sierraItemIdentifier = SierraItemIdentifier("1292185")

          whenReady(
            sierraService.getItemStatus(sierraItemIdentifier)
          ) { stacksItemStatus =>
            stacksItemStatus shouldBe StacksItemStatus("available", "Available")
          }
        }
      }
    }

    describe("getStacksUserHolds") {
      it("gets a StacksUserHolds") {
        withSierraService { case (sierraService, _) =>
          val stacksUserIdentifier = StacksUserIdentifier("1234567")

          whenReady(
            sierraService.getStacksUserHolds(stacksUserIdentifier)
          ) { stacksUserHolds =>
            stacksUserHolds shouldBe StacksUserHolds(userId = "1234567",
              holds = List(
                StacksHold(
                  itemId = SierraItemIdentifier("1292185"),
                  pickup = StacksPickup(
                    location = StacksLocation(
                      id = "sepbb",
                      label = "Rare Materials Room"
                    ),
                    pickUpBy = Instant.parse("2019-12-03T04:00:00Z")
                  ),
                  status = StacksHoldStatus(
                    id = "i",
                    label = "item hold ready for pickup."
                  )
                )
              )
            )
          }
        }
      }
    }

    describe("placeHold") {
      it("should request a hold from the Sierra API") {
        withSierraService { case (sierraService, wireMockServer) =>
          val sierraItemIdentifier = SierraItemIdentifier("1292185")
          val stacksUserIdentifier = StacksUserIdentifier("1234567")
          val stacksLocation = StacksLocation("sicon", "this value is ignored")

          whenReady(
            sierraService.placeHold(
              userIdentifier = stacksUserIdentifier,
              sierraItemIdentifier = sierraItemIdentifier,
              itemLocation = stacksLocation
            )) { _ =>

            wireMockServer.verify(1, postRequestedFor(
              urlEqualTo("/iii/sierra-api/v5/patrons/1234567/holds/requests")
            ).withRequestBody(equalToJson(
              """
                |{
                |  "recordType" : "i",
                |  "recordNumber" : 1292185,
                |  "pickupLocation" : "sicon"
                |}
                |""".stripMargin)
              ))
          }
        }
      }
    }
  }
}