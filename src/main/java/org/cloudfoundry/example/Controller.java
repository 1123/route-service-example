/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.cloudfoundry.example;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestOperations;

import java.net.URI;


@RestController
@Slf4j
final class Controller {

    static final String FORWARDED_URL = "X-CF-Forwarded-Url";

    static final String PROXY_METADATA = "X-CF-Proxy-Metadata";

    static final String PROXY_SIGNATURE = "X-CF-Proxy-Signature";

    private static final URI CANARY_URL = URI.create(System.getenv("CANARY_URL"));

    private static final float CANARY_PERCENTAGE = Float.parseFloat(System.getenv("CANARY_PERCENTAGE"));

    private final RestOperations restOperations;

    @Autowired
    Controller(RestOperations restOperations) {
        this.restOperations = restOperations;
    }

    @RequestMapping(headers = {FORWARDED_URL, PROXY_METADATA, PROXY_SIGNATURE})
    ResponseEntity<?> service(RequestEntity<byte[]> incoming) {
        log.info("Incoming Request: {}", incoming);

        RequestEntity<?> outgoing = getOutgoingRequest(incoming);
        log.info("Outgoing Request: {}", outgoing);

        return this.restOperations.exchange(outgoing, byte[].class);
    }

    private static RequestEntity<?> getOutgoingRequest(RequestEntity<?> incoming) {
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(incoming.getHeaders());

        URI uri = headers.remove(FORWARDED_URL).stream()
            .findFirst()
            .map(URI::create)
            .orElseThrow(() -> new IllegalStateException(String.format("No %s header present", FORWARDED_URL)));
        log.info("Forwarding to: " + uri.toString());
        log.info("Diverting {} of the traffic to canary. ", CANARY_PERCENTAGE);

        if (Math.random() < CANARY_PERCENTAGE) {
            log.info("Diverting to canary");
            return new RequestEntity<>(incoming.getBody(), headers, incoming.getMethod(), CANARY_URL);
        } else {
            log.info("Not diverting to canary");
            return new RequestEntity<>(incoming.getBody(), headers, incoming.getMethod(), uri);
        }
    }


}
