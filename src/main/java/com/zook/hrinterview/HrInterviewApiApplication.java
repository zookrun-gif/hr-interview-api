package com.zook.hrinterview;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.zook.hrinterview.**.mapper")
@SpringBootApplication
public class HrInterviewApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(HrInterviewApiApplication.class, args);
    }
}
