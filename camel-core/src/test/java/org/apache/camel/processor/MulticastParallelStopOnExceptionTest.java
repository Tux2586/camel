/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.processor;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version $Revision$
 */
public class MulticastParallelStopOnExceptionTest extends ContextTestSupport {

    public void testMulticastParallelStopOnExceptionOk() throws Exception {
        getMockEndpoint("mock:foo").expectedBodiesReceived("Hello");
        getMockEndpoint("mock:bar").expectedBodiesReceived("Hello");
        getMockEndpoint("mock:baz").expectedBodiesReceived("Hello");
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello");

        template.sendBody("direct:start", "Hello");

        assertMockEndpointsSatisfied();
    }

    public void testMulticastParalllelStopOnExceptionStop() throws Exception {
        getMockEndpoint("mock:foo").expectedBodiesReceived("Kaboom");
        getMockEndpoint("mock:bar").expectedMessageCount(0);
        // we do stop so we should NOT continue and thus baz do not receive any message
        getMockEndpoint("mock:baz").expectedMessageCount(0);
        getMockEndpoint("mock:result").expectedMessageCount(0);

        try {
            template.sendBody("direct:start", "Kaboom");
            fail("Should thrown an exception");
        } catch (CamelExecutionException e) {
            ExecutionException ee = assertIsInstanceOf(ExecutionException.class, e.getCause());
            CamelExchangeException cause = assertIsInstanceOf(CamelExchangeException.class, ee.getCause());
            assertTrue(cause.getMessage().startsWith("Parallel processing failed for number "));
            assertTrue(cause.getMessage().endsWith("on the exchange: Exchange[Message: Kaboom]"));
            assertEquals("Forced", cause.getCause().getMessage());
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // use a pool with 2 concurrent tasks so we cannot run to fast
                ExecutorService service = Executors.newFixedThreadPool(2);

                from("direct:start")
                    .multicast()
                        .parallelProcessing().stopOnException().executorService(service).to("direct:foo", "direct:bar", "direct:baz")
                    .end()
                    .to("mock:result");

                // need a little delay to slow these okays down so we better can test stop when parallel
                from("direct:foo").delay(1000).to("mock:foo");

                from("direct:bar")
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                String body = exchange.getIn().getBody(String.class);
                                if ("Kaboom".equals(body)) {
                                    throw new IllegalArgumentException("Forced");
                                }
                            }
                        }).to("mock:bar");

                // need a little delay to slow these okays down so we better can test stop when parallel
                from("direct:baz").delay(1000).to("mock:baz");
            }
        };
    }
}