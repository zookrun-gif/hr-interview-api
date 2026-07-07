package com.zook.hrinterview.interfaces.job.service.impl;

import com.zook.hrinterview.interfaces.job.service.JobService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zook.hrinterview.common.BusinessException;
import com.zook.hrinterview.common.ErrorCode;
import com.zook.hrinterview.common.IdRequest;
import com.zook.hrinterview.common.PageResponse;
import com.zook.hrinterview.interfaces.auth.security.LoginUserContext;
import com.zook.hrinterview.interfaces.candidate.entity.Candidate;
import com.zook.hrinterview.interfaces.candidate.mapper.CandidateMapper;
import com.zook.hrinterview.interfaces.interview.entity.InterviewSession;
import com.zook.hrinterview.interfaces.interview.mapper.InterviewSessionMapper;
import com.zook.hrinterview.interfaces.job.dto.EvaluationDimensionRequest;
import com.zook.hrinterview.interfaces.job.dto.EvaluationDimensionResponse;
import com.zook.hrinterview.interfaces.job.dto.JobCreateRequest;
import com.zook.hrinterview.interfaces.job.dto.JobDetailResponse;
import com.zook.hrinterview.interfaces.job.dto.JobListItemResponse;
import com.zook.hrinterview.interfaces.job.dto.JobListRequest;
import com.zook.hrinterview.interfaces.job.dto.JobUpdateRequest;
import com.zook.hrinterview.interfaces.job.entity.EvaluationDimension;
import com.zook.hrinterview.interfaces.job.entity.JobPosition;
import com.zook.hrinterview.interfaces.job.mapper.EvaluationDimensionMapper;
import com.zook.hrinterview.interfaces.job.mapper.JobPositionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class JobServiceImpl extends ServiceImpl<JobPositionMapper, JobPosition> implements JobService {

    private static final String STATUS_ENABLED = "ENABLED";
    private static final String STATUS_DISABLED = "DISABLED";

    @Resource
    private EvaluationDimensionMapper evaluationDimensionMapper;

    @Resource
    private CandidateMapper candidateMapper;

    @Resource
    private InterviewSessionMapper interviewSessionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobDetailResponse create(JobCreateRequest request) {
        JobPosition job = new JobPosition();
        job.setTitle(request.getTitle());
        job.setJd(request.getJd());
        job.setRequirements(request.getRequirements());
        job.setStatus(STATUS_ENABLED);
        job.setCreatedBy(LoginUserContext.getUserId());
        save(job);
        saveDimensions(job.getId(), request.getDimensions());
        return detailById(job.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobDetailResponse update(JobUpdateRequest request) {
        JobPosition job = getById(request.getId());
        if (job == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "岗位不存在");
        }
        job.setTitle(request.getTitle());
        job.setJd(request.getJd());
        job.setRequirements(request.getRequirements());
        if (!STATUS_ENABLED.equals(request.getStatus()) && !STATUS_DISABLED.equals(request.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "岗位状态不合法");
        }
        job.setStatus(request.getStatus());
        updateById(job);

        evaluationDimensionMapper.delete(Wrappers.lambdaQuery(EvaluationDimension.class)
                .eq(EvaluationDimension::getJobId, job.getId()));
        saveDimensions(job.getId(), request.getDimensions());
        return detailById(job.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean delete(IdRequest request) {
        JobPosition job = getById(request.getId());
        if (job == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "岗位不存在");
        }
        Long candidateCount = candidateMapper.selectCount(
                Wrappers.lambdaQuery(Candidate.class).eq(Candidate::getJobId, request.getId()));
        if (candidateCount > 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "该岗位已绑定候选人，不能删除");
        }
        Long interviewCount = interviewSessionMapper.selectCount(
                Wrappers.lambdaQuery(InterviewSession.class).eq(InterviewSession::getJobId, request.getId()));
        if (interviewCount > 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "该岗位已有面试记录，不能删除");
        }
        evaluationDimensionMapper.delete(Wrappers.lambdaQuery(EvaluationDimension.class)
                .eq(EvaluationDimension::getJobId, request.getId()));
        removeById(request.getId());
        return Boolean.TRUE;
    }

    @Override
    public JobDetailResponse detail(IdRequest request) {
        return detailById(request.getId());
    }

    @Override
    public PageResponse<JobListItemResponse> list(JobListRequest request) {
        LambdaQueryWrapper<JobPosition> wrapper = Wrappers.lambdaQuery(JobPosition.class)
                .like(StringUtils.isNotBlank(request.getKeyword()), JobPosition::getTitle, request.getKeyword())
                .eq(StringUtils.isNotBlank(request.getStatus()), JobPosition::getStatus, request.getStatus())
                .orderByDesc(JobPosition::getCreatedAt);

        Page<JobPosition> page = page(Page.of(request.getPageNo(), request.getPageSize()), wrapper);
        List<JobListItemResponse> records = page.getRecords().stream()
                .map(this::toListItemResponse)
                .collect(Collectors.toList());
        return new PageResponse<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    private void saveDimensions(Long jobId, List<EvaluationDimensionRequest> dimensions) {
        List<EvaluationDimension> entities = buildDimensionEntities(jobId, dimensions);
        entities.forEach(evaluationDimensionMapper::insert);
    }

    private List<EvaluationDimension> buildDimensionEntities(Long jobId, List<EvaluationDimensionRequest> dimensions) {
        List<EvaluationDimension> entities = new ArrayList<>();
        if (dimensions == null || dimensions.isEmpty()) {
            return entities;
        }
        for (EvaluationDimensionRequest request : dimensions) {
            if (request == null || StringUtils.isBlank(request.getName()) || request.getWeight() == null) {
                continue;
            }
            EvaluationDimension dimension = new EvaluationDimension();
            dimension.setJobId(jobId);
            dimension.setName(request.getName());
            dimension.setDescription(request.getDescription());
            dimension.setWeight(request.getWeight());
            entities.add(dimension);
        }
        return entities;
    }

    private JobDetailResponse detailById(Long id) {
        JobPosition job = getById(id);
        if (job == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "岗位不存在");
        }
        JobDetailResponse response = new JobDetailResponse();
        response.setId(job.getId());
        response.setTitle(job.getTitle());
        response.setJd(job.getJd());
        response.setRequirements(job.getRequirements());
        response.setStatus(job.getStatus());
        response.setCreatedAt(job.getCreatedAt());
        response.setDimensions(listDimensionResponses(job.getId()));
        return response;
    }

    private List<EvaluationDimensionResponse> listDimensionResponses(Long jobId) {
        List<EvaluationDimension> dimensions = evaluationDimensionMapper.selectList(
                Wrappers.lambdaQuery(EvaluationDimension.class)
                        .eq(EvaluationDimension::getJobId, jobId)
                        .orderByAsc(EvaluationDimension::getId));
        if (dimensions == null) {
            return Collections.emptyList();
        }
        return dimensions.stream().map(this::toDimensionResponse).collect(Collectors.toList());
    }

    private EvaluationDimensionResponse toDimensionResponse(EvaluationDimension dimension) {
        EvaluationDimensionResponse response = new EvaluationDimensionResponse();
        response.setId(dimension.getId());
        response.setName(dimension.getName());
        response.setDescription(dimension.getDescription());
        response.setWeight(dimension.getWeight());
        return response;
    }

    private JobListItemResponse toListItemResponse(JobPosition job) {
        JobListItemResponse response = new JobListItemResponse();
        response.setId(job.getId());
        response.setTitle(job.getTitle());
        response.setJdSummary(summary(job.getJd()));
        response.setRequirementsSummary(summary(job.getRequirements()));
        response.setStatus(job.getStatus());
        response.setCreatedAt(job.getCreatedAt());
        return response;
    }

    private String summary(String value) {
        if (StringUtils.isBlank(value)) {
            return "-";
        }
        String text = value.trim();
        if (text.length() <= 5) {
            return text;
        }
        return text.substring(0, 5) + "...";
    }
}
