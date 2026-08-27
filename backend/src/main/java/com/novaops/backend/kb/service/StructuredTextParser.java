package com.novaops.backend.kb.service;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * 解析 Tika ToXMLContentHandler 输出的 XHTML,还原文档结构块(标题/正文/代码)
 * 供 TextChunker 做结构感知切片;同时收集全部文本,结构块为空时作为纯文本切片兜底。
 */
public final class StructuredTextParser {

  /** 文档结构块:HEADING 携带 1~6 级标题层级,TEXT 为段落/列表项/表格单元格,CODE 为代码块。 */
  public record Block(Type type, int level, String text) {
    public enum Type { HEADING, TEXT, CODE }
  }

  public record Result(List<Block> blocks, String plainText) {
  }

  private StructuredTextParser() {
  }

  public static Result parse(String xhtml) {
    Handler handler = new Handler();
    SAXParserFactory factory = SAXParserFactory.newInstance();
    factory.setNamespaceAware(true);
    try {
      factory.newSAXParser().parse(new InputSource(new StringReader(xhtml)), handler);
    } catch (ParserConfigurationException | SAXException | IOException ex) {
      throw new IllegalStateException("文档结构解析失败: " + ex.getMessage(), ex);
    }
    return new Result(handler.blocks, handler.raw.toString());
  }

  /**
   * 解析 Markdown 原文为结构块。Tika 3.x 标准包不含 markdown 解析器,
   * md 若走 Tika 会被魔数检测误判为纯文本而丢失标题/代码结构,故走此原生路径。
   */
  public static Result parseMarkdown(String markdown) {
    if (markdown == null) {
      return new Result(List.of(), "");
    }
    List<Block> blocks = new ArrayList<>();
    StringBuilder paragraph = new StringBuilder();
    StringBuilder code = new StringBuilder();
    String fenceChar = null;
    for (String line : markdown.split("\r\n|\n|\r", -1)) {
      if (fenceChar != null) {
        String stripped = line.strip();
        if (isClosingFence(stripped, fenceChar)) {
          addCode(blocks, code);
          fenceChar = null;
        } else {
          code.append(line).append('\n');
        }
        continue;
      }
      String stripped = line.strip();
      if (stripped.isEmpty()) {
        flushParagraph(blocks, paragraph);
      } else if (isFence(stripped)) {
        flushParagraph(blocks, paragraph);
        fenceChar = String.valueOf(stripped.charAt(0));
      } else if (isHorizontalRule(stripped)) {
        flushParagraph(blocks, paragraph);
      } else if (isAtxHeading(stripped)) {
        flushParagraph(blocks, paragraph);
        String text = atxHeadingText(stripped);
        if (!text.isEmpty()) {
          blocks.add(new Block(Block.Type.HEADING, atxLevel(stripped), text));
        }
      } else {
        // 段落/列表/引用行按原样累积,行间保留单换行供边界识别
        if (paragraph.length() > 0) {
          paragraph.append('\n');
        }
        paragraph.append(stripped.startsWith("> ") ? stripped.substring(2) : stripped);
      }
    }
    if (fenceChar != null) {
      addCode(blocks, code); // 未闭合的代码块按原样保留
    }
    flushParagraph(blocks, paragraph);
    return new Result(blocks, markdown);
  }

  private static int leadingRun(String line, char c) {
    int n = 0;
    while (n < line.length() && line.charAt(n) == c) {
      n++;
    }
    return n;
  }

  private static boolean isFence(String line) {
    return leadingRun(line, '`') >= 3 || leadingRun(line, '~') >= 3;
  }

  /** 闭合围栏须与开启字符一致且不含其他内容(允许空白)。 */
  private static boolean isClosingFence(String line, String fenceChar) {
    char c = fenceChar.charAt(0);
    return leadingRun(line, c) >= 3
        && line.chars().allMatch(ch -> ch == c || Character.isWhitespace(ch));
  }

  private static boolean isAtxHeading(String line) {
    int run = leadingRun(line, '#');
    return run >= 1 && run <= 6 && run < line.length()
        && (line.charAt(run) == ' ' || line.charAt(run) == '\t');
  }

  private static int atxLevel(String line) {
    return leadingRun(line, '#');
  }

  /** 去掉标题尾部装饰用的 # 序列,如 "## 标题 ##"。 */
  private static String atxHeadingText(String line) {
    return line.substring(atxLevel(line)).trim().replaceAll("[ \\t#]+$", "");
  }

  private static boolean isHorizontalRule(String line) {
    String s = line.replace(" ", "").replace("\t", "");
    return s.length() >= 3
        && (s.chars().allMatch(c -> c == '-') || s.chars().allMatch(c -> c == '*') || s.chars().allMatch(c -> c == '_'));
  }

  private static void flushParagraph(List<Block> blocks, StringBuilder paragraph) {
    if (paragraph.length() > 0) {
      blocks.add(new Block(Block.Type.TEXT, 0, paragraph.toString().trim()));
      paragraph.setLength(0);
    }
  }

  private static void addCode(List<Block> blocks, StringBuilder code) {
    String text = code.toString().trim();
    if (!text.isEmpty()) {
      blocks.add(new Block(Block.Type.CODE, 0, text));
    }
    code.setLength(0);
  }

  /** 流式收集块级元素文本,跳过 head 等元数据区域,忽略空块。 */
  private static final class Handler extends DefaultHandler {
    private final List<Block> blocks = new ArrayList<>();
    private final StringBuilder raw = new StringBuilder();
    private final StringBuilder current = new StringBuilder();
    private Block.Type currentType;
    private int currentLevel;
    private int skipDepth;

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) {
      String name = localName.isEmpty() ? qName : localName;
      if (name.equals("head") || name.equals("script") || name.equals("style")) {
        skipDepth++;
        return;
      }
      if (skipDepth > 0) {
        return;
      }
      if (name.length() == 2 && name.charAt(0) == 'h' && name.charAt(1) >= '1' && name.charAt(1) <= '6') {
        beginBlock(Block.Type.HEADING, name.charAt(1) - '0');
      } else if (name.equals("pre")) {
        beginBlock(Block.Type.CODE, 0);
      } else if (name.equals("p") || name.equals("li") || name.equals("td") || name.equals("th") || name.equals("blockquote")) {
        beginBlock(Block.Type.TEXT, 0);
      } else if (name.equals("br")) {
        current.append('\n');
        raw.append('\n');
      }
    }

    @Override
    public void endElement(String uri, String localName, String qName) {
      String name = localName.isEmpty() ? qName : localName;
      if (name.equals("head") || name.equals("script") || name.equals("style")) {
        skipDepth--;
        return;
      }
      if (skipDepth > 0) {
        return;
      }
      boolean heading = name.length() == 2 && name.charAt(0) == 'h' && name.charAt(1) >= '1' && name.charAt(1) <= '6';
      if (heading || name.equals("pre") || name.equals("p") || name.equals("li") || name.equals("td") || name.equals("th") || name.equals("blockquote")) {
        flushBlock();
      }
    }

    @Override
    public void characters(char[] ch, int start, int length) {
      if (skipDepth > 0) {
        return;
      }
      current.append(ch, start, length);
      raw.append(ch, start, length);
    }

    private void beginBlock(Block.Type type, int level) {
      flushBlock();
      currentType = type;
      currentLevel = level;
    }

    private void flushBlock() {
      if (currentType == null) {
        return;
      }
      String text = current.toString().trim();
      current.setLength(0);
      if (!text.isEmpty()) {
        blocks.add(new Block(currentType, currentLevel, text));
      }
      currentType = null;
      currentLevel = 0;
      // 块间补换行,保证兜底纯文本的段落可读性
      if (raw.length() > 0 && raw.charAt(raw.length() - 1) != '\n') {
        raw.append('\n');
      }
    }
  }
}
