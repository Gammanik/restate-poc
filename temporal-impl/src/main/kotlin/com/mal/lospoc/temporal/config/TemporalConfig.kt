package com.mal.lospoc.temporal.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.mal.lospoc.temporal.workflow.CreditCheckWorkflowImpl
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowClientOptions
import io.temporal.common.converter.DefaultDataConverter
import io.temporal.common.converter.JacksonJsonPayloadConverter
import io.temporal.serviceclient.WorkflowServiceStubs
import io.temporal.serviceclient.WorkflowServiceStubsOptions
import io.temporal.worker.Worker
import io.temporal.worker.WorkerFactory
import io.temporal.worker.WorkerOptions
import jakarta.annotation.PreDestroy
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener

@Configuration
class TemporalConfig(
    @Value("\${temporal.server-url}") private val serverUrl: String,
    @Value("\${temporal.task-queue}") private val taskQueue: String,
    @Value("\${temporal.httpbin-url}") private val httpbinUrl: String,
    @Value("\${temporal.los-url}") private val losUrl: String
) {

    private lateinit var service: WorkflowServiceStubs
    private lateinit var factory: WorkerFactory

    @Bean
    fun workflowServiceStubs(): WorkflowServiceStubs {
        service = WorkflowServiceStubs.newLocalServiceStubs()
        return service
    }

    @Bean
    fun workflowClient(service: WorkflowServiceStubs): WorkflowClient {
        val objectMapper = ObjectMapper()
            .registerModule(JavaTimeModule())
            .registerKotlinModule()

        val dataConverter = DefaultDataConverter.newDefaultInstance()
            .withPayloadConverterOverrides(JacksonJsonPayloadConverter(objectMapper))

        val options = WorkflowClientOptions.newBuilder()
            .setDataConverter(dataConverter)
            .build()

        return WorkflowClient.newInstance(service, options)
    }

    @Bean
    fun workerFactory(client: WorkflowClient): WorkerFactory {
        factory = WorkerFactory.newInstance(client)
        return factory
    }

    @EventListener(ApplicationReadyEvent::class)
    fun startWorker() {
        val workerOptions = WorkerOptions.newBuilder()
            .setMaxConcurrentWorkflowTaskExecutionSize(500)
            .setMaxConcurrentActivityExecutionSize(500)
            .build()

        val worker: Worker = factory.newWorker(taskQueue, workerOptions)
        worker.registerWorkflowImplementationTypes(CreditCheckWorkflowImpl::class.java)
        worker.registerActivitiesImplementations(CreditCheckWorkflowImpl.ActivitiesImpl(httpbinUrl, losUrl))

        factory.start()
        println("Temporal worker started on queue: $taskQueue")
        println("Worker config: maxConcurrentWorkflowTasks=500, maxConcurrentActivityTasks=500")
    }

    @PreDestroy
    fun stopWorker() {
        factory.shutdown()
        service.shutdown()
    }
}
