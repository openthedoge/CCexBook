package com.ziruoling.quiz.core;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.InputStream;

/** SAX 解析工具（禁用外部实体，安全解析） */
final class Xml {

    private Xml() {}

    static SAXParserFactory factory() {
        SAXParserFactory f = SAXParserFactory.newInstance();
        f.setNamespaceAware(true);
        try {
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (Exception ignored) {
            // 部分实现不支持时忽略
        }
        return f;
    }

    static void parse(InputStream in, DefaultHandler handler) throws IOException {
        try {
            SAXParser p = factory().newSAXParser();
            p.parse(new InputSource(in), handler);
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("文档格式解析失败：" + e.getMessage(), e);
        }
    }
}
