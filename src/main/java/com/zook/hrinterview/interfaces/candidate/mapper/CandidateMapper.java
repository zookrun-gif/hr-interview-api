package com.zook.hrinterview.interfaces.candidate.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zook.hrinterview.interfaces.candidate.entity.Candidate;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CandidateMapper extends BaseMapper<Candidate> {
}
