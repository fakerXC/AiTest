package com.example.aiapp.chat;

/** 一条引用：前端点击 [n] 时展示的来源信息 */
public record SourceRef(
        int index,        // 编号，对应答案中的 [n]
        String docName,   // 来源文档名
        String titlePath, // 标题路径，如「员工考勤与报销制度 > 第八条」
        String chunkId,   // chunk 唯一标识，前端用它定位原文
        String snippet    // 片段摘要，鼠标悬停时预览
) {}
