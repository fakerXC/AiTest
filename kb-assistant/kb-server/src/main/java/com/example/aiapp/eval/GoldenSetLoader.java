package com.example.aiapp.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** 从 classpath 的 JSONL 文件加载 golden set */
@Component
public class GoldenSetLoader {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<GoldenCase> load(Resource resource) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines()
                    .filter(line -> !line.isBlank())
                    .map(line -> {
                        try {
                            return objectMapper.readValue(line, GoldenCase.class);
                        } catch (Exception e) {
                            throw new IllegalStateException("golden set 解析失败: " + line, e);
                        }
                    })
                    .toList();
        }
    }
}
