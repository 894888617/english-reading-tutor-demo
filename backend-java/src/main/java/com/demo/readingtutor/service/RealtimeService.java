package com.demo.readingtutor.service;

import com.demo.readingtutor.config.RealtimeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class RealtimeService {
    private static final Logger log = LoggerFactory.getLogger(RealtimeService.class);
    private static final MediaType APPLICATION_SDP = MediaType.parseMediaType("application/sdp");

    private final RealtimeProperties properties;
    private final RestClient restClient;

    public RealtimeService(RealtimeProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    public String exchangeSdp(String offerSdp) {
        if (!properties.hasApiKey()) {
            throw new ResponseStatusException(BAD_REQUEST, "DASHSCOPE_API_KEY is not configured. Please set the environment variable before starting the backend.");
        }
        if (!properties.hasWebrtcEndpoint()) {
            throw new ResponseStatusException(BAD_REQUEST, "ALIYUN_WEBRTC_ENDPOINT is not configured. Please set the WebRTC endpoint host without https://.");
        }
        if (offerSdp == null || offerSdp.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Offer SDP must not be empty.");
        }

        String model = properties.getRealtimeModel();
        String targetUrl = UriComponentsBuilder
                .fromHttpUrl("https://" + properties.normalizedEndpointHost())
                .path("/api/v1/webrtc/realtime")
                .queryParam("model", model)
                .build()
                .toUriString();

        log.info("Forwarding SDP offer to DashScope WebRTC. offerLength={}, model={}", offerSdp.length(), model);

        try {
            ResponseEntity<String> response = restClient.post()
                    .uri(targetUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                    .contentType(APPLICATION_SDP)
                    .accept(APPLICATION_SDP)
                    .body(offerSdp)
                    .retrieve()
                    .toEntity(String.class);

            String answerSdp = response.getBody();
            log.info("DashScope WebRTC SDP response status={}, answerLength={}",
                    response.getStatusCode().value(),
                    answerSdp == null ? 0 : answerSdp.length());

            if (answerSdp == null || answerSdp.isBlank()) {
                throw new ResponseStatusException(BAD_GATEWAY, "DashScope returned an empty Answer SDP.");
            }
            return answerSdp;
        } catch (RestClientResponseException ex) {
            log.warn("DashScope WebRTC request failed. status={}, bodyLength={}",
                    ex.getStatusCode().value(),
                    ex.getResponseBodyAsString().length());
            throw new ResponseStatusException(BAD_GATEWAY,
                    "DashScope WebRTC SDP exchange failed with status " + ex.getStatusCode().value() + ". Check ALIYUN_WEBRTC_ENDPOINT, model, and API key permissions.", ex);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("DashScope WebRTC request failed before receiving a valid SDP answer: {}", ex.getMessage());
            throw new ResponseStatusException(BAD_GATEWAY,
                    "DashScope WebRTC SDP exchange failed. Check network access, endpoint, model, and API key permissions.", ex);
        }
    }
}
