package org.who.ddccverifier

import android.app.Application
import android.content.Context
import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.FhirVersionEnum
import com.google.android.fhir.DatabaseErrorStrategy.RECREATE_AT_OPEN
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.FhirEngineConfiguration
import com.google.android.fhir.FhirEngineProvider
import org.hl7.fhir.r4.model.Library
import org.who.ddccverifier.services.cql.CqlBuilder
import org.who.ddccverifier.services.cql.FhirOperator
import org.who.ddccverifier.trust.TrustRegistry
import org.who.ddccverifier.trust.pathcheck.PCFTrustRegistry
import kotlin.system.measureTimeMillis
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

class FhirApplication : Application() {
  private val inSync = LazyThreadSafetyMode.SYNCHRONIZED

  // Only initiate the FhirEngine when used for the first time, not when the app is created.
  private val fhirEngine: FhirEngine by lazy(inSync) { timed( "FhirEngine", ::constructFhirEngine) }
  private val fhirContext: FhirContext by lazy(inSync) { timed( "FhirContext", ::loadContext) }
  private val fhirOperator: FhirOperator by lazy(inSync) { timed( "FhirOperator", ::constructOperator) }
  private val subscribedIGs: List<Library> by lazy(inSync) { timed( "CQL Libs", ::compileIGs) }
  private val registry: TrustRegistry by lazy(inSync) { timed("Registry", ::createRegistry) }

  private val availableIGs = listOf(
    "DDCCPass-1.0.0.cql",
    "EuroPass-1.0.0.cql",
    "LacPass-1.0.0.cql"
  )

  override fun onCreate() {
    super.onCreate()
    FhirEngineProvider.init(
      FhirEngineConfiguration(
        testMode = true // does not save anything.
      )
    )
  }

  private fun constructFhirEngine(): FhirEngine {
    return FhirEngineProvider.getInstance(this)
  }

  private fun loadContext(): FhirContext {
    return FhirContext.forCached(FhirVersionEnum.R4)
  }

  private fun constructOperator(): FhirOperator {
    return FhirOperator(fhirContext, fhirEngine)
  }

  private fun compileIGs(): List<Library> {
    val libs = availableIGs.map { CqlBuilder.compileAndBuild(resources.assets.open(it)) }
    libs.forEach { fhirOperator.loadLib(it) }
    return libs
  }

  private fun createRegistry(): PCFTrustRegistry {
    val reg = PCFTrustRegistry();
    reg.init(*BuildConfig.TRUST_REGISTRY_URL);
    return reg
  }

  @OptIn(ExperimentalTime::class)
  private fun <T> timed(name: String, function: () -> T): T {
    val (result, elapsed) = measureTimedValue {
      function()
    }
    println("TIME: $name Initialized in $elapsed")
    return result
  }

  companion object {
    fun fhirEngine(context: Context) = (context.applicationContext as FhirApplication).fhirEngine
    fun fhirContext(context: Context) = (context.applicationContext as FhirApplication).fhirContext
    fun fhirOperator(context: Context) = (context.applicationContext as FhirApplication).fhirOperator
    fun trustRegistry(context: Context) = (context.applicationContext as FhirApplication).registry
    fun subscribedIGs(context: Context) = (context.applicationContext as FhirApplication).subscribedIGs
  }
}
