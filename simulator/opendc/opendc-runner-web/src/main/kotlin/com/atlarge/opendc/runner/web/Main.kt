package com.atlarge.opendc.runner.web

import com.atlarge.odcsim.SimulationEngineProvider
import com.atlarge.opendc.compute.virt.service.allocation.*
import com.atlarge.opendc.experiments.sc20.experiment.attachMonitor
import com.atlarge.opendc.experiments.sc20.experiment.createFailureDomain
import com.atlarge.opendc.experiments.sc20.experiment.createProvisioner
import com.atlarge.opendc.experiments.sc20.experiment.model.Workload
import com.atlarge.opendc.experiments.sc20.experiment.monitor.ParquetExperimentMonitor
import com.atlarge.opendc.experiments.sc20.experiment.processTrace
import com.atlarge.opendc.experiments.sc20.trace.Sc20ParquetTraceReader
import com.atlarge.opendc.experiments.sc20.trace.Sc20RawParquetTraceReader
import com.atlarge.opendc.format.trace.sc20.Sc20PerformanceInterferenceReader
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import com.mongodb.MongoClientSettings
import com.mongodb.MongoCredential
import com.mongodb.ServerAddress
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import java.io.File
import java.util.*
import kotlin.random.Random
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import mu.KotlinLogging
import org.bson.Document

private val logger = KotlinLogging.logger {}

/**
 * The provider for the simulation engine to use.
 */
private val provider = ServiceLoader.load(SimulationEngineProvider::class.java).first()

/**
 * Represents the CLI command for starting the OpenDC web runner.
 */
class RunnerCli : CliktCommand(name = "runner") {
    /**
     * The name of the database to use.
     */
    private val mongoDb by option(
        "--mongo-db",
        help = "name of the database to use",
        envvar = "OPENDC_DB"
    )
        .default("opendc")

    /**
     * The database host to connect to.
     */
    private val mongoHost by option(
        "--mongo-host",
        help = "database host to connect to",
        envvar = "OPENDC_DB_HOST"
    )
        .default("localhost")

    /**
     * The database port to connect to.
     */
    private val mongoPort by option(
        "--mongo-port",
        help = "database port to connect to",
        envvar = "OPENDC_DB_PORT"
    )
        .int()
        .default(27017)

    /**
     * The database user to connect with.
     */
    private val mongoUser by option(
        "--mongo-user",
        help = "database user to connect with",
        envvar = "OPENDC_DB_USER"
    )
        .default("opendc")

    /**
     * The database password to connect with.
     */
    private val mongoPassword by option(
        "--mongo-password",
        help = "database password to connect with",
        envvar = "OPENDC_DB_PASSWORD"
    )
        .convert { it.toCharArray() }
        .required()

    /**
     * The path to the traces directory.
     */
    private val tracePath by option(
        "--traces",
        help = "path to the directory containing the traces",
        envvar = "OPENDC_TRACES"
    )
        .file(canBeFile = false)
        .defaultLazy { File("traces/") }

    /**
     * The path to the output directory.
     */
    private val outputPath by option(
        "--output",
        help = "path to the results directory",
        envvar = "OPENDC_OUTPUT"
    )
        .file(canBeFile = false)
        .defaultLazy { File("results/") }

    /**
     * The Spark master to connect to.
     */
    private val spark by option(
        "--spark",
        help = "Spark master to connect to",
        envvar = "OPENDC_SPARK"
    )
        .default("local[*]")

    /**
     * Connect to the user-specified database.
     */
    private fun createDatabase(): MongoDatabase {
        val credential = MongoCredential.createScramSha1Credential(
            mongoUser,
            mongoDb,
            mongoPassword
        )

        val settings = MongoClientSettings.builder()
            .credential(credential)
            .applyToClusterSettings { it.hosts(listOf(ServerAddress(mongoHost, mongoPort))) }
            .build()
        val client = MongoClients.create(settings)
        return client.getDatabase(mongoDb)
    }

    /**
     * Run a single scenario.
     */
    private suspend fun runScenario(portfolio: Document, scenario: Document, topologies: MongoCollection<Document>) {
        val id = scenario.getString("_id")

        logger.info { "Constructing performance interference model" }

        val traceDir = File(
            tracePath,
            scenario.getEmbedded(listOf("trace", "traceId"), String::class.java)
        )
        val traceReader = Sc20RawParquetTraceReader(traceDir)
        val performanceInterferenceReader = let {
            val path = File(traceDir, "performance-interference-model.json")
            val operational = scenario.get("operational", Document::class.java)
            val enabled = operational.getBoolean("performanceInterferenceEnabled")

            if (!enabled || !path.exists()) {
                return@let null
            }

            path.inputStream().use { Sc20PerformanceInterferenceReader(it) }
        }

        val targets = portfolio.get("targets", Document::class.java)

        repeat(targets.getInteger("repeatsPerScenario")) {
            logger.info { "Starting repeat $it" }
            runRepeat(scenario, it, topologies, traceReader, performanceInterferenceReader)
        }

        logger.info { "Finished simulation for scenario $id" }
    }

    /**
     * Run a single repeat.
     */
    private suspend fun runRepeat(
        scenario: Document,
        repeat: Int,
        topologies: MongoCollection<Document>,
        traceReader: Sc20RawParquetTraceReader,
        performanceInterferenceReader: Sc20PerformanceInterferenceReader?
    ) {
        val id = scenario.getString("_id")
        val seed = repeat
        val traceDocument = scenario.get("trace", Document::class.java)
        val workloadName = traceDocument.getString("traceId")
        val workloadFraction = traceDocument.get("loadSamplingFraction", Number::class.java).toDouble()

        val seeder = Random(seed)
        val system = provider("experiment-$id")
        val root = system.newDomain("root")

        val chan = Channel<Unit>(Channel.CONFLATED)

        val operational = scenario.get("operational", Document::class.java)
        val allocationPolicy =
            when (val policyName = operational.getString("schedulerName")) {
                "mem" -> AvailableMemoryAllocationPolicy()
                "mem-inv" -> AvailableMemoryAllocationPolicy(true)
                "core-mem" -> AvailableCoreMemoryAllocationPolicy()
                "core-mem-inv" -> AvailableCoreMemoryAllocationPolicy(true)
                "active-servers" -> NumberOfActiveServersAllocationPolicy()
                "active-servers-inv" -> NumberOfActiveServersAllocationPolicy(true)
                "provisioned-cores" -> ProvisionedCoresAllocationPolicy()
                "provisioned-cores-inv" -> ProvisionedCoresAllocationPolicy(true)
                "random" -> RandomAllocationPolicy(Random(seeder.nextInt()))
                else -> throw IllegalArgumentException("Unknown policy $policyName")
            }

        val performanceInterferenceModel = performanceInterferenceReader?.construct(seeder) ?: emptyMap()
        val trace = Sc20ParquetTraceReader(
            listOf(traceReader),
            performanceInterferenceModel,
            Workload(workloadName, workloadFraction),
            seed
        )
        val topologyId = scenario.getEmbedded(listOf("topology", "topologyId"), String::class.java)
        val environment = TopologyParser(topologies, topologyId)
        val monitor = ParquetExperimentMonitor(
            outputPath,
            "scenario_id=$id/run_id=$repeat",
            4096
        )

        root.launch {
            val (bareMetalProvisioner, scheduler) = createProvisioner(
                root,
                environment,
                allocationPolicy
            )

            val failureDomain = if (operational.getBoolean("failuresEnabled")) {
                logger.debug("ENABLING failures")
                createFailureDomain(
                    seeder.nextInt(),
                    operational.get("failureFrequency", Number::class.java)?.toDouble() ?: 24.0 * 7,
                    bareMetalProvisioner,
                    chan
                )
            } else {
                null
            }

            attachMonitor(scheduler, monitor)
            processTrace(
                trace,
                scheduler,
                chan,
                monitor,
                emptyMap()
            )

            logger.debug("SUBMIT=${scheduler.submittedVms}")
            logger.debug("FAIL=${scheduler.unscheduledVms}")
            logger.debug("QUEUED=${scheduler.queuedVms}")
            logger.debug("RUNNING=${scheduler.runningVms}")
            logger.debug("FINISHED=${scheduler.finishedVms}")

            failureDomain?.cancel()
            scheduler.terminate()
        }

        try {
            system.run()
        } finally {
            system.terminate()
            monitor.close()
        }
    }

    val POLL_INTERVAL = 5000L // ms = 5 s
    val HEARTBEAT_INTERVAL = 60000L // ms = 1 min

    override fun run() = runBlocking(Dispatchers.Default) {
        logger.info { "Starting OpenDC web runner" }
        logger.info { "Connecting to MongoDB instance" }
        val database = createDatabase()
        val manager = ScenarioManager(database.getCollection("scenarios"))
        val portfolios = database.getCollection("portfolios")
        val topologies = database.getCollection("topologies")

        logger.info { "Launching Spark" }
        val resultProcessor = ResultProcessor(spark, outputPath)

        logger.info { "Watching for queued scenarios" }

        while (true) {
            val scenario = manager.findNext()

            if (scenario == null) {
                delay(POLL_INTERVAL)
                continue
            }

            val id = scenario.getString("_id")

            logger.info { "Found queued scenario $id: attempting to claim" }

            if (!manager.claim(id)) {
                logger.info { "Failed to claim scenario" }
                continue
            }

            coroutineScope {
                // Launch heartbeat process
                val heartbeat = launch {
                    while (true) {
                        delay(HEARTBEAT_INTERVAL)
                        manager.heartbeat(id)
                    }
                }

                try {
                    val portfolio = portfolios.find(Filters.eq("_id", scenario.getString("portfolioId"))).first()!!
                    runScenario(portfolio, scenario, topologies)

                    logger.info { "Starting result processing" }

                    val result = resultProcessor.process(id)
                    manager.finish(id, result)

                    logger.info { "Successfully finished scenario $id" }
                } catch (e: Exception) {
                    logger.warn(e) { "Scenario failed to finish" }
                    manager.fail(id)
                } finally {
                    heartbeat.cancel()
                }
            }
        }
    }
}

/**
 * Main entry point of the runner.
 */
fun main(args: Array<String>) = RunnerCli().main(args)