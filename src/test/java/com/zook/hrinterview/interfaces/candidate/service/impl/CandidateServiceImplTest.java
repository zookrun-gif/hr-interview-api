package com.zook.hrinterview.interfaces.candidate.service.impl;

import com.zook.hrinterview.interfaces.candidate.dto.ResumeParseResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CandidateServiceImplTest {

    @Test
    void shouldRemovePdfKeywordWatermarkNoise() throws Exception {
        String keywords = "6a0396021fff60cb1HJ62ty8E1ZVwom4VvOZWOKrn_LSNxJl";
        String rawText = """
                6a0396021fff60cb1HJ62ty8E1ZVwom4VvOZWOKrn_LSNxJl
                OKrn_LSNxJ
                l
                021fff6
                0cb
                1HJ6
                2ty8
                E1ZVwom4VvO
                ZW
                郑一
                性别：女 电话：15136172890
                邮箱：545459982@qq.com 出生日期：1997.09.12
                396
                6a0
                教育背景
                2016.07 - 2018.06 河南牧业经济学院
                工作经历
                中期执行6以a0及任务的推进，辅助店面销量任务的达成
                OKrn_LSNxJ
                l
                2024.05 - 2025.03 四川宣晟旺科技有限公司 V培w训赋能
                3.活动经验丰富，熟练掌握线上线下全品类营销活动
                xJ 落地，涵盖社群运营、直播团购、门店节点活动、
                大型酒店会销等多种形式。 rn_L
                4.具备运营管理能力、执行力强，可以迅速WOZ 适应不同的工作节奏
                Jl l
                Nx xJ
                LS SN
                n_ rn_L
                OZ ZW
                Vv vO
                m4 4V
                wo om
                ZV Vw
                E1 1Z
                J6 62
                1H HJ
                cb b1
                60 0c
                02 21
                96 60
                03 39
                """;

        CandidateServiceImpl service = new CandidateServiceImpl();
        Method method = CandidateServiceImpl.class.getDeclaredMethod("removePdfWatermarkNoise", String.class, String.class);
        method.setAccessible(true);
        String cleaned = (String) method.invoke(service, rawText, keywords);

        assertFalse(cleaned.contains("6a0396021fff60cb1HJ62ty8E1ZVwom4VvOZWOKrn_LSNxJl"));
        assertFalse(cleaned.contains("OKrn_LSNxJ"));
        assertFalse(cleaned.contains("E1ZVwom4VvO"));
        assertFalse(cleaned.contains("执行6以a0及"));
        assertFalse(cleaned.contains("V培w训"));
        assertFalse(cleaned.contains("xJ 落地"));
        assertFalse(cleaned.contains("rn_L"));
        assertFalse(cleaned.contains("迅速WOZ 适应"));
        assertFalse(cleaned.contains("Nx xJ"));
        assertFalse(cleaned.contains("OZ ZW"));
        assertTrue(cleaned.contains("郑一"));
        assertTrue(cleaned.contains("15136172890"));
        assertTrue(cleaned.contains("工作经历"));
        assertTrue(cleaned.contains("中期执行以及任务的推进"));
        assertTrue(cleaned.contains("四川宣晟旺科技有限公司培训赋能"));
        assertTrue(cleaned.contains("活动经验丰富，熟练掌握线上线下全品类营销活动"));
        assertTrue(cleaned.contains("落地，涵盖社群运营、直播团购、门店节点活动"));
        assertTrue(cleaned.contains("大型酒店会销等多种形式。"));
        assertTrue(cleaned.contains("可以迅速适应不同的工作节奏"));
    }

    @Test
    void shouldExtractCandidateProfileFromResumeText() throws Exception {
        String plainText = """
                郑一
                性别：女 电话：15136172890
                邮箱：545459982@qq.com 出生日期：1997.09.12
                教育背景
                工作经历
                """;
        ResumeParseResponse response = new ResumeParseResponse();
        CandidateServiceImpl service = new CandidateServiceImpl();
        Method method = CandidateServiceImpl.class.getDeclaredMethod("fillResumeProfile", ResumeParseResponse.class, String.class);
        method.setAccessible(true);
        method.invoke(service, response, plainText);

        assertEquals("郑一", response.getName());
        assertEquals("FEMALE", response.getGender());
        assertEquals("15136172890", response.getPhone());
        assertEquals("545459982@qq.com", response.getEmail());
        assertTrue(response.getAge() >= 28);
    }

    @Test
    void shouldCleanGenericResumeNoiseAndExtractStickyPhone() throws Exception {
        String plainText = """
                b0e1af35e3c3600b1HFz09-9FFJZxYy3VP6aWOGmnPTUNxhmNx
                赵宏杰
                P 6a
                RESUME
                求职意向：python 工程师 -9
                z 09
                b 1H
                基本信息： 5e
                32020.10 参加校手工社展览 b 0
                b0e1af35e3c3600b1HFz09-9FFJZxYy3VP6aWOGmnPTUNxhm
                ⚫ 电话：184340028522023.4 参加蓝桥杯大赛 c 语言 b 组
                ⚫ 学历：大学本科
                参与项目 project
                ⚫ 专业：智能科学与技术
                一、手指关键点检测与手势识别项目 2024.7—2024.8
                ⚫ 邮箱：
                1.基于 MediaPipe 提取多个手部关键点，训练 CNN 分类模型识别动态手势
                2536338076@qq.com
                教育背景：
                ◆ 现居：重庆市渝中区 1. 采集 3000 张跌倒/正常姿态图像，使用 Labelme 标注关键点；
                2，基于 YOLOv5 N xh训练人体关键点检测模型，结合时序姿态分析降低误报
                2020--2024 TU
                院校：山西工程技术学院 a W
                1.P6采集 12 万张 CMYK 分通道高清图，构建缺陷样本库；设计双分支网络：
                9F 3.使用 Lab 颜色空间+K-means 聚类实现实时四色分离，定位误≤0.5mm；
                1H 四、文档 OCR 与图像差异比对系统 2025.6—2025.7
                语言、Python、C++程序
                b0 2.对图像进行透视矫正+自适应二值化，SSIM+感知哈希融合，检测差异；
                工作经历 work
                专业技能： 2024.9—2025.1 在太原畅利成科技有限公司担任算法工程师，负责文件
                Python, JS 深 度 学 习 文档识别检测。
                """;

        CandidateServiceImpl service = new CandidateServiceImpl();
        Method normalizeMethod = CandidateServiceImpl.class.getDeclaredMethod("normalizeResumeText", String.class);
        normalizeMethod.setAccessible(true);
        String cleaned = (String) normalizeMethod.invoke(service, plainText);

        Method phoneMethod = CandidateServiceImpl.class.getDeclaredMethod("extractPhone", String.class);
        phoneMethod.setAccessible(true);
        String phone = (String) phoneMethod.invoke(service, cleaned);

        assertFalse(cleaned.contains("b0e1af35e3c3600b1HFz09"));
        assertFalse(cleaned.contains("RESUME"));
        assertFalse(cleaned.contains("P 6a"));
        assertTrue(cleaned.contains("赵宏杰"));
        assertTrue(cleaned.contains("求职意向：python 工程师"));
        assertTrue(cleaned.contains("2536338076@qq.com"));
        assertTrue(cleaned.contains("太原畅利成科技有限公司"));
        assertEquals("18434002852", phone);
    }

    @Test
    void shouldExtractProfileFromAllTextCandidatesWhenDisplayTextLosesHeader() throws Exception {
        String rawProfileText = """
                郑一
                性别：女 电话：15136172890
                邮箱：545459982@qq.com 出生日期：1997.09.12
                工作经历
                """;
        String displayText = """
                郑一
                教育背景
                工作经历
                6a0396021fff60cb1HJ62ty8E1ZVwom4VvOZWOKrn_LSNxJl2.活动主持版块：负责活动正常宣讲
                """;

        CandidateServiceImpl service = new CandidateServiceImpl();
        Method normalizeMethod = CandidateServiceImpl.class.getDeclaredMethod("normalizeResumeText", String.class);
        normalizeMethod.setAccessible(true);
        String cleanedDisplay = (String) normalizeMethod.invoke(service, displayText);
        assertFalse(cleanedDisplay.contains("6a0396021fff60cb1HJ62ty8E1ZVwom4VvOZWOKrn_LSNxJl"));
        assertTrue(cleanedDisplay.contains("2.活动主持版块"));

        ResumeParseResponse response = new ResumeParseResponse();
        Method profileMethod = CandidateServiceImpl.class.getDeclaredMethod("fillResumeProfile", ResumeParseResponse.class, String.class);
        profileMethod.setAccessible(true);
        profileMethod.invoke(service, response, rawProfileText + "\n" + cleanedDisplay);

        assertEquals("郑一", response.getName());
        assertEquals("FEMALE", response.getGender());
        assertEquals("15136172890", response.getPhone());
        assertEquals("545459982@qq.com", response.getEmail());
        assertTrue(response.getAge() >= 28);
    }
}
