package org.who.ddccverifier.services.cql

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.context.FhirVersionEnum
import com.google.android.fhir.FhirEngine
import com.google.android.fhir.workflow.FhirEngineDal
import com.google.android.fhir.workflow.FhirEngineLibraryContentProvider
import com.google.android.fhir.workflow.FhirEngineRetrieveProvider
import com.google.android.fhir.workflow.FhirEngineTerminologyProvider
import org.hl7.fhir.instance.model.api.IBaseParameters
import org.hl7.fhir.r4.model.*
import org.opencds.cqf.cql.engine.data.CompositeDataProvider
import org.opencds.cqf.cql.engine.fhir.converter.FhirTypeConverterFactory
import org.opencds.cqf.cql.engine.fhir.model.R4FhirModelResolver
import org.opencds.cqf.cql.engine.retrieve.RetrieveProvider
import org.opencds.cqf.cql.engine.runtime.Code
import org.opencds.cqf.cql.engine.runtime.Interval
import org.opencds.cqf.cql.engine.terminology.TerminologyProvider
import org.opencds.cqf.cql.evaluator.CqlOptions
import org.opencds.cqf.cql.evaluator.activitydefinition.r4.ActivityDefinitionProcessor
import org.opencds.cqf.cql.evaluator.builder.Constants
import org.opencds.cqf.cql.evaluator.builder.CqlEvaluatorBuilder
import org.opencds.cqf.cql.evaluator.builder.EndpointConverter
import org.opencds.cqf.cql.evaluator.builder.ModelResolverFactory
import org.opencds.cqf.cql.evaluator.builder.data.DataProviderFactory
import org.opencds.cqf.cql.evaluator.builder.data.FhirModelResolverFactory
import org.opencds.cqf.cql.evaluator.builder.data.TypedRetrieveProviderFactory
import org.opencds.cqf.cql.evaluator.builder.library.LibrarySourceProviderFactory
import org.opencds.cqf.cql.evaluator.builder.library.TypedLibrarySourceProviderFactory
import org.opencds.cqf.cql.evaluator.builder.terminology.TerminologyProviderFactory
import org.opencds.cqf.cql.evaluator.builder.terminology.TypedTerminologyProviderFactory
import org.opencds.cqf.cql.evaluator.cql2elm.util.LibraryVersionSelector
import org.opencds.cqf.cql.evaluator.engine.model.CachingModelResolverDecorator
import org.opencds.cqf.cql.evaluator.expression.ExpressionEvaluator
import org.opencds.cqf.cql.evaluator.fhir.adapter.r4.AdapterFactory
import org.opencds.cqf.cql.evaluator.library.CqlFhirParametersConverter
import org.opencds.cqf.cql.evaluator.library.LibraryProcessor
import org.opencds.cqf.cql.evaluator.measure.r4.R4MeasureProcessor
import org.opencds.cqf.cql.evaluator.plandefinition.r4.OperationParametersParser
import org.opencds.cqf.cql.evaluator.plandefinition.r4.PlanDefinitionProcessor
import java.util.function.Supplier
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

class MyRetrieveProvider(fhirEngine: FhirEngine, fhirEngineTerminologyProvider: TerminologyProvider) : RetrieveProvider {
  val prov = FhirEngineRetrieveProvider(fhirEngine).apply {
    terminologyProvider = fhirEngineTerminologyProvider
    isExpandValueSets = true
  }

  @OptIn(ExperimentalTime::class)
  override fun retrieve(
    context: String?,
    contextPath: String?,
    contextValue: Any?,
    dataType: String?,
    templateId: String?,
    codePath: String?,
    codes: MutableIterable<Code>?,
    valueSet: String?,
    datePath: String?,
    dateLowPath: String?,
    dateHighPath: String?,
    dateRange: Interval?,
  ): Iterable<Any> {
    val (result, elapsedStructureMapLoad) = measureTimedValue {
      prov.retrieve(context, contextPath, contextValue, dataType, templateId, codePath, codes, valueSet, datePath, dateLowPath, dateHighPath, dateRange);
    }

    println("TIME: Retrieve $elapsedStructureMapLoad for $context $contextPath $contextValue $dataType ${result.joinToString("-")}")

    return result;
  }
}

object LazyLoaderR4FhirModelResolver: R4FhirModelResolver() {
  override fun initialize() {
    // Override the creation of a new Context, use Cached Version instead
    fhirContext = FhirContext.forCached(FhirVersionEnum.R4)
    // do not load everything (Overriding initialize cuts 50% of evaluation time)
    fhirContext.registerCustomType(AnnotatedUuidType::class.java)
  }
}

class FhirOperator(fhirContext: FhirContext, fhirEngine: FhirEngine) {
  // Initialize the measure processor
  private val fhirEngineTerminologyProvider = FhirEngineTerminologyProvider(fhirContext, fhirEngine)
  private val adapterFactory = AdapterFactory()
  private val libraryContentProvider = FhirEngineLibraryContentProvider(adapterFactory)
  private val fhirTypeConverter = FhirTypeConverterFactory().create(FhirVersionEnum.R4)
  private val fhirEngineRetrieveProvider = MyRetrieveProvider(fhirEngine, fhirEngineTerminologyProvider)

  private val dataProvider =
    CompositeDataProvider(
      CachingModelResolverDecorator(R4FhirModelResolver()),
      fhirEngineRetrieveProvider
    )
  private val fhirEngineDal = FhirEngineDal(fhirEngine)

  private val measureProcessor =
    R4MeasureProcessor(
      fhirEngineTerminologyProvider,
      libraryContentProvider,
      dataProvider,
      fhirEngineDal
    )

  // Initialize the plan definition processor
  private val cqlFhirParameterConverter =
    CqlFhirParametersConverter(fhirContext, adapterFactory, fhirTypeConverter)
  private val libraryContentProviderFactory =
    LibrarySourceProviderFactory(
      fhirContext,
      adapterFactory,
      hashSetOf<TypedLibrarySourceProviderFactory>(
        object : TypedLibrarySourceProviderFactory {
          override fun getType() = Constants.HL7_FHIR_FILES
          override fun create(url: String?, headers: MutableList<String>?) = libraryContentProvider
        }
      ),
      LibraryVersionSelector(adapterFactory)
    )
  private val fhirModelResolverFactory = FhirModelResolverFactory()

  private val dataProviderFactory =
    DataProviderFactory(
      fhirContext,
      hashSetOf<ModelResolverFactory>(fhirModelResolverFactory),
      hashSetOf<TypedRetrieveProviderFactory>(
        object : TypedRetrieveProviderFactory {
          override fun getType() = Constants.HL7_FHIR_FILES
          override fun create(url: String?, headers: MutableList<String>?) =
            fhirEngineRetrieveProvider
        }
      )
    )
  private val terminologyProviderFactory =
    TerminologyProviderFactory(
      fhirContext,
      hashSetOf<TypedTerminologyProviderFactory>(
        object : TypedTerminologyProviderFactory {
          override fun getType() = Constants.HL7_FHIR_FILES
          override fun create(url: String?, headers: MutableList<String>?) =
            fhirEngineTerminologyProvider
        }
      )
    )
  private val endpointConverter = EndpointConverter(adapterFactory)

  private val evaluatorBuilderSupplier = Supplier {
    CqlEvaluatorBuilder()
      .withLibrarySourceProvider(libraryContentProvider)
      .withCqlOptions(CqlOptions.defaultOptions())
      .withTerminologyProvider(fhirEngineTerminologyProvider)
  }

  private val libraryProcessor =
    LibraryProcessor(
      fhirContext,
      cqlFhirParameterConverter,
      libraryContentProviderFactory,
      dataProviderFactory,
      terminologyProviderFactory,
      endpointConverter,
      fhirModelResolverFactory,
      evaluatorBuilderSupplier
    )

  private val expressionEvaluator =
    ExpressionEvaluator(
      fhirContext,
      cqlFhirParameterConverter,
      libraryContentProviderFactory,
      dataProviderFactory,
      terminologyProviderFactory,
      endpointConverter,
      fhirModelResolverFactory,
      evaluatorBuilderSupplier
    )

  private val activityDefinitionProcessor =
    ActivityDefinitionProcessor(fhirContext, fhirEngineDal, libraryProcessor)
  private val operationParametersParser =
    OperationParametersParser(adapterFactory, fhirTypeConverter)

  private val planDefinitionProcessor =
    PlanDefinitionProcessor(
      fhirContext,
      fhirEngineDal,
      libraryProcessor,
      expressionEvaluator,
      activityDefinitionProcessor,
      operationParametersParser
    )

  fun loadLib(lib: Library) {
    if (lib.url != null) {
      fhirEngineDal.libs[lib.url] = lib
    }
    if (lib.name != null) {
      libraryContentProvider.libs[lib.name] = lib
    }
  }

  fun loadLibs(libBundle: Bundle) {
    for (entry in libBundle.entry) {
      loadLib(entry.resource as Library)
    }
  }

  /**
   * The function evaluates a FHIR library against a patient's records.
   * @param libraryUrl the url of the Library to evaluate
   * @param patientId the Id of the patient to be evaluated
   * @param expressions names of expressions in the Library to evaluate.
   * @return a Parameters resource that contains an evaluation result for each expression requested
   */
  fun evaluateLibrary(
    libraryUrl: String,
    patientId: String,
    expressions: Set<String>,
  ): IBaseParameters {
    val dataEndpoint =
      Endpoint()
        .setAddress("localhost")
        .setConnectionType(Coding().setCode(Constants.HL7_FHIR_FILES))

    return libraryProcessor.evaluate(
      libraryUrl,
      patientId,
      null,
      null,
      null,
      dataEndpoint,
      null,
      expressions
    )
  }

  fun evaluateMeasure(
    measureUrl: String,
    start: String,
    end: String,
    reportType: String,
    subject: String?,
    practitioner: String?,
    lastReceivedOn: String?,
  ): MeasureReport {
    return measureProcessor.evaluateMeasure(
      measureUrl,
      start,
      end,
      reportType,
      subject,
      practitioner,
      lastReceivedOn,
      /* contentEndpoint= */ null,
      /* terminologyEndpoint= */ null,
      /* dataEndpoint= */ null,
      /* additionalData= */ null
    )
  }

  fun generateCarePlan(planDefinitionId: String, patientId: String, encounterId: String): CarePlan {
    return planDefinitionProcessor.apply(
      IdType("PlanDefinition", planDefinitionId),
      patientId,
      encounterId,
      /* practitionerId= */ null,
      /* organizationId= */ null,
      /* userType= */ null,
      /* userLanguage= */ null,
      /* userTaskContext= */ null,
      /* setting= */ null,
      /* settingContext= */ null,
      /* mergeNestedCarePlans= */ null,
      /* parameters= */ Parameters(),
      /* useServerData= */ null,
      /* bundle= */ null,
      /* prefetchData= */ null,
      /* dataEndpoint= */ null,
      /* contentEndpoint*/ null,
      /* terminologyEndpoint= */ null
    )
  }
}