package com.novaops.backend.kb.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.novaops.backend.kb.service.StructuredTextParser.Block;
import com.novaops.backend.kb.service.StructuredTextParser.Result;
import org.junit.jupiter.api.Test;

/**
 * StructuredTextParser 的 XHTML 结构还原测试:标题层级、正文/代码块识别、
 * 元数据区域跳过与纯文本兜底收集。
 */
class StructuredTextParserTest {

  @Test
  void parsesHeadingsParagraphsAndCodeFromXhtml() {
    String xhtml = "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>忽略</title></head><body>"
        + "<h1>第一章</h1><p>这是正文。</p><h2>1.1 代码示例</h2>"
        + "<pre><code>public static void main() {\n  // hi\n}</code></pre>"
        + "</body></html>";

    Result result = StructuredTextParser.parse(xhtml);

    assertThat(result.blocks()).hasSize(4);
    assertThat(result.blocks().get(0)).isEqualTo(new Block(Block.Type.HEADING, 1, "第一章"));
    assertThat(result.blocks().get(1)).isEqualTo(new Block(Block.Type.TEXT, 0, "这是正文。"));
    assertThat(result.blocks().get(2)).isEqualTo(new Block(Block.Type.HEADING, 2, "1.1 代码示例"));
    assertThat(result.blocks().get(3).type()).isEqualTo(Block.Type.CODE);
    assertThat(result.blocks().get(3).text()).contains("public static void main()");
  }

  @Test
  void skipsHeadMetadataAndEmptyBlocks() {
    Result result = StructuredTextParser.parse(
        "<html><head><meta name=\"x\" content=\"y\"/></head><body><p> </p><p>有效</p></body></html>");

    assertThat(result.blocks()).extracting(Block::text).containsExactly("有效");
  }

  @Test
  void collectsPlainTextForFallback() {
    Result result = StructuredTextParser.parse("<html><body><h1>标题</h1><p>段落内容</p></body></html>");

    assertThat(result.plainText()).contains("标题").contains("段落内容");
  }

  @Test
  void treatsDivWrappedPagesAsParagraphs() {
    // PDF 解析结果常为 <div class="page"><p>...</p></div>,div 本身不产生结构块
    Result result = StructuredTextParser.parse(
        "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body>"
            + "<div class=\"page\"><p>第一页</p></div><div class=\"page\"><p>第二页</p></div>"
            + "</body></html>");

    assertThat(result.blocks()).extracting(Block::text).containsExactly("第一页", "第二页");
  }

  @Test
  void markdownKeepsHeadingAndCodeStructure() {
    // md 走原生 Markdown 解析(Tika 标准包无 markdown 解析器),标题/代码块结构必须可还原
    Result result = StructuredTextParser.parseMarkdown(
        "# 标题一\n\n正文段落\n\n## 子标题\n\n```java\nint a = 1;\n```\n\n- 列表项\n");

    assertThat(result.blocks()).anyMatch(b ->
        b.type() == Block.Type.HEADING && b.level() == 1 && b.text().equals("标题一"));
    assertThat(result.blocks()).anyMatch(b ->
        b.type() == Block.Type.TEXT && b.text().equals("正文段落"));
    assertThat(result.blocks()).anyMatch(b ->
        b.type() == Block.Type.HEADING && b.level() == 2 && b.text().equals("子标题"));
    assertThat(result.blocks()).anyMatch(b ->
        b.type() == Block.Type.CODE && b.text().contains("int a = 1;"));
    assertThat(result.blocks()).anyMatch(b -> b.text().equals("- 列表项"));
  }
}
