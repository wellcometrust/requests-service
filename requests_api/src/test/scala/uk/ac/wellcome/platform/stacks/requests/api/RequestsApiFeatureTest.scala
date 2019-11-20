package uk.ac.wellcome.platform.stacks.requests.api

import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model.{HttpHeader, StatusCodes}
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.json.utils.JsonAssertions
import uk.ac.wellcome.platform.stacks.requests.api.fixtures.{CatalogueWireMockFixture, RequestsApiFixture, SierraWireMockFixture}

class RequestsApiFeatureTest
    extends FunSpec
    with Matchers
    with RequestsApiFixture
    with JsonAssertions
    with CatalogueWireMockFixture
    with SierraWireMockFixture
    with IntegrationPatience {

  describe("requests") {
    it("responds to requests containing an Sierra-Patron-Id header") {
      withMockCatalogueServer { catalogueApiUrl: String =>
        withMockSierraServer { sierraApiUrl: String =>
          withConfiguredApp(catalogueApiUrl, sierraApiUrl) { case (_, _) =>
            val path = "/requests"

            val headers = List(
              HttpHeader.parse(
                name = "Sierra-Patron-Id",
                value = "1234567"
              ).asInstanceOf[ParsingResult.Ok].header
            )

            whenGetRequestReady(path, headers) { response =>
              response.status shouldBe StatusCodes.OK
            }
          }
        }
      }
    }

    it("provides information about a users' holds") {
      withMockCatalogueServer { catalogueApiUrl: String =>
        withMockSierraServer { sierraApiUrl: String =>
          withConfiguredApp(catalogueApiUrl, sierraApiUrl) { case (_, _) =>
            val path = "/requests"

            val headers = List(
              HttpHeader.parse(
                name = "Sierra-Patron-Id",
                value = "1234567"
              ).asInstanceOf[ParsingResult.Ok].header
            )

            val expectedJson =
              s"""
                 |{
                 |  "userId" : "1234567",
                 |  "holds" : [
                 |    {
                 |      "itemId" : "ys3ern6x",
                 |      "pickup" : {
                 |        "location" : {
                 |          "id" : "sepbb",
                 |          "label" : "Rare Materials Room"
                 |        },
                 |        "pickUpBy" : "2019-12-03T04:00:00Z"
                 |      },
                 |      "status" : {
                 |        "id" : "i",
                 |        "label" : "item hold ready for pickup."
                 |      }
                 |    }
                 |  ]
                 |}"""
                .stripMargin

            whenGetRequestReady(path, headers) { response =>
              response.status shouldBe StatusCodes.OK

              withStringEntity(response.entity) { actualJson =>
                assertJsonStringsAreEqual(actualJson, expectedJson)
              }
            }
          }
        }
      }
    }
  }
}