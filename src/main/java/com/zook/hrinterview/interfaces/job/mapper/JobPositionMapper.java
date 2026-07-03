package com.zook.hrinterview.interfaces.job.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zook.hrinterview.interfaces.job.entity.JobPosition;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface JobPositionMapper extends BaseMapper<JobPosition> {
}
