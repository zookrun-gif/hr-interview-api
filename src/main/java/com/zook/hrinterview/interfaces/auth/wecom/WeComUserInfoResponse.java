package com.zook.hrinterview.interfaces.auth.wecom;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class WeComUserInfoResponse {

    @JsonProperty("errcode")
    private Integer errCode;

    @JsonProperty("errmsg")
    private String errMsg;

    @JsonAlias({"UserId", "userid"})
    @JsonProperty("userid")
    private String userId;

    @JsonProperty("user_ticket")
    private String userTicket;

    @JsonAlias({"OpenId", "openid"})
    @JsonProperty("OpenId")
    private String openId;
}
