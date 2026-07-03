package com.zook.hrinterview.interfaces.auth.wecom;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class WeComTokenResponse {

    @JsonProperty("errcode")
    private Integer errCode;

    @JsonProperty("errmsg")
    private String errMsg;

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("expires_in")
    private Long expiresIn;
}
