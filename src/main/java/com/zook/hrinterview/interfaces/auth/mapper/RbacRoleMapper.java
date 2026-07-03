package com.zook.hrinterview.interfaces.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zook.hrinterview.interfaces.auth.entity.RbacRole;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RbacRoleMapper extends BaseMapper<RbacRole> {
}
