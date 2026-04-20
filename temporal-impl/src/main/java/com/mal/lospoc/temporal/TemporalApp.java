package com.mal.lospoc.temporal;

import com.mal.lospoc.temporal.workflow.CreditCheckWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;

public class TemporalApp {
    public static final String TASK_QUEUE = "CREDIT_CHECK_QUEUE";

    public static void main(String[] args) {
        WorkflowServiceStubs service = WorkflowServiceStubs.newLocalServiceStubs();
        WorkflowClient client = WorkflowClient.newInstance(service);
        WorkerFactory factory = WorkerFactory.newInstance(client);

        Worker worker = factory.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(CreditCheckWorkflowImpl.class);
        worker.registerActivitiesImplementations(new CreditCheckWorkflowImpl.ActivitiesImpl());

        factory.start();
        System.out.println("[Temporal] Worker started on queue: " + TASK_QUEUE);
    }
}
