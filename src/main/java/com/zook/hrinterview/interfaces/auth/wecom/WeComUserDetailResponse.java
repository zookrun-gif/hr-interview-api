package com.zook.hrinterview.interfaces.auth.wecom;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class WeComUserDetailResponse {

    @JsonProperty("errcode")
    private Integer errCode;

    @JsonProperty("errmsg")
    private String errMsg;

    @JsonAlias({"UserId", "userid"})
    @JsonProperty("userid")
    private String userId;

    private String name;

    private String mobile;

    private String email;

    private String avatar;

    private String gender;

    private String status;
}
