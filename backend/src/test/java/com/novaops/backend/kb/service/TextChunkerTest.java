package com.novaops.backend.kb.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.novaops.backend.kb.config.KbProperties;
import com.novaops.backend.kb.service.StructuredTextParser.Block;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * TextChunker 切片行为测试:结构感知分层切片、章节路径前缀、语义边界选取、
 * 重叠衔接、token 换算窗口、代理对防护与尾部碎片合并。
 */
class TextChunkerTest {

  /** 18 字符/句的中文测试句(含两位数字),句号位置 17+18k。 */
  private static final String SENTENCE18 = "这是用于测试切片的中文内容第01句。";
  /** 19 字符/句的纯 CJK 测试句,句号位置 18+19k。 */
  private static final String SENTENCE19 = "这是用于测试切片重叠衔接的中文内容句。";

  private final KbProperties properties = new KbProperties();
  private final TextChunker chunker = new TextChunker(properties);

  private static Block h(int level, String text) {
    return new Block(Block.Type.HEADING, level, text);
  }

  private static Block p(String text) {
    return new Block(Block.Type.TEXT, 0, text);
  }

  @Test
  void emptyInputReturnsNoChunks() {
    assertThat(chunker.splitPlain(" \n\n ")).isEmpty();
    assertThat(chunker.split(List.of(), "")).isEmpty();
    assertThat(chunker.split(null, null)).isEmpty();
  }

  @Test
  void whitespaceNormalizationCollapsesJunk() {
    assertThat(chunker.splitPlain("  你好\u0000世界  \n\n\n\n换行 "))
        .containsExactly("你好 世界 \n\n换行");
  }

  @Test
  void smallStructuredDocumentRenderedWholeWithHeadings() {
    List<String> chunks = chunker.split(List.of(h(1, "标题"), p("正文")), "");

    assertThat(chunks).containsExactly("标题\n\n正文");
  }

  @Test
  void nestedSectionsKeptInReadingOrderWhenWholeDocumentFits() {
    List<String> chunks = chunker.split(List.of(h(1, "第一章"), p("A"), h(2, "1.1"), p("B")), "");

    assertThat(chunks).containsExactly("第一章\n\nA\n\n1.1\n\nB");
  }

  @Test
  void oversizedLeafSplitsBySentenceWithPathPrefix() {
    List<String> chunks = chunker.split(
        List.of(h(1, "第一章"), p("这是引言,共一小段。"), h(2, "1.1 背景"), p(SENTENCE18.repeat(50))), "");

    assertThat(chunks).hasSize(3);
    assertThat(chunks.get(0)).isEqualTo("第一章\n\n这是引言,共一小段。");
    // 超长叶子回退滑动窗口,前缀携带完整章节路径
    String prefix = "第一章 > 1.1 背景\n\n";
    assertThat(chunks.get(1)).startsWith(prefix).endsWith("。");
    assertThat(chunks.get(2)).startsWith(prefix).endsWith("。");
  }

  @Test
  void nestedHeadingPathIncludesAllAncestors() {
    List<String> chunks = chunker.split(
        List.of(h(1, "A"), h(2, "A.1"), h(3, "A.1.1"), p(SENTENCE18.repeat(50))), "");

    assertThat(chunks).isNotEmpty();
    assertThat(chunks).allSatisfy(c -> assertThat(c).startsWith("A > A.1 > A.1.1\n\n"));
  }

  @Test
  void sentenceBoundaryWithOverlapBridgesAdjacentChunks() {
    // 665 字符纯中文,token 换算窗口 600、重叠 90,首个切点落在句号后的 589;
    // 末块新增 76 字符(超过 60 合并阈值),保留独立尾块用于验证重叠衔接
    String text = SENTENCE19.repeat(35);
    List<String> chunks = chunker.splitPlain(text);

    assertThat(chunks).hasSize(2);
    assertThat(chunks.get(0)).endsWith("。");
    assertThat(chunks.get(0)).hasSize(589);
    assertThat(chunks.get(1)).startsWith(chunks.get(0).substring(chunks.get(0).length() - 90));
  }

  @Test
  void singleNewlineUsedAsBoundary() {
    // 80 字符/行的纯英文无标点文本,只能依赖单换行切块
    String text = ("A".repeat(80) + "\n").repeat(40);
    List<String> chunks = chunker.splitPlain(text);

    assertThat(chunks).hasSize(2);
    assertThat(chunks.get(0).split("\n")).hasSize(29);
  }

  @Test
  void englishPeriodSkipsDecimalDigits() {
    // 每句含 3.14 / v1.2 数字小数点,切点必须落在句末句号而非数字后
    String sentence = "The value is 3.14 dollars in total. The version is v1.2 now. ";
    List<String> chunks = chunker.splitPlain(sentence.repeat(40));

    assertThat(chunks).hasSizeGreaterThanOrEqualTo(2);
    for (String chunk : chunks) {
      assertThat(chunk).endsWith("now.").doesNotEndWith("3.").doesNotEndWith("1.");
    }
  }

  @Test
  void tinyTailMergedIntoPreviousChunk() {
    // 末块新增内容 39 字符,小于 60 应并入前块
    String merged = chunker.splitPlain(SENTENCE19.repeat(33) + "尾").get(0);
    assertThat(merged).isEqualTo(SENTENCE19.repeat(33) + "尾");

    // 末块新增内容 171 字符,应保留独立尾块
    assertThat(chunker.splitPlain(SENTENCE19.repeat(40))).hasSize(2);
  }

  @Test
  void emojiSurrogatePairsStayIntact() {
    // "好😀" 每单元 3 字符,追加单个 x 破坏对齐,窗口硬切会恰好落在代理对中间
    String text = "好😀".repeat(800) + "x";
    List<String> chunks = chunker.splitPlain(text);

    assertThat(chunks).hasSize(2);
    for (String chunk : chunks) {
      assertThat(Character.isLowSurrogate(chunk.charAt(0))).isFalse();
      assertThat(Character.isHighSurrogate(chunk.charAt(chunk.length() - 1))).isFalse();
    }
    // 1439 处落在代理对中间,防护应前移一位,块尾回到"好"
    assertThat(chunks.get(0)).hasSize(1438).endsWith("好");
  }

  @Test
  void surrogateGuardsAdjustCutPoints() {
    String text = "a😀b";
    assertThat(TextChunker.avoidSurrogateSplit(text, 2)).isEqualTo(1);
    assertThat(TextChunker.avoidSurrogateSplit(text, 3)).isEqualTo(3);
  }

  @Test
  void structuredCodeBlockKeptIntactWhenItFits() {
    Block code = new Block(Block.Type.CODE, 0, "int a = 1;\nint b = 2;");
    List<String> chunks = chunker.split(List.of(h(1, "示例"), code), "");

    assertThat(chunks).containsExactly("示例\n\nint a = 1;\nint b = 2;");
  }

  @Test
  void fallsBackToPlainTextWhenBlocksMissing() {
    assertThat(chunker.split(List.of(), "纯文本兜底")).containsExactly("纯文本兜底");
  }
}
