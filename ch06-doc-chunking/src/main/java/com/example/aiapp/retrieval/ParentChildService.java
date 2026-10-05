package com.example.aiapp.retrieval;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 动手练习 3 路线 A：父子检索最小实现。
 *
 * 检索想要的块小（语义聚焦、命中准），模型想要的块大（上下文完整）——
 * 父子检索不做取舍：两套块各司其职，用 parentId 连起来。
 *
 *   子块（~128 token）：做 embedding 进 pgvector，负责被检索命中；
 *   父块（按「章」聚合）：不进向量库，存普通表 parent_chunk，命中后喂模型。
 *
 * 流程：用户提问 -> 向量检索命中子块 -> 按 parentId 换父块 -> 父块全文拼入 prompt。
 */
@Service
public class ParentChildService {

    private static final Pattern CHAPTER_BOUNDARY =
            Pattern.compile("(?=第[一二三四五六七八九十百0-9]+章)");

    /** 子块目标块长：小而纯，负责命中精度 */
    private static final int CHILD_CHUNK_SIZE = 128;

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;

    public ParentChildService(VectorStore vectorStore, JdbcTemplate jdbcTemplate) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 建父块表（普通关系表，不是向量表） */
    public void initSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS parent_chunk (
                    parent_id  VARCHAR(128) PRIMARY KEY,
                    doc_id     VARCHAR(128) NOT NULL,
                    title      VARCHAR(256),
                    text       TEXT NOT NULL
                )
                """);
    }

    /**
     * 父子入库：父块按「章」聚合存 parent_chunk 表；
     * 每章内用 TokenTextSplitter 切子块，metadata 记 parentId 后进 pgvector。
     * 幂等：同一 docId 先删旧子块（向量库）再删旧父块（关系表）。
     */
    public void ingest(List<Document> cleanedDocs, String docId, String docName) {
        initSchema();
        vectorStore.delete(new FilterExpressionBuilder().eq("docId", docId).build());
        jdbcTemplate.update("DELETE FROM parent_chunk WHERE doc_id = ?", docId);

        TokenTextSplitter childSplitter = TokenTextSplitter.builder()
                .withChunkSize(CHILD_CHUNK_SIZE)
                .withMinChunkSizeChars(CHILD_CHUNK_SIZE / 2)
                .withMinChunkLengthToEmbed(5)
                .withMaxNumChunks(10000)
                .withKeepSeparator(true)
                .build();

        int childCount = 0;
        for (Document doc : cleanedDocs) {
            String[] chapters = CHAPTER_BOUNDARY.split(doc.getText());
            int parentIndex = 0;
            for (String chapter : chapters) {
                String parentText = chapter.trim();
                if (parentText.isEmpty()) {
                    continue;
                }
                String parentId = docId + "-p" + parentIndex++;
                String title = parentText.lines().findFirst().orElse(docName);
                jdbcTemplate.update(
                        "INSERT INTO parent_chunk (parent_id, doc_id, title, text) VALUES (?, ?, ?, ?)",
                        parentId, docId, title, parentText);

                List<Document> children = childSplitter.apply(
                        List.of(new Document(parentText)));
                for (Document child : children) {
                    child.getMetadata().put("docId", docId);
                    child.getMetadata().put("docName", docName);
                    child.getMetadata().put("parentId", parentId);
                    child.getMetadata().put("chunkType", "child");
                }
                vectorStore.add(children);
                childCount += children.size();
            }
        }
        System.out.printf("父子入库完成：%s，子块 %d 个（父块见 parent_chunk 表）%n",
                docName, childCount);
    }

    /**
     * 检索：命中子块 -> 按 parentId 去重换父块全文。
     * 返回的父块文本列表直接拼入 prompt 喂模型。
     */
    public List<String> retrieveParentContexts(String question, int topK) {
        var hits = vectorStore.similaritySearch(SearchRequest.builder()
                .query(question)
                .topK(topK)
                .similarityThreshold(0.5)
                .build());

        Set<String> parentIds = new LinkedHashSet<>();
        for (var hit : hits) {
            Object parentId = hit.getMetadata().get("parentId");
            if (parentId != null) {
                parentIds.add(parentId.toString());
            }
            System.out.printf("命中子块 score=%.3f parentId=%s | %s...%n",
                    hit.getScore(), parentId,
                    hit.getText().replace("\n", " ")
                            .substring(0, Math.min(50, hit.getText().length())));
        }

        List<String> parents = new ArrayList<>();
        for (String parentId : parentIds) {
            parents.addAll(jdbcTemplate.query(
                    "SELECT text FROM parent_chunk WHERE parent_id = ?",
                    (rs, rowNum) -> rs.getString("text"), parentId));
        }
        return parents;
    }
}
