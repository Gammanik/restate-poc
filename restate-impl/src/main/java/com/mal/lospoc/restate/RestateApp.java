package com.mal.lospoc.restate;

import com.mal.lospoc.restate.workflow.CreditCheckWorkflow;
import dev.restate.sdk.http.vertx.RestateHttpEndpointBuilder;

public class RestateApp {
    public static void main(String[] args) {
        RestateHttpEndpointBuilder.builder()
            .bind(new CreditCheckWorkflow())
            .buildAndListen(9080);
    }
}
