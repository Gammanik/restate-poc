package com.mal.lospoc.temporal;

import com.mal.lospoc.temporal.activities.CreditCheckActivities;
import com.mal.lospoc.temporal.activities.CreditCheckActivitiesImpl;
import com.mal.lospoc.temporal.workflow.CreditCheckWorkflow;
import com.mal.lospoc.temporal.workflow.CreditCheckWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;

public class TemporalApp {
    public static final String TASK_QUEUE = "CREDIT_CHECK_QUEUE";

    public static void main(String[] args) {
        String temporalUrl = System.getenv().getOrDefault("TEMPORAL_URL", "localhost:7233");
        String httpbinUrl = System.getenv().getOrDefault("HTTPBIN_URL", "http://localhost:8090");
        String losUrl = System.getenv().getOrDefault("LOS_URL", "http://localhost:8000");

        WorkflowServiceStubs service = WorkflowServiceStubs.newLocalServiceStubs();
        WorkflowClient client = WorkflowClient.newInstance(service);
        WorkerFactory factory = WorkerFactory.newInstance(client);

        Worker worker = factory.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(CreditCheckWorkflowImpl.class);
        worker.registerActivitiesImplementations(new CreditCheckActivitiesImpl(httpbinUrl, losUrl));

        factory.start();

        System.out.println("Temporal worker started on task queue: " + TASK_QUEUE);
    }
}
