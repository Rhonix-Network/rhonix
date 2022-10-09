package io.rhonix.node.api.v1

import io.rhonix.casper.protocol.{BlockInfo, LightBlockInfo}
import io.rhonix.node.api.WebApi.{
  ApiStatus,
  DataAtNameByBlockHashRequest,
  DeployExecStatus,
  DeployRequest,
  ExploreDeployRequest,
  RhoDataResponse
}
import io.rhonix.node.api.json.JsonSchemaDerivations
import endpoints4s.algebra
import cats.syntax.all._

/**
  * Defines the HTTP endpoints description of Web API v1.
  */
trait WebApiEndpoints
    extends algebra.Endpoints
    with algebra.JsonEntitiesFromSchemas
    with JsonSchemaDerivations {

  val status: Endpoint[Unit, ApiStatus] = endpoint(
    get(path / "status"),
    ok(jsonResponse[ApiStatus]),
    docs = EndpointDocs().withDescription("API status data".some)
  )

  // Prepare deploy

  // Deploy

  val deploy: Endpoint[DeployRequest, String] = endpoint(
    post(path / "deploy", jsonRequest[DeployRequest]),
    ok(jsonResponse[String])
  )

  val deployStatus: Endpoint[String, DeployExecStatus] = endpoint(
    get(path / "deploy-status" / deploySignature),
    ok(jsonResponse[DeployExecStatus]),
    docs =
      EndpointDocs().withDescription("Get status of deploy with specified deploy signature".some)
  )

  val exploreDeploy: Endpoint[String, RhoDataResponse] = endpoint(
    post(path / "explore-deploy", jsonRequest[String]),
    ok(jsonResponse[RhoDataResponse]),
    docs = EndpointDocs().withDescription("Exploratory deploy on last finalized state".some)
  )

  val exploreDeployByBlockHash: Endpoint[ExploreDeployRequest, RhoDataResponse] = endpoint(
    post(path / "explore-deploy-by-block-hash", jsonRequest[ExploreDeployRequest]),
    ok(jsonResponse[RhoDataResponse]),
    docs = EndpointDocs().withDescription("Exploratory deploy".some)
  )

  // Get data

  val dataAtName: Endpoint[DataAtNameByBlockHashRequest, RhoDataResponse] = endpoint(
    post(path / "data-at-name-by-block-hash", jsonRequest[DataAtNameByBlockHashRequest]),
    ok(jsonResponse[RhoDataResponse])
  )

  // Blocks

  val getBlocks: Endpoint[Unit, List[LightBlockInfo]] = endpoint(
    get(path / "blocks"),
    ok(jsonResponse[List[LightBlockInfo]])
  )

  val getBlock: Endpoint[String, BlockInfo] = endpoint(
    get(path / "block" / hashString),
    ok(jsonResponse[BlockInfo])
  )

  //    val getTransaction: Endpoint[String, TransactionResponse] = endpoint(
  //      get(path / "transactions" / hashString),
  //      ok(jsonResponse[TransactionResponse])
  //    )

  // Segments

  lazy val hashString = segment[String](name = "hash", docs = "Hex encoded string".some)
  lazy val deploySignature =
    segment[String](name = "deploySignature", docs = "Signature of deploy as HEX string".some)
}
