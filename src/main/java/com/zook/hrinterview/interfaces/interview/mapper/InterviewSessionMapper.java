package com.zook.hrinterview.interfaces.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zook.hrinterview.interfaces.interview.entity.InterviewSession;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InterviewSessionMapper extends BaseMapper<InterviewSession> {
}
