package com.novaops.backend.kb.service;

import com.novaops.backend.kb.config.KbProperties;
import com.novaops.backend.kb.service.StructuredTextParser.Block;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 文档切片器:优先按标题层级做结构感知切片,超长叶子块回退到基于语义边界
 * (段落/换行/中英文句末标点)的滑动窗口。块大小按目标 token 数与文本的中英文
 * 占比换算字符预算,重叠按块大小的 15% 派生。
 */
@Component
public class TextChunker {

  /** 滑动窗口字符预算的上下限,token 换算结果不会超出该区间。 */
  private static final int MIN_WINDOW_CHARS = 200;
  private static final int MAX_WINDOW_CHARS = 4000;
  /** 最后一块比前一块多出的新内容少于此字符数时并入前一块,避免碎片噪声。 */
  private static final int TAIL_MIN_CHARS = 60;
  /** 相邻块重叠比例及其边界值。 */
  private static final double OVERLAP_RATIO = 0.15;
  private static final int OVERLAP_MIN_CHARS = 50;
  private static final int OVERLAP_MAX_CHARS = 300;
  /** 块级 token 目标下限,防止配置过小导致碎片化。 */
  private static final int MIN_TOKEN_TARGET = 200;

  private final KbProperties properties;

  public TextChunker(KbProperties properties) {
    this.properties = properties;
  }

  /** 结构感知切片入口:无结构块时回退纯文本滑动窗口。 */
  public List<String> split(List<Block> blocks, String plainText) {
    if (blocks == null || blocks.isEmpty()) {
      return splitPlain(plainText);
    }
    int tokenTarget = Math.max(MIN_TOKEN_TARGET, properties.getChunkTokens());
    Section root = buildSections(blocks);
    List<String> chunks = new ArrayList<>();
    emitSection(root, "", chunks, tokenTarget);
    return chunks;
  }

  /** 纯文本兜底切片:归一化后按 token 换算窗口并滑动切块。 */
  public List<String> splitPlain(String raw) {
    String text = normalize(raw);
    if (text.isEmpty()) {
      return List.of();
    }
    int tokenTarget = Math.max(MIN_TOKEN_TARGET, properties.getChunkTokens());
    int size = windowSize(text, tokenTarget);
    return splitWindow(text, size, overlapFor(size));
  }

  /** 按标题层级聚合出的章节:标题块 + 直属正文块 + 子章节,totalTokens 为整棵子树估算 token。 */
  private static final class Section {
    Block heading;
    final List<Block> body = new ArrayList<>();
    final List<Section> children = new ArrayList<>();
    int totalTokens;
  }

  /** 扁平块序列按标题层级建树,标题开启新章节,正文归入当前最内层章节。 */
  private static Section buildSections(List<Block> blocks) {
    Section root = new Section();
    List<Section> stack = new ArrayList<>();
    stack.add(root);
    for (Block block : blocks) {
      if (block.type() == Block.Type.HEADING) {
        int level = Math.max(1, Math.min(6, block.level()));
        while (stack.size() > 1 && stack.get(stack.size() - 1).heading.level() >= level) {
          stack.remove(stack.size() - 1);
        }
        Section section = new Section();
        section.heading = block;
        stack.get(stack.size() - 1).children.add(section);
        stack.add(section);
      } else {
        stack.get(stack.size() - 1).body.add(block);
      }
    }
    computeTokens(root);
    return root;
  }

  private static void computeTokens(Section section) {
    int tokens = 0;
    if (section.heading != null) {
      tokens += estimateTokens(section.heading.text());
    }
    for (Block block : section.body) {
      tokens += estimateTokens(block.text());
    }
    for (Section child : section.children) {
      computeTokens(child);
      tokens += child.totalTokens;
    }
    section.totalTokens = tokens;
  }

  /**
   * 整节未超 token 目标时整段成块(保留标题与正文的原始阅读顺序);
   * 否则直属正文单独成块,子章节逐个递归,下钻路径作为块前缀。
   */
  private void emitSection(Section section, String path, List<String> chunks, int tokenTarget) {
    if (section.totalTokens <= tokenTarget) {
      String whole = renderWhole(section);
      if (!whole.isEmpty()) {
        chunks.add(whole);
      }
      return;
    }
    String headingText = section.heading == null ? "" : section.heading.text();
    String prefix = path.isEmpty() ? headingText : (headingText.isEmpty() ? path : path + " > " + headingText);
    if (!section.body.isEmpty()) {
      String bodyText = renderBlocks(section.body);
      int size = windowSize(bodyText, tokenTarget);
      for (String chunk : splitWindow(bodyText, size, overlapFor(size))) {
        chunks.add(withPrefix(prefix, chunk));
      }
    }
    for (Section child : section.children) {
      emitSection(child, prefix, chunks, tokenTarget);
    }
  }

  /** 渲染整节子树为单个文本:标题 + 正文 + 递归子节,块间保留空行。 */
  private static String renderWhole(Section section) {
    StringBuilder sb = new StringBuilder();
    if (section.heading != null) {
      sb.append(section.heading.text()).append("\n\n");
    }
    appendBlocks(sb, section.body);
    for (Section child : section.children) {
      sb.append(renderWhole(child)).append("\n\n");
    }
    return sb.toString().trim();
  }

  private static String renderBlocks(List<Block> blocks) {
    StringBuilder sb = new StringBuilder();
    appendBlocks(sb, blocks);
    return sb.toString().trim();
  }

  private static void appendBlocks(StringBuilder sb, List<Block> blocks) {
    for (Block block : blocks) {
      sb.append(block.text()).append("\n\n");
    }
  }

  private static String withPrefix(String prefix, String chunk) {
    return prefix.isEmpty() ? chunk : prefix + "\n\n" + chunk;
  }

  private static String normalize(String raw) {
    if (raw == null) {
      return "";
    }
    return raw.replace('\u0000', ' ')
        .replaceAll("[ \\t]+", " ")
        .replaceAll("\\n{3,}", "\n\n")
        .trim();
  }

  /** 按文本的中英文占比把目标 token 数换算成滑动窗口字符预算。 */
  private static int windowSize(String text, int tokenTarget) {
    int tokens = Math.max(1, estimateTokens(text));
    long size = (long) tokenTarget * text.length() / tokens;
    return (int) Math.min(MAX_WINDOW_CHARS, Math.max(MIN_WINDOW_CHARS, size));
  }

  private static int overlapFor(int size) {
    return (int) Math.min(OVERLAP_MAX_CHARS, Math.max(OVERLAP_MIN_CHARS, size * OVERLAP_RATIO));
  }

  /** 粗略估算 token:CJK 字符 1 字≈1 token,其余按 4 字符≈1 token,emoji 等代理对按 1 个字符计。 */
  static int estimateTokens(String text) {
    int cjk = 0;
    int nonCjk = 0;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (Character.isHighSurrogate(c) && i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
        nonCjk++;
        i++;
      } else if (isCjk(c)) {
        cjk++;
      } else {
        nonCjk++;
      }
    }
    return cjk + (nonCjk + 3) / 4;
  }

  private static boolean isCjk(char c) {
    return (c >= 0x4E00 && c <= 0x9FFF) // CJK 统一表意文字
        || (c >= 0x3400 && c <= 0x4DBF) // 扩展 A
        || (c >= 0x3000 && c <= 0x303F) // CJK 符号与标点(。、！？等)
        || (c >= 0xFF00 && c <= 0xFFEF); // 全角字符
  }

  /**
   * 在窗口后半段 [start + (hardEnd-start)/2 + 1, hardEnd) 内找语义边界,
   * 返回切点 end(不含);优先级:段落 > 单换行 > 中文句末标点 > 英文句末标点,
   * 找不到返回 -1 由调用方硬切。
   */
  private static int findBoundary(String text, int start, int hardEnd) {
    int minPos = start + (hardEnd - start) / 2 + 1;
    int from = hardEnd - 1;
    int idx = text.lastIndexOf("\n\n", from);
    if (idx >= minPos) {
      return idx + 2;
    }
    idx = text.lastIndexOf('\n', from);
    if (idx >= minPos) {
      return idx + 1;
    }
    for (char c : new char[]{'。', '！', '？', '；'}) {
      idx = text.lastIndexOf(c, from);
      if (idx >= minPos) {
        return idx + 1;
      }
    }
    for (char c : new char[]{'!', '?', ';', '.'}) {
      idx = text.lastIndexOf(c, from);
      while (idx >= minPos) {
        // 英文句号需跳过数字小数点,避免切断 3.14 / v1.2
        if (c != '.' || isSentencePeriod(text, idx)) {
          return idx + 1;
        }
        idx = text.lastIndexOf(c, idx - 1);
      }
    }
    return -1;
  }

  private static boolean isSentencePeriod(String text, int idx) {
    return idx == 0 || !Character.isDigit(text.charAt(idx - 1));
  }

  /** 切点恰好落在代理对中间时前移一位,避免块尾出现孤立高代理。 */
  static int avoidSurrogateSplit(String text, int end) {
    if (end > 0 && end < text.length()
        && Character.isHighSurrogate(text.charAt(end - 1))
        && Character.isLowSurrogate(text.charAt(end))) {
      return end - 1;
    }
    return end;
  }

  /** 滑动起点落在代理对中间时后移一位,避免块首出现孤立低代理。 */
  private static int skipLoneLowSurrogate(String text, int start) {
    if (start > 0 && start < text.length()
        && Character.isLowSurrogate(text.charAt(start))
        && Character.isHighSurrogate(text.charAt(start - 1))) {
      return start + 1;
    }
    return start;
  }

  /** 滑动窗口切块:优先语义边界,找不到才硬切;末尾新增内容过短的尾块并入前块。 */
  private static List<String> splitWindow(String text, int size, int overlap) {
    List<int[]> ranges = new ArrayList<>();
    int start = 0;
    while (start < text.length()) {
      int hardEnd = Math.min(text.length(), start + size);
      int end = hardEnd;
      if (hardEnd < text.length()) {
        int boundary = findBoundary(text, start, hardEnd);
        end = boundary > 0 ? boundary : hardEnd;
      }
      end = avoidSurrogateSplit(text, end);
      ranges.add(new int[]{start, end});
      if (end >= text.length()) {
        break;
      }
      start = skipLoneLowSurrogate(text, Math.max(start + 1, end - overlap));
    }
    if (ranges.size() > 1) {
      int[] last = ranges.get(ranges.size() - 1);
      int[] prev = ranges.get(ranges.size() - 2);
      if (last[1] - prev[1] < TAIL_MIN_CHARS) {
        prev[1] = last[1];
        ranges.remove(ranges.size() - 1);
      }
    }
    List<String> chunks = new ArrayList<>();
    for (int[] range : ranges) {
      String chunk = text.substring(range[0], range[1]).trim();
      if (!chunk.isEmpty()) {
        chunks.add(chunk);
      }
    }
    return chunks;
  }
}
