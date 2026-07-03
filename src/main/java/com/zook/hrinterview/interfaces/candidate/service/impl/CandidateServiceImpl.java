package com.zook.hrinterview.interfaces.candidate.service.impl;

import com.zook.hrinterview.interfaces.candidate.service.CandidateService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zook.hrinterview.interfaces.auth.security.LoginUserContext;
import com.zook.hrinterview.interfaces.candidate.dto.CandidateCreateRequest;
import com.zook.hrinterview.interfaces.candidate.dto.CandidateDetailResponse;
import com.zook.hrinterview.interfaces.candidate.dto.CandidateListRequest;
import com.zook.hrinterview.interfaces.candidate.dto.CandidateUpdateRequest;
import com.zook.hrinterview.interfaces.candidate.dto.ResumeParseResponse;
import com.zook.hrinterview.interfaces.candidate.entity.Candidate;
import com.zook.hrinterview.interfaces.candidate.mapper.CandidateMapper;
import com.zook.hrinterview.interfaces.candidate.service.ResumeAiParseService;
import com.zook.hrinterview.common.BusinessException;
import com.zook.hrinterview.common.ErrorCode;
import com.zook.hrinterview.common.IdRequest;
import com.zook.hrinterview.common.PageResponse;
import com.zook.hrinterview.interfaces.job.entity.JobPosition;
import com.zook.hrinterview.interfaces.job.mapper.JobPositionMapper;
import com.zook.hrinterview.interfaces.interview.entity.InterviewSession;
import com.zook.hrinterview.interfaces.interview.mapper.InterviewSessionMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class CandidateServiceImpl extends ServiceImpl<CandidateMapper, Candidate> implements CandidateService {

    private static final String STATUS_ENABLED = "ENABLED";

    @Resource
    private JobPositionMapper jobPositionMapper;

    @Resource
    private InterviewSessionMapper interviewSessionMapper;

    @Resource
    private ResumeAiParseService resumeAiParseService;

    @Override
    public CandidateDetailResponse create(CandidateCreateRequest request) {
        mustGetEnabledJob(request.getJobId());
        Candidate candidate = new Candidate();
        candidate.setJobId(request.getJobId());
        candidate.setName(request.getName());
        candidate.setGender(request.getGender());
        candidate.setAge(request.getAge());
        candidate.setPhone(request.getPhone());
        candidate.setEmail(request.getEmail());
        candidate.setResumeText(request.getResumeText());
        candidate.setCreatedBy(LoginUserContext.getUserId());
        save(candidate);
        return detailById(candidate.getId());
    }

    @Override
    public CandidateDetailResponse update(CandidateUpdateRequest request) {
        Candidate candidate = getById(request.getId());
        if (candidate == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "候选人不存在");
        }
        JobPosition job = mustGetJob(request.getJobId());
        if (!request.getJobId().equals(candidate.getJobId())) {
            if (hasInterviewRecord(candidate.getId())) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "候选人已有面试记录，不能修改绑定岗位");
            }
            validateJobEnabled(job);
        }
        candidate.setJobId(request.getJobId());
        candidate.setName(request.getName());
        candidate.setGender(request.getGender());
        candidate.setAge(request.getAge());
        candidate.setPhone(request.getPhone());
        candidate.setEmail(request.getEmail());
        candidate.setResumeText(request.getResumeText());
        updateById(candidate);
        return detailById(candidate.getId());
    }

    @Override
    public Boolean delete(IdRequest request) {
        Candidate candidate = getById(request.getId());
        if (candidate == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "候选人不存在");
        }
        if (hasInterviewRecord(candidate.getId())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "候选人已有面试记录，不能删除");
        }
        removeById(request.getId());
        return Boolean.TRUE;
    }

    @Override
    public CandidateDetailResponse detail(IdRequest request) {
        return detailById(request.getId());
    }

    @Override
    public PageResponse<CandidateDetailResponse> list(CandidateListRequest request) {
        LambdaQueryWrapper<Candidate> wrapper = Wrappers.lambdaQuery(Candidate.class)
                .eq(request.getJobId() != null, Candidate::getJobId, request.getJobId())
                .and(StringUtils.isNotBlank(request.getKeyword()), item -> item
                        .like(Candidate::getName, request.getKeyword())
                        .or()
                        .like(Candidate::getPhone, request.getKeyword())
                        .or()
                        .like(Candidate::getEmail, request.getKeyword()))
                .orderByDesc(Candidate::getCreatedAt);
        Page<Candidate> page = page(Page.of(request.getPageNo(), request.getPageSize()), wrapper);
        List<CandidateDetailResponse> records = page.getRecords().stream()
                .map(this::toDetailResponse)
                .collect(Collectors.toList());
        return new PageResponse<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public ResumeParseResponse parseResumePdf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请上传 PDF 简历文件");
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.toLowerCase().endsWith(".pdf")) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "仅支持 PDF 文件");
        }

        ResumeTextExtraction extraction;
        try {
            byte[] pdfBytes = file.getBytes();
            extraction = extractResumeTexts(pdfBytes);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "PDF 简历解析失败，请检查文件内容");
        }

        String plainText = extraction.displayText();
        if (StringUtils.isBlank(plainText)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "PDF 未解析到文本内容");
        }

        ResumeParseResponse response = new ResumeParseResponse();
        response.setFileName(fileName);
        response.setPlainText(plainText);
        response.setHtmlContent(toResumeHtml(plainText));
        String profileText = StringUtils.isBlank(extraction.profileText()) ? plainText : extraction.profileText();
        if (!resumeAiParseService.fillProfile(response, profileText)) {
            response.setAiParsed(Boolean.FALSE);
            fillResumeProfile(response, profileText, fileName);
        }
        return response;
    }

    private ResumeTextExtraction extractResumeTexts(byte[] pdfBytes) throws IOException {
        List<String> candidates = new ArrayList<>();
        String pdfBoxText = extractTextByPdfBox(pdfBytes);
        if (StringUtils.isNotBlank(pdfBoxText)) {
            candidates.add(pdfBoxText);
        }
        String popplerLayoutText = extractTextByPoppler(pdfBytes, true);
        if (StringUtils.isNotBlank(popplerLayoutText)) {
            candidates.add(popplerLayoutText);
        }
        String popplerRawText = extractTextByPoppler(pdfBytes, false);
        if (StringUtils.isNotBlank(popplerRawText)) {
            candidates.add(popplerRawText);
        }

        List<String> normalizedCandidates = candidates.stream()
                .map(this::normalizeResumeText)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());
        String displayText = normalizedCandidates.stream()
                .max(Comparator.comparingInt(this::resumeTextQualityScore))
                .orElse("");
        String profileText = String.join("\n\n", candidates) + "\n\n" + String.join("\n\n", normalizedCandidates);
        return new ResumeTextExtraction(displayText, profileText);
    }

    private String extractBestResumeText(byte[] pdfBytes) throws IOException {
        return extractResumeTexts(pdfBytes).displayText();
    }

    private String extractTextByPdfBox(byte[] pdfBytes) throws IOException {
        try (InputStream inputStream = new ByteArrayInputStream(pdfBytes); PDDocument document = PDDocument.load(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String keywords = document.getDocumentInformation() == null ? "" : document.getDocumentInformation().getKeywords();
            return removePdfWatermarkNoise(stripper.getText(document), keywords);
        }
    }

    private String extractTextByPoppler(byte[] pdfBytes, boolean keepLayout) {
        Path input = null;
        Path output = null;
        try {
            input = Files.createTempFile("resume-", ".pdf");
            output = Files.createTempFile("resume-", ".txt");
            Files.write(input, pdfBytes);
            List<String> command = new ArrayList<>();
            command.add("pdftotext");
            command.add(keepLayout ? "-layout" : "-raw");
            command.add("-nopgbrk");
            command.add("-enc");
            command.add("UTF-8");
            command.add(input.toAbsolutePath().toString());
            command.add(output.toAbsolutePath().toString());
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            boolean finished = process.waitFor(Duration.ofSeconds(8).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "";
            }
            if (process.exitValue() != 0 || !Files.exists(output)) {
                return "";
            }
            return Files.readString(output, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return "";
        } finally {
            deleteTempFile(input);
            deleteTempFile(output);
        }
    }

    private void deleteTempFile(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private CandidateDetailResponse detailById(Long id) {
        Candidate candidate = getById(id);
        if (candidate == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "候选人不存在");
        }
        return toDetailResponse(candidate);
    }

    private CandidateDetailResponse toDetailResponse(Candidate candidate) {
        CandidateDetailResponse response = new CandidateDetailResponse();
        response.setId(candidate.getId());
        response.setJobId(candidate.getJobId());
        JobPosition job = candidate.getJobId() == null ? null : jobPositionMapper.selectById(candidate.getJobId());
        response.setJobTitle(job == null ? "-" : job.getTitle());
        response.setName(candidate.getName());
        response.setGender(candidate.getGender());
        response.setAge(candidate.getAge());
        response.setPhone(candidate.getPhone());
        response.setEmail(candidate.getEmail());
        response.setResumeText(candidate.getResumeText());
        response.setResumeFileUrl(candidate.getResumeFileUrl());
        response.setCreatedAt(candidate.getCreatedAt());
        return response;
    }

    private JobPosition mustGetJob(Long jobId) {
        JobPosition job = jobPositionMapper.selectById(jobId);
        if (job == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "绑定岗位不存在");
        }
        return job;
    }

    private JobPosition mustGetEnabledJob(Long jobId) {
        JobPosition job = mustGetJob(jobId);
        validateJobEnabled(job);
        return job;
    }

    private void validateJobEnabled(JobPosition job) {
        if (!STATUS_ENABLED.equals(job.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "绑定岗位已停用，不能选择");
        }
    }

    private boolean hasInterviewRecord(Long candidateId) {
        Long count = interviewSessionMapper.selectCount(
                Wrappers.lambdaQuery(InterviewSession.class).eq(InterviewSession::getCandidateId, candidateId));
        return count > 0;
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n")
                .replace("\r", "\n")
                .replaceAll("[\\t\\x0B\\f]+", " ")
                .replaceAll(" *\\n *", "\n")
                .trim();
    }

    private String normalizeResumeText(String text) {
        String normalized = normalizeText(text)
                .replace('\u00A0', ' ')
                .replaceAll("[\\u200B-\\u200F\\uFEFF]", "")
                .replaceAll("(?i)\\bpage\\s*\\d+\\b", "")
                .replaceAll("第\\s*\\d+\\s*页\\s*/\\s*共\\s*\\d+\\s*页", "")
                .replaceAll("(?<=[A-Za-z0-9._%+-])\\s+(?=[A-Za-z0-9._%+-]*@)", "")
                .replaceAll("(?<=@)\\s+(?=[A-Za-z0-9.-])", "")
                .replaceAll("(?<=[A-Za-z0-9.-])\\s+(?=\\.[A-Za-z]{2,})", "")
                .replaceAll("(?<=1[3-9]\\d)\\s+(?=\\d)", "")
                .replaceAll("(?<=\\d)\\s+(?=\\d{2,})", "")
                .replaceAll("(?<![A-Za-z0-9@._%+-])[A-Za-z0-9_-]{16,}?(?=\\d+[.、．])", "")
                .replaceAll("(?<![A-Za-z0-9@._%+-])[A-Za-z0-9_-]{16,}(?=[\\u4e00-\\u9fa5])", "")
                .replaceAll("(?m)^\\s*(?:RESUME|PERSON|ABOUT\\s*ME|RES)\\s*$", "")
                .replaceAll("(?m)^\\s*[A-Za-z0-9_-]{1,4}\\s*$", "")
                .replaceAll("\\n{3,}", "\n\n")
                .replaceAll(" {2,}", " ")
                .trim();
        return removeObviousNoiseLines(normalized);
    }

    private String removeObviousNoiseLines(String text) {
        StringBuilder builder = new StringBuilder();
        for (String line : text.split("\n")) {
            String value = line.trim();
            if (StringUtils.isBlank(value)) {
                builder.append('\n');
                continue;
            }
            if (isLikelyNoiseLine(value)) {
                continue;
            }
            builder.append(cleanResumeContentLine(value)).append('\n');
        }
        return builder.toString().replaceAll("\\n{3,}", "\n\n").trim();
    }

    private boolean isLikelyNoiseLine(String line) {
        String compact = compactAscii(line);
        boolean hasChinese = line.matches(".*[\\u4e00-\\u9fa5].*");
        if (!hasChinese && isStandaloneContactOrDateLine(line)) {
            return false;
        }
        if (compact.length() >= 8 && compact.matches("[A-Za-z0-9_-]+") && !hasChinese) {
            return true;
        }
        if (!hasChinese && compact.length() >= 3 && compact.length() <= 16 && hasLetterAndDigit(compact)) {
            return true;
        }
        if (!hasChinese && line.matches("(?i)^\\s*(resume|person|res|about\\s*me)\\s*$")) {
            return true;
        }
        long chineseCount = line.chars().filter(ch -> ch >= 0x4e00 && ch <= 0x9fa5).count();
        long asciiLetterDigitCount = line.chars().filter(ch -> Character.isLetterOrDigit(ch) && ch < 128).count();
        return chineseCount > 0 && asciiLetterDigitCount > chineseCount * 2 && compact.length() >= 12;
    }

    private boolean isStandaloneContactOrDateLine(String line) {
        String value = line.trim();
        return value.matches(".*[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}.*")
                || value.replaceAll("\\s+", "").matches(".*1[3-9]\\d{9}.*")
                || value.matches("^\\d{4}\\s*[-—~至]+\\s*\\d{4}\\s*$")
                || value.matches("^\\d{4}[.\\-/年]\\d{1,2}.*");
    }

    private String cleanResumeContentLine(String line) {
        if (!line.matches(".*[\\u4e00-\\u9fa5].*")) {
            return line;
        }
        String cleaned = line
                .replaceAll("(?<![A-Za-z0-9@._%+-])[A-Za-z0-9_-]{16,}?(?=\\d+[.、．])", "")
                .replaceAll("(?<![A-Za-z0-9@._%+-])[A-Za-z0-9_-]{16,}(?=[\\u4e00-\\u9fa5])", "")
                .replaceAll("^\\s*[A-Za-z0-9_-]{1,3}(?=\\d{4}[.\\-/年])", "")
                .replaceAll("(^|[\\s\\d.、，；;])([A-Za-z][A-Za-z0-9_-]{1,3})(?=[\\u4e00-\\u9fa5])", "$1")
                .replaceAll("\\s+[A-Za-z0-9_-]{1,3}\\s+[A-Za-z0-9_-]{1,3}(?=[\\u4e00-\\u9fa5])", " ")
                .replaceAll("(?<=[\\u4e00-\\u9fa5，。；、：:])\\s*[A-Za-z0-9_-]{1,3}\\s*(?=[\\u4e00-\\u9fa5])", "")
                .replaceAll("(?<=[\\u4e00-\\u9fa5])\\s+(?=[\\u4e00-\\u9fa5])", "")
                .replaceAll(" {2,}", " ")
                .trim();
        return cleaned;
    }

    private int resumeTextQualityScore(String text) {
        if (StringUtils.isBlank(text)) {
            return 0;
        }
        int score = Math.min(text.length(), 8000) / 20;
        score += countPattern(text, "[\\u4e00-\\u9fa5]") * 2;
        score += countPattern(text, "1[3-9]\\d{9}") * 300;
        score += countPattern(text, "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}") * 300;
        score += countPattern(text, "(教育经历|教育背景|工作经历|项目经历|个人评价|自我评价|技能)") * 120;
        score -= countPattern(text, "(?m)^\\s*[A-Za-z0-9_-]{8,}\\s*$") * 160;
        score -= countPattern(text, "(?m)^\\s*[A-Za-z0-9_-]{1,4}\\s+[A-Za-z0-9_-]{1,4}\\s*$") * 90;
        score -= countPattern(text, "(?i)\\b(resume|person|about\\s*me)\\b") * 120;
        score -= countPattern(text, "[�□]") * 200;
        return score;
    }

    private int countPattern(String text, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private void fillResumeProfile(ResumeParseResponse response, String plainText, String fileName) {
        response.setName(extractCandidateName(plainText, fileName));
        response.setGender(extractGender(plainText));
        response.setAge(extractAge(plainText));
        response.setPhone(extractPhone(plainText));
        response.setEmail(extractEmail(plainText));
    }

    private void fillResumeProfile(ResumeParseResponse response, String plainText) {
        fillResumeProfile(response, plainText, "");
    }

    private String extractCandidateName(String plainText, String fileName) {
        String nameFromFile = extractNameFromFileName(fileName);
        if (StringUtils.isNotBlank(nameFromFile)) {
            return nameFromFile;
        }
        Matcher explicitMatcher = Pattern.compile("(?:姓名|名字)[:：\\s]*([\\u4e00-\\u9fa5]{2,5})").matcher(plainText);
        if (explicitMatcher.find()) {
            return explicitMatcher.group(1);
        }
        int checkedLines = 0;
        for (String line : plainText.split("\n")) {
            String value = line.trim();
            if (StringUtils.isBlank(value)) {
                continue;
            }
            checkedLines++;
            if (checkedLines > 12) {
                break;
            }
            if (value.matches("^[\\u4e00-\\u9fa5]{2,5}$")) {
                return value;
            }
            Matcher looseMatcher = Pattern.compile("^([\\u4e00-\\u9fa5]{2,5})\\s*(?:男|女|\\d{2}\\s*岁|电话|手机|邮箱)?").matcher(value);
            if (looseMatcher.find() && !isCommonResumeSection(looseMatcher.group(1))) {
                return looseMatcher.group(1);
            }
        }
        return "";
    }

    private String extractNameFromFileName(String fileName) {
        if (StringUtils.isBlank(fileName)) {
            return "";
        }
        String cleaned = fileName.replaceAll("\\.pdf$", "")
                .replaceAll("[【】\\[\\]()（）_\\-]+", " ");
        Matcher matcher = Pattern.compile("([\\u4e00-\\u9fa5]{2,5})\\s*(?:\\d+\\s*年|男|女|简历|$)").matcher(cleaned);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!isCommonResumeSection(name)) {
                return name;
            }
        }
        return "";
    }

    private boolean isCommonResumeSection(String value) {
        return Set.of("个人信息", "基本信息", "教育背景", "教育经历", "工作经历", "项目经历", "个人评价", "自我评价", "求职意向")
                .contains(value);
    }

    private String extractGender(String plainText) {
        Matcher matcher = Pattern.compile("(?:性别|性别\\s*)[:：\\s]*(男|女)").matcher(plainText);
        if (matcher.find()) {
            return "男".equals(matcher.group(1)) ? "MALE" : "FEMALE";
        }
        return "";
    }

    private Integer extractAge(String plainText) {
        Matcher ageMatcher = Pattern.compile("(?:年龄|年纪)[:：\\s]*(\\d{1,3})\\s*岁?").matcher(plainText);
        if (ageMatcher.find()) {
            return normalizeAge(ageMatcher.group(1));
        }

        Matcher birthMatcher = Pattern.compile("(?:出生日期|出生年月|生日)[:：\\s]*(\\d{4})[.\\-/年](\\d{1,2})").matcher(plainText);
        if (birthMatcher.find()) {
            int birthYear = Integer.parseInt(birthMatcher.group(1));
            int birthMonth = Integer.parseInt(birthMatcher.group(2));
            java.time.LocalDate now = java.time.LocalDate.now();
            int age = now.getYear() - birthYear;
            if (now.getMonthValue() < birthMonth) {
                age -= 1;
            }
            return age >= 0 && age <= 120 ? age : null;
        }
        return null;
    }

    private Integer normalizeAge(String value) {
        try {
            int age = Integer.parseInt(value);
            return age >= 0 && age <= 120 ? age : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String extractPhone(String plainText) {
        Matcher matcher = Pattern.compile("1[3-9]\\d{9}").matcher(plainText.replaceAll("\\s+", ""));
        return matcher.find() ? matcher.group() : "";
    }

    private String extractEmail(String plainText) {
        Matcher matcher = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").matcher(plainText);
        return matcher.find() ? matcher.group() : "";
    }

    private String removePdfWatermarkNoise(String text, String keywords) {
        if (StringUtils.isBlank(text) || StringUtils.isBlank(keywords)) {
            return text;
        }
        String normalizedKeywords = compactAscii(keywords);
        if (StringUtils.isBlank(normalizedKeywords)) {
            return text;
        }

        Set<String> noiseTokens = new HashSet<>();
        Set<String> keywordFragments = buildKeywordFragments(normalizedKeywords);
        List<String> contentLines = new ArrayList<>();
        for (String line : text.replace("\r\n", "\n").replace("\r", "\n").split("\n")) {
            String compactLine = compactAscii(line);
            if (isWatermarkLine(compactLine, normalizedKeywords) || isSplitWatermarkLine(line, normalizedKeywords, keywordFragments)) {
                if (compactLine.length() >= 3 && !compactLine.chars().allMatch(Character::isDigit)) {
                    noiseTokens.add(compactLine);
                }
                continue;
            }
            contentLines.add(line);
        }

        StringBuilder builder = new StringBuilder();
        for (String line : contentLines) {
            builder.append(cleanEmbeddedWatermarkTokens(line, normalizedKeywords, noiseTokens, keywordFragments)).append('\n');
        }
        String cleaned = builder.toString();
        List<String> sortedTokens = new ArrayList<>(noiseTokens);
        sortedTokens.sort(Comparator.comparingInt(String::length).reversed());
        for (String token : sortedTokens) {
            if (shouldRemoveEmbeddedWatermarkToken(token)) {
                cleaned = cleaned.replaceAll(Pattern.quote(token), "");
            }
        }
        return removeResidualWatermarkLines(cleaned, normalizedKeywords);
    }

    private String cleanEmbeddedWatermarkTokens(String line, String normalizedKeywords, Set<String> noiseTokens, Set<String> keywordFragments) {
        if (StringUtils.isBlank(line) || !line.matches(".*[\\u4e00-\\u9fa5].*")) {
            return line;
        }
        Matcher matcher = Pattern.compile("[A-Za-z0-9_]{1,3}").matcher(line);
        List<String> candidates = new ArrayList<>();
        while (matcher.find()) {
            String token = matcher.group();
            if (isShortWatermarkToken(token, normalizedKeywords, noiseTokens, keywordFragments)) {
                candidates.add(token);
            }
        }
        if (candidates.isEmpty()) {
            return line;
        }

        String cleaned = line;
        for (String token : candidates) {
            String quotedToken = Pattern.quote(token);
            if (token.length() == 1) {
                if (token.matches("[A-Z]")) {
                    cleaned = cleaned.replaceAll("(?<=[\\u4e00-\\u9fa5，。；、])\\s*" + quotedToken + "|" + quotedToken + "\\s*(?=[\\u4e00-\\u9fa5])", "");
                } else {
                    cleaned = cleaned.replaceAll("(?<=[\\u4e00-\\u9fa5])" + quotedToken + "(?=[\\u4e00-\\u9fa5])", "");
                }
            } else {
                cleaned = cleaned.replaceAll("(?<=[\\u4e00-\\u9fa5，。；、])\\s*" + quotedToken + "|" + quotedToken + "\\s*(?=[\\u4e00-\\u9fa5])", "");
            }
        }
        return cleaned.replaceAll("(?<=[\\u4e00-\\u9fa5])\\s+(?=[\\u4e00-\\u9fa5])", "")
                .replaceAll(" {2,}", " ")
                .trim();
    }

    private boolean isShortWatermarkToken(String token, String normalizedKeywords, Set<String> noiseTokens, Set<String> keywordFragments) {
        if (StringUtils.isBlank(token)) {
            return false;
        }
        if (noiseTokens.contains(token)) {
            return true;
        }
        if (token.length() >= 2 && token.length() <= 3 && token.matches("[A-Za-z_]+") && !token.equals(token.toLowerCase())) {
            return true;
        }
        if (!normalizedKeywords.contains(token) && !keywordFragments.contains(token)) {
            return false;
        }
        if (token.length() == 1 && token.matches("[A-Z]")) {
            return true;
        }
        if (token.length() == 1 && token.matches("[a-z]")) {
            return true;
        }
        return token.matches(".*[0-9].*") || token.matches(".*[A-Z].*") || token.length() >= 2;
    }

    private Set<String> buildKeywordFragments(String normalizedKeywords) {
        Set<String> fragments = new HashSet<>();
        for (int start = 0; start < normalizedKeywords.length(); start += 1) {
            for (int length = 1; length <= 4 && start + length <= normalizedKeywords.length(); length += 1) {
                fragments.add(normalizedKeywords.substring(start, start + length));
            }
        }
        return fragments;
    }

    private String removeResidualWatermarkLines(String text, String normalizedKeywords) {
        StringBuilder builder = new StringBuilder();
        for (String line : text.split("\n")) {
            String compactLine = compactAscii(line);
            if (isWatermarkLine(compactLine, normalizedKeywords)) {
                continue;
            }
            builder.append(line).append('\n');
        }
        return builder.toString();
    }

    private boolean isWatermarkLine(String compactLine, String normalizedKeywords) {
        if (StringUtils.isBlank(compactLine)) {
            return false;
        }
        if (!compactLine.matches("[A-Za-z0-9_]+")) {
            return false;
        }
        return normalizedKeywords.equals(compactLine) || normalizedKeywords.contains(compactLine);
    }

    private boolean isSplitWatermarkLine(String line, String normalizedKeywords, Set<String> keywordFragments) {
        String value = line == null ? "" : line.trim();
        if (StringUtils.isBlank(value) || value.matches(".*[\\u4e00-\\u9fa5].*")) {
            return false;
        }
        String[] parts = value.split("\\s+");
        if (parts.length < 2 || parts.length > 4) {
            return false;
        }
        for (String part : parts) {
            if (!part.matches("[A-Za-z0-9_]{1,4}")) {
                return false;
            }
            if (!normalizedKeywords.contains(part) && !keywordFragments.contains(part)) {
                return false;
            }
        }
        return true;
    }

    private boolean shouldRemoveEmbeddedWatermarkToken(String token) {
        if (token.length() >= 6) {
            return true;
        }
        return token.length() >= 3
                && token.matches(".*[A-Za-z].*")
                && token.matches(".*[0-9].*");
    }

    private String compactAscii(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", "").trim();
    }

    private boolean hasLetterAndDigit(String value) {
        return value.matches(".*[A-Za-z].*") && value.matches(".*\\d.*");
    }

    private record ResumeTextExtraction(String displayText, String profileText) {
    }

    private String toResumeHtml(String plainText) {
        StringBuilder builder = new StringBuilder();
        String[] blocks = plainText.split("\\n{2,}");
        for (String block : blocks) {
            String value = block.trim();
            if (value.isEmpty()) {
                continue;
            }
            builder.append("<p>")
                    .append(escapeHtml(value).replace("\n", "<br/>"))
                    .append("</p>");
        }
        return builder.toString();
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
