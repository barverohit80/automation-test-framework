package com.automation.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TokenResponse {

    @JsonProperty("token")
    private String token;

    @JsonProperty("expires")
    private String expires;

    @JsonProperty("status")
    private String status;

    @JsonProperty("result")
    private String result;
}
