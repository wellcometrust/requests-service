package uk.ac.wellcome.platform.stacks.requests.api

import java.net.URL

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import uk.ac.wellcome.monitoring.typesafe.MetricsBuilder
import uk.ac.wellcome.platform.stacks.common.http.config.builders.HTTPServerBuilder
import uk.ac.wellcome.platform.stacks.common.http.{HttpMetrics, WellcomeHttpApp}
import uk.ac.wellcome.platform.stacks.common.services.StacksService
import uk.ac.wellcome.platform.stacks.common.services.config.builders.{SierraServiceBuilder, StacksServiceBuilder}
import uk.ac.wellcome.typesafe.WellcomeTypesafeApp
import uk.ac.wellcome.typesafe.config.builders.AkkaBuilder

import scala.concurrent.ExecutionContext

object Main extends WellcomeTypesafeApp {
  runWithConfig { config: Config =>
    implicit val asMain: ActorSystem =
      AkkaBuilder.buildActorSystem()

    implicit val ecMain: ExecutionContext =
      AkkaBuilder.buildExecutionContext()

    implicit val amMain: ActorMaterializer =
      AkkaBuilder.buildActorMaterializer()

    val workService: StacksService =
      new StacksServiceBuilder().build(config)

    val router: RequestsApi = new RequestsApi {
      override implicit val ec: ExecutionContext = ecMain
      override implicit val stacksWorkService: StacksService = workService
    }

    val appName = "RequestsApi"

    new WellcomeHttpApp(
      routes = router.routes,
      httpMetrics = new HttpMetrics(
        name = appName,
        metrics = MetricsBuilder.buildMetricsSender(config)
      ),
      httpServerConfig = HTTPServerBuilder.buildHTTPServerConfig(config),
      contextURL = new URL(
        "https://api.wellcomecollection.org/stacks/v1/context.json"),
      appName = appName
    )
  }
}
